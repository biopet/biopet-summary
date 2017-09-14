package nl.biopet.summary

import slick.jdbc.H2Profile.api._

import scala.concurrent.ExecutionContext

class SummaryDbReadOnly(val db: Database)(implicit val ec: ExecutionContext)
    extends SummaryDb
