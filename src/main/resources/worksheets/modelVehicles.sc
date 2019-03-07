
import scala.collection.immutable.ListMap


object modelVehicles {


  println("sequence of trials that led to vehicle aggregate addition for serving")

  var allScores: List[ItemScore]  = List(new ItemScore("1", 1.0, "d1", "Announcement"),
    new ItemScore("2", 2.8, "d2", "Newsletter"),
    new ItemScore("3", -3.4, "d1", "Survey"),
    new ItemScore("4", 2.1, "d1", "Announcement"),
    new ItemScore("5", -1.2, "d2", "Survey"))


  var totalOccurrences = 0

  var combinedWithOthers = List[ItemScore]()

  allScores.foreach(e => {
    //val domain = populate.get(e.item).get.domain
    //val itemType = populate.get(e.item).get.itemType
    totalOccurrences += 1
    combinedWithOthers ::= new ItemScore(e.item, e.score, e.domain, e.itemType)
  })
  combinedWithOthers

  totalOccurrences

  var vehicularScoreMap = allScores.groupBy(e => e.itemType).mapValues(_.foldLeft(0.0)(_ + _.score))

  var vehicularMap = allScores.groupBy(e => e.itemType).mapValues(_.size)

  val total = vehicularMap.values.sum.toDouble

  val vehicularSegmentMap = new ListMap() ++ vehicularMap.map({ case (k, v) => k -> VehicleScore(k, vehicularScoreMap(k), (v / total)) }).toSeq.sortWith(_._2.frequency > _._2.frequency)

  val newMap = new ListMap() ++ vehicularSegmentMap.toSeq.sortWith(_._2.frequency > _._2.frequency)

  vehicularSegmentMap.values.toArray


  // OR

  val vehicularSegmentMap2 = vehicularMap.map { case (k,v) => VehicleScore(k, vehicularScoreMap(k), (v / total)) }
  vehicularSegmentMap2.toArray

  case class ItemScore(
                        item: String,
                        score: Double,
                        domain: String,
                        itemType: String
                      )

  case class VehicleScore(
                           itemType: String,
                           aggregateScore: Double,
                           frequency: Double
                         )

  case class Item(
                   val categories: Option[List[String]],
                   val domain: String,
                   val itemType: String)
}