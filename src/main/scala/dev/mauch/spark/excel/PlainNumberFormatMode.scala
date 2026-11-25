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

import java.util.Locale
import scala.language.implicitConversions

/** Which numeric cells the `usePlainNumberFormat` read option renders through [[PlainNumberFormat]], i.e. at full
  * precision without rounding or scientific notation. Spelled `false`, `true` or `all` in the option.
  */
sealed trait PlainNumberFormatMode {
  def optionValue: String
}

object PlainNumberFormatMode {

  /** Cells render as POI displays them, through their number format. */
  case object Off extends PlainNumberFormatMode { val optionValue = "false" }

  /** Cells whose number format is `General` or `@` render through [[PlainNumberFormat]]; cells with an explicit number
    * format keep their formatted rendering.
    */
  case object General extends PlainNumberFormatMode { val optionValue = "true" }

  /** Every non-date numeric cell renders through [[PlainNumberFormat]] regardless of its number format; date-formatted
    * cells keep their formatted rendering.
    */
  case object All extends PlainNumberFormatMode { val optionValue = "all" }

  val values: Seq[PlainNumberFormatMode] = Seq(Off, General, All)

  def parse(value: String): PlainNumberFormatMode = {
    val normalized = Option(value).map(_.toLowerCase(Locale.ROOT)).getOrElse(Off.optionValue)
    values.find(_.optionValue == normalized).getOrElse {
      throw new IllegalArgumentException(
        s"usePlainNumberFormat must be one of ${values.map(_.optionValue).mkString(", ")}, got '$value'"
      )
    }
  }

  /** Keeps `usePlainNumberFormat = true` / `= false` compiling in the Scala API. */
  implicit def fromBoolean(enabled: Boolean): PlainNumberFormatMode = if (enabled) General else Off
}
