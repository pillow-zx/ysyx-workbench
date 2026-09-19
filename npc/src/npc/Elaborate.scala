package npc

import circt.stage.ChiselStage

object ElaborateOptions {
  val firtool: Array[String] = Array(
    "--default-layer-specialization=enable",
    "--verification-flavor=immediate",
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).mkString(",")
  )
}

object Elaborate extends App {
  ChiselStage.emitSystemVerilogFile(new ysyx_00000000, args, ElaborateOptions.firtool)
}
