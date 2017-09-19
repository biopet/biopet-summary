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

import java.io.File
import java.sql.Date

import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import org.testng.annotations.Test
import play.api.libs.json.{JsDefined, JsString, Json}
import nl.biopet.summary.SummaryDb._
import nl.biopet.summary.Implicts._
import nl.biopet.summary.Schema.{Project, Setting, Stat}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
  * Testing for [[SummaryDb]]
  * Created by pjvanthof on 14/02/2017.
  */
class SummaryDbTest extends TestNGSuite with Matchers {

  @Test
  def testProjects(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    Await.result(db.getProjects(), Duration.Inf) shouldBe empty
    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    Await.result(db.getProjects(), Duration.Inf) shouldBe List(Project(projectId, "name"))
    Await.result(db.getProjects(name = Some("name")), Duration.Inf) shouldBe List(Project(projectId, "name"))
    Await.result(db.getProject(projectId), Duration.Inf) shouldBe Some(Project(projectId, "name"))
  }

  @Test
  def testRuns(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val date = new Date(System.currentTimeMillis())

    Await.result(db.getRuns(), Duration.Inf) shouldBe empty
    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("name", projectId, "dir", "version", "hash", date), Duration.Inf)
    Await.result(db.getRuns(), Duration.Inf).size shouldBe 1
    val runIdAgain = Await.result(db.createOrUpdateRun("name", projectId, "dir", "version", "hash", date), Duration.Inf)
    Await.result(db.getRuns(), Duration.Inf).size shouldBe 1
    runId shouldBe runIdAgain
    val run1 = Schema.Run(runId, projectId, "name", "dir", "version", "hash", date)
    val runs = Await.result(db.getRuns(), Duration.Inf)
    runs shouldBe Await.result(db.getRuns(runName = Some("name")), Duration.Inf)
    runs shouldBe Await.result(db.getRuns(outputDir = Some("dir")), Duration.Inf)
    runs shouldBe Await.result(db.getRuns(runId = Some(runId)), Duration.Inf)
    runs shouldBe Await.result(db.getRuns(runId = Some(runId), outputDir = Some("dir")), Duration.Inf)
    runs.size shouldBe 1
    runs.head.id shouldBe runId
    runs.head.name shouldBe run1.name
    runs.head.outputDir shouldBe run1.outputDir
    runs.head.commitHash shouldBe run1.commitHash
    Await.result(db.createOrUpdateRun("name2", projectId, "dir", "version", "hash", date), Duration.Inf)
    val runs2 = Await.result(db.getRuns(), Duration.Inf)
    runs2.size shouldBe 2
    runs2.map(_.name) shouldBe List("name", "name2")

