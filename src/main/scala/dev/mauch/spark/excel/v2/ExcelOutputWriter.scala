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
import org.apache.hadoop.mapreduce.TaskAttemptContext
import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.execution.datasources.OutputWriter
import org.apache.spark.sql.types.StructType

class ExcelOutputWriter(_path: String, dataSchema: StructType, context: TaskAttemptContext, options: ExcelOptions)
    extends OutputWriter
    with Logging {

  @ifdef("exposedPathProperty")
  val path: String = _path

  private val gen = new ExcelGenerator(_path, dataSchema, context.getConfiguration, options)
  if (options.header) { gen.writeHeaders() }

  override def write(row: InternalRow): Unit = gen.write(row)

  override def close(): Unit = gen.close()
}
