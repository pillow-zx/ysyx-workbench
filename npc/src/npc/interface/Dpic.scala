package npc.interface

import chisel3._
import chisel3.util.circt.dpi.{DPIClockedVoidFunctionImport, DPINonVoidFunctionImport}

object Dpic {
  object fetchInst extends DPINonVoidFunctionImport[UInt] {
    override val functionName = "fetchInst"
    override def ret          = UInt(32.W)
    override val clocked      = false
    override val inputNames   = Some(Seq("addr"))
    override val outputName   = Some("inst")
  }

  object readData extends DPINonVoidFunctionImport[UInt] {
    override val functionName = "readData"
    override def ret          = UInt(32.W)
    override val clocked      = false
    override val inputNames   = Some(Seq("addr"))
    override val outputName   = Some("data")
  }

  object writeData extends DPIClockedVoidFunctionImport {
    override val functionName = "writeData"
    override val inputNames   = Some(Seq("addr", "data", "wmask"))
  }
}
