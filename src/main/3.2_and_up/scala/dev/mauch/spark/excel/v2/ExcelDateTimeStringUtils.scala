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

import org.apache.spark.unsafe.types.UTF8String
import org.apache.spark.sql.catalyst.util._

import java.time.ZoneId
import org.apache.spark.sql.catalyst.util.DateFormatter
import org.apache.spark.sql.catalyst.util.TimestampFormatter
import org.apache.spark.sql.catalyst.util.LegacyDateFormats.FAST_DATE_FORMAT

import scala.annotation.nowarn
import scala.util.{Try, Success, Failure}
import java.lang.reflect.Method

object ExcelDateTimeStringUtils {
  
  // Cache reflection methods for performance
  private lazy val cleanLegacyTimestampStrMethod: Option[Method] = {
    Try {
      val method = DateTimeUtils.getClass.getMethod("cleanLegacyTimestampStr", classOf[UTF8String])
      method.setAccessible(true)
      method
    }.toOption
  }
  
  private lazy val stringToTimestampMethod: Option[Method] = {
    Try {
      val method = DateTimeUtils.getClass.getMethod("stringToTimestamp", classOf[UTF8String], classOf[ZoneId])
      method.setAccessible(true)
      method
    }.toOption
  }
  
  private lazy val stringToDateMethod: Option[Method] = {
    Try {
      val method = DateTimeUtils.getClass.getMethod("stringToDate", classOf[UTF8String])
      method.setAccessible(true)
      method
    }.toOption
  }

  def stringToTimestamp(v: String, zoneId: ZoneId): Option[Long] = {
    Try {
      val utf8Str = UTF8String.fromString(v)
      val str = cleanLegacyTimestampStrMethod match {
        case Some(method) => method.invoke(DateTimeUtils, utf8Str).asInstanceOf[UTF8String]
        case None => utf8Str // Fallback if method doesn't exist
      }
      
      stringToTimestampMethod match {
        case Some(method) => method.invoke(DateTimeUtils, str, zoneId).asInstanceOf[Option[Long]]
        case None => 
          // Fallback for Spark 4.0 - parse directly using Java Time API
          val instant = java.time.Instant.parse(v)
          Some(instant.toEpochMilli * 1000) // Convert to microseconds
      }
    }.getOrElse(None)
  }

  @nowarn
  def stringToDate(v: String, zoneId: ZoneId): Option[Int] = {
    Try {
      val utf8Str = UTF8String.fromString(v)
      val str = cleanLegacyTimestampStrMethod match {
        case Some(method) => method.invoke(DateTimeUtils, utf8Str).asInstanceOf[UTF8String]
        case None => utf8Str // Fallback if method doesn't exist
      }
      
      stringToDateMethod match {
        case Some(method) => method.invoke(DateTimeUtils, str).asInstanceOf[Option[Int]]
        case None =>
          // Fallback for Spark 4.0 - parse directly using Java Time API
          val localDate = java.time.LocalDate.parse(v)
          Some(Math.toIntExact(localDate.toEpochDay))
      }
    }.getOrElse(None)
  }

  def getTimestampFormatter(options: ExcelOptions): TimestampFormatter = {
    Try {
      TimestampFormatter(
        options.timestampFormat,
        options.zoneId,
        options.locale,
        legacyFormat = FAST_DATE_FORMAT,
        isParsing = true
      )
    }.getOrElse {
      // Fallback with simpler constructor for Spark 4.0
      TimestampFormatter(
        options.timestampFormat,
        options.zoneId,
        isParsing = true
      )
    }
  }

  def getDateFormatter(options: ExcelOptions): DateFormatter = {
    Try {
      DateFormatter(options.dateFormat, options.locale, legacyFormat = FAST_DATE_FORMAT, isParsing = true)
    }.getOrElse {
      // Fallback with simpler constructor for Spark 4.0
      DateFormatter(options.dateFormat, isParsing = true)
    }
  }

}
