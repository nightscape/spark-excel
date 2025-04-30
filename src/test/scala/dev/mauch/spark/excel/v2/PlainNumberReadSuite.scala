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
import org.apache.spark.sql.Row
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite

import java.util
import scala.jdk.CollectionConverters._

object PlainNumberReadSuite {
  val expectedInferredSchema = StructType(
    List(
      StructField("only_numbers", DoubleType, true),
      StructField("numbers_and_text", StringType, true),
      StructField("date_formula", StringType, true)
    )
  )

  /** Breaking change with V1: Keep row will all empty cells More detail:
    * https://github.dev/mauch/spark-excel/issues/285
    */
  val expectedPlainDataInferSchema: util.List[Row] = List(
    Row(12345678901d, "12345678901-123", "12/1/20"),
    Row(123456789012d, "123456789012", "0.01"),
    Row(-0.12345678901, "0.05", "0h 14m"),
    Row(null, null, null),
    Row(Double.NaN, "abc.def", null)
  ).asJava

  /** Breaking change with V1: More data after row with all empty cells
    */
  val expectedExcelDataInferSchema: util.List[Row] = List(
    Row(1.2345678901e10, "12345678901-123", "12/1/20"),
    Row(1.23456789012e11, "1.23457E+11", "0.01"), // values are displayed in scientific notation and rounded up
    Row(-0.12345678901, "0.05", "0h 14m"),
    Row(null, null, null),
    Row(Double.NaN, "abc.def", null)
  ).asJava

  val expectedNonInferredSchema = StructType(
    List(
      StructField("only_numbers", StringType, true),
      StructField("numbers_and_text", StringType, true),
      StructField("date_formula", StringType, true)
    )
  )

  val expectedPlainDataNonInferSchema: util.List[Row] = List(
    Row("12345678901", "12345678901-123", "12/1/20"),
    Row("123456789012", "123456789012", "0.01"),
    Row("-0.12345678901", "0.05", "0h 14m"),
    Row(null, null, null),
    Row("#DIV/0!", "abc.def", null)
  ).asJava

  val expectedExcelDataNonInferSchema: util.List[Row] = List(
    Row("12345678901", "12345678901-123", "12/1/20"),
    Row("1.23457E+11", "1.23457E+11", "0.01"), // values are displayed in scientific notation and rounded up
    Row("-0.123456789", "0.05", "0h 14m"), // values are rounded up
    Row(null, null, null),
    Row("#DIV/0!", "abc.def", null)
  ).asJava

  // Predefined data for Issue #747 tests
  val issue747Schema = StructType(
    List(StructField("only_numbers", StringType, true), StructField("numbers_and_text", StringType, true))
  )

  val issue747Data: util.List[Row] = List(Row("9024523", "902"), Row("1020001", "102"), Row("9764342", "L906")).asJava
}

class PlainNumberReadSuite extends AnyFunSuite with DataFrameSuiteBase with ExcelTestingUtilities {
  import PlainNumberReadSuite._

  test("plain number format when usePlainNumberFormat=true and inferSchema=true") {
    val df = readFromResources(
      spark,
      path = "plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> true, "inferSchema" -> true)
    )
    val expected = spark.createDataFrame(expectedPlainDataInferSchema, expectedInferredSchema)
    assertDataFrameEquals(expected, df)
  }

  test("plain number format when usePlainNumberFormat=true, inferSchema=true and maxRowsInMemory") {
    val df = readFromResources(
      spark,
      path = "plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> true, "inferSchema" -> true, "maxRowsInMemory" -> 1)
    )
    val expected = spark.createDataFrame(expectedPlainDataInferSchema, expectedInferredSchema)
    assertDataFrameEquals(expected, df)
  }

  test("plain number format when usePlainNumberFormat=true and inferSchema=false") {
    val df = readFromResources(
      spark,
      path = "plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> true, "inferSchema" -> false)
    )
    val expected = spark.createDataFrame(expectedPlainDataNonInferSchema, expectedNonInferredSchema)
    assertDataFrameEquals(expected, df)
  }

  test("excel general number format when usePlainNumberFormat=false and inferSchema=true") {
    val df = readFromResources(
      spark,
      path = "plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> false, "inferSchema" -> true)
    )
    val expected = spark.createDataFrame(expectedExcelDataInferSchema, expectedInferredSchema)
    assertDataFrameEquals(expected, df)
  }

  test("excel general number format when usePlainNumberFormat=false and inferSchema=false") {
    val df = readFromResources(
      spark,
      path = "plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> false, "inferSchema" -> false)
    )
    val expected = spark.createDataFrame(expectedExcelDataNonInferSchema, expectedNonInferredSchema)
    assertDataFrameEquals(expected, df)
  }

  // Tests for Issue #747 - Integer columns should not have decimal points with usePlainNumberFormat=true
  test("integer values should not have decimal points when usePlainNumberFormat=true (Issue #747)") {
    // Read with plain number format
    val df = readFromResources(
      spark,
      path = "Issue_747_plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> true, "inferSchema" -> false)
    )

    // Create expected DataFrame using predefined data
    val expected = spark.createDataFrame(issue747Data, issue747Schema)

    // Verify against expected
    assertDataFrameEquals(expected, df)
  }

  test("integer values should not have decimal points when usePlainNumberFormat=false (Issue #747)") {
    // Read with excel default format
    val df = readFromResources(
      spark,
      path = "Issue_747_plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> false, "inferSchema" -> false)
    )

    // Create expected DataFrame using predefined data
    val expected = spark.createDataFrame(issue747Data, issue747Schema)

    // Verify against expected
    assertDataFrameEquals(expected, df)
  }

  test("integer values should be consistent with both usePlainNumberFormat settings (Issue #747)") {
    // Read with plain number format
    val dfWithPlain = readFromResources(
      spark,
      path = "Issue_747_plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> true, "inferSchema" -> false)
    )

    // Read with excel default format
    val dfWithoutPlain = readFromResources(
      spark,
      path = "Issue_747_plain_number.xlsx",
      options = Map("usePlainNumberFormat" -> false, "inferSchema" -> false)
    )

    // Verify both dataframes should be equal
    assertDataFrameEquals(dfWithPlain, dfWithoutPlain)
  }
}
