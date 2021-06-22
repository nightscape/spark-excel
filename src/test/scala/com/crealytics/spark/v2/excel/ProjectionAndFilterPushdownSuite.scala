/** Copyright 2016 - 2021 Martin Mauch (@nightscape)
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
package com.crealytics.spark.v2.excel

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import org.apache.spark.sql.Row
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.scalatest.FunSuite

import java.util
import scala.collection.JavaConverters._

/** Projection and Filter Pushdown test cases
  */
object ProjectionAndFilterPushdownSuite {

  /* No projection*/
  val expectedInferredSchema = StructType(List(
    StructField("Day", DoubleType, true),
    StructField("Month", DoubleType, true),
    StructField("Customer ID", StringType, true),
    StructField("Customer Name", StringType, true),
    StructField("Standard Package", DoubleType, true),
    StructField("Extra Option 1", DoubleType, true),
    StructField("Extra Option 2", DoubleType, true),
    StructField("Extra Option 3", DoubleType, true),
    StructField("Staff", StringType, true)
  ))

  val expectedDataInferSchema: util.List[Row] = List(
    Row(1.0d, 12.0d, "CA869", "Phạm Uyển Trinh", null, null, 2200.0d, null, "Ella Fitzgerald"),
    Row(1.0d, 12.0d, "CA870", "Nguyễn Liên Thảo", null, null, 2000.0d, 1350.0d, "Ella Fitzgerald"),
    Row(1.0d, 12.0d, "CA871", "Lê Thị Nga", 17000.0d, null, null, null, "Ella Fitzgerald"),
    Row(1.0d, 12.0d, "CA872", "Phan Tố Nga", null, null, 2000.0d, null, "Teresa Teng"),
    Row(1.0d, 12.0d, "CA873", "Nguyễn Thị Teresa Teng", null, null, 1200.0d, null, "Jesse Thomas")
  ).asJava

  /* Subset of columns, same order*/
  val expectedProjectionInferredSchema_01 = StructType(List(
    StructField("Day", DoubleType, true),
    StructField("Month", DoubleType, true),
    StructField("Customer ID", StringType, true),
    StructField("Customer Name", StringType, true),
    StructField("Staff", StringType, true)
  ))

  val expectedProjectionDataInferSchema_01: util.List[Row] = List(
    Row(1.0d, 12.0d, "CA869", "Phạm Uyển Trinh", "Ella Fitzgerald"),
    Row(1.0d, 12.0d, "CA870", "Nguyễn Liên Thảo", "Ella Fitzgerald"),
    Row(1.0d, 12.0d, "CA871", "Lê Thị Nga", "Ella Fitzgerald"),
    Row(1.0d, 12.0d, "CA872", "Phan Tố Nga", "Teresa Teng"),
    Row(1.0d, 12.0d, "CA873", "Nguyễn Thị Teresa Teng", "Jesse Thomas")
  ).asJava

  /* Subset of columns, out of order*/
  val expectedProjectionInferredSchema_02 = StructType(List(
    StructField("Staff", StringType, true),
    StructField("Month", DoubleType, true),
    StructField("Day", DoubleType, true),
    StructField("Customer ID", StringType, true),
    StructField("Customer Name", StringType, true),
    StructField("Standard Package", DoubleType, true)
  ))

  val expectedProjectionDataInferSchema_02: util.List[Row] = List(
    Row("Ella Fitzgerald", 12.0d, 1.0d, "CA869", "Phạm Uyển Trinh", null),
    Row("Ella Fitzgerald", 12.0d, 1.0d, "CA870", "Nguyễn Liên Thảo", null),
    Row("Ella Fitzgerald", 12.0d, 1.0d, "CA871", "Lê Thị Nga", 17000.0d),
    Row("Teresa Teng", 12.0d, 1.0d, "CA872", "Phan Tố Nga", null),
    Row("Jesse Thomas", 12.0d, 1.0d, "CA873", "Nguyễn Thị Teresa Teng", null)
  ).asJava

  /* Filtering, with same schemas as in projection*/
  val expectedDataFilterInferSchema_01: util.List[Row] = List(
    Row(4.0d, 12.0d, "CA883", "Phạm Thanh Mai", 7000.0d, 4000.0d, null, null, "Jesse Thomas"),
    Row(4.0d, 12.0d, "CA884", "Hoàng Ngọc Hà", 8000.0d, null, null, null, "Teresa Teng"),
    Row(4.0d, 12.0d, "CA885", "Lê Minh Ngọc", 15000.0d, 12000.0d, null, null, "Teresa Teng")
  ).asJava

  val expectedDataFilterInferSchema_02: util.List[Row] = List(
    Row(4.0d, 12.0d, "CA883", "Phạm Thanh Mai", 7000.0d, 4000.0d, null, null, "Jesse Thomas"),
    Row(4.0d, 12.0d, "CA885", "Lê Minh Ngọc", 15000.0d, 12000.0d, null, null, "Teresa Teng")
  ).asJava
}

class ProjectionAndFilterPushdownSuite extends FunSuite with DataFrameSuiteBase {
  import ProjectionAndFilterPushdownSuite._

  private val dataRoot = getClass.getResource("/spreadsheets").getPath

  def readFromResources(path: String, inferSchema: Boolean): DataFrame = spark.read.format("excel")
    .option("header", true).option("inferSchema", inferSchema).load(path)

  test("no projection check first 5 rows with inferSchema=true") {
    val df = readFromResources(s"$dataRoot/ca_dataset/2019/Quarter=4/ca_12.xlsx", true).limit(5)
    val expected = spark.createDataFrame(expectedDataInferSchema, expectedInferredSchema)

    assertDataFrameEquals(expected, df)
  }

  test("projection with subset of columns, same order and inferSchema=true") {
    val df = readFromResources(s"$dataRoot/ca_dataset/2019/Quarter=4/ca_12.xlsx", true)
      .select("Day", "Month", "Customer ID", "Customer Name", "Staff").limit(5)
    val expected = spark
      .createDataFrame(expectedProjectionDataInferSchema_01, expectedProjectionInferredSchema_01)

    assertDataFrameEquals(expected, df)
  }

  test("projection with subset of columns, out of order and inferSchema=true") {
    val df = readFromResources(s"$dataRoot/ca_dataset/2019/Quarter=4/ca_12.xlsx", true)
      .select("Staff", "Month", "Day", "Customer ID", "Customer Name", "Standard Package").limit(5)
    val expected = spark
      .createDataFrame(expectedProjectionDataInferSchema_02, expectedProjectionInferredSchema_02)

    assertDataFrameEquals(expected, df)
  }

  test("filter with one column inferSchema=true") {
    val df = readFromResources(s"$dataRoot/ca_dataset/2019/Quarter=4/ca_12.xlsx", true)
      .filter("Day = 4")
    val expected = spark.createDataFrame(expectedDataFilterInferSchema_01, expectedInferredSchema)

    assertDataFrameEquals(expected, df)
  }

  test("filter with two columns inferSchema=true") {
    val df = readFromResources(s"$dataRoot/ca_dataset/2019/Quarter=4/ca_12.xlsx", true)
      .filter("Day = 4 and `Extra Option 1` is not null")
    val expected = spark.createDataFrame(expectedDataFilterInferSchema_02, expectedInferredSchema)

    assertDataFrameEquals(expected, df)
  }

  test("filter and count matched inferSchema=true") {
    val df = readFromResources(s"$dataRoot/ca_dataset/2019/Quarter=4/ca_12.xlsx", true)
      .filter("Staff = 'Teresa Teng'")

    assert(df.count() == 16)
  }
}
