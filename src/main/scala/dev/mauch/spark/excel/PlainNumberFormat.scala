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

import java.math.BigDecimal
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition

/** A format that formats a double as a plain string without rounding and scientific notation. All other operations are
  * unsupported.
  * @see
  *   [[org.apache.poi.ss.usermodel.ExcelGeneralNumberFormat]] and SSNFormat from
  *   [[org.apache.poi.ss.usermodel.DataFormatter]] from Apache POI.
  */
object PlainNumberFormat extends Format {

  override def format(number: AnyRef, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer = {
    // Convert to BigDecimal for formatting
    val bd = new BigDecimal(number.toString)
    // Check if the number is an integer (scale == 0 after stripping trailing zeros)
    val stripped = bd.stripTrailingZeros()
    if (stripped.scale() <= 0) {
      // It's an integer, format without decimal point
      toAppendTo.append(stripped.toBigInteger().toString())
    } else {
      // It's not an integer, format as plain string
      toAppendTo.append(bd.toPlainString)
    }
  }

  override def parseObject(source: String, pos: ParsePosition): AnyRef =
    throw new UnsupportedOperationException()
}
