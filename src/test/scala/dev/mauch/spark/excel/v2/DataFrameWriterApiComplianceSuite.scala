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

import dev.mauch.spark.DataFrameSuiteBase
import org.apache.spark.sql._
import org.scalatest.wordspec.AnyWordSpec

class DataFrameWriterApiComplianceSuite extends AnyWordSpec with DataFrameSuiteBase with LocalFileTestingUtilities {

  private def simpleDf = {
    val data = Seq(("foo", "bar", "1"), ("baz", "bang", "2"))
    spark.createDataFrame(data).toDF("col1", "col2", "col3")
  }

  /** Checks that the excel data files in given folder equal the provided dataframe */
  private def assertWrittenExcelData(expectedDf: DataFrame, folder: String): Unit = {
    val actualDf = spark.read
      .format("excel")
      .option("path", folder)
      .load()

    /* assertDataFrameNoOrderEquals is sensitive to order of columns, so we
      order both dataframes in the same way
     */
    val orderedSchemaColumns = expectedDf.schema.fields.map(f => f.name).sorted

    assertDataFrameNoOrderEquals(
      expectedDf.select(orderedSchemaColumns.head, orderedSchemaColumns.tail.toIndexedSeq: _*),
      actualDf.select(orderedSchemaColumns.head, orderedSchemaColumns.tail.toIndexedSeq: _*)
    )

  }
  "excel v2 complies to DataFrameWriter SaveMode and Partitioning behavior" can {

    val writeModes = Seq(SaveMode.Overwrite, SaveMode.Append)

    for (writeMode <- writeModes) {
      s"write a dataframe to xlsx with ${writeMode.toString}" in withExistingCleanTempDir("v2") { targetDir =>
        // create a df from csv then write as xlsx
        val df = simpleDf

        df.write
          .format("excel")
          .option("path", targetDir)
          .option("header", value = true)
          .mode(writeMode)
          .save()

        val listOfFiles = getListOfFilesFilteredByExtension(targetDir, "xlsx")
        assert(listOfFiles.nonEmpty, s"expected at least one excel file")

        // is the result really the same?
        assertWrittenExcelData(df, targetDir)

      }
      s"write a dataframe to xlsx with ${writeMode.toString} (partitioned)" in withExistingCleanTempDir("v2") {
        targetDir =>
          assume(spark.sparkContext.version >= "3.0.1")
          // create a df from csv then write as xlsx
          val df = simpleDf

          df.write
            .partitionBy("col1")
            .format("excel")
            .option("path", targetDir)
            .option("header", value = true)
            .mode(writeMode)
            .save()

          // some file based checks
          val listOfFolders = getListOfFolders(targetDir)
          assert(listOfFolders.length == 2, s"expected two folders because there are two partitions")
          for (folder <- listOfFolders) {
            assert(folder.getName.startsWith("col1="), s"expected partition folders and those must start with col1=")
            val listOfFiles = getListOfFilesFilteredByExtension(folder.getAbsolutePath, "xlsx")
            assert(listOfFiles.nonEmpty, s"expected at least one xlsx per folder but got $listOfFiles")
          }

          // is the result really the same?
          assertWrittenExcelData(df, targetDir)

      }
    }

    for (isPartitioned <- Seq(false, true)) {
      s"multiple appends to folder (partitioned == $isPartitioned)" in withExistingCleanTempDir("v2") { targetDir =>
        if (isPartitioned) {
          assume(spark.sparkContext.version >= "3.0.1")
        }

        val df = simpleDf

        val dfWriter = if (isPartitioned) df.write else df.write.partitionBy("col1")

        dfWriter
          .format("excel")
          .option("path", targetDir)
          .option("header", value = true)
          .mode(SaveMode.Append)
          .save()
        dfWriter
          .format("excel")
          .option("path", targetDir)
          .option("header", value = true)
          .mode(SaveMode.Append)
          .save()

        val orderedSchemaColumns = df.schema.fields.map(f => f.name).sorted
        val expectedDf =
          df.union(df).select(orderedSchemaColumns.head, orderedSchemaColumns.tail.toIndexedSeq: _*)

        assertWrittenExcelData(expectedDf, targetDir)
      }
    }
  }
}
