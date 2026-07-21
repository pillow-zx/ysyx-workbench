package npc.unit

import chisel3._
import chisel3.util._
import chisel3.util.circt.dpi.{DPIClockedVoidFunctionImport, DPINonVoidFunctionImport}
import npc.bus.axi4lite.Axi4LiteSlaveIO
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

object DpicMemorySimpleBusState extends ChiselEnum {
  val idle, response = Value
}

class DpicMemorySimpleBus(xlen: Int) extends Module {
  require(xlen == 32, "the current DPI memory interface supports RV32 only")

  val io: SimpleBusSlaveIO = IO(new SimpleBusSlaveIO(xlen))

  private val state:   DpicMemorySimpleBusState.Type = RegInit(DpicMemorySimpleBusState.idle)
  private val respReg: SimpleBusResp                 = Reg(new SimpleBusResp(xlen))

  io.req.ready  := !reset.asBool && state === DpicMemorySimpleBusState.idle
  io.resp.valid := state === DpicMemorySimpleBusState.response
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
    is(DpicMemorySimpleBusState.idle) {
      when(requestFire) {
        respReg.error := false.B

        when(io.req.bits.wen) {
          respReg.rdata := 0.U
        }.otherwise {
          respReg.rdata := Dpic.readData.call(io.req.bits.addr)
        }
        state := DpicMemorySimpleBusState.response
      }
    }
    is(DpicMemorySimpleBusState.response) {
      when(io.resp.fire) {
        state := DpicMemorySimpleBusState.idle
      }
    }
  }
}

// TODO: Use two wait state to replace idle to splite write and read
object DpicMemoryAxi4LiteState extends ChiselEnum {
  val idle, writeResponse, readResponse = Value
}

class DpicMemoryAxi4Lite(addrWidth: Int, dataWidth: Int) extends Module {
  val io: Axi4LiteSlaveIO = IO(new Axi4LiteSlaveIO(addrWidth, dataWidth))

  private val state:       DpicMemoryAxi4LiteState.Type = RegInit(DpicMemoryAxi4LiteState.idle)
  private val awAddr:      UInt                         = RegInit(0.U(addrWidth.W))
  private val awAddrValid: Bool                         = RegInit(false.B)
  private val wData:       UInt                         = RegInit(0.U(dataWidth.W))
  private val wDataValid:  Bool                         = RegInit(false.B)
  private val wStrb:       UInt                         = RegInit(0.U((dataWidth / 8).W))
  private val rData:       UInt                         = RegInit(0.U(dataWidth.W))

  io.aw.ready    := !reset.asBool && state === DpicMemoryAxi4LiteState.idle && !awAddrValid
  io.w.ready     := !reset.asBool && state === DpicMemoryAxi4LiteState.idle && !wDataValid
  io.b.valid     := state === DpicMemoryAxi4LiteState.writeResponse
  io.b.bits.resp := 0.U // assert if error in verilator, return 0 default

  // INFO: Cannot handle aw/w/ar in one cycle
  io.ar.ready    := !reset.asBool && state === DpicMemoryAxi4LiteState.idle
  io.r.valid     := !reset.asBool && state === DpicMemoryAxi4LiteState.readResponse
  io.r.bits.resp := 0.U // same to below
  io.r.bits.data := rData

  switch(state) {
    is(DpicMemoryAxi4LiteState.idle) {
      val awAvailable: Bool = awAddrValid || io.aw.fire
      val wAvailable:  Bool = wDataValid || io.w.fire
      val writeNow:    Bool = awAvailable && wAvailable

      val selectedAddr:  UInt = Mux(io.aw.fire, io.aw.bits.addr, awAddr)
      val selectedData:  UInt = Mux(io.w.fire, io.w.bits.data, wData)
      val selectedWStrb: UInt = Mux(io.w.fire, io.w.bits.strb, wStrb)

      when(io.aw.fire) {
        awAddr      := io.aw.bits.addr
        awAddrValid := true.B
        state       := DpicMemoryAxi4LiteState.idle
      }
      when(io.w.fire) {
        wData      := io.w.bits.data
        wStrb      := io.w.bits.strb
        wDataValid := true.B
        state      := DpicMemoryAxi4LiteState.idle
      }
      when(writeNow) {
        Dpic.writeData.callWithEnable(
          true.B,
          selectedAddr,
          selectedData,
          selectedWStrb.pad(8)
        )
        awAddrValid := false.B
        wDataValid  := false.B
        state       := DpicMemoryAxi4LiteState.writeResponse
      }
      when(io.ar.fire) {
        rData := Dpic.readData.call(io.ar.bits.addr)
        state := DpicMemoryAxi4LiteState.readResponse
      }
    }
    is(DpicMemoryAxi4LiteState.writeResponse) {
      when(io.b.fire) {
        state := DpicMemoryAxi4LiteState.idle
      }
    }
    is(DpicMemoryAxi4LiteState.readResponse) {
      when(io.r.fire) {
        state := DpicMemoryAxi4LiteState.idle
      }
    }
  }
}
