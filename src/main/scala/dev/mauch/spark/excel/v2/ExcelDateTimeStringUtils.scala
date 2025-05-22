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
import org.apache.spark.unsafe.types.UTF8String
import org.apache.spark.sql.catalyst.util._
import java.time.ZoneId
import org.apache.spark.sql.catalyst.util.TimestampFormatter

trait ExcelDateTimeStringUtils {


  @ifdef("legacyTimestampStringCleaning")
  private def prepareTimestampString(v: String): UTF8String =
    UTF8String.fromString(DateTimeUtils.cleanLegacyTimestampStr(v))

  @ifndef("legacyTimestampStringCleaning")
  private def prepareTimestampString(v: String): UTF8String =
    DateTimeUtils.cleanLegacyTimestampStr(UTF8String.fromString(v))

  def stringToTimestamp(v: String, zoneId: ZoneId): Option[Long] =
    DateTimeUtils.stringToTimestamp(prepareTimestampString(v), zoneId)

  @ifndef("stringToDateZoneIdSupport")
  def stringToDate(v: String, zoneId: ZoneId): Option[Int] =
    DateTimeUtils.stringToDate(prepareTimestampString(v))

  @ifdef("stringToDateZoneIdSupport")
  def stringToDate(v: String, zoneId: ZoneId): Option[Int] =
    DateTimeUtils.stringToDate(prepareTimestampString(v), zoneId)

  def extractTimeZone(zoneId: ZoneId): ZoneId = zoneId

  def getTimestampFormatter(options: ExcelOptions): TimestampFormatter = {
    import org.apache.spark.sql.catalyst.util.LegacyDateFormats.FAST_DATE_FORMAT
    TimestampFormatter(
      options.timestampFormat,
      options.zoneId,
      options.locale,
      legacyFormat = FAST_DATE_FORMAT,
      isParsing = true
    )
  }

  @ifdef("dateFormatter:WithZoneId")
  def getDateFormatter(options: ExcelOptions): DateFormatter = {
    import org.apache.spark.sql.catalyst.util.LegacyDateFormats.FAST_DATE_FORMAT
    import org.apache.spark.sql.catalyst.util.DateFormatter
    DateFormatter(options.dateFormat, options.zoneId, options.locale, legacyFormat = FAST_DATE_FORMAT, isParsing = true)
  }

  @ifndef("dateFormatter:WithZoneId")
  def getDateFormatter(options: ExcelOptions): DateFormatter = {
    import org.apache.spark.sql.catalyst.util.LegacyDateFormats.FAST_DATE_FORMAT
    import org.apache.spark.sql.catalyst.util.DateFormatter
    DateFormatter(options.dateFormat, options.locale, legacyFormat = FAST_DATE_FORMAT, isParsing = true)
  }
}

object ExcelDateTimeStringUtils extends ExcelDateTimeStringUtils
