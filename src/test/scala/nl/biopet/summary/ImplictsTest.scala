package nl.biopet.summary

import org.scalatest.Matchers
import org.scalatest.testng.TestNGSuite
import nl.biopet.summary.Implicts._
import nl.biopet.summary.SummaryDb._
import org.testng.annotations.Test

class ImplictsTest extends TestNGSuite with Matchers {
  @Test
  def testPipeline(): Unit = {
    val pipelineId: PipelineQuery = 4
    pipelineId shouldBe PipelineId(4)
    val pipelineIdOpt: Option[PipelineQuery] = 4
    pipelineIdOpt shouldBe Some(PipelineId(4))
    val opt: Option[PipelineQuery] = pipelineId
    opt shouldBe Some(pipelineId)

    val pipelineString: PipelineQuery = "name"
    pipelineString shouldBe PipelineName("name")
    val pipelineStringOpt: Option[PipelineQuery] = "name"
    pipelineStringOpt shouldBe Some(PipelineName("name"))
  }

  @Test
  def testModule(): Unit = {
    val moduleId: ModuleQuery = 4
    moduleId shouldBe ModuleId(4)
    val moduleIdOpt: Option[ModuleQuery] = 4
    moduleIdOpt shouldBe Some(ModuleId(4))
    val opt: Option[ModuleQuery] = moduleId
    opt shouldBe Some(moduleId)

    val moduleString: ModuleQuery = "name"
    moduleString shouldBe ModuleName("name")
    val moduleStringOpt: Option[ModuleQuery] = "name"
    moduleStringOpt shouldBe Some(ModuleName("name"))
  }

  @Test
  def testSample(): Unit = {
    val sampleId: SampleQuery = 4
    sampleId shouldBe SampleId(4)
    val sampleIdOpt: Option[SampleQuery] = 4
    sampleIdOpt shouldBe Some(SampleId(4))
    val opt: Option[SampleQuery] = sampleId
    opt shouldBe Some(sampleId)

    val sampleString: SampleQuery = "name"
    sampleString shouldBe SampleName("name")
    val sampleStringOpt: Option[SampleQuery] = "name"
    sampleStringOpt shouldBe Some(SampleName("name"))
  }

  @Test
  def testLibrary(): Unit = {
    val libraryId: LibraryQuery = 4
    libraryId shouldBe LibraryId(4)
    val libraryIdOpt: Option[LibraryQuery] = 4
    libraryIdOpt shouldBe Some(LibraryId(4))
    val opt: Option[LibraryQuery] = libraryId
    opt shouldBe Some(libraryId)

    val libraryString: LibraryQuery = "name"
    libraryString shouldBe LibraryName("name")
    val libraryStringOpt: Option[LibraryQuery] = "name"
    libraryStringOpt shouldBe Some(LibraryName("name"))
  }

}
