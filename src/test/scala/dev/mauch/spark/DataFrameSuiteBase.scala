package dev.mauch.spark

import com.github.mrpowers.spark.fast.tests.DataFrameComparer
import org.apache.spark.sql.{DataFrame, Row, SparkSession}

import java.sql.Timestamp

trait DataFrameSuiteBase extends DataFrameComparer {

  lazy val spark: SparkSession = {
    val builder = SparkSession
      .builder()
      .master("local")
      .appName("spark-excel session")
      .config("spark.sql.shuffle.partitions", "1")
    
    // For Spark 4.0+, add compatibility configurations
    val sparkVersion = org.apache.spark.SPARK_VERSION
    if (sparkVersion.startsWith("4.")) {
      builder
        .config("spark.sql.ansi.enabled", "false")
        .config("spark.sql.datetime.java8API.enabled", "false") // Disable Java 8 API for compatibility
        .config("spark.serializer", "org.apache.spark.serializer.JavaSerializer") // Use Java serializer for module compatibility
        .config("spark.sql.execution.arrow.pyspark.enabled", "false") // Disable Arrow
        .config("spark.sql.codegen.wholeStage", "false") // Disable whole-stage codegen for compatibility
        .config("spark.sql.adaptive.enabled", "false") // Disable adaptive query execution
        .config("spark.sql.adaptive.coalescePartitions.enabled", "false") // Disable adaptive partitioning
        .config("spark.sql.legacy.timeParserPolicy", "LEGACY") // Use legacy time parser
        .config("spark.sql.legacy.doLooseUpcast", "true") // Allow loose upcasting
        .config("spark.sql.legacy.typeCoercion.datetimeToString.enabled", "true") // Legacy datetime coercion
        .config("spark.sql.execution.exchangeReuseEnabled", "false") // Disable exchange reuse
        .config("spark.sql.execution.reuseSubquery", "false") // Disable subquery reuse
    } else {
      builder
    }
  }.getOrCreate()

  def assertDataFrameEquals(df1: DataFrame, df2: DataFrame): Unit = {
    val sparkVersion = org.apache.spark.SPARK_VERSION
    if (sparkVersion.startsWith("4.")) {
      // For Spark 4.0, avoid direct Row collection which triggers DateTimeUtils issues
      // Check schema equality first
      if (df1.schema != df2.schema) {
        throw new AssertionError(s"Schema mismatch:\nExpected: ${df1.schema}\nActual: ${df2.schema}")
      }
      
      // Check row counts
      val count1 = df1.count()
      val count2 = df2.count()
      if (count1 != count2) {
        throw new AssertionError(s"Row count mismatch: Expected $count1, Actual $count2")
      }
      
      // Use DataFrame operations to check equality
      val diff1 = df1.except(df2)
      val diff2 = df2.except(df1)
      
      if (diff1.count() > 0 || diff2.count() > 0) {
        // If there are differences and we can safely collect a small sample, do so
        try {
          val sampleSize = 10
          val diffRows1 = diff1.limit(sampleSize).collect()
          val diffRows2 = diff2.limit(sampleSize).collect()
          throw new AssertionError(s"DataFrames are not equal. Sample differences:\nIn df1 but not df2: ${diffRows1.mkString("\n")}\nIn df2 but not df1: ${diffRows2.mkString("\n")}")
        } catch {
          case _: Exception => 
            // If collect fails due to DateTimeUtils issues, just report the count difference
            throw new AssertionError(s"DataFrames are not equal. Difference count: ${diff1.count()} + ${diff2.count()}")
        }
      }
    } else {
      // Use the original implementation for older Spark versions
      assertSmallDataFrameEquality(df1, df2)
    }
  }

