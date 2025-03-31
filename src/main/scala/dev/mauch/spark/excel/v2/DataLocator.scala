/*
 * Copyright 2022 Martin Mauch (@nightscape)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mauch.spark.excel.v2

import org.apache.poi.ss.usermodel.Row.MissingCellPolicy
import org.apache.poi.ss.usermodel.{Cell, Sheet, Workbook}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.spark.internal.Logging

import scala.jdk.CollectionConverters._
import scala.util.Try

/** For handling Excel data address and read data from there */
trait DataLocator {

  /** Get cell-row itertor for given workbook with parsed address from option
    *
    * @param workbook
    *   to be create iterator for
    * @return
    *   cell-row iterator
    */
  def readFrom(workbook: Workbook): Iterator[Vector[Cell]]

  def actualReadFromSheet(options: ExcelOptions, sheet: Sheet, rowInd: Range, colInd: Range): Iterator[Vector[Cell]] = {
    if (options.keepUndefinedRows) {
      rowInd.iterator.map(rid => {
        val r = sheet.getRow(rid)
        if (r == null) { Vector.empty }
        else {
          colInd
            .filter(_ < r.getLastCellNum)
            .map(r.getCell(_, MissingCellPolicy.CREATE_NULL_AS_BLANK))
            .toVector
        }
      })

    } else {
      sheet.iterator.asScala
        .filter(r => rowInd.contains(r.getRowNum))
        .map(r =>
          colInd
            .filter(_ < r.getLastCellNum)
            .map(r.getCell(_, MissingCellPolicy.CREATE_NULL_AS_BLANK))
            .toVector
        )
    }
  }
}

object DataLocator {
  def apply(options: ExcelOptions): DataLocator = {
    val tableAddressRegex = """(.*)\[(.*)\]""".r
    options.dataAddress match {
      case tableAddressRegex(tableName, _) => new TableDataLocator(options, tableName)
      case _ => new CellRangeAddressDataLocator(options)
    }
  }
}

/** Locating the data in Excel Range Address
  *
  * @param options
  *   user specified excel option
  */
class CellRangeAddressDataLocator(val options: ExcelOptions) extends DataLocator with Logging {

  // in case of keepUndefinedRows==true DataLocator.actualReadFromSheet utilizes  Sheet.getRow(rowNum), which is not implemented
  // in streaming reader. So we have to make sure that keepUndefinedRows==true is not combined with streaming reader
  require(
    if (options.keepUndefinedRows && options.maxRowsInMemory.nonEmpty) false
    else true,
    "maxRowsInMemory (i.e. the streaming reader) cannot be combined with keepUndefinedRows==true"
  )

  override def readFrom(workbook: Workbook): Iterator[Vector[Cell]] = {

    val sheets = findSheets(workbook, sheetName)
    val sheetIterators = sheets.zipWithIndex.map(it => {
      val rowInd =
        if (it._2 == 0 || !options.header)
          rowIndices(it._1) // on first sheet we read header row, on all others we skip it *if* they contain header
        else
          rowIndices(it._1).drop(1) // skip header row for all but the first sheet
      val colInd = columnIndices()
      logInfo(s"Reading data from sheet ${it._1.getSheetName} with row indices $rowInd and column indices $colInd")
      actualReadFromSheet(options, it._1, rowInd, colInd)
    })
    sheetIterators.foldLeft(sheetIterators.head)(_ ++ _) // concatenate all iterators
  }

  private def findSheets(workbook: Workbook, name: Option[String]): Seq[Sheet] = {
    name match {
      case Some(n) if options.sheetNameIsRegex =>
        val sheetNames = Range(0, workbook.getNumberOfSheets).map(index => workbook.getSheetName(index)).toList
        val matchingSheets = sheetNames.filter(sn => sn.matches(n))
        if (matchingSheets.isEmpty) {
          throw new IllegalArgumentException(s"No sheet found matching regex $n")
        }
        matchingSheets.map(workbook.getSheet)
      case _ => Seq(findSheet(workbook, name))
    }
  }

  private def findSheet(workbook: Workbook, name: Option[String]): Sheet = {
    val sheet = name
      .map(sn =>
        // first we try to interpret the sheet name as string, then try to convert it to a number (issue #942)
        Option(workbook.getSheet(sn))
          .orElse(Try(Option(workbook.getSheetAt(sn.toInt))).toOption.flatten)
          .getOrElse(throw new IllegalArgumentException(s"Unknown sheet $sn"))
      )
      .getOrElse(workbook.getSheetAt(0))
    sheet
  }

  private val dataAddress = ExcelHelper(options).parsedRangeAddress()

  private val sheetName = Option(dataAddress.getFirstCell.getSheetName)

  private def columnIndices(): Range =
    dataAddress.getFirstCell.getCol.toInt to dataAddress.getLastCell.getCol.toInt

  private def rowIndices(sheet: Sheet): Range = {
    /* issue #944: if the sheet has a faulty dimension tag the streaming reader will not know the correct value for
        sheet.getLastRowNum(). Therefore we only use the data address to determine the row range.

        For non-streaming reader the sheet.getLastRowNum() returns a valid value, so we can use it. We need to do
        that to support keepUndefinedRows==true which iterates over the existing rows in the sheet.
     */
    options.maxRowsInMemory match {
      case Some(_) =>
        // using streaming reader
        dataAddress.getFirstCell.getRow to dataAddress.getLastCell.getRow
      case None =>
        // using default poi reader (load all data into memory)
        math.max(dataAddress.getFirstCell.getRow, sheet.getFirstRowNum) to
          math.min(dataAddress.getLastCell.getRow, sheet.getLastRowNum)
    }

  }
}

/** Locating the data in Excel Table
  *
  * @param options
  *   user specified excel option
  */
class TableDataLocator(val options: ExcelOptions, tableName: String) extends DataLocator with Logging {
  override def readFrom(workbook: Workbook): Iterator[Vector[Cell]] = {
    workbook match {
      case xssfWorkbook: XSSFWorkbook => {
        val table = xssfWorkbook.getTable(tableName)
        val sheet = table.getXSSFSheet()
        val rowInd = (table.getStartRowIndex to table.getEndRowIndex)
        val colInd = (table.getStartColIndex to table.getEndColIndex)

        actualReadFromSheet(options, sheet, rowInd, colInd)
      }
      case _ => {
        logWarning("TableDataLocator only properly supports xlsx files read without maxRowsInMemory setting")
        List.empty.iterator
      }
    }
  }
}
