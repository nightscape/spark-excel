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

import dev.mauch.spark.DataFrameSuiteBase
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.time.{Instant, LocalDate, ZoneId}
import java.time.format.DateTimeFormatter
import java.util
import scala.jdk.CollectionConverters._

/** Regression test for the v1 (`dev.mauch.spark.excel`) write path: `AreaDataLocator.toCell` must be able to handle
  * `java.time.Instant`/`java.time.LocalDate` values, which is what Spark materializes for `TimestampType`/`DateType`
  * columns when `spark.sql.datetime.java8API.enabled=true`, instead of `java.sql.Timestamp`/`java.sql.Date`.
  *
  * Mirrors `dev.mauch.spark.excel.v2.WriteAndReadSuite`'s "write then read java.time.Instant and java.time.LocalDate"
  * test, which already passes for the v2 (`excel`) format since that path works on Catalyst's `InternalRow` and is
  * unaffected by the java8API flag.
  */
class WriteAndReadSuite extends AnyFunSuite with DataFrameSuiteBase {

  private val DATETIME_JAVA8API_ENABLED = "spark.sql.datetime.java8API.enabled"

  private val schema = StructType(
    List(
      StructField("Id", IntegerType, nullable = true),
      StructField("Date", DateType, nullable = true),
      StructField("Timestamp", TimestampType, nullable = true)
    )
  )

  private val rows: List[(Int, String, String)] = List(
    (1, "2021-10-01", "2021-10-01 01:23:45"),
    (2, "2021-11-01", "2021-11-01 11:23:45"),
    (3, "2022-10-11", "2022-10-11 16:23:05")
  )

  test("write then read java.sql.Date and java.sql.Timestamp (v1)") {
    val previousConfigValue = spark.conf.getOption(DATETIME_JAVA8API_ENABLED)
    try {
      spark.conf.set(DATETIME_JAVA8API_ENABLED, false)
      val data: util.List[Row] = rows
        .map { case (id, d, ts) => Row(id, java.sql.Date.valueOf(d), java.sql.Timestamp.valueOf(ts)) }
        .asJava
      val dfSource = spark.createDataFrame(data, schema).sort("Id")

      val fileName = File.createTempFile("spark_excel_test_", ".xlsx").getAbsolutePath
      dfSource.write
        .format("dev.mauch.spark.excel")
        .option("header", "true")
        .mode(SaveMode.Overwrite)
        .save(fileName)

      val dfRead = spark.read
        .format("dev.mauch.spark.excel")
        .option("header", "true")
        .schema(schema)
        .load(fileName)
        .sort("Id")

      assertDataFrameEquals(dfSource, dfRead)
    } finally {
      previousConfigValue match {
        case Some(value) => spark.conf.set(DATETIME_JAVA8API_ENABLED, value)
        case None => spark.conf.unset(DATETIME_JAVA8API_ENABLED)
      }
    }
  }

  test("write then read java.time.Instant and java.time.LocalDate (v1)") {
    if (spark.version.startsWith("2.")) {
      cancel(DATETIME_JAVA8API_ENABLED + " didn't exist before spark 3.0. Nothing to test!")
    }
    val previousConfigValue = spark.conf.getOption(DATETIME_JAVA8API_ENABLED)
    try {
      spark.conf.set(DATETIME_JAVA8API_ENABLED, true)
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault)
      val data: util.List[Row] = rows
        .map { case (id, d, ts) => Row(id, LocalDate.parse(d), Instant.from(formatter.parse(ts))) }
        .asJava
      val dfSource = spark.createDataFrame(data, schema).sort("Id")

      val fileName = File.createTempFile("spark_excel_test_", ".xlsx").getAbsolutePath
      dfSource.write
        .format("dev.mauch.spark.excel")
        .option("header", "true")
        .mode(SaveMode.Overwrite)
        .save(fileName)

      val dfRead = spark.read
        .format("dev.mauch.spark.excel")
        .option("header", "true")
        .schema(schema)
        .load(fileName)
        .sort("Id")

      assertDataFrameEquals(dfSource, dfRead)
    } finally {
      previousConfigValue match {
        case Some(value) => spark.conf.set(DATETIME_JAVA8API_ENABLED, value)
        case None => spark.conf.unset(DATETIME_JAVA8API_ENABLED)
      }
    }
  }
}
