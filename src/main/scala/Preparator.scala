

package com.hs.haystack.tachyon.constituent.recommenduserstocontexts

import org.apache.predictionio.controller.PPreparator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

class Preparator
  extends PPreparator[TrainingData, PreparedData] {

  def prepare(sc: SparkContext, trainingData: TrainingData): PreparedData = {
    new PreparedData(
        users = trainingData.users,
        items = trainingData.items,
        ratings = trainingData.ratings,
        viewEvents = trainingData.viewEvents, // ADDED
        likeEvents = trainingData.likeEvents) // ADDED
  }
}

class PreparedData(
  val users: RDD[(String, User)],
  val items: RDD[(String, Item)],
  val ratings: RDD[Rating],
  val viewEvents: RDD[ViewEvent], // ADDED
  val likeEvents: RDD[LikeEvent] // ADDED
  ) extends Serializable
