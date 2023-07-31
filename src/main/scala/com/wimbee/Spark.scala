package com.wimbee

import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession

object Spark extends App {
  val sc = new SparkContext("local", "Bikes")
  val bikesRDD = sc.textFile("src/main/resources/Used_Bikes.csv")
  val spliterRDD = bikesRDD.map(line => line.split(",").map(column => column.trim))
  val resultRDD = spliterRDD.filter(bike => bike(4).equalsIgnoreCase("First Owner") && bike(6).toDouble > 150 && bike(7).equalsIgnoreCase("Yamaha"))
  resultRDD.map(bike => (bike(0), bike(4))).distinct().foreach(println)
  println("Count of the finel Datasets " + resultRDD.count())
}
