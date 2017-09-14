package utils

import scala.concurrent.ExecutionContext

import slick.driver.H2Driver.api._

class SummaryDbReadOnly(val db: Database)(implicit val ec: ExecutionContext) extends SummaryDb
