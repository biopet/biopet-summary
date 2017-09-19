/**
  * Biopet is built on top of GATK Queue for building bioinformatic
  * pipelines. It is mainly intended to support LUMC SHARK cluster which is running
  * SGE. But other types of HPC that are supported by GATK Queue (such as PBS)
  * should also be able to execute Biopet tools and pipelines.
  *
  * Copyright 2014 Sequencing Analysis Support Core - Leiden University Medical Center
  *
  * Contact us at: sasc@lumc.nl
  *
  * A dual licensing mode is applied. The source code within this project is freely available for non-commercial use under an AGPL
  * license; For commercial users or users who do not want to follow the AGPL
  * license, please contact us to obtain a separate license.
  */
package nl.biopet.summary

import java.io.{Closeable, File}

import nl.biopet.summary.Schema._
import nl.biopet.summary.SummaryDb._
import nl.biopet.summary.Implicts._
import nl.biopet.utils.Logging
import play.api.libs.json.{JsLookupResult, JsValue, Json}
import slick.jdbc.H2Profile.api._
import slick.jdbc.meta.MTable

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.implicitConversions

/**
  * This class interface wityh a summary database
  *
  * Created by pjvanthof on 05/02/2017.
  */
trait SummaryDb extends Closeable with Logging {

  lazy val schemas = List(projects, runs, samples, libraries, pipelines, modules, stats, settings, files, executables)

  implicit val ec: ExecutionContext

  def db: Database

  def close(): Unit = {
    logger.debug(s"Closing database: $db")
    db.close()
  }

  def tablesExist(): Boolean = {
    val tables = Await.result(db.run(MTable.getTables), Duration.Inf).toList

    schemas.forall(s => tables.exists(_.name.name == s.baseTableRow.tableName))
  }

  def getProjects(name: Option[String] = None): Future[Seq[Project]] = {
    name match {
      case Some(n) => db.run(projects.filter(_.name === n).result)
      case _ => db.run(projects.result)
    }
  }

  def getProject(id: Int): Future[Option[Project]] = {
    db.run(projects.filter(_.id === id).result).map(_.headOption)
  }

  /** This will return all runs that match the critiria given */
  def getRuns(runId: Option[Int] = None,
              protectId: Option[Int] = None,
              runName: Option[String] = None,
              outputDir: Option[String] = None): Future[Seq[Run]] = {
    val q = runs.filter { run =>
      List(
        runId.map(run.id === _),
        protectId.map(run.projectId === _),
        runName.map(run.runName === _),
        outputDir.map(run.outputDir === _)
      ).collect({ case Some(criteria) => criteria })
        .reduceLeftOption(_ && _)
        .getOrElse(true: Rep[Boolean])
    }
    db.run(q.result)
  }

  /** This will return all samples that match given criteria */
  def getSamples(sampleId: Option[Int] = None,
                 runId: Option[Int] = None,
                 name: Option[String] = None): Future[Seq[Sample]] = {
    val q = samples.filter { sample =>
      List(
        sampleId.map(sample.id === _),
        runId.map(sample.runId === _),
        name.map(sample.name === _)
      ).collect({ case Some(criteria) => criteria })
        .reduceLeftOption(_ && _)
        .getOrElse(true: Rep[Boolean])
    }
    db.run(q.result)
  }

  /** Return samplId of a specific runId + sampleName */
  def getSampleId(runId: Int, sampleName: String): Future[Option[Int]] = {
    getSamples(runId = Some(runId), name = Some(sampleName))
      .map(_.headOption.map(_.id))
  }

  /** Return sampleName of a specific sampleId */
  def getSampleName(sampleId: Int): Future[Option[String]] = {
    getSamples(sampleId = Some(sampleId)).map(_.headOption.map(_.name))
  }

  /** Return sample tags of a specific sample as a map */
  def getSampleTags(sampleId: Int): Future[Option[JsValue]] = {
    db.run(samples.filter(_.id === sampleId).map(_.tags).result)
      .map(_.headOption.flatten.map(Json.parse))
  }

