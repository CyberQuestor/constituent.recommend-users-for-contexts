

package com.hs.haystack.tachyon.constituent.recommendcontextstouser

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


class LikeALSAlgorithm(ap: ALSAlgorithmParams)
  extends ALSAlgorithm(ap) {

  @transient lazy override val logger = Logger[this.type]

  if (ap.numIterations > 30) {
    logger.warn(
      s"ALSAlgorithmParams.numIterations > 30, current: ${ap.numIterations}. " +
      s"There is a chance of running to StackOverflowException." +
      s"To remedy it, set lower numIterations or checkpoint parameters.")
  }

  override
  def train(sc: SparkContext, data: PreparedData): ALSModel = {
    require(!data.likeEvents.take(1).isEmpty,
      s"likeEvents in PreparedData cannot be empty." +
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

    // create User and item's String ID to integer index BiMap
    val userStringIntMap = BiMap.stringInt(data.users.keys)
    val itemStringIntMap = BiMap.stringInt(data.items.keys)
    
    // collect Item as Map and convert ID to Int index
    val items: Map[Int, Item] = data.items.map { case (id, item) =>
      (itemStringIntMap(id), item)
    }.collectAsMap.toMap
    
    val mllibRatings = data.likeEvents
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

        // key is (uindex, iindex) tuple, value is (like, t) tuple
        ((uindex, iindex), (r.like, r.t))
      }.filter { case ((u, i), v) =>
        // keep events with valid user and item index
        (u != -1) && (i != -1)
      }.reduceByKey { case (v1, v2) => // MODIFIED
        // An user may like an item and change to dislike it later,
        // or vice versa. Use the latest value for this case.
        val (like1, t1) = v1
        val (like2, t2) = v2
        // keep the latest value
        if (t1 > t2) v1 else v2
      }.map { case ((u, i), (like, t)) => // MODIFIED
        // With ALS.trainImplicit(), we can use negative value to indicate
        // nagative siginal (ie. dislike)
        val r = if (like) 1 else -1
        // MLlibRating requires integer index for user and item
        MLlibRating(u, i, r)
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
      
    new ALSModel(
      rank = m.rank,
      userFeatures = m.userFeatures,
      productFeatures = m.productFeatures,
      userStringIntMap = userStringIntMap,
      itemStringIntMap = itemStringIntMap,
      items = items
    )

  }

  override
  def predict(model: ALSModel, query: Query): PredictedResult = {
    // Convert String ID to Int index for Mllib
    model.userStringIntMap.get(query.user).map { userInt =>
      // create inverse view of itemStringIntMap
      val itemIntStringMap = model.itemStringIntMap.inverse
      // recommendProducts() returns Array[MLlibRating], which uses item Int
      // index. Convert it to String ID for returning PredictedResult
      val itemScores = model.recommendProducts(userInt, query.num)
        .map (r => ItemScore(itemIntStringMap(r.product), r.rating, model.items(r.product).domain, model.items(r.product).itemType))
      PredictedResult(itemScores)
    }.getOrElse{
      logger.info(s"No prediction for unknown user ${query.user}.")
      PredictedResult(Array.empty)
    }
  }

}
