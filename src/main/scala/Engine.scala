

package com.hs.haystack.tachyon.constituent.recommendcontextstouser

import org.apache.predictionio.controller.EngineFactory
import org.apache.predictionio.controller.Engine

case class Query(
  user: String,
  num: Int
)

case class PredictedResult(
  itemScores: Array[ItemScore]
)

case class ActualResult(
  ratings: Array[Rating]
)

case class ItemScore(
  item: String,
  score: Double,
  domain: String,
  itemType: String
)

object RecommendationEngine extends EngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("als" -> classOf[ALSAlgorithm],
          "likealgo" -> classOf[LikeALSAlgorithm]), // ADDED),
      classOf[Serving])
  }
}