  /** This returns all libraries that match the given criteria */
  def getLibraries(libId: Option[Int] = None,
                   name: Option[String] = None,
                   runId: Option[Int] = None,
                   sampleId: Option[Int] = None): Future[Seq[Library]] = {
    val q = libraries.filter { lib =>
      List(
        libId.map(lib.id === _),
        sampleId.map(lib.sampleId === _),
        runId.map(lib.runId === _),
        name.map(lib.name === _) // not a condition as `criteriaRoast` evaluates to `None`
      ).collect({ case Some(criteria) => criteria })
        .reduceLeftOption(_ && _)
        .getOrElse(true: Rep[Boolean])
    }
    db.run(q.result)
  }

  /** Return a libraryId for a specific combination */
  def getLibraryId(runId: Int,
                   sampleId: Int,
                   name: String): Future[Option[Int]] = {
    getLibraries(runId = Some(runId),
                 sampleId = Some(sampleId),
                 name = Some(name))
      .map(_.headOption.map(_.id))
  }

  /** Return a libraryId for a specific combination */
  def getLibraryName(libraryId: Int): Future[Option[String]] = {
    getLibraries(libId = Some(libraryId)).map(_.headOption.map(_.name))
  }

  /** Return library tags as a map */
  def getLibraryTags(libId: Int): Future[Option[JsValue]] = {
    db.run(libraries.filter(_.id === libId).map(_.tags).result)
      .map(_.headOption.flatten.map(Json.parse))
  }

  /** Get all pipelines that match given criteria */
  def getPipelines(pipelineId: Option[Int] = None,
                   name: Option[String] = None): Future[Seq[Pipeline]] = {
    val q = pipelines.filter { lib =>
      List(
        pipelineId.map(lib.id === _),
        name.map(lib.name === _)
      ).collect({ case Some(criteria) => criteria })
        .reduceLeftOption(_ && _)
        .getOrElse(true: Rep[Boolean])
    }
    db.run(q.result)
  }

  /** Return pipelineId of a specific pipelineName */
  def getPipelineId(pipelineName: String): Future[Option[Int]] = {
    getPipelines(name = Some(pipelineName)).map(_.headOption.map(_.id))
  }

  /** Return name of a pipeline */
  def getPipelineName(pipelineId: Int): Future[Option[String]] = {
    getPipelines(pipelineId = Some(pipelineId)).map(_.headOption.map(_.name))
  }

  /** Return all module with given criteria */
  def getModules(moduleId: Option[Int] = None,
                 name: Option[String] = None,
                 pipelineId: Option[Int] = None): Future[Seq[Module]] = {
    val q = modules.filter { lib =>
      List(
        moduleId.map(lib.id === _),
        pipelineId.map(lib.pipelineId === _),
        name.map(lib.name === _)
      ).collect({ case Some(criteria) => criteria })
        .reduceLeftOption(_ && _)
        .getOrElse(true: Rep[Boolean])
    }
    db.run(q.result)
  }

  /** Return moduleId of a specific moduleName */
  def getmoduleId(moduleName: String, pipelineId: Int): Future[Option[Int]] = {
    getModules(name = Some(moduleName), pipelineId = Some(pipelineId))
      .map(_.headOption.map(_.id))
  }

  /** Returns name of a module */
  def getModuleName(pipelineId: Int, moduleId: Int): Future[Option[String]] = {
    getModules(pipelineId = Some(pipelineId), moduleId = Some(moduleId))
      .map(_.headOption.map(_.name))
  }

