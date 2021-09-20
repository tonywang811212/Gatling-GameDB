package simulations

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random


class BasicSimulation extends Simulation {

  val httpConf = http
    .baseUrl("http://localhost:8080/app/") // Here is the root for all relative URLs
    .header("Accept", "application/json")
   // .proxy(Proxy("localhost", 8888))


  /** Variables  */
  //runtime variables
  def userCount:Int = getProperty("USERS","3").toInt
  def rampDuration:Int = getProperty("RAMP_DURATION", "10").toInt
  def testDuration:Int = getProperty("DURATION","30").toInt

  //other variables
  var idNumbers = (20 to 1000).iterator
  var rnd = new Random()
  var now = LocalDate.now()
  var pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")


  /** Helper methods */
  private def getProperty(propertyName:String, defaultValue: String): String = {
    Option(System.getenv(propertyName))
      .orElse(Option(System.getProperty(propertyName)))
      .getOrElse(defaultValue)
  }

  def randomString(length: Int) = {
    rnd.alphanumeric.filter(_.isLetter).take(length).mkString
  }

  def getRandomDate(startDate:LocalDate, random: Random): String = {
    startDate.minusDays(random.nextInt(30)).format(pattern)
  }

  /** Custom Feeder */
  //to generate the date for the Create new game JSON
  //for the custom feeder, or the defaults for the runtime parameters... and anything
  val customFeeder = Iterator.continually(Map(
    "gameId" -> idNumbers.next(),
    "name" -> ("GAME-" + randomString(5)),
    "releaseDate" -> getRandomDate(now, rnd),
    "reviewScore" -> rnd.nextInt(100),
    "category" -> ("Category-" + randomString(6)),
    "rating" -> ("Rating-" + randomString(4))
  )
  )



  /** Before */

  println(s"Running test with ${userCount} users")
  println(s"Ramping users over ${rampDuration} seconds")
  println(s"Total test duration ${testDuration} seconds")

  /***  HTTP CALLS  ***/
  //add other calls here

  def getAllVideoGames()= {
    exec(
      http("Get all video games")
        .get("videogames")
        .check(status.is(200))
        .check(bodyString.saveAs("responseBody"))
    )
}

  def postNewGame()= {
    feed(customFeeder)
    .exec(http("Post new game")
        .post("videogames")
          .body(ElFileBody("data/bodies/NewGameTemplate.json")).asJson
        .check(status.is(200))
    )
  }

  def getLastPostedGame()= {
    exec(http("Get last posted game")
      .get("videogames/${gameId}")
      .check(jsonPath("$.name").is("${name}"))
      .check(status.is(200))
    )
  }

  def deleteLastPostedGame()= {
    exec(http("Detete last posted game")
      .delete("videogames/${gameId}")
      .check(status.is(200))
    )
  }

  /** SCENARIO DESIGN */
  //using the http calls, create a scenario that does the following
  //1. Get all games
  //2. Create new game
  //3. Get details of that single
  //4. Delete the game
  var scn = scenario("Video Game DB")
    .forever(){
      exec(getAllVideoGames())
        //.exec {session => println(session("responseBody").as[String]); session}
        .pause(2)
        .exec(postNewGame())
        .pause(2)
        .exec(getLastPostedGame())
        .pause(2)
        .exec(deleteLastPostedGame())

    }

  /** SETUP LOAD SIMULATION */
  //create a scenario that have run time parameters for:
  //1.Users
  //2.Ramp up time
  //3.Test duration
    setUp(
      scn.inject(
        nothingFor(5),
        rampUsers(userCount) during(rampDuration seconds)
      )
    ).protocols(httpConf).maxDuration(testDuration seconds)



  /** After  */
  // to print out the message at the start and end of the test
  after{
    println("Stress test completed")
  }

  //for the helper methods
}


