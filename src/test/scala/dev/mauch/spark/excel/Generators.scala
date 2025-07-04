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

package dev.mauch.spark.excel

import org.scalacheck.Arbitrary.{arbBigDecimal => _, arbLong => _, arbString => _, _}
import org.scalacheck.ScalacheckShapeless._
import org.apache.poi.ss.util.CellReference
import org.apache.spark.sql.catalyst.ScalaReflection
import org.apache.spark.sql.types.StructType
import org.scalacheck.{Arbitrary, Gen}
import spoiwo.model.{Cell, CellRange, Row => SRow, Sheet, Table => STable, TableColumn}

import java.sql.{Date, Timestamp}
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId, ZoneOffset}
import scala.jdk.CollectionConverters._
import scala.collection.compat._

case class ExampleData(
  aBoolean: Boolean,
  aBooleanOption: Option[Boolean],
  aByte: Byte,
  aByteOption: Option[Byte],
  aShort: Short,
  aShortOption: Option[Short],
  anInt: Int,
  anIntOption: Option[Int],
  aLong: Long,
  aLongOption: Option[Long],
  aFloat: Float,
  aFloatOption: Option[Float],
  aDouble: Double,
  aDoubleOption: Option[Double],
  aBigDecimal: BigDecimal,
  aBigDecimalOption: Option[BigDecimal],
  aJavaBigDecimal: java.math.BigDecimal,
  aJavaBigDecimalOption: Option[java.math.BigDecimal],
  aString: String,
  aStringOption: Option[String],
  // Use Timestamp/Date types for Spark 4.0 compatibility - encoders handle java.sql types properly
  aTimestamp: java.sql.Timestamp,
  aTimestampOption: Option[java.sql.Timestamp],
  aDate: java.sql.Date,
  aDateOption: Option[java.sql.Date]
)

trait Generators {
  val exampleDataSchema = ScalaReflection.schemaFor[ExampleData].dataType.asInstanceOf[StructType]

  private val dstTransitionDays =
    ZoneId.systemDefault().getRules.getTransitions.asScala.map(_.getInstant.truncatedTo(ChronoUnit.DAYS))
  def isDstTransitionDay(instant: Instant): Boolean = dstTransitionDays.contains(instant.truncatedTo(ChronoUnit.DAYS))
  // Generate java.sql.Timestamp for both Spark 3.x and 4.0 compatibility
  val timestampGen: Gen[java.sql.Timestamp] = 
    Gen
      .chooseNum[Long](0L, (new java.util.Date).getTime + 1000000)
      .map(new java.sql.Timestamp(_))
      .filterNot(d => isDstTransitionDay(d.toInstant))

  // Generate java.sql.Date for both Spark 3.x and 4.0 compatibility
  val dateGen: Gen[java.sql.Date] = 
    Gen
      .chooseNum[Long](0L, (new java.util.Date).getTime + 1000000)
      .map(new java.sql.Date(_))
      .filterNot(d => isDstTransitionDay(d.toLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant))

  // Legacy Arbitrary instances for backward compatibility (not used in Spark 4.0)
  implicit val arbitraryDateFourDigits: Arbitrary[Date] = Arbitrary[java.sql.Date](
    Gen
      .chooseNum[Long](0L, (new java.util.Date).getTime + 1000000)
      .map(new java.sql.Date(_))
      // We get some weird DST problems when the chosen date is a DST transition
      .filterNot(d => isDstTransitionDay(d.toLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant))
  )

  implicit val arbitraryTimestamp: Arbitrary[Timestamp] = Arbitrary[java.sql.Timestamp](
    Gen
      .chooseNum[Long](0L, (new java.util.Date).getTime + 1000000)
      .map(new java.sql.Timestamp(_))
      // We get some weird DST problems when the chosen date is a DST transition
      .filterNot(d => isDstTransitionDay(d.toInstant))
  )

  implicit val arbitraryBigDecimal: Arbitrary[BigDecimal] =
    Arbitrary[BigDecimal](Gen.chooseNum[Double](-1.0e15, 1.0e15).map(BigDecimal.apply))

  implicit val arbitraryJavaBigDecimal: Arbitrary[java.math.BigDecimal] =
    Arbitrary[java.math.BigDecimal](arbitraryBigDecimal.arbitrary.map(_.bigDecimal))

  // Unfortunately we're losing some precision when parsing Longs
  // due to the fact that we have to read them as Doubles and then cast.
  // We're restricting our tests to Int-sized Longs in order not to fail
  // because of this issue.
  implicit val arbitraryLongWithLosslessDoubleConvertability: Arbitrary[Long] =
    Arbitrary[Long] {
      arbitrary[Int].map(_.toLong)
    }

  implicit val arbitraryStringWithoutUnicodeCharacters: Arbitrary[String] =
    Arbitrary[String](Gen.alphaNumStr)

