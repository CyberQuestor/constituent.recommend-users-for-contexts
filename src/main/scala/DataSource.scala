

package com.hs.haystack.tachyon.constituent.recommendcontextstouser

import org.apache.predictionio.controller.PDataSource
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.EmptyActualResult
import org.apache.predictionio.controller.Params
import org.apache.predictionio.data.storage.Event
import org.apache.predictionio.data.store.PEventStore

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger

case class DataSourceEvalParams(kFold: Int, queryNum: Int)

case class DataSourceParams(
  appName: String,
  evalParams: Option[DataSourceEvalParams]) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, ActualResult] {

  @transient lazy val logger = Logger[this.type]

  def getRatings(sc: SparkContext): RDD[Rating] = {

    val eventsRDD: RDD[Event] = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(List("view")), // MODIFIED
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("item")))(sc)

    val ratingsRDD: RDD[Rating] = eventsRDD.map { event =>
      try {
        val ratingValue: Double = event.event match {
          case "view" => 1.0 // MODIFIED
          case _ => throw new Exception(s"Unexpected event ${event} is read.")
        }
        // MODIFIED
        // key is (user id, item id)
        // value is the rating value, which is 1.
        ((event.entityId, event.targetEntityId.get), ratingValue)
      } catch {
        case e: Exception => {
          logger.error(s"Cannot convert ${event} to Rating. Exception: ${e}.")
          throw e
        }
      }
    }
    // MODIFIED
    // sum all values for the same user id and item id key
    .reduceByKey { case (a, b) => a + b }
    .map { case ((uid, iid), r) =>
      Rating(uid, iid, r)
    }.cache()

    ratingsRDD
  }
  
  def getInterests(sc: SparkContext): RDD[LikeEvent] = {
    // ADDED
    // get all "user" "like" and "dislike" "item" events
    val likeEventsRDD: RDD[LikeEvent] = PEventStore.find(
      appName = dsp.appName,
      entityType = Some("user"),
      eventNames = Some(List("like", "dislike")),
      // targetEntityType is optional field of an event.
      targetEntityType = Some(Some("item")))(sc)
      // eventsDb.find() returns RDD[Event]
      .map { event =>
        val likeEvent = try {
          event.event match {
            case "like" | "dislike" => LikeEvent(
              user = event.entityId,
              item = event.targetEntityId.get,
              t = event.eventTime.getMillis,
              like = (event.event == "like"))
            case _ => throw new Exception(s"Unexpected event ${event} is read.")
          }
        } catch {
          case e: Exception => {
            logger.error(s"Cannot convert ${event} to LikeEvent." +
              s" Exception: ${e}.")
            throw e
          }
        }
        likeEvent
      }.cache()
      
      likeEventsRDD
  }
  
  def getUsers(sc: SparkContext): RDD[(String, User)] = {
    // create a RDD of (entityID, User)
    val usersRDD: RDD[(String, User)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "user"
    )(sc).map { case (entityId, properties) =>
      val user = try {
        User()
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get properties ${properties} of" +
            s" user ${entityId}. Exception: ${e}.")
          throw e
        }
      }
      (entityId, user)
    }.cache()
    
    usersRDD
  }
  
  def getItems(sc: SparkContext): RDD[(String, Item)] = {
    // create a RDD of (entityID, Item)
    val itemsRDD: RDD[(String, Item)] = PEventStore.aggregateProperties(
      appName = dsp.appName,
      entityType = "item"
    )(sc).map { case (entityId, properties) =>
      val item = try {
        // Assume categories is optional property of item.
        Item(categories = properties.getOpt[List[String]]("categories"), properties.getOrElse[String]("domain",""), properties.getOrElse[String]("type",""))
      } catch {
        case e: Exception => {
          logger.error(s"Failed to get properties ${properties} of" +
            s" item ${entityId}. Exception: ${e}.")
          throw e
        }
      }
      (entityId, item)
    }.cache()
    
    itemsRDD
  }

  override
  def readTraining(sc: SparkContext): TrainingData = {
    new TrainingData(getUsers(sc), getItems(sc), getRatings(sc), getInterests(sc))
  }

  override
  def readEval(sc: SparkContext)
  : Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
    require(!dsp.evalParams.isEmpty, "Must specify evalParams")
    val evalParams = dsp.evalParams.get

    val kFold = evalParams.kFold
    val ratings: RDD[(Rating, Long)] = getRatings(sc).zipWithUniqueId
    ratings.cache

    (0 until kFold).map { idx => {
      val trainingRatings = ratings.filter(_._2 % kFold != idx).map(_._1)
      val testingRatings = ratings.filter(_._2 % kFold == idx).map(_._1)

      val testingUsers: RDD[(String, Iterable[Rating])] = testingRatings.groupBy(_.user)

      (new TrainingData(null, null, trainingRatings, null),
        new EmptyEvaluationInfo(),
        testingUsers.map {
          case (user, ratings) => (Query(user, evalParams.queryNum), ActualResult(ratings.toArray))
        }
      )
    }}
  }
}

case class Rating(
  user: String,
  item: String,
  rating: Double
)

case class LikeEvent( // ADDED
  user: String,
  item: String,
  t: Long,
  like: Boolean // true: like. false: dislike
)

case class User()

case class Item(
    val categories: Option[List[String]],
    val domain: String,
    val itemType: String)

class TrainingData(
  val users: RDD[(String, User)],
  val items: RDD[(String, Item)],
  val ratings: RDD[Rating],
  val likeEvents: RDD[LikeEvent] // ADDED
) extends Serializable {
  override def toString = {
    s"ratings: [${ratings.count()}] (${ratings.take(2).toList}...)"
  }
}
