package npc

import circt.stage.ChiselStage
import npc.core.Core

object Elaborate extends App {
  private val firtoolOption = Array(
    "--default-layer-specialization=enable",
    "--verification-flavor=immediate",
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).mkString(",")
  )

  ChiselStage.emitSystemVerilogFile(new Core, args, firtoolOption)
}