  /** Return a Query for [[Stats]] */
  def statsFilter(runId: Option[Int] = None,
                  pipeline: Option[PipelineQuery] = None,
                  module: Option[ModuleQuery] = None,
                  sample: Option[SampleQuery] = None,
                  library: Option[LibraryQuery] = None,
                  mustHaveSample: Boolean = false,
                  mustHaveLibrary: Boolean = false)
    : slick.jdbc.H2Profile.api.Query[Stats, Stat, Seq] = {
    var f: Query[Stats, Stats#TableElementType, Seq] = stats
    runId.foreach(r => f = f.filter(_.runId === r))
    f = pipeline match {
      case Some(p: PipelineId) => f.filter(_.pipelineId === p.id)
      case Some(p: PipelineName) =>
        f.join(pipelines)
          .on(_.pipelineId === _.id)
          .filter(_._2.name === p.name)
          .map(_._1)
      case _ => f
    }
    f = module match {
      case Some(m: ModuleId) => f.filter(_.moduleId === m.id)
      case Some(m: ModuleName) =>
        f.join(modules)
          .on(_.moduleId === _.id)
          .filter(_._2.name === m.name)
          .map(_._1)
      case Some(NoModule) => f.filter(_.moduleId.isEmpty)
      case _ => f
    }
    f = sample match {
      case Some(s: SampleId) => f.filter(_.sampleId === s.id)
      case Some(s: SampleName) =>
        f.join(samples)
          .on(_.sampleId === _.id)
          .filter(_._2.name === s.name)
          .map(_._1)
      case Some(NoSample) => f.filter(_.sampleId.isEmpty)
      case _ => f
    }
    f = library match {
      case Some(l: LibraryId) => f.filter(_.libraryId === l.id)
      case Some(l: LibraryName) =>
        f.join(libraries)
          .on(_.libraryId === _.id)
          .filter(_._2.name === l.name)
          .map(_._1)
      case Some(NoLibrary) => f.filter(_.libraryId.isEmpty)
      case _ => f
    }

    if (mustHaveSample) f = f.filter(_.sampleId.nonEmpty)
    if (mustHaveLibrary) f = f.filter(_.libraryId.nonEmpty)
    f
  }

  /** Return all stats that match given criteria */
  def getStats(runId: Option[Int] = None,
               pipeline: Option[PipelineQuery] = None,
               module: Option[ModuleQuery] = None,
               sample: Option[SampleQuery] = None,
               library: Option[LibraryQuery] = None,
               mustHaveSample: Boolean = false,
               mustHaveLibrary: Boolean = false): Future[Seq[Stat]] = {
    db.run(
      statsFilter(runId,
                  pipeline,
                  module,
                  sample,
                  library,
                  mustHaveSample,
                  mustHaveLibrary).result)
  }

  /** Return number of results */
  def getStatsSize(runId: Option[Int] = None,
                   pipeline: Option[PipelineQuery] = None,
                   module: Option[ModuleQuery] = None,
                   sample: Option[SampleQuery] = None,
                   library: Option[LibraryQuery] = None,
                   mustHaveSample: Boolean = false,
                   mustHaveLibrary: Boolean = false): Future[Int] = {
    db.run(
      statsFilter(runId,
                  pipeline,
                  module,
                  sample,
                  library,
                  mustHaveSample,
                  mustHaveLibrary).size.result)
  }

  /** Get a single stat as [[Map[String, Any]] */
  def getStat(runId: Int,
              pipeline: PipelineQuery,
              module: ModuleQuery = NoModule,
              sample: SampleQuery = NoSample,
              library: LibraryQuery = NoLibrary): Future[Option[JsValue]] = {
    getStats(Some(runId), pipeline, module, sample, library)
      .map(_.headOption.map(x => Json.parse(x.content)))
  }

  def getStatKeys(runId: Int,
                  pipeline: PipelineQuery,
                  module: ModuleQuery = NoModule,
                  sample: SampleQuery = NoSample,
                  library: LibraryQuery = NoLibrary,
                  keyValues: Map[String, List[String]])
    : Map[String, Option[JsLookupResult]] = {
    val stats = Await.result(getStat(runId, pipeline, module, sample, library),
                             Duration.Inf)
    keyValues.map {
      case (key, path) =>
        stats match {
          case Some(map) => key -> Some(path.foldLeft(map.result)(_ \ _))
          case None => key -> None
        }
    }
  }

  def getStatsForSamples(runId: Int,
                         pipeline: PipelineQuery,
                         module: ModuleQuery = NoModule,
                         sample: Option[SampleQuery] = None,
                         keyValues: Map[String, List[String]])
    : Map[Int, Map[String, Option[Any]]] = {
    val samples =
      Await.result(getSamples(runId = Some(runId), sampleId = sample.collect {
        case s: SampleId => s.id
      }, name = sample.collect { case s: SampleName => s.name }), Duration.Inf)
    (for (s <- samples) yield {
      s.id -> getStatKeys(runId,
                          pipeline,
                          module,
                          SampleId(s.id),
                          NoLibrary,
                          keyValues = keyValues)
    }).toMap
  }

  def getStatsForLibraries(runId: Int,
                           pipeline: PipelineQuery,
                           module: ModuleQuery = NoModule,
                           sampleId: Option[Int] = None,
                           keyValues: Map[String, List[String]])
    : Map[(Int, Int), Map[String, Option[Any]]] = {
    val libraries =
      Await.result(getLibraries(runId = Some(runId), sampleId = sampleId),
                   Duration.Inf)
    (for (lib <- libraries) yield {
      (lib.sampleId, lib.id) -> getStatKeys(runId,
                                            pipeline,
                                            module,
                                            SampleId(lib.sampleId),
                                            LibraryId(lib.id),
                                            keyValues = keyValues)
    }).toMap
  }

  def settingsFilter(
      runId: Option[Int] = None,
      pipeline: Option[PipelineQuery] = None,
      module: Option[ModuleQuery] = None,
      sample: Option[SampleQuery] = None,
      library: Option[LibraryQuery] = None,
      mustHaveSample: Boolean = false,
      mustHaveLibrary: Boolean = false): Query[Settings, Setting, Seq] = {
    var f: Query[Settings, Settings#TableElementType, Seq] = settings
    runId.foreach(r => f = f.filter(_.runId === r))
    f = pipeline match {
      case Some(p: PipelineId) => f.filter(_.pipelineId === p.id)
      case Some(p: PipelineName) =>
        f.join(pipelines)
          .on(_.pipelineId === _.id)
          .filter(_._2.name === p.name)
          .map(_._1)
      case _ => f
    }
    f = module match {
      case Some(m: ModuleId) => f.filter(_.moduleId === m.id)
      case Some(m: ModuleName) =>
        f.join(modules)
          .on(_.moduleId === _.id)
          .filter(_._2.name === m.name)
          .map(_._1)
      case Some(NoModule) => f.filter(_.moduleId.isEmpty)
      case _ => f
    }
    f = sample match {
      case Some(s: SampleId) => f.filter(_.sampleId === s.id)
      case Some(s: SampleName) =>
        f.join(samples)
          .on(_.sampleId === _.id)
          .filter(_._2.name === s.name)
          .map(_._1)
      case Some(NoSample) => f.filter(_.sampleId.isEmpty)
      case _ => f
    }
    f = library match {
      case Some(l: LibraryId) => f.filter(_.libraryId === l.id)
      case Some(l: LibraryName) =>
        f.join(libraries)
          .on(_.libraryId === _.id)
          .filter(_._2.name === l.name)
          .map(_._1)
      case Some(NoLibrary) => f.filter(_.libraryId.isEmpty)
      case _ => f
    }

    if (mustHaveSample) f = f.filter(_.sampleId.nonEmpty)
    if (mustHaveLibrary) f = f.filter(_.libraryId.nonEmpty)
    f
  }

  /** Return all settings that match the given criteria */
  def getSettings(runId: Option[Int] = None,
                  pipeline: Option[PipelineQuery] = None,
                  module: Option[ModuleQuery] = None,
                  sample: Option[SampleQuery] = None,
                  library: Option[LibraryQuery] = None,
                  mustHaveSample: Boolean = false,
                  mustHaveLibrary: Boolean = false): Future[Seq[Setting]] = {
    db.run(
      settingsFilter(runId,
                     pipeline,
                     module,
                     sample,
                     library,
                     mustHaveSample,
                     mustHaveLibrary).result)
  }

  /** Return a specific setting as [[Map[String, Any]] */
  def getSetting(
      runId: Int,
      pipeline: PipelineQuery,
      module: ModuleQuery = NoModule,
      sample: SampleQuery = NoSample,
      library: LibraryQuery = NoLibrary): Future[Option[JsValue]] = {
    getSettings(Some(runId), Some(pipeline), module, sample, library)
      .map(_.headOption.map(x => Json.parse(x.content)))
  }

  def getSettingKeys(
      runId: Int,
      pipeline: PipelineQuery,
      module: ModuleQuery = NoModule,
      sample: SampleQuery = NoSample,
      library: LibraryQuery = NoLibrary,
      keyValues: Map[String, List[String]]): Map[String, Option[Any]] = {
    val stats = Await.result(
      getSetting(runId, pipeline, module, sample, library),
      Duration.Inf)
    keyValues.map {
      case (key, path) =>
        stats match {
          case Some(map) => key -> Some(path.foldLeft(map.result)(_ \ _))
          case None => key -> None
        }
    }
  }

  def getSettingsForSamples(runId: Int,
                            pipeline: PipelineQuery,
                            module: ModuleQuery = NoModule,
                            sampleId: Option[Int] = None,
                            keyValues: Map[String, List[String]])
    : Map[Int, Map[String, Option[Any]]] = {
    val samples = Await.result(
      getSamples(runId = Some(runId), sampleId = sampleId),
      Duration.Inf)
    (for (sample <- samples) yield {
      sample.id -> getSettingKeys(runId,
                                  pipeline,
                                  module,
                                  SampleId(sample.id),
                                  NoLibrary,
                                  keyValues = keyValues)
    }).toMap
  }

  def getSettingsForLibraries(runId: Int,
                              pipeline: PipelineQuery,
                              module: ModuleQuery = NoModule,
                              sampleId: Option[Int] = None,
                              keyValues: Map[String, List[String]])
    : Map[(Int, Int), Map[String, Option[Any]]] = {
    val libraries =
      Await.result(getLibraries(runId = Some(runId), sampleId = sampleId),
                   Duration.Inf)
    (for (lib <- libraries) yield {
      (lib.sampleId, lib.id) -> getSettingKeys(runId,
                                               pipeline,
                                               module,
                                               SampleId(lib.sampleId),
                                               LibraryId(lib.id),
                                               keyValues = keyValues)
    }).toMap
  }

  /** Return a [[Query]] for [[Files]] */
  def filesFilter(runId: Option[Int] = None,
                  pipeline: Option[PipelineQuery] = None,
                  module: Option[ModuleQuery] = None,
                  sample: Option[SampleQuery] = None,
                  library: Option[LibraryQuery] = None,
                  key: Option[String] = None,
                  pipelineName: Option[String] = None,
                  moduleName: Option[Option[String]] = None,
                  sampleName: Option[Option[String]] = None,
                  libraryName: Option[Option[String]] = None)
    : Query[Files, Files#TableElementType, Seq] = {
    var f: Query[Files, Files#TableElementType, Seq] = files
    runId.foreach(r => f = f.filter(_.runId === r))
    key.foreach(r => f = f.filter(_.key === r))

    f = pipeline match {
      case Some(p: PipelineId) => f.filter(_.pipelineId === p.id)
      case Some(p: PipelineName) =>
        f.join(pipelines)
          .on(_.pipelineId === _.id)
          .filter(_._2.name === p.name)
          .map(_._1)
      case _ => f
    }
    f = module match {
      case Some(m: ModuleId) => f.filter(_.moduleId === m.id)
      case Some(m: ModuleName) =>
        f.join(modules)
          .on(_.moduleId === _.id)
          .filter(_._2.name === m.name)
          .map(_._1)
      case Some(NoModule) => f.filter(_.moduleId.isEmpty)
      case _ => f
    }
    f = sample match {
      case Some(s: SampleId) => f.filter(_.sampleId === s.id)
      case Some(s: SampleName) =>
        f.join(samples)
          .on(_.sampleId === _.id)
          .filter(_._2.name === s.name)
          .map(_._1)
      case Some(NoSample) => f.filter(_.sampleId.isEmpty)
      case _ => f
    }
    f = library match {
      case Some(l: LibraryId) => f.filter(_.libraryId === l.id)
      case Some(l: LibraryName) =>
        f.join(libraries)
          .on(_.libraryId === _.id)
          .filter(_._2.name === l.name)
          .map(_._1)
      case Some(NoLibrary) => f.filter(_.libraryId.isEmpty)
      case _ => f
    }
    f
  }

  /** Returns all [[Files]] with the given criteria */
  def getFiles(runId: Option[Int] = None,
               pipeline: Option[PipelineQuery] = None,
               module: Option[ModuleQuery] = None,
               sample: Option[SampleQuery] = None,
               library: Option[LibraryQuery] = None,
               key: Option[String] = None): Future[Seq[Schema.File]] = {
    db.run(filesFilter(runId, pipeline, module, sample, library, key).result)
  }

  def getFile(runId: Int,
              pipeline: PipelineQuery,
              module: ModuleQuery = NoModule,
              sample: SampleQuery = NoSample,
              library: LibraryQuery = NoLibrary,
              key: String): Future[Option[Schema.File]] = {
    db.run(
        filesFilter(Some(runId),
                    Some(pipeline),
                    Some(module),
                    Some(sample),
                    Some(library),
                    Some(key)).result)
      .map(_.headOption)
  }

  /** Returns a [[Query]] for [[Executables]] */
  def executablesFilter(
      runId: Option[Int],
      toolName: Option[String]): Query[Executables, Executable, Seq] = {
    var q: Query[Executables, Executables#TableElementType, Seq] = executables
    runId.foreach(r => q = q.filter(_.runId === r))
    toolName.foreach(r => q = q.filter(_.toolName === r))
    q
  }

  /** Return all executables with given criteria */
  def getExecutables(
      runId: Option[Int] = None,
      toolName: Option[String] = None): Future[Seq[Executable]] = {
    db.run(executablesFilter(runId, toolName).result)
  }

}

object SummaryDb {

  trait PipelineQuery
  case class PipelineId(id: Int) extends PipelineQuery
  case class PipelineName(name: String) extends PipelineQuery

  trait SampleQuery
  case object NoSample extends SampleQuery
  case class SampleId(id: Int) extends SampleQuery
  case class SampleName(name: String) extends SampleQuery

  trait LibraryQuery
  case object NoLibrary extends LibraryQuery
  case class LibraryId(id: Int) extends LibraryQuery
  case class LibraryName(name: String) extends LibraryQuery

  trait ModuleQuery
  case object NoModule extends ModuleQuery
  case class ModuleId(id: Int) extends ModuleQuery
  case class ModuleName(name: String) extends ModuleQuery

  private var summaryConnections = Map[File, SummaryDbWrite]()

  /** This closing all summary that are still in the cache */
  def closeAll(): Unit = {
    summaryConnections.foreach(_._2.close())
    summaryConnections = summaryConnections.empty
  }

  /** This will open a sqlite database and create tables when the database did not exist yet */
  def openH2Summary(file: File)(implicit ec: ExecutionContext): SummaryDbWrite = {
    if (!summaryConnections.contains(file)) {
      val exist = file.exists()
      val s = openSummary(s"jdbc:h2:${file.getAbsolutePath}")
      s.createTables()
      summaryConnections += file -> s
    }
    summaryConnections(file)
  }

  def openSummary(url: String)(implicit ec: ExecutionContext): SummaryDbWrite = {
    new SummaryDbWrite(Database.forURL(url, executor = AsyncExecutor("single_thread", 1, 1000)))
  }

  def openReadOnlyH2Summary(file: File)(
      implicit ec: ExecutionContext): SummaryDbReadOnly = {
    require(file.exists(), s"File does not exist: $file")
    Logging.logger.debug(s"Opening H2 database: $file")
    openReadOnlySummary(s"jdbc:h2:${file.getAbsolutePath}")
  }

  def openReadOnlySummary(url: String)(
    implicit ec: ExecutionContext): SummaryDbReadOnly = {
    new SummaryDbReadOnly(Database.forURL(url))
  }
}