  // Explicit generator for ExampleData to handle version-specific timestamp/date fields
  implicit val arbitraryExampleData: Arbitrary[ExampleData] = Arbitrary {
    for {
      aBoolean <- arbitrary[Boolean]
      aBooleanOption <- arbitrary[Option[Boolean]]
      aByte <- arbitrary[Byte]
      aByteOption <- arbitrary[Option[Byte]]
      aShort <- arbitrary[Short]
      aShortOption <- arbitrary[Option[Short]]
      anInt <- arbitrary[Int]
      anIntOption <- arbitrary[Option[Int]]
      aLong <- arbitraryLongWithLosslessDoubleConvertability.arbitrary
      aLongOption <- arbitrary[Option[Long]]
      aFloat <- arbitrary[Float]
      aFloatOption <- arbitrary[Option[Float]]
      aDouble <- arbitrary[Double]
      aDoubleOption <- arbitrary[Option[Double]]
      aBigDecimal <- arbitraryBigDecimal.arbitrary
      aBigDecimalOption <- arbitrary[Option[BigDecimal]]
      aJavaBigDecimal <- arbitraryJavaBigDecimal.arbitrary
      aJavaBigDecimalOption <- arbitrary[Option[java.math.BigDecimal]]
      aString <- arbitraryStringWithoutUnicodeCharacters.arbitrary
      aStringOption <- arbitrary[Option[String]]
      aTimestamp <- timestampGen
      aTimestampOption <- Gen.option(timestampGen)
      aDate <- dateGen
      aDateOption <- Gen.option(dateGen)
    } yield ExampleData(
      aBoolean, aBooleanOption, aByte, aByteOption, aShort, aShortOption,
      anInt, anIntOption, aLong, aLongOption, aFloat, aFloatOption,
      aDouble, aDoubleOption, aBigDecimal, aBigDecimalOption,
      aJavaBigDecimal, aJavaBigDecimalOption, aString, aStringOption,
      aTimestamp, aTimestampOption, aDate, aDateOption
    )
  }

  val rowGen: Gen[ExampleData] = arbitrary[ExampleData].map(d => if (d.aString.isEmpty) d.copy(aString = null) else d)
  val rowsGen: Gen[List[ExampleData]] = Gen.listOf(rowGen)
  val cellAddressGen = for {
    row <- Gen.choose(0, 100)
    col <- Gen.choose(0, 100)
  } yield new CellReference(row, col)

  val sheetName = "test sheet"

  def sheetGenerator(withHeader: Gen[Boolean], numCols: Gen[Int] = Gen.choose(0, 200)): Gen[Sheet] =
    for {
      numRows <- Gen.choose(0, 200)
      numCol <- numCols
      hasHeader <- withHeader
    } yield {
      val header = if (hasHeader) Some(SRow((0 until numCol).map(c => Cell(s"col_$c", index = c)))) else None
      Sheet(
        name = sheetName,
        rows = header.toList ++ (0 until numRows)
          .map(r => SRow((0 until numCol).map(c => Cell(s"$r,$c", index = c)), index = r))
          .to(List)
      )
    }

  val sheetGen = sheetGenerator(withHeader = Gen.const(false))

  val tableName = "TestTable"

  val sheetWithTableGen = for {
    sheet <- sheetGen
    startCellAddress <- cellAddressGen
    width <- Gen.choose(0, 50)
    height <- Gen.choose(0, 200)
  } yield {
    val columns =
      (startCellAddress.getCol.toInt to startCellAddress.getCol.toInt + width)
        .map(c => TableColumn(s"col_$c", c.toLong))
        .toList
    val columnsByIndex = columns.map(c => c.id -> Cell[String](value = c.name, index = c.id.toInt)).toMap
    sheet
      .withRows(sheet.rows.map {
        case r if r.index.contains(startCellAddress.getRow) =>
          val cellIndices = (r.cells.map(_.index.get) ++ columns.map(_.id.toInt)).toList.distinct.sorted
          r.withCells(cellIndices.map { ci =>
            columnsByIndex
              .getOrElse(ci.toLong, r.cells.find(_.index.get == ci).get)
          })
        case r => r
      })
      .withTables(
        STable(
          columns = columns,
          cellRange = CellRange(
            rowRange = (startCellAddress.getRow, startCellAddress.getRow + height),
            columnRange = (startCellAddress.getCol.toInt, startCellAddress.getCol.toInt + width)
          ),
          name = tableName,
          displayName = tableName
        )
      )
  }

  val dataAndLocationGen = for {
    rows <- rowsGen
    startAddress <- cellAddressGen
  } yield (
    rows,
    startAddress,
    new CellReference(startAddress.getRow + rows.size, startAddress.getCol + exampleDataSchema.size - 1)
  )
}
