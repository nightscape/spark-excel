/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.catalyst.util

import java.sql.{Date, Timestamp}
import java.time.{Instant, LocalDate, ZoneOffset}

/**
 * Compatibility object for Spark 4.0 DateTimeUtils
 * Provides missing toJavaTimestamp and toJavaDate methods that were removed in Spark 4.0
 */
object DateTimeCompatUtils {
  
  // Constants used by other parts of the system
  val TIMEZONE_OPTION = "timeZone"
  
  /**
   * Converts Spark's internal timestamp representation to java.sql.Timestamp
   * Spark 4.0 implementation using microseconds since epoch
   */
  def toJavaTimestamp(us: Long): Timestamp = {
    // Convert microseconds to milliseconds and create Timestamp
    val millis = us / 1000
    val nanos = ((us % 1000000) * 1000).toInt
    val ts = new Timestamp(millis)
    ts.setNanos(nanos)
    ts
  }
  
  /**
   * Converts Spark's internal date representation to java.sql.Date
   * Spark 4.0 implementation using days since epoch
   */
  def toJavaDate(daysSinceEpoch: Int): Date = {
    // Convert days since epoch to java.sql.Date
    val localDate = LocalDate.ofEpochDay(daysSinceEpoch.toLong)
    Date.valueOf(localDate)
  }
  
  /**
   * Converts java.sql.Date to Spark's internal date representation
   * Returns days since epoch
   */
  def fromJavaDate(date: Date): Int = {
    val localDate = date.toLocalDate
    localDate.toEpochDay.toInt
  }
}

// For backward compatibility, provide an object that delegates to our compat utils
object DateTimeUtils {
  val TIMEZONE_OPTION = DateTimeCompatUtils.TIMEZONE_OPTION
  def toJavaTimestamp(us: Long): Timestamp = DateTimeCompatUtils.toJavaTimestamp(us)
  def toJavaDate(daysSinceEpoch: Int): Date = DateTimeCompatUtils.toJavaDate(daysSinceEpoch)
  def fromJavaDate(date: Date): Int = DateTimeCompatUtils.fromJavaDate(date)
}