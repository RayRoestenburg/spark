package spark

import akka.actor.Actor._

class SparkEnv (
  val cache: Cache,
  val serializer: Serializer,
  val cacheTracker: CacheTracker,
  val mapOutputTracker: MapOutputTracker,
  val shuffleFetcher: ShuffleFetcher,
  val eventReporter: EventReporter
)

object SparkEnv extends Logging {
  private val env = new ThreadLocal[SparkEnv]

  def set(e: SparkEnv) {
    env.set(e)
  }

  def get: SparkEnv = {
    env.get()
  }

  def createFromSystemProperties(isMaster: Boolean): SparkEnv = {
    val cacheClass = System.getProperty("spark.cache.class", "spark.SoftReferenceCache")
    val cache = Class.forName(cacheClass).newInstance().asInstanceOf[Cache]
    
    val serializerClass = System.getProperty("spark.serializer", "spark.JavaSerializer")
    val serializer = Class.forName(serializerClass).newInstance().asInstanceOf[Serializer]

    val cacheTracker = new CacheTracker(isMaster, cache)

    val mapOutputTracker = new MapOutputTracker(isMaster)

    val shuffleFetcherClass = System.getProperty("spark.shuffle.fetcher", "spark.SimpleShuffleFetcher")
    val shuffleFetcher = Class.forName(shuffleFetcherClass).newInstance().asInstanceOf[ShuffleFetcher]

    // Initialize the Akka server on the master
    if (isMaster) {
      val host = System.getProperty("spark.master.host")
      val remoteServer = remote.start(host, Utils.freePort)
      System.setProperty("spark.master.akkaPort", remoteServer.address.getPort.toString)
      logInfo("Akka listening at %s:%d".format(host, remoteServer.address.getPort))
    }
    val akkaDispatcher = new DaemonDispatcher("dispatcher")

    val eventReporter = new EventReporter(isMaster, akkaDispatcher)

    new SparkEnv(cache, serializer, cacheTracker, mapOutputTracker, shuffleFetcher, eventReporter)
  }
}
