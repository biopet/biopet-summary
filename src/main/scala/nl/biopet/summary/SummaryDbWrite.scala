package nl.biopet.summary

import java.sql.Date

import nl.biopet.summary.Schema._
import nl.biopet.summary.SummaryDb._
import nl.biopet.summary.Implicts._
import slick.jdbc.H2Profile.api._
import slick.jdbc.meta.MTable

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class SummaryDbWrite(val db: Database)(implicit val ec: ExecutionContext)
    extends SummaryDb {

  /** This method will create all tables */
  def createTables(): Unit = {
    if (!tablesExist()) {
      val tables = Await.result(db.run(MTable.getTables), Duration.Inf).toList

      val setup = schemas
        .filter(t => !tables.exists(_.name.name == t.baseTableRow.tableName))
        .map(_.schema)
      if (setup.nonEmpty) {
        val setupFuture = db.run(setup.reduce(_ ++ _).create)
        Await.result(setupFuture, Duration.Inf)
      }
    }
  }

  /** This method will create or update a run and return the runId */
  def createOrUpdateRun(runName: String,
                        projectId: Int,
                        outputDir: String,
                        version: String,
                        commitHash: String,
                        creationDate: Date): Future[Int] = {
    val q = runs
      .filter(_.projectId === projectId)
      .filter(_.runName === runName)
    db.run(q.result).map(_.headOption).flatMap {
      case Some(run) =>
        db.run(
            q.update(
              run.copy(outputDir = outputDir,
                       version = version,
                       commitHash = commitHash,
                       creationDate = creationDate)))
          .map(_ => run.id)
      case _ =>
        createRun(runName,
                  projectId,
                  outputDir,
                  version,
                  commitHash,
                  creationDate)
    }
  }

  /** This method will create a new run and return the runId */
  def createRun(runName: String,
                projectId: Int,
                outputDir: String,
                version: String,
                commitHash: String,
                creationDate: Date): Future[Int] = {
    db.run(runs.map(c => (c.runName, c.projectId, c.outputDir, c.version, c.commitHash, c.creationDate)) +=
      (runName, projectId, outputDir, version, commitHash, creationDate))
      .flatMap(_ => getRuns(protectId = Some(projectId), runName = Some(runName)).map(_.head.id))
  }

  /** This method will create a new run and return the runId */
  def createProject(name: String): Future[Int] = {
    db.run(projects.map(_.name) += name)
      .flatMap(_ => getProjects(Some(name)).map(_.head.id))
  }

  /** This creates a new sample and return the sampleId */
  def createSample(name: String,
                   runId: Int,
                   tags: Option[String] = None): Future[Int] = {
    db.run(samples.map(s => (s.name, s.runId, s.tags)) += (name, runId, tags))
      .flatMap(_ => getSampleId(runId, name).map(_.head))
  }

  def createOrUpdateSample(name: String,
                           runId: Int,
                           tags: Option[String] = None): Future[Int] = {
    getSampleId(runId, name).flatMap {
      case Some(id: Int) =>
        db.run(
            samples
              .filter(_.name === name)
              .filter(_.id === id)
              .map(_.tags)
              .update(tags))
          .map(_ => id)
      case _ => createSample(name, runId, tags)
    }
  }

  /** This will create a new library */
  def createLibrary(name: String,
                    runId: Int,
                    sampleId: Int,
                    tags: Option[String] = None): Future[Int] = {
    db.run(libraries
        .map(s => (s.name, s.runId, s.sampleId, s.tags)) += (name, runId, sampleId, tags))
      .flatMap(_ => getLibraryId(runId, sampleId, name).map(_.head))
  }

  def createOrUpdateLibrary(name: String,
                            runId: Int,
                            sampleId: Int,
                            tags: Option[String] = None): Future[Int] = {
    getLibraryId(runId, sampleId, name).flatMap {
      case Some(id: Int) =>
        db.run(
            libraries
              .filter(_.name === name)
              .filter(_.id === id)
              .filter(_.sampleId === sampleId)
              .map(_.tags)
              .update(tags))
          .map(_ => id)
      case _ => createLibrary(name, runId, sampleId, tags)
    }
  }

  /** Creates a new pipeline, even if it already exist. This may give a database exeption */
  def forceCreatePipeline(name: String): Future[Int] = {
    db.run(pipelines.map(_.name) += name)
      .flatMap(_ => getPipelineId(name).map(_.get))
  }

  /** Creates a new pipeline if it does not yet exist */
  def createPipeline(name: String): Future[Int] = {
    getPipelines(name = Some(name))
      .flatMap { m =>
        if (m.isEmpty) forceCreatePipeline(name)
        else Future(m.head.id)
      }
  }

  /** Creates a new module, even if it already exist. This may give a database exeption */
  def forceCreateModule(name: String, pipelineId: Int): Future[Int] = {
    db.run(modules.map(c => (c.name, c.pipelineId)) += (name, pipelineId))
      .flatMap(_ => getmoduleId(name, pipelineId).map(_.get))
  }

  /** Creates a new module if it does not yet exist */
  def createModule(name: String, pipelineId: Int): Future[Int] = {
    getModules(name = Some(name), pipelineId = Some(pipelineId))
      .flatMap { m =>
        if (m.isEmpty) forceCreateModule(name, pipelineId)
        else Future(m.head.id)
      }
  }

  /** Create a new stat in the database, This method is need checking before */
  def createStat(runId: Int,
                 pipelineId: Int,
                 moduleId: Option[Int] = None,
                 sampleId: Option[Int] = None,
                 libId: Option[Int] = None,
                 content: String): Future[Int] = {
    db.run(
      stats +=
        Stat(runId, pipelineId, moduleId, sampleId, libId, content))
  }

  /** This create or update a stat */
  def createOrUpdateStat(runId: Int,
                         pipelineId: Int,
                         moduleId: Option[Int] = None,
                         sampleId: Option[Int] = None,
                         libId: Option[Int] = None,
                         content: String): Future[Int] = {
    val filter = statsFilter(
      Some(runId),
      pipelineId,
      Some(moduleId.map(ModuleId).getOrElse(NoModule)),
      Some(sampleId.map(SampleId).getOrElse(NoSample)),
      Some(libId.map(LibraryId).getOrElse(NoLibrary))
    )
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createStat(runId, pipelineId, moduleId, sampleId, libId, content)
    else db.run(filter.map(_.content).update(content))
  }

  /** This method creates a new setting. This method need checking before */
  def createSetting(runId: Int,
                    pipelineId: Int,
                    moduleId: Option[Int] = None,
                    sampleId: Option[Int] = None,
                    libId: Option[Int] = None,
                    content: String): Future[Int] = {
    db.run(
      settings +=
        Setting(runId, pipelineId, moduleId, sampleId, libId, content))
  }

  /** This method creates or update a setting. */
  def createOrUpdateSetting(runId: Int,
                            pipelineId: Int,
                            moduleId: Option[Int] = None,
                            sampleId: Option[Int] = None,
                            libId: Option[Int] = None,
                            content: String): Future[Int] = {
    val filter = settingsFilter(Some(runId),
                                PipelineId(pipelineId),
                                moduleId.map(ModuleId),
                                sampleId.map(SampleId),
                                libId.map(LibraryId))
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createSetting(runId, pipelineId, moduleId, sampleId, libId, content)
    else
      db.run(
        filter.update(
          Setting(runId, pipelineId, moduleId, sampleId, libId, content)))
  }

  /** Creates a file. This method will raise exception if it already exist */
  def createFile(runId: Int,
                 pipelineId: Int,
                 moduleId: Option[Int] = None,
                 sampleId: Option[Int] = None,
                 libId: Option[Int] = None,
                 key: String,
                 path: String,
                 md5: String,
                 link: Boolean = false,
                 size: Long): Future[Int] = {
    db.run(
      files +=
        Schema.File(runId,
                    pipelineId,
                    moduleId,
                    sampleId,
                    libId,
                    key,
                    path,
                    md5,
                    link,
                    size))
  }

  /** Create or update a File */
  def createOrUpdateFile(runId: Int,
                         pipelineId: Int,
                         moduleId: Option[Int] = None,
                         sampleId: Option[Int] = None,
                         libId: Option[Int] = None,
                         key: String,
                         path: String,
                         md5: String,
                         link: Boolean = false,
                         size: Long): Future[Int] = {
    val filter = filesFilter(Some(runId),
                             PipelineId(pipelineId),
                             moduleId.map(ModuleId),
                             sampleId.map(SampleId),
                             libId.map(LibraryId),
                             Some(key))
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createFile(runId,
                 pipelineId,
                 moduleId,
                 sampleId,
                 libId,
                 key,
                 path,
                 md5,
                 link,
                 size)
    else
      db.run(
        filter.update(
          Schema.File(runId,
                      pipelineId,
                      moduleId,
                      sampleId,
                      libId,
                      key,
                      path,
                      md5,
                      link,
                      size)))
  }

  /** Creates a exeutable. This method will raise expection if it already exist */
  def createExecutable(runId: Int,
                       toolName: String,
                       version: Option[String] = None,
                       path: Option[String] = None,
                       javaVersion: Option[String] = None,
                       exeMd5: Option[String] = None,
                       javaMd5: Option[String] = None,
                       jarPath: Option[String] = None): Future[Int] = {
    db.run(
      executables +=
        Executable(runId,
                   toolName,
                   version,
                   path,
                   javaVersion,
                   exeMd5,
                   javaMd5,
                   jarPath))
  }

  /** Create or update a [[Executable]] */
  def createOrUpdateExecutable(runId: Int,
                               toolName: String,
                               version: Option[String] = None,
                               path: Option[String] = None,
                               javaVersion: Option[String] = None,
                               exeMd5: Option[String] = None,
                               javaMd5: Option[String] = None,
                               jarPath: Option[String] = None): Future[Int] = {
    val filter = executablesFilter(Some(runId), Some(toolName))
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createExecutable(runId, toolName, version, javaVersion, exeMd5, javaMd5)
    else
      db.run(
        filter.update(
          Executable(runId,
                     toolName,
                     version,
                     path,
                     javaVersion,
                     exeMd5,
                     javaMd5,
                     jarPath)))
  }

}
