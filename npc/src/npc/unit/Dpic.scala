package npc.unit

import chisel3._
import chisel3.util._
import chisel3.util.circt.dpi.{DPIClockedVoidFunctionImport, DPINonVoidFunctionImport}
import npc.bus.simplebus.{SimpleBusResp, SimpleBusSlaveIO}

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

object DpicMemoryState extends ChiselEnum {
  val idle, response = Value
}

class DpicMemory(xlen: Int) extends Module {
  require(xlen == 32, "the current DPI memory interface supports RV32 only")

  val io: SimpleBusSlaveIO = IO(new SimpleBusSlaveIO(xlen))

  private val state:   DpicMemoryState.Type = RegInit(DpicMemoryState.idle)
  private val respReg: SimpleBusResp        = Reg(new SimpleBusResp(xlen))

  io.req.ready  := !reset.asBool && state === DpicMemoryState.idle
  io.resp.valid := state === DpicMemoryState.response
  io.resp.bits  := respReg

  private val requestFire: Bool = io.req.fire
  private val writeFire:   Bool = requestFire && io.req.bits.wen

  Dpic.writeData.callWithEnable(
    writeFire,
    io.req.bits.addr,
    io.req.bits.wdata,
    io.req.bits.wmask.pad(8)
  )

  switch(state) {
    is(DpicMemoryState.idle) {
      when(requestFire) {
        respReg.error := false.B

        when(io.req.bits.wen) {
          respReg.rdata := 0.U
        }.otherwise {
          respReg.rdata := Dpic.readData.call(io.req.bits.addr)
        }
        state := DpicMemoryState.response
      }
    }
    is(DpicMemoryState.response) {
      when(io.resp.fire) {
        state := DpicMemoryState.idle
      }
    }
  }
}
