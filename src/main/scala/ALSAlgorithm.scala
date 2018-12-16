

package com.hs.haystack.tachyon.constituent.recommenduserstocontexts

import org.apache.predictionio.controller.PAlgorithm
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.BiMap

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.{Rating => MLlibRating}
import org.apache.spark.mllib.recommendation.ALSModel

import grizzled.slf4j.Logger

import scala.collection.mutable.ArrayBuffer

case class ALSAlgorithmParams(
  rank: Int,
  numIterations: Int,
  lambda: Double,
  seed: Option[Long]) extends Params

class ALSAlgorithm(val ap: ALSAlgorithmParams)
  extends PAlgorithm[PreparedData, ALSModel, Query, PredictedResult] {

  @transient lazy val logger = Logger[this.type]

  if (ap.numIterations > 30) {
    logger.warn(
      s"ALSAlgorithmParams.numIterations > 30, current: ${ap.numIterations}. " +
      s"There is a chance of running to StackOverflowException." +
      s"To remedy it, set lower numIterations or checkpoint parameters.")
  }

  def train(sc: SparkContext, data: PreparedData): ALSModel = {
    require(!data.viewEvents.take(1).isEmpty,
      s"viewEvents in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    require(!data.users.take(1).isEmpty,
      s"users in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
    require(!data.items.take(1).isEmpty,
      s"items in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preprator generates PreparedData correctly.")
      
      println("Model training for ALS view only initiated")
      
      // create User and item's String ID to integer index BiMap
    val userStringIntMap = BiMap.stringInt(data.users.keys)
    val itemStringIntMap = BiMap.stringInt(data.items.keys)
    
    // collect Item as Map and convert ID to Int index
    val items: Map[Int, Item] = data.items.map { case (id, item) =>(itemStringIntMap.getOrElse(id, 0), item)
      case default => (0, Item("00000000-0000-0000-0000-000000000000", None,"haystack.in","POV"))
    }.collectAsMap.toMap
    
    val mllibRatings = data.viewEvents
      .map { r =>
        // Convert user and item String IDs to Int index for MLlib
        val uindex = userStringIntMap.getOrElse(r.user, -1)
        val iindex = itemStringIntMap.getOrElse(r.item, -1)

        if (uindex == -1)
          logger.info(s"Couldn't convert nonexistent user ID ${r.user}"
            + " to Int index.")

        if (iindex == -1)
          logger.info(s"Couldn't convert nonexistent item ID ${r.item}"
            + " to Int index.")

        ((uindex, iindex), 1)
      }.filter { case ((u, i), v) =>
        // keep events with valid user and item index
        (u != -1) && (i != -1)
      }.reduceByKey(_ + _) // aggregate all view events of same user-item pair
      .map { case ((u, i), v) =>
        // MLlibRating requires integer index for user and item
        MLlibRating(u, i, v)
      }
      .cache()

    // MLLib ALS cannot handle empty training data.
    require(!mllibRatings.take(1).isEmpty,
      s"mllibRatings cannot be empty." +
      " Please check if your events contain valid user and item ID.")

    // seed for MLlib ALS
    val seed = ap.seed.getOrElse(System.nanoTime)
    
    val m = ALS.trainImplicit(
      ratings = mllibRatings,
      rank = ap.rank,
      iterations = ap.numIterations,
      lambda = ap.lambda,
      blocks = -1,
      alpha = 1.0,
      seed = seed)
    
    println("Model through ALS view only training complete")

    new ALSModel(
      rank = m.rank,
      userFeatures = m.userFeatures,
      productFeatures = m.productFeatures,
      userStringIntMap = userStringIntMap,
      itemStringIntMap = itemStringIntMap,
      items = items)
  }

  def predict(model: ALSModel, query: Query): PredictedResult = {
    model.items.withDefaultValue(new Item("00000000-0000-0000-0000-000000000000", None, "haystack.in", "POV"))
    
    println("Entering prediction")
    
    // convert items to Int index
    val queryList: Set[Int] = query.items.map(model.itemStringIntMap.get(_))
      .flatten.toSet
    
    var combinedWithOthers = ArrayBuffer[UserScore]()
    queryList.foreach (e => {
      println("item now is: " + e)
      val userIntStringMap = model.userStringIntMap.inverse
      val userScores = model.recommendUsers(e, query.num)
      println("found user score for item: "+ e)
      userScores.take(50).foreach(println)
      //val userScoreMap = userScores.map(r => UserScore(userIntStringMap(r.user), r.rating))
      //combinedWithOthers ++ userScoreMap
    })
    
    
    /*query.items.foreach (e => {
      // Convert String ID to Int index for Mllib
       model.itemStringIntMap.get(e).map { itemInt =>
         // create inverse view of userStringIntMap
         val userIntStringMap = model.userStringIntMap.inverse
         // recommendUsers() returns Array[MLlibRating], which uses item Int
         // index. Convert it to String ID for returning PredictedResult
         val userScores = model.recommendUsers(itemInt, query.num)
         .map (r => UserScore(userIntStringMap(r.user), r.rating))
         combinedWithOthers ++ userScores
       }.getOrElse{
        logger.info(s"No prediction for unknown item ${e}.")
       }
       println("what is being combined")
       combinedWithOthers.take(50).foreach(println)
    })*/
    
    PredictedResult(combinedWithOthers.toArray)
  }

}
