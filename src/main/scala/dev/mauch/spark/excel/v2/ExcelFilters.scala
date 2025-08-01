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

import com.eed3si9n.ifdef.ifdef
import org.apache.spark.sql.sources
import org.apache.spark.sql.types.StructType

/** Wrapping the API change between spark 3.0 vs 3.1 */
@ifdef("filterHandling:CsvBased")
class ExcelFilters(filters: Seq[sources.Filter], requiredSchema: StructType)
    extends org.apache.spark.sql.catalyst.csv.CSVFilters(filters, requiredSchema) {
}

@ifdef("filterHandling:Structured")
class ExcelFilters(filters: Seq[sources.Filter], requiredSchema: StructType)
    extends org.apache.spark.sql.catalyst.OrderedFilters(filters, requiredSchema) {
}

object ExcelFilters {
  @ifdef("filterHandling:CsvBased")
  def pushedFilters(filters: Array[sources.Filter], schema: StructType): Array[sources.Filter] = {
    import org.apache.spark.sql.catalyst.csv.CSVFilters
    CSVFilters.pushedFilters(filters, schema)
  }

  @ifdef("filterHandling:Structured")
  def pushedFilters(filters: Array[sources.Filter], schema: StructType): Array[sources.Filter] = {
    import org.apache.spark.sql.catalyst.StructFilters
    StructFilters.pushedFilters(filters, schema)
  }
}
