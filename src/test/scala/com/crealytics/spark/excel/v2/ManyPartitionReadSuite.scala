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

package com.crealytics.spark.excel.v2

import com.crealytics.spark.DataFrameSuiteBase
import org.apache.spark.sql._
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.types.IntegerType
import org.scalatest.wordspec.AnyWordSpec
import org.apache.spark.sql.types.{StructType, StructField, StringType}

class ManyPartitionReadSuite extends AnyWordSpec with DataFrameSuiteBase with LocalFileTestingUtilities {

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

  def createExpected(targetDir: String): DataFrame = {

    // Generate data programmatically
    val data = (1 to 19).flatMap { col1 =>
      // Each col1 value has multiple rows (around 10-11 rows each)
      val rowsPerPartition = if (col1 == 1) 8 else if (col1 == 2) 16 else 11
      (0 until rowsPerPartition).map { i =>
        val index = (col1 - 1) * 11 + i + 1234  // Starting from 1234 as in original data
        Row(
          Integer.valueOf(col1),  // Make it nullable Integer
          s"fubar_$index",
          s"bazbang_${index + 77000}",
          s"barfang_${index + 237708}",
          s"lorem_ipsum_$index"
        )
      }
    }

    // Define schema explicitly to match expected nullability
    val schema = StructType(Array(
      StructField("col1", IntegerType, nullable = true),
      StructField("col2", StringType, nullable = true),
      StructField("col3", StringType, nullable = true),
      StructField("col4", StringType, nullable = true),
      StructField("col5", StringType, nullable = true)
    ))

    val dfInput = spark.createDataFrame(spark.sparkContext.parallelize(data), schema)



    val dfFinal = dfInput.union(dfInput)

    val dfWriter = dfFinal.write
      .partitionBy("col1")
      .format("excel")
      .option("path", targetDir)
      .option("header", value = true)
      .mode(SaveMode.Append)

    dfWriter.save()
    dfWriter.save()

    val orderedSchemaColumns = dfInput.schema.fields.map(f => f.name).sorted

    dfFinal
      .union(dfFinal)
      .withColumn("col1", col("col1").cast(IntegerType))
      .select(orderedSchemaColumns.head, orderedSchemaColumns.tail.toIndexedSeq: _*)

  }

  for (run <- Range(0, 3)) {

    s"many partitions read (run=$run)" in withExistingCleanTempDir("v2") { targetDir =>
      assume(spark.sparkContext.version >= "3.0.1")
      val expectedDf = createExpected(targetDir)
      assertWrittenExcelData(expectedDf, targetDir)
    }
  }

}
