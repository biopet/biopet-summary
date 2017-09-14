package nl.biopet.utils

import org.apache.log4j.Logger

/**
  * Trait to implement logger function on local class/object
  */
trait Logging {

  /**
    *
    * @return Global biopet logger
    */
  def logger: Logger = Logging.logger
}

/**
  * Logger object, has a global logger
  */
object Logging {
  val logger: Logger = Logger.getRootLogger
}
