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

package com.crealytics.spark.v2.excel

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import org.apache.spark.sql._
import org.apache.spark.sql.functions.{col, max}
import org.scalatest.wordspec.AnyWordSpec

class MaxNumRowsSuite extends AnyWordSpec with DataFrameSuiteBase with LocalFileTestingUtilities {

  "excel v2 and maxNumRows" can {

    s"read with maxNumRows=200" in {

      val dfExcel = spark.read
        .format("excel")
        // .format("com.crealytics.spark.excel")
        .option("path", "src/test/resources/v2readwritetest/large_excel/largefile-wide-single-sheet.xlsx")
        .option("header", value = false)
        // .option("dataAddress", "'Sheet1'!B7:M16")
        .option("maxRowsInMemory", "200")
        .option("inferSchema", false)
        .load()

      assert(dfExcel.count() == 2241)

      // val maxId = dfExcel.select(max("seq")).first().getInt(0)
      // assert(maxId == 60000)

    }

  }
}
