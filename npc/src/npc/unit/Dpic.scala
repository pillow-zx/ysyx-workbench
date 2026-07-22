package npc.unit

import chisel3._
import chisel3.util._
import chisel3.util.circt.dpi.{DPIClockedVoidFunctionImport, DPINonVoidFunctionImport}
import npc.interface.{MemoryOperation, MemoryResponse, MemoryResponseCode, MemorySlaveIO}

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

object DpicState extends ChiselEnum {
  val idle, response = Value
}

class DpicMemory(addrWidth: Int, dataWidth: Int) extends Module {
  require(addrWidth == 32, "the current DPI memory interface supports RV32 only")
  require(dataWidth == 32, "the current DPI memory interface supports RV32 only")

  val io: MemorySlaveIO = IO(new MemorySlaveIO(addrWidth, dataWidth))

  private val state:       DpicState.Type = RegInit(DpicState.idle)
  private val responseReg: MemoryResponse = Reg(new MemoryResponse(dataWidth))

  io.request.ready  := !reset.asBool && state === DpicState.idle
  io.response.valid := state === DpicState.response
  io.response.bits  := responseReg

  private val requestFire: Bool = io.request.fire
  private val writeFire:   Bool = requestFire && io.request.bits.operation === MemoryOperation.write

  Dpic.writeData.callWithEnable(
    writeFire,
    io.request.bits.address,
    io.request.bits.writeData,
    io.request.bits.writeMask.pad(8)
  )

  switch(state) {
    is(DpicState.idle) {
      when(requestFire) {
        responseReg.code := MemoryResponseCode.okay

        when(io.request.bits.operation === MemoryOperation.write) {
          responseReg.readData := 0.U
        }.otherwise {
          responseReg.readData := Dpic.readData.call(io.request.bits.address)
        }
        state := DpicState.response
      }
    }
    is(DpicState.response) {
      when(io.response.fire) {
        state := DpicState.idle
      }
    }
  }
}