    db.close()
  }

  @Test
  def testSamples(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val date = new Date(System.currentTimeMillis())

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("name", projectId, "dir", "version", "hash", date), Duration.Inf)
    Await.result(db.getSamples(), Duration.Inf) shouldBe empty
    val sampleId = Await.result(db.createSample("test_sample", runId), Duration.Inf)
    Await.result(db.createOrUpdateSample("test_sample", runId), Duration.Inf) shouldBe sampleId
    Await.result(db.getSamples(), Duration.Inf) shouldBe Seq(
      Schema.Sample(sampleId, "test_sample", runId, None))
    Await.result(db.getSampleName(sampleId), Duration.Inf) shouldBe Some("test_sample")
    Await.result(db.getSampleId(runId, "test_sample"), Duration.Inf) shouldBe Some(sampleId)
    Await.result(db.getSampleTags(sampleId), Duration.Inf) shouldBe None
    Await.result(db.createOrUpdateSample("test_sample", runId, Some("""{"test": "test"}""")),
                 Duration.Inf) shouldBe sampleId
    Await.result(db.getSampleTags(sampleId), Duration.Inf) shouldBe Some(Json.toJson(Map("test" -> "test")))

    val sampleId2 = Await.result(
      db.createOrUpdateSample("test_sample2", runId, Some("""{"test": "test"}""")),
      Duration.Inf)
    Await.result(db.getSampleTags(sampleId2), Duration.Inf) shouldBe Some(Json.toJson(Map("test" -> "test")))
    Await.result(db.getSamples(), Duration.Inf) shouldBe Seq(
      Schema.Sample(sampleId, "test_sample", runId, Some("""{"test": "test"}""")),
      Schema.Sample(sampleId2, "test_sample2", runId, Some("""{"test": "test"}""")))

    db.close()
  }

  @Test
  def testLibraries(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val date = new Date(System.currentTimeMillis())

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("name", projectId, "dir", "version", "hash", date), Duration.Inf)
    val sampleId = Await.result(db.createSample("test_sample", runId), Duration.Inf)
    Await.result(db.getLibraries(), Duration.Inf) shouldBe empty
    val libraryId = Await.result(db.createLibrary("test_lib", sampleId), Duration.Inf)
    Await.result(db.createOrUpdateLibrary("test_lib", sampleId), Duration.Inf) shouldBe libraryId

    Await.result(db.getLibraries(), Duration.Inf) shouldBe Seq(
      Schema.Library(libraryId, "test_lib", sampleId, None))
    Await.result(db.getLibraryName(libraryId), Duration.Inf) shouldBe Some("test_lib")
    Await.result(db.getLibraryId(sampleId, "test_lib"), Duration.Inf) shouldBe Some(
      libraryId)
    Await.result(db.getLibraryTags(sampleId), Duration.Inf) shouldBe None

    val libraryId2 = Await.result(
      db.createOrUpdateLibrary("test_lib2", sampleId, Some("""{"test": "test"}""")),
      Duration.Inf)
    Await.result(db.getLibraryTags(libraryId2), Duration.Inf) shouldBe Some(Json.toJson(Map("test" -> "test")))
    Await.result(db.getLibraries(), Duration.Inf) shouldBe Seq(
      Schema.Library(sampleId, "test_lib", sampleId, None),
      Schema.Library(libraryId2, "test_lib2", sampleId, Some("""{"test": "test"}""")))

    db.close()
  }

  @Test
  def testPipelines(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    Await.result(db.getPipelines(), Duration.Inf) shouldBe empty
    Await.result(db.getPipelineName(0), Duration.Inf) shouldBe None
    val pipelineId = Await.result(db.createPipeline("test"), Duration.Inf)
    Await.result(db.getPipelineName(pipelineId), Duration.Inf) shouldBe Some("test")
    Await.result(db.getPipelines(), Duration.Inf) shouldBe Seq(
      Schema.Pipeline(pipelineId, "test"))
    Await.result(db.getPipelines(pipelineId = Some(pipelineId), name = Some("test")), Duration.Inf) shouldBe Seq(
      Schema.Pipeline(pipelineId, "test"))
    Await.result(db.getPipelineId("test"), Duration.Inf) shouldBe Some(pipelineId)
    Await.result(db.createPipeline("test"), Duration.Inf) shouldBe pipelineId
    Await.result(db.getPipelines(), Duration.Inf) shouldBe Seq(
      Schema.Pipeline(pipelineId, "test"))

    db.close()
  }

  @Test
  def testModules(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val pipelineId = Await.result(db.createPipeline("test"), Duration.Inf)
    Await.result(db.getModules(), Duration.Inf) shouldBe empty
    Await.result(db.getModuleName(pipelineId, 0), Duration.Inf) shouldBe None
    val moduleId = Await.result(db.createModule("test", pipelineId), Duration.Inf)
    Await.result(db.getmoduleId("test", pipelineId), Duration.Inf) shouldBe Some(moduleId)
    Await.result(db.getModuleName(pipelineId, moduleId), Duration.Inf) shouldBe Some("test")
    Await.result(db.getModules(), Duration.Inf) shouldBe Seq(
      Schema.Module(pipelineId, "test", pipelineId))
    Await.result(db.createModule("test", pipelineId), Duration.Inf) shouldBe pipelineId
    Await.result(db.getModules(), Duration.Inf) shouldBe Seq(
      Schema.Module(pipelineId, "test", pipelineId))

    db.close()
  }

  @Test
  def testSettings(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.delete()
    val db = SummaryDb.openH2Summary(dbFile)
    dbFile.deleteOnExit()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("sample", runId), Duration.Inf)
    val libraryId = Await.result(db.createLibrary("library", sampleId), Duration.Inf)

    Await.result(db.createOrUpdateSetting(runId, pipelineId, Some(moduleId), Some(sampleId), Some(libraryId), """{"content": "test" }"""),
                 Duration.Inf)
    Await.result(db.getSetting(runId, "pipeline", "module", "sample", "library"), Duration.Inf) shouldBe Some(
      Json.toJson(Map("content" -> "test")))
    Await.result(db.getSettings(Some(runId), Some("pipeline"), Some("module"), mustHaveLibrary = true, mustHaveSample = true), Duration.Inf) shouldBe
      List(Setting(runId,pipelineId,Some(moduleId),Some(sampleId),Some(libraryId),"""{"content": "test" }"""))
    Await.result(db.getSetting(runId, pipelineId, moduleId, sampleId, libraryId), Duration.Inf) shouldBe Some(
      Json.toJson(Map("content" -> "test")))
    Await.result(db.getSetting(runId, pipelineId, NoModule, NoSample, NoLibrary), Duration.Inf) shouldBe None
    Await.result(db.createOrUpdateSetting(runId, pipelineId, Some(moduleId), Some(sampleId), Some(libraryId), """{"content": "test2" }"""),
                 Duration.Inf)
    Await.result(db.getSetting(runId, pipelineId, moduleId, sampleId, libraryId), Duration.Inf) shouldBe Some(
      Json.toJson(Map("content" -> "test2")))
    db.close()
  }

  @Test
  def testSettingKeys(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("sample", runId), Duration.Inf)
    val libraryId = Await.result(db.createLibrary("sample", sampleId), Duration.Inf)

    Await.result(
      db.createOrUpdateSetting(
        runId,
        pipelineId,
        Some(moduleId),
        Some(sampleId),
        Some(libraryId),
        """
        |{
        |"content": "test",
        |"content2": {
        |  "key": "value"
        |}
        | }""".stripMargin
      ),
      Duration.Inf
    )

    db.getSettingKeys(runId, pipelineId, moduleId, sampleId, libraryId, keyValues = Map()) shouldBe Map()
    db.getSettingKeys(runId, pipelineId, moduleId, sampleId, NoLibrary, keyValues = Map("content" -> List("content"))) shouldBe Map("content" -> None)
    db.getSettingKeys(runId, pipelineId, moduleId, sampleId, libraryId, keyValues = Map("content" -> List("content"))) shouldBe Map(
      "content" -> Some(JsDefined(JsString("test"))))
    db.getSettingKeys(runId, pipelineId, moduleId, sampleId, libraryId, keyValues = Map("content" -> List("content2", "key"))) shouldBe Map(
      "content" -> Some(JsDefined(JsString("value"))))

    db.close()
  }

  @Test
  def testSettingsForSamples(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("sample", runId), Duration.Inf)

    Await.result(
      db.createOrUpdateSetting(
        runId,
        pipelineId,
        Some(moduleId),
        Some(sampleId),
        None,
        """
        |{
        |"content": "test",
        |"content2": {
        |  "key": "value"
        |}
        | }""".stripMargin
      ),
      Duration.Inf
    )

    db.getSettingsForSamples(runId, pipelineId, moduleId, keyValues = Map()) shouldBe Map(sampleId -> Map())
    db.getSettingsForSamples(runId, pipelineId, moduleId, keyValues = Map("content" -> List("content"))) shouldBe Map(
      sampleId -> Map("content" -> Some(JsDefined(JsString("test")))))

    db.close()
  }

  @Test
  def testSettingsForLibraries(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("sample", runId), Duration.Inf)
    val libraryId = Await.result(db.createLibrary("sample", sampleId), Duration.Inf)

    Await.result(
      db.createOrUpdateSetting(
        runId,
        pipelineId,
        Some(moduleId),
        Some(sampleId),
        Some(libraryId),
        """
        |{
        |"content": "test",
        |"content2": {
        |  "key": "value"
        |}
        | }""".stripMargin
      ),
      Duration.Inf
    )

    db.getSettingsForLibraries(runId, pipelineId, moduleId, keyValues = Map()) shouldBe Map((sampleId, libraryId) -> Map())
    db.getSettingsForLibraries(runId, pipelineId, moduleId, keyValues = Map("content" -> List("content"))) shouldBe Map(
      (sampleId, libraryId) -> Map("content" -> Some(JsDefined(Json.toJson("test")))))

    db.close()
  }

  @Test
  def testStats(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(
      db.createRun("test", projectId, "", "", "", new Date(System.currentTimeMillis())),
      Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("test_pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("test_module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("test_sample", runId), Duration.Inf)
    val libraryId = Await.result(db.createLibrary("test_library", sampleId), Duration.Inf)

    Await.result(db.getStatsSize(), Duration.Inf) shouldBe 0

    Await.result(
      db.createOrUpdateStat(runId, pipelineId, None, None, None, """{"content": "test" }"""),
      Duration.Inf)
    Await.result(db.getStat(runId, pipelineId, NoModule, NoSample, NoLibrary), Duration.Inf) shouldBe Some(
      Json.toJson(Map("content" -> "test")))
    Await.result(db.getStatsSize(), Duration.Inf) shouldBe 1
    Await.result(
      db.createOrUpdateStat(runId, pipelineId, None, None, None, """{"content": "test2" }"""),
      Duration.Inf)
    Await.result(db.getStat(runId, pipelineId, NoModule, NoSample, NoLibrary), Duration.Inf) shouldBe Some(
      Json.toJson(Map("content" -> "test2")))
    Await.result(db.getStatsSize(), Duration.Inf) shouldBe 1

    // Test join queries
    Await.result(db.createOrUpdateStat(runId,
                                       pipelineId,
                                       Some(moduleId),
                                       Some(sampleId),
                                       Some(libraryId),
                                       """{"content": "test3" }"""),
                 Duration.Inf)
    Await.result(db.getStat(runId, "test_pipeline", "test_module", "test_sample", "test_library"),
      Duration.Inf) shouldBe Some(Json.toJson(Map("content" -> "test3")))
    Await.result(db.getStats(Some(runId), Some("test_pipeline"), Some("test_module"), mustHaveSample = true, mustHaveLibrary = true),
      Duration.Inf) shouldBe Seq(Stat(runId,pipelineId,Some(moduleId),Some(sampleId),Some(libraryId),"""{"content": "test3" }"""))
    Await.result(db.getStatsSize(), Duration.Inf) shouldBe 2

    db.close()
  }

  @Test
  def testStatKeys(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("sample", runId), Duration.Inf)
    val libraryId = Await.result(db.createLibrary("sample", sampleId), Duration.Inf)

    Await.result(
      db.createOrUpdateStat(
        runId,
        pipelineId,
        Some(moduleId),
        Some(sampleId),
        Some(libraryId),
        """
        |{
        |"content": "test",
        |"content2": {
        |  "key": "value"
        |}
        | }""".stripMargin
      ),
      Duration.Inf
    )

    db.getStatKeys(runId, pipelineId, moduleId, sampleId, libraryId, keyValues = Map()) shouldBe Map()
    db.getStatKeys(runId, pipelineId, moduleId, sampleId, NoLibrary, keyValues = Map("content" -> List("content"))) shouldBe Map("content" -> None)
    db.getStatKeys(runId, pipelineId, moduleId, sampleId, libraryId, keyValues = Map("content" -> List("content"))) shouldBe Map(
      "content" -> Some(JsDefined(JsString("test"))))
    db.getStatKeys(runId, pipelineId, moduleId, sampleId, libraryId, keyValues = Map("content" -> List("content2", "key"))) shouldBe Map(
      "content" -> Some(JsDefined(JsString("value"))))

    db.close()
  }

  @Test
  def testStatsForSamples(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("sample", runId), Duration.Inf)

    Await.result(
      db.createOrUpdateStat(
        runId,
        pipelineId,
        Some(moduleId),
        Some(sampleId),
        None,
        """
        |{
        |"content": "test",
        |"content2": {
        |  "key": "value"
        |}
        | }""".stripMargin
      ),
      Duration.Inf
    )

    db.getStatsForSamples(runId, pipelineId, moduleId, keyValues = Map()) shouldBe Map(sampleId -> Map())
    db.getStatsForSamples(runId, pipelineId, moduleId, keyValues = Map("content" -> List("content"))) shouldBe Map(
      sampleId -> Map("content" -> Some(JsDefined(JsString("test")))))
    db.getStatsForSamples(runId, pipelineId, moduleId, sample = sampleId, keyValues = Map("content" -> List("content"))) shouldBe Map(
      sampleId -> Map("content" -> Some(JsDefined(JsString("test")))))
    db.getStatsForSamples(runId, pipelineId, moduleId, sample = "sample", keyValues = Map("content" -> List("content"))) shouldBe Map(
      sampleId -> Map("content" -> Some(JsDefined(JsString("test")))))

    db.close()
  }

  @Test
  def testStatsForLibraries(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("sample", runId), Duration.Inf)
    val libraryId = Await.result(db.createLibrary("sample", sampleId), Duration.Inf)

    Await.result(
      db.createOrUpdateStat(
        runId,
        pipelineId,
        Some(moduleId),
        Some(sampleId),
        Some(libraryId),
        """
        |{
        |"content": "test",
        |"content2": {
        |  "key": "value"
        |}
        | }""".stripMargin
      ),
      Duration.Inf
    )

    db.getStatsForLibraries(runId, pipelineId, moduleId, keyValues = Map()) shouldBe Map((sampleId, libraryId) -> Map())
    db.getStatsForLibraries(runId, pipelineId, moduleId, keyValues = Map("content" -> List("content"))) shouldBe Map(
      (sampleId, libraryId) -> Map("content" -> Some(JsDefined(JsString("test")))))

    db.close()
  }

  @Test
  def testFiles(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(
      db.createRun("test", projectId, "", "", "", new Date(System.currentTimeMillis())),
      Duration.Inf)
    val pipelineId = Await.result(db.createPipeline("test_pipeline"), Duration.Inf)
    val moduleId = Await.result(db.createModule("test_module", pipelineId), Duration.Inf)
    val sampleId = Await.result(db.createSample("test_sample", runId), Duration.Inf)
    val libraryId = Await.result(db.createLibrary("test_library", sampleId), Duration.Inf)

    Await.result(db.createOrUpdateFile(runId,
                                       pipelineId,
                                       None,
                                       None,
                                       None,
                                       "key",
                                       "path",
                                       "md5",
                                       link = false,
                                       1),
                 Duration.Inf)
    Await.result(db.getFile(runId, pipelineId, NoModule, NoSample, NoLibrary, "key"), Duration.Inf) shouldBe Some(
      Schema.File(runId, pipelineId, None, None, None, "key", "path", "md5", link = false, 1))
    Await.result(db.getFiles(), Duration.Inf) shouldBe Seq(
      Schema.File(runId, pipelineId, None, None, None, "key", "path", "md5", link = false, 1))
    Await.result(db.createOrUpdateFile(runId,
                                       pipelineId,
                                       None,
                                       None,
                                       None,
                                       "key",
                                       "path2",
                                       "md5",
                                       link = false,
                                       1),
                 Duration.Inf)
    Await.result(db.getFile(runId, pipelineId, NoModule, NoSample, NoLibrary, "key"), Duration.Inf) shouldBe Some(
      Schema.File(runId, pipelineId, None, None, None, "key", "path2", "md5", link = false, 1))
    Await.result(db.getFiles(), Duration.Inf) shouldBe Seq(
      Schema.File(runId, pipelineId, None, None, None, "key", "path2", "md5", link = false, 1))

    // Test join queries
    Await.result(db.createOrUpdateFile(runId,
                                       pipelineId,
                                       Some(moduleId),
                                       Some(sampleId),
                                       Some(libraryId),
                                       "key",
                                       "path3",
                                       "md5",
                                       link = false,
                                       1),
                 Duration.Inf)
    Await.result(
      db.getFile(runId, "test_pipeline", "test_module", "test_sample", "test_library", "key"),
      Duration.Inf) shouldBe Some(
      Schema.File(runId, pipelineId,
                  Some(moduleId),
                  Some(sampleId),
                  Some(libraryId),
                  "key",
                  "path3",
                  "md5",
                  link = false,
                  1))
    Await.result(db.getFiles(), Duration.Inf) shouldBe Seq(
      Schema.File(runId, pipelineId, None, None, None, "key", "path2", "md5", link = false, 1),
      Schema.File(runId, pipelineId,
                  Some(moduleId),
                  Some(sampleId),
                  Some(libraryId),
                  "key",
                  "path3",
                  "md5",
                  link = false,
                  1)
    )

    db.close()
  }

  @Test
  def testExecutable(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    dbFile.deleteOnExit()
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("run", projectId, "dir", "version", "hash", new Date(System.currentTimeMillis())), Duration.Inf)

    Await.result(db.createOrUpdateExecutable(runId, "name"), Duration.Inf)
    Await.result(db.createOrUpdateExecutable(runId, "name", Some("test")), Duration.Inf)
    Await.result(db.getExecutables(Some(runId)), Duration.Inf).head shouldBe Schema.Executable(
      runId,
      "name",
      Some("test"))
    db.close()
  }

  @Test
  def testReadOnly(): Unit = {
    val dbFile = File.createTempFile("summary.", ".db")
    val db = SummaryDb.openH2Summary(dbFile)
    db.createTables()

    val date = new Date(System.currentTimeMillis())

    val projectId = Await.result(db.createProject("name"), Duration.Inf)
    val runId = Await.result(db.createRun("name", projectId, "dir", "version", "hash", date), Duration.Inf)

    val readOnlyDb = SummaryDb.openReadOnlyH2Summary(dbFile)

    val run1 = Schema.Run(runId, projectId, "name", "dir", "version", "hash", date)
    val runs = Await.result(readOnlyDb.getRuns(), Duration.Inf)
    runs.size shouldBe 1
    runs.head.id shouldBe runId
    runs.head.name shouldBe run1.name
    runs.head.outputDir shouldBe run1.outputDir
    runs.head.commitHash shouldBe run1.commitHash

    SummaryDb.closeAll()
    dbFile.delete()
    intercept[IllegalArgumentException] {
      SummaryDb.openReadOnlyH2Summary(dbFile)
    }
  }

}
