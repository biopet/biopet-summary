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

import java.sql.Date

import slick.jdbc.H2Profile.api._

/**
  * Created by pjvan_thof on 27-1-17.
  */
object Schema {

  case class Run(id: Int,
                 name: String,
                 outputDir: String,
                 version: String,
                 commitHash: String,
                 creationDate: Date)
  class Runs(tag: Tag) extends Table[Run](tag, "Runs") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def runName = column[String]("runName")
    def outputDir = column[String]("outputDir")
    def version = column[String]("version")
    def commitHash = column[String]("commitHash")
    def creationDate = column[Date]("creationDate")

    def * =
      (id, runName, outputDir, version, commitHash, creationDate) <> (Run.tupled, Run.unapply)
  }
  val runs = TableQuery[Runs]

  case class Sample(id: Int, name: String, runId: Int, tags: Option[String])
  class Samples(tag: Tag) extends Table[Sample](tag, "Samples") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def runId = column[Int]("runId")
    def tags = column[Option[String]]("tags")

    def * = (id, name, runId, tags) <> (Sample.tupled, Sample.unapply)

    def idx = index("idx_samples", (runId, name), unique = true)

    def run = foreignKey("sample_run_fk", runId, runs)(_.id)
  }
  val samples = TableQuery[Samples]

  case class Library(id: Int,
                     name: String,
                     runId: Int,
                     sampleId: Int,
                     tags: Option[String])
  class Libraries(tag: Tag) extends Table[Library](tag, "Libraries") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def runId = column[Int]("runId")
    def sampleId = column[Int]("sampleId")
    def tags = column[Option[String]]("tags")

    def * =
      (id, name, runId, sampleId, tags) <> (Library.tupled, Library.unapply)

    def run = foreignKey("library_run_fk", runId, runs)(_.id)
    def sample = foreignKey("library_sample_fk", sampleId, samples)(_.id)

    def idx = index("idx_libraries", (runId, sampleId, name), unique = true)
  }
  val libraries = TableQuery[Libraries]

  case class Pipeline(id: Int, name: String)
  class Pipelines(tag: Tag) extends Table[Pipeline](tag, "PipelineNames") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")

    def * = (id, name) <> (Pipeline.tupled, Pipeline.unapply)

    def idx = index("idx_pipeline_names", name, unique = true)
  }
  val pipelines = TableQuery[Pipelines]

  case class Module(id: Int, name: String, pipelineId: Int)
  class Modules(tag: Tag) extends Table[Module](tag, "ModuleNames") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def pipelineId = column[Int]("pipelineId")

    def * = (id, name, pipelineId) <> (Module.tupled, Module.unapply)

    def idx = index("idx_module_names", (name, pipelineId), unique = true)
    def pipeline =
      foreignKey("module_pipeline_fk", pipelineId, pipelines)(_.id)
  }
  val modules = TableQuery[Modules]

  case class Stat(runId: Int,
                  pipelineId: Int,
                  moduleId: Option[Int],
                  sampleId: Option[Int],
                  library: Option[Int],
                  content: String)
  class Stats(tag: Tag) extends Table[Stat](tag, "Stats") {
    def runId = column[Int]("runId")
    def pipelineId = column[Int]("pipelineId")
    def moduleId = column[Option[Int]]("moduleId")
    def sampleId = column[Option[Int]]("sampleId")
    def libraryId = column[Option[Int]]("libraryId")
    def content = column[String]("content")

    def * =
      (runId, pipelineId, moduleId, sampleId, libraryId, content) <> (Stat.tupled, Stat.unapply)

    def run = foreignKey("stats_run_fk", runId, runs)(_.id)
    def pipeline = foreignKey("stats_pipeline_fk", pipelineId, pipelines)(_.id)
    def module = foreignKey("stats_module_fk", moduleId, modules)(_.id.?)
    def sample = foreignKey("stats_sample_fk", sampleId, samples)(_.id.?)
    def library = foreignKey("stats_library_fk", libraryId, libraries)(_.id.?)
  }
  val stats = TableQuery[Stats]

  case class Setting(runId: Int,
                     pipelineId: Int,
                     moduleId: Option[Int],
                     sampleId: Option[Int],
                     library: Option[Int],
                     content: String)
  class Settings(tag: Tag) extends Table[Setting](tag, "Settings") {
    def runId = column[Int]("runId")
    def pipelineId = column[Int]("pipelineId")
    def moduleId = column[Option[Int]]("moduleId")
    def sampleId = column[Option[Int]]("sampleId")
    def libraryId = column[Option[Int]]("libraryId")
    def content = column[String]("content")

    def * =
      (runId, pipelineId, moduleId, sampleId, libraryId, content) <> (Setting.tupled, Setting.unapply)

    def run = foreignKey("settings_run_fk", runId, runs)(_.id)
    def pipeline =
      foreignKey("settings_pipeline_fk", pipelineId, pipelines)(_.id)
    def module = foreignKey("settings_module_fk", moduleId, modules)(_.id.?)
    def sample = foreignKey("settings_sample_fk", sampleId, samples)(_.id.?)
    def library = foreignKey("settings_library_fk", libraryId, libraries)(_.id.?)
  }
  val settings = TableQuery[Settings]

  case class File(runId: Int,
                  pipelineId: Int,
                  moduleId: Option[Int],
                  sampleId: Option[Int],
                  libraryId: Option[Int],
                  key: String,
                  path: String,
                  md5: String,
                  link: Boolean,
                  size: Long)
  class Files(tag: Tag) extends Table[File](tag, "Files") {
    def runId = column[Int]("runId")
    def pipelineId = column[Int]("pipelineId")
    def moduleId = column[Option[Int]]("moduleId")
    def sampleId = column[Option[Int]]("sampleId")
    def libraryId = column[Option[Int]]("libraryId")
    def key = column[String]("key")
    def path =
      column[String]("path") // This should be relative to the outputDir
    def md5 = column[String]("md5")
    def link = column[Boolean]("link", O.Default(false))
    def size = column[Long]("size")

    def * =
      (runId,
       pipelineId,
       moduleId,
       sampleId,
       libraryId,
       key,
       path,
       md5,
       link,
       size) <> (File.tupled, File.unapply)

    def idx =
      index("idx_files",
            (runId, pipelineId, moduleId, sampleId, libraryId, key))

    def run = foreignKey("files_run_fk", runId, runs)(_.id)
    def pipeline = foreignKey("files_pipeline_fk", pipelineId, pipelines)(_.id)
    def module = foreignKey("files_module_fk", moduleId, modules)(_.id.?)
    def sample = foreignKey("files_sample_fk", sampleId, samples)(_.id.?)
    def library = foreignKey("files_library_fk", libraryId, libraries)(_.id.?)

  }
  val files = TableQuery[Files]

  case class Executable(runId: Int,
                        toolName: String,
                        version: Option[String] = None,
                        path: Option[String] = None,
                        javaVersion: Option[String] = None,
                        exeMd5: Option[String] = None,
                        javaMd5: Option[String] = None,
                        jarPath: Option[String] = None)
  class Executables(tag: Tag) extends Table[Executable](tag, "Executables") {
    def runId = column[Int]("runId")
    def toolName = column[String]("toolName")
    def version = column[Option[String]]("version")
    def path = column[Option[String]]("path")
    def javaVersion = column[Option[String]]("javaVersion")
    def exeMd5 = column[Option[String]]("exeMd5")
    def javaMd5 = column[Option[String]]("javaMd5")
    def jarPath = column[Option[String]]("jarPath")

    def * =
      (runId, toolName, version, path, javaVersion, exeMd5, javaMd5, jarPath) <> (Executable.tupled, Executable.unapply)

    def idx = index("idx_executables", (runId, toolName), unique = true)

    def run = foreignKey("executables_run_fk", runId, runs)(_.id)
  }
  val executables = TableQuery[Executables]

}