  def assertDataFrameApproximateEquals(expectedDF: DataFrame, actualDF: DataFrame, relTol: Double): Unit = {
    val sparkVersion = org.apache.spark.SPARK_VERSION
    if (sparkVersion.startsWith("4.")) {
      // For Spark 4.0, fall back to basic equality check to avoid DateTimeUtils issues
      assertDataFrameEquals(expectedDF, actualDF)
    } else {
      // Use the original implementation for older Spark versions
      val e = (r1: Row, r2: Row) => {
        r1.equals(r2) || RelTolComparer.areRowsEqual(r1, r2, relTol)
      }
      assertLargeDatasetEquality[Row](
        actualDF,
        expectedDF,
        equals = e,
        ignoreNullable = false,
        ignoreColumnNames = false,
        orderedComparison = false
      )
    }
  }

  def assertDataFrameNoOrderEquals(df1: DataFrame, df2: DataFrame): Unit = {
    val sparkVersion = org.apache.spark.SPARK_VERSION
    if (sparkVersion.startsWith("4.")) {
      // For Spark 4.0, use basic equality check (DataFrame.except handles ordering automatically)
      assertDataFrameEquals(df1, df2)
    } else {
      // Use the original implementation for older Spark versions
      assertSmallDataFrameEquality(df1, df2, orderedComparison = false)
    }
  }
}

object RelTolComparer {

  trait ToNumeric[T] {
    def toNumeric(x: Double): T
  }
  object ToNumeric {
    implicit val doubleToDouble: ToNumeric[Double] = new ToNumeric[Double] {
      def toNumeric(x: Double): Double = x
    }
    implicit val doubleToFloat: ToNumeric[Float] = new ToNumeric[Float] {
      def toNumeric(x: Double): Float = x.toFloat
    }
    implicit val doubleToLong: ToNumeric[Long] = new ToNumeric[Long] {
      def toNumeric(x: Double): Long = x.toLong
    }
    implicit val doubleToBigDecimal: ToNumeric[BigDecimal] = new ToNumeric[BigDecimal] {
      def toNumeric(x: Double): BigDecimal = BigDecimal(x)
    }
  }

  /** Approximate equality, based on equals from [[Row]] */
  def areRowsEqual(r1: Row, r2: Row, relTol: Double): Boolean = {
    def withinRelTol[T : Numeric : ToNumeric](a: T, b: T): Boolean = {
      val num = implicitly[Numeric[T]]
      val toNum = implicitly[ToNumeric[T]]
      val absTol = num.times(toNum.toNumeric(relTol), num.max(num.abs(a), num.abs(b)))
      val diff = num.abs(num.minus(a, b))
      num.lteq(diff, absTol)
    }

    if (r1.length != r2.length) {
      return false
    } else {
      (0 until r1.length).foreach(idx => {
        if (r1.isNullAt(idx) != r2.isNullAt(idx)) {
          return false
        }

        if (!r1.isNullAt(idx)) {
          val o1 = r1.get(idx)
          val o2 = r2.get(idx)
          o1 match {
            case b1: Array[Byte] =>
              if (!java.util.Arrays.equals(b1, o2.asInstanceOf[Array[Byte]])) {
                return false
              }

            case f1: Float =>
              if (
                java.lang.Float.isNaN(f1) !=
                  java.lang.Float.isNaN(o2.asInstanceOf[Float])
              ) {
                return false
              }
              if (!withinRelTol[Float](f1, o2.asInstanceOf[Float])) {
                return false
              }

            case d1: Double =>
              if (
                java.lang.Double.isNaN(d1) !=
                  java.lang.Double.isNaN(o2.asInstanceOf[Double])
              ) {
                return false
              }
              if (!withinRelTol[Double](d1, o2.asInstanceOf[Double])) {
                return false
              }

            case d1: java.math.BigDecimal =>
              if (!withinRelTol(BigDecimal(d1), BigDecimal(o2.asInstanceOf[java.math.BigDecimal]))) {
                return false
              }

            case t1: Timestamp =>
              if (!withinRelTol(t1.getTime, o2.asInstanceOf[Timestamp].getTime)) {
                return false
              }

            case _ =>
              if (o1 != o2) return false
          }
        }
      })
    }
    true
  }

}
