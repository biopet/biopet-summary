package utils

import scala.concurrent.ExecutionContext

import slick.jdbc.H2Profile.api._

class SummaryDbReadOnly(val db: Database)(implicit val ec: ExecutionContext) extends SummaryDb
