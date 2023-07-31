import org.apache.arrow.flatbuf.Date
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row

import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Define case classes to represent the data
case class Transaction(customer_id: Int, transaction_date: String, transaction_type: String, amount: Double)
case class CustomerTransactionFrequency(customer_id: Int, transaction_frequency: Long)
case class TransactionPattern(customer_id: Int, transaction_date: String, transaction_type: String, amount: Double)

object BankDataAnalysis {

  def main(args: Array[String]):Unit = {
    val sc = new SparkContext("local", "BankDataAnalysis")
    val data = loadDataFromCSV(sc, "src/main/resources/data.csv")
    //data.foreach(println)

    val handledData = handleMissingData(data)
    handledData.foreach(println)
    calculateBasicStatistics(handledData)
    customerTransactionFrequency(handledData).foreach(println)
    groupByTransactionDate(data, "yearly").foreach(println)
  }

  // Function to load the bank dataset from the CSV file into an RDD
  def loadDataFromCSV(sc: SparkContext, filename: String): RDD[Transaction] = {
    val dataRDD = sc.textFile(filename)
    val headerRDD = dataRDD.zipWithIndex.filter { case (_, index) => index > 0 }.keys
    headerRDD.map(line => {
      val fields = line.split(",")
      Transaction(fields(0).toInt, fields(1), fields(2), fields(3).toDouble)
    })
  }

  // Function to handle missing or erroneous data and return a cleaned RDD
  def handleMissingData(data: RDD[Transaction]): RDD[Transaction] = {
    // Implement your logic here to handle missing or erroneous data
    // For example, you can use RDD transformations like filter or map to clean the data
    // Return the cleaned RDD
    var newRdd = data.filter(_.amount != null)
    newRdd = newRdd.filter(_.transaction_type != null)
    newRdd = newRdd.filter(_.transaction_date != null)
    newRdd = newRdd.filter(trans => trans.transaction_type == "deposit" || trans.transaction_type == "withdrawal")
    newRdd.filter(transaction => transaction.amount > 0)
  }

  // Function to calculate and display basic statistics
  def calculateBasicStatistics(data: RDD[Transaction]): Unit = {
    val totalDeposits = data.filter(_.transaction_type == "deposit").map(_.amount).sum()
    val totalWithdrawals = data.filter(_.transaction_type == "withdrawal").map(_.amount).sum()
    val averageTransactionAmount = data.map(_.amount).mean()

    println(s"Total Deposits: $totalDeposits")
    println(s"Total Withdrawals: $totalWithdrawals")
    println(s"Average Transaction Amount: $averageTransactionAmount")
    plotTransactionTrends(data.map(x => (x.transaction_date, x.amount)))
  }

  // Function to determine the number of unique customers and the frequency of transactions per customer
  def customerTransactionFrequency(data: RDD[Transaction]): RDD[CustomerTransactionFrequency] = {
    val transactionCounts = data.map(transaction => (transaction.customer_id, 1))
      .reduceByKey(_ + _)

    transactionCounts.map { case (customer_id, frequency) =>
      CustomerTransactionFrequency(customer_id, frequency)
    }
  }

  def parseDate(dateStr: String): LocalDate = {
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  LocalDate.parse(dateStr, formatter)
}

  // Function to group transactions based on the transaction date
  def groupByTransactionDate(data: RDD[Transaction], timeUnit: String): RDD[(String, Double)] = {
    val lowerCaseTimeUnit = timeUnit.toLowerCase
    val groupedRDD = data.map(transaction => {
      val date = parseDate(transaction.transaction_date)
      val dateUnitKey = lowerCaseTimeUnit match {
        case "daily" => date.toString
        case "monthly" => s"${date.getYear}-${date.getMonthValue}"
        case "yearly" => s"${date.getYear}"
        case _ =>  date.toString
      }
      (dateUnitKey, transaction.amount)
    })
    groupedRDD.reduceByKey(_ + _)

  }

  // Function to calculate the average transaction amount for each customer segment
  def calculateAvgTransactionAmount(data: RDD[Transaction], segments: RDD[(Int, String)]): RDD[(String, Double)] = {
    val customerAmounts = data.map(transaction => (transaction.customer_id, transaction.amount))
    val joinedData = customerAmounts.join(segments)
    val avgTransactionAmounts = joinedData.map { case (_, (amount, segment)) => (segment, amount) }
      .aggregateByKey((0.0, 0L))(
        (acc, value) => (acc._1 + value, acc._2 + 1),
        (acc1, acc2) => (acc1._1 + acc2._1, acc1._2 + acc2._2)
      )
      .mapValues { case (totalAmount, count) => totalAmount / count.toDouble }

    avgTransactionAmounts
  }

  // Function to identify patterns in customer transactions
  def identifyTransactionPatterns(data: RDD[Transaction]): RDD[TransactionPattern] = {
    // Group transactions by customer ID
    val transactionsByCustomer = data.groupBy(_.customer_id)

    // Identify patterns for each customer
    val identifiedPatterns = transactionsByCustomer.flatMap {
      case (customerId, transactions) =>
        val sortedTransactions = transactions.toSeq.sortBy(_.transaction_date)

        // Find patterns in sorted transactions
        val patterns = sortedTransactions.sliding(2).flatMap {
          case Seq(transaction1, transaction2) =>
            if (transaction1.amount > 500 && transaction2.amount < -500) {
              // Large deposit followed by large withdrawal pattern
              Some(TransactionPattern(customerId, transaction2.transaction_date, transaction2.transaction_type, transaction2.amount))
            } else {
              None // No pattern found
            }
          case _ => None // Not enough transactions for a pattern
        }

        patterns
    }

    identifiedPatterns
  }

 // Optional: Function to detect fraudulent transactions based on unusual patterns
def detectFraudulentTransactions(data: RDD[Transaction]): RDD[Transaction] = {
  val flaggedTransactions = data.filter(transaction => transaction.amount > 10000)
  flaggedTransactions
}


 // Optional: Function to optimize performance using Spark configurations and caching strategies
def optimizePerformance(data: RDD[Transaction]): RDD[Transaction] = {
  data.cache()
  data
}

}