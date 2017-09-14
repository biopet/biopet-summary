package nl.biopet.summary

import nl.biopet.summary.SummaryDb._

object Implicts {

  implicit def intToPipelineQuery(x: Int): PipelineQuery = PipelineId(x)
  implicit def stringToPipelineQuery(x: String): PipelineQuery =
    PipelineName(x)
  implicit def intToOptionPipelineQuery(x: Int): Option[PipelineQuery] =
    Some(PipelineId(x))
  implicit def stringToOptionPipelineQuery(x: String): Option[PipelineQuery] =
    Some(PipelineName(x))
  implicit def sampleQueryToOptionPipelineQuery(
      x: PipelineQuery): Option[PipelineQuery] =
    Some(x)

  implicit def intToModuleQuery(x: Int): ModuleQuery = ModuleId(x)
  implicit def stringToModuleQuery(x: String): ModuleQuery = ModuleName(x)
  implicit def intToOptionModuleQuery(x: Int): Option[ModuleQuery] =
    Some(ModuleId(x))
  implicit def intToOptionModuleQuery(x: String): Option[ModuleQuery] =
    Some(ModuleName(x))
  implicit def moduleQueryToOptionModuleQuery(
      x: ModuleQuery): Option[ModuleQuery] = Some(x)

  implicit def intToSampleQuery(x: Int): SampleQuery = SampleId(x)
  implicit def stringToSampleQuery(x: String): SampleQuery = SampleName(x)
  implicit def intToOptionSampleQuery(x: Int): Option[SampleQuery] =
    Some(SampleId(x))
  implicit def stringToOptionSampleQuery(x: String): Option[SampleQuery] =
    Some(SampleName(x))
  implicit def sampleQueryToOptionSampleQuery(
      x: SampleQuery): Option[SampleQuery] = Some(x)

  implicit def intToLibraryQuery(x: Int): LibraryQuery = LibraryId(x)
  implicit def stringToLibraryQuery(x: String): LibraryQuery = LibraryName(x)
  implicit def intToOptionLibraryQuery(x: Int): Option[LibraryQuery] =
    Some(LibraryId(x))
  implicit def stringToOptionLibraryQuery(x: String): Option[LibraryQuery] =
    Some(LibraryName(x))
  implicit def libraryQueryToOptionLibraryQuery(
      x: LibraryQuery): Option[LibraryQuery] = Some(x)
}
