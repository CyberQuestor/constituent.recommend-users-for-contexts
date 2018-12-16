

package com.hs.haystack.tachyon.constituent.recommenduserstocontexts

import org.apache.predictionio.controller.LServing
import breeze.stats.meanAndVariance
import breeze.stats.MeanAndVariance

class Serving
  extends LServing[Query, PredictedResult] {

  override
  def serve(query: Query,
    predictedResults: Seq[PredictedResult]): PredictedResult = {
    
    println("all predictions")
    //predictedResults.map(_.userScores).take(50).foreach(println)
    
     predictedResults.map(_.userScores).take(50).foreach(e => {
      e.take(50).foreach(println)
    })
    
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
    
    // sum the standardized score if same item
    val combined = standard.flatten // Array of ItemScore
      .groupBy(_.user) // groupBy item id
      .mapValues(userScores => userScores.map(_.score).reduce(_ + _))
      .toArray // array of (item id, score)
      .sortBy(_._2)(Ordering.Double.reverse)
      .take(query.num)
      .map { case (k,v) => UserScore(k, v) }
    
    PredictedResult(combined)
  }
}
