

package com.hs.haystack.tachyon.constituent.recommenduserstocontexts

import org.apache.predictionio.controller.LServing
import breeze.stats.meanAndVariance
import breeze.stats.MeanAndVariance
import scala.collection.immutable.ListMap

class Serving
  extends LServing[Query, PredictedResult] {

  override
  def serve(query: Query,
    predictedResults: Seq[PredictedResult]): PredictedResult = {
    val prediction = query.aim match {
      case "item" => serveItems(query, predictedResults)
      case "user" => serveUsers(query, predictedResults)
      case "vehicle" => serveVehicles(query, predictedResults)
      case _ => PredictedResult(Array(), Array(), Array())
    }
    prediction
  }
  
  def serveUsers(query: Query,
    predictedResults: Seq[PredictedResult]): PredictedResult = {
    // MODFIED
    val standard: Seq[Array[UserScore]] = if (query.num == 1) {
      // if query 1 item, don't standardize
      predictedResults.map(_.userScores)
    } else {
      // Standardize the score before combine
      val mvList: Seq[MeanAndVariance] = predictedResults.map { pr =>
        meanAndVariance(pr.userScores.map(_.score))
      }

      predictedResults.zipWithIndex
        .map { case (pr, i) =>
          pr.userScores.map { is =>
            // standardize score (z-score)
            // if standard deviation is 0 (when all items have the same score,
            // meaning all items are ranked equally), return 0.
            val score = if (mvList(i).stdDev == 0) {
              0
            } else {
              (is.score - mvList(i).mean) / mvList(i).stdDev
            }

            UserScore(is.user, score)
          }
        }
    }
    
    // sum the standardized score if same user
    val combined = standard.flatten // Array of UserScore
      .groupBy(_.user) // groupBy item id
      .mapValues(userScores => userScores.map(_.score).reduce(_ + _))
      .toArray // array of (user id, score)
      .sortBy(_._2)(Ordering.Double.reverse)
      .take(query.num)
      .map { case (k,v) => UserScore(k, v) }
    
    PredictedResult(Array(), Array(), combined)
  }
  
  def serveItems(query: Query,
    predictedResults: Seq[PredictedResult]): PredictedResult = {
    val combinedWithOthers = serveCommonItems(query, predictedResults)
    PredictedResult(combinedWithOthers.toArray, Array(), Array())
  }
  
  def serveVehicles(query: Query,
    predictedResults: Seq[PredictedResult]): PredictedResult = {
    
    val combinedWithOthers = serveCommonItems(query, predictedResults)
					
		var vehicularScoreMap = combinedWithOthers.groupBy(e => e.vehicleType).mapValues(_.foldLeft(0.0)(_ + _.score))
		var vehicularMap = combinedWithOthers.groupBy(e => e.vehicleType).mapValues(_.size)
		val total = vehicularMap.values.sum.toDouble
		val vehicularSegmentMap = new ListMap() ++ vehicularMap.map({ case (k, v) => k -> VehicleScore(k, vehicularScoreMap(k), (v / total)) }).toSeq.sortWith(_._2.frequency > _._2.frequency)
		
    PredictedResult(Array(), vehicularSegmentMap.values.toArray, Array())
  }
  
  def serveCommonItems(query: Query,
    predictedResults: Seq[PredictedResult]): List[ItemScore] = {
    
    // MODFIED
    val standard: Seq[Array[ItemScore]] = if (query.num == 1) {
      // if query 1 item, don't standardize
      predictedResults.map(_.itemScores)
    } else {
      // Standardize the score before combine
      val mvList: Seq[MeanAndVariance] = predictedResults.map { pr =>
        meanAndVariance(pr.itemScores.map(_.score))
      }

      predictedResults.zipWithIndex
        .map { case (pr, i) =>
          pr.itemScores.map { is =>
            // standardize score (z-score)
            // if standard deviation is 0 (when all items have the same score,
            // meaning all items are ranked equally), return 0.
            val score = if (mvList(i).stdDev == 0) {
              0
            } else {
              (is.score - mvList(i).mean) / mvList(i).stdDev
            }

            ItemScore(is.item, score, is.domain, is.itemType, is.vehicleType)
          }
        }
    }
    
    //println("being served is")
    //standard.take(8).foreach(println)
    
    val populate = standard.flatten.map (t => t.item -> t).toMap.withDefaultValue(new ItemScore("00000000-0000-0000-0000-000000000000",4.0,"haystack.in","POV","00000000-0000-0000-0000-000000000000"))
    
    // sum the standardized score if same item
    val combined = standard.flatten // Array of ItemScore
      .groupBy(_.item) // groupBy item id
      .mapValues(itemScores => itemScores.map(_.score).reduce(_ + _))
      .toArray // array of (item id, score)
      .sortBy(_._2)(Ordering.Double.reverse)
      .take(query.num)
      .map { case (k,v) => ItemScore(k, v, "", "", "") }
    
    //println("and that being combined")
    //combined.take(8).foreach(println)
    
    var combinedWithOthers = List[ItemScore]()     
    
    combined.foreach(e => {
					val domain = populate.get(e.item).get.domain
					val itemType = populate.get(e.item).get.itemType
					val vehicleType = populate.get(e.item).get.vehicleType
					combinedWithOthers ::= new ItemScore(e.item, e.score, domain, itemType, vehicleType)
					})
		combinedWithOthers
  }
  
}
