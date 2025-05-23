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

import java.util

import dev.mauch.spark.DataFrameSuiteBase
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.sql.types._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

object PlainNumberReadSuite {
  val expectedInferredSchema = StructType(
    List(
      StructField("only_numbers", DoubleType, true),
      StructField("numbers_and_text", StringType, true),
      StructField("date_formula", StringType, true)
    )
  )

  val expectedPlainDataInferSchema: util.List[Row] = List(
    Row(12345678901d, "12345678901-123", "12/1/20"),
    Row(123456789012d, "123456789012", "0.01"),
    Row(-0.12345678901, "0.05", "0h 14m"),
    Row(null, null, null),
    Row(null, "abc.def", null)
  ).asJava

  val expectedExcelDataInferSchema: util.List[Row] = List(
    Row(1.2345678901e10, "12345678901-123", "12/1/20"),
    Row(1.23456789012e11, "1.23457E+11", "0.01"), // values are displayed in scientific notation and rounded up
    Row(-0.12345678901, "0.05", "0h 14m"),
    Row(null, null, null),
    Row(null, "abc.def", null)
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
    Row(null, "abc.def", null)
  ).asJava

  val expectedExcelDataNonInferSchema: util.List[Row] = List(
    Row("12345678901", "12345678901-123", "12/1/20"),
    Row("1.23457E+11", "1.23457E+11", "0.01"), // values are displayed in scientific notation and rounded up
    Row("-0.123456789", "0.05", "0h 14m"), // values are rounded up
    Row(null, null, null),
    Row(null, "abc.def", null)
  ).asJava

  // Predefined data for Issue #747 tests
  val issue747Schema = StructType(
    List(StructField("only_numbers", StringType, true), StructField("numbers_and_text", StringType, true))
  )

  val issue747Data: util.List[Row] = List(Row("9024523", "902"), Row("1020001", "102"), Row("9764342", "L906")).asJava
}

class PlainNumberReadSuite extends AnyFunSpec with DataFrameSuiteBase with Matchers {
  import PlainNumberReadSuite._

  def readFromResources(path: String, usePlainNumberFormat: Boolean, inferSchema: Boolean): DataFrame = {
    val url = getClass.getResource(path)
    spark.read.excel(usePlainNumberFormat = usePlainNumberFormat, inferSchema = inferSchema).load(url.getPath)
  }

  describe("spark-excel") {
    it("should read numbers in plain number format when usePlainNumberFormat=true and inferSchema=true") {
      val df = readFromResources("/spreadsheets/plain_number.xlsx", true, true)
      val expected = spark.createDataFrame(expectedPlainDataInferSchema, expectedInferredSchema)
      assertDataFrameEquals(expected, df)
    }

    it("should read numbers in plain number format when usePlainNumberFormat=true and inferSchema=false") {
      val df = readFromResources("/spreadsheets/plain_number.xlsx", true, false)
      val expected = spark.createDataFrame(expectedPlainDataNonInferSchema, expectedNonInferredSchema)
      assertDataFrameEquals(expected, df)
    }

    it("should read numbers in excel general number format when usePlainNumberFormat=false and inferSchema=true") {
      val df = readFromResources("/spreadsheets/plain_number.xlsx", false, true)
      val expected = spark.createDataFrame(expectedExcelDataInferSchema, expectedInferredSchema)
      assertDataFrameEquals(expected, df)
    }

    it("should read numbers in excel general number format when usePlainNumberFormat=false and inferSchema=false") {
      val df = readFromResources("/spreadsheets/plain_number.xlsx", false, false)
      val expected = spark.createDataFrame(expectedExcelDataNonInferSchema, expectedNonInferredSchema)
      assertDataFrameEquals(expected, df)
    }

    // Tests for Issue #747 - Integer columns should not have decimal points with usePlainNumberFormat=true
    it("should not add decimal points to integer values when usePlainNumberFormat=true (Issue #747)") {
      // Read with plain number format
      val df = readFromResources("/spreadsheets/Issue_747_plain_number.xlsx", true, false)

      // Create expected DataFrame using predefined data
      val expected = spark.createDataFrame(issue747Data, issue747Schema)

      // Verify against expected
      assertDataFrameEquals(expected, df)
    }

    it("should not add decimal points to integer values when usePlainNumberFormat=false (Issue #747)") {
      // Read with excel default format
      val df = readFromResources("/spreadsheets/Issue_747_plain_number.xlsx", false, false)

      // Create expected DataFrame using predefined data
      val expected = spark.createDataFrame(issue747Data, issue747Schema)

      // Verify against expected
      assertDataFrameEquals(expected, df)
    }

    it("should properly handle integer values with both usePlainNumberFormat settings (Issue #747)") {
      // Read with plain number format
      val dfWithPlain = readFromResources("/spreadsheets/Issue_747_plain_number.xlsx", true, false)

      // Read with excel default format
      val dfWithoutPlain = readFromResources("/spreadsheets/Issue_747_plain_number.xlsx", false, false)

      // Verify both dataframes should be equal
      assertDataFrameEquals(dfWithPlain, dfWithoutPlain)
    }
  }
}
