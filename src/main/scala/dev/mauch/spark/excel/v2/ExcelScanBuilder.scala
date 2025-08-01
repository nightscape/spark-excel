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

import com.eed3si9n.ifdef.{ifdef, ifndef}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.read.Scan
import org.apache.spark.sql.execution.datasources.PartitioningAwareFileIndex
import org.apache.spark.sql.execution.datasources.v2.FileScanBuilder
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.util.CaseInsensitiveStringMap

@ifdef("catalystFilterPushdown")
case class ExcelScanBuilder(
  sparkSession: SparkSession,
  fileIndex: PartitioningAwareFileIndex,
  schema: StructType,
  dataSchema: StructType,
  options: CaseInsensitiveStringMap
) extends FileScanBuilder(sparkSession, fileIndex, dataSchema)
    with org.apache.spark.sql.internal.connector.SupportsPushDownCatalystFilters {

  override def build(): Scan = {
    ExcelScan(sparkSession, fileIndex, dataSchema, readDataSchema(), readPartitionSchema(), options, pushedDataFilters)
  }
}

@ifndef("catalystFilterPushdown")
case class ExcelScanBuilder(
  sparkSession: SparkSession,
  fileIndex: PartitioningAwareFileIndex,
  schema: StructType,
  dataSchema: StructType,
  options: CaseInsensitiveStringMap
) extends FileScanBuilder(sparkSession, fileIndex, dataSchema)
    with org.apache.spark.sql.connector.read.SupportsPushDownFilters {

  import org.apache.spark.sql.sources.Filter

  override def build(): Scan = {
    ExcelScan(sparkSession, fileIndex, dataSchema, readDataSchema(), readPartitionSchema(), options, pushedFilters())
  }

  private var _pushedFilters: Array[Filter] = Array.empty

  override def pushFilters(filters: Array[Filter]): Array[Filter] = {
    _pushedFilters = ExcelFilters.pushedFilters(filters, dataSchema)
    filters
  }

  override def pushedFilters(): Array[Filter] = _pushedFilters
}
