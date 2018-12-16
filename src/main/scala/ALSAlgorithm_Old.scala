

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
/*
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
    // MLLib ALS cannot handle empty training data.
    require(!data.ratings.take(1).isEmpty,
      s"RDD[Rating] in PreparedData cannot be empty." +
      " Please check if DataSource generates TrainingData" +
      " and Preparator generates PreparedData correctly.")
      
      println("Training recommended items model through ALS view only")
    // Convert user and item String IDs to Int index for MLlib

    val userStringIntMap = BiMap.stringInt(data.ratings.map(_.user))
    val itemStringIntMap = BiMap.stringInt(data.ratings.map(_.item))
    
    // collect Item as Map and convert ID to Int index
    val items: Map[Int, Item] = data.items.map { case (id, item) => (itemStringIntMap.getOrElse(id, 0), item)
    case default => (0, Item("00000000-0000-0000-0000-000000000000", None,"haystack.in","POV"))
    }.collectAsMap.toMap
    
    val mllibRatings = data.ratings.map( r =>
      // MLlibRating requires integer index for user and item
      MLlibRating(userStringIntMap(r.user), itemStringIntMap(r.item), r.rating)
    )

    // seed for MLlib ALS
    val seed = ap.seed.getOrElse(System.nanoTime)

    // Set checkpoint directory
    // sc.setCheckpointDir("checkpoint")

    // If you only have one type of implicit event (Eg. "view" event only),
    // set implicitPrefs to true
    // MODIFIED
    val implicitPrefs = true
    val als = new ALS()
    als.setUserBlocks(-1)
    als.setProductBlocks(-1)
    als.setRank(ap.rank)
    als.setIterations(ap.numIterations)
    als.setLambda(ap.lambda)
    als.setImplicitPrefs(implicitPrefs)
    als.setAlpha(1.0)
    als.setSeed(seed)
    als.setCheckpointInterval(10)
    val m = als.run(mllibRatings)
    
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
    
    var combinedWithOthers = ArrayBuffer[UserScore]()
    
    query.items.foreach (e => {
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
    })
    
    PredictedResult(combinedWithOthers.toArray)
  }

}*/