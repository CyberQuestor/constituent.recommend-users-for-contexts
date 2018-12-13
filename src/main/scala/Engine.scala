

package com.hs.haystack.tachyon.constituent.recommenduserstocontexts

import org.apache.predictionio.controller.EngineFactory
import org.apache.predictionio.controller.Engine

case class Query(
  items: List[String],
  num: Int
)

case class PredictedResult(
  userScores: Array[UserScore]
)

case class ActualResult(
  ratings: Array[Rating]
)

case class UserScore(
  user: String,
  score: Double
)

object RecommendationEngine extends EngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("als" -> classOf[ALSAlgorithm]),
          //"likealgo" -> classOf[LikeALSAlgorithm]), // ADDED),
      classOf[Serving])
  }
}
