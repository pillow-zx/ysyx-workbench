package npc.bus.axi4lite

import chisel3._
import chisel3.util._

object AXI4LiteInterconnectState extends ChiselEnum {
  val idle, sendWriteRequest, waitReadResponse, waitWriteResponse = Value
}

class AXI4LiteInterconnect(addrWidth: Int, dataWidth: Int, masterCount: Int) extends Module {
  require(masterCount == 2, "the current interconnect supports exactly IFU and LSU")
  val io = IO(new Bundle {
    val upstream:   Vec[Axi4LiteSlaveIO] = Vec(masterCount, new Axi4LiteSlaveIO(addrWidth, dataWidth))
    val downstream: Axi4LiteMasterIO     = new Axi4LiteMasterIO(addrWidth, dataWidth)
  })
  private val ownerWidth: Int = math.max(1, log2Ceil(masterCount))
  private val ownerReg: UInt = Reg(UInt(ownerWidth.W))

  private val state:       AXI4LiteInterconnectState.Type = RegInit(AXI4LiteInterconnectState.idle)
  private val awSent:      Bool                           = RegInit(false.B)
  private val wSent:       Bool                           = RegInit(false.B)
  private val lastGranted: UInt                           = RegInit(0.U(ownerWidth.W))

  for (master <- io.upstream) {
    master.aw.ready := false.B
    master.w.ready  := false.B
    master.ar.ready := false.B

    master.b.valid := false.B
    master.b.bits  := 0.U.asTypeOf(master.b.bits)

    master.r.valid := false.B
    master.r.bits  := 0.U.asTypeOf(master.r.bits)
  }

  io.downstream.aw.valid := false.B
  io.downstream.aw.bits  := 0.U.asTypeOf(io.downstream.aw.bits)

  io.downstream.w.valid := false.B
  io.downstream.w.bits  := 0.U.asTypeOf(io.downstream.w.bits)

  io.downstream.ar.valid := false.B
  io.downstream.ar.bits  := 0.U.asTypeOf(io.downstream.ar.bits)

  io.downstream.b.ready := false.B
  io.downstream.r.ready := false.B

  private def hasRequest(master: Axi4LiteSlaveIO): Bool = {
    master.ar.valid || master.aw.valid || master.w.valid
  }

  private val ifuRequest: Bool = hasRequest(io.upstream(0))
  private val lsuRequest: Bool = hasRequest(io.upstream(1))

  private val selectedValid:  Bool = ifuRequest || lsuRequest
  private val selectedMaster: UInt = WireDefault(0.U(ownerWidth.W))

  when(ifuRequest && lsuRequest) {
    selectedMaster := Mux(lastGranted === 0.U, 1.U, 0.U)
  }.elsewhen(lsuRequest) {
    selectedMaster := 1.U
  }.otherwise {
    selectedMaster := 0.U
  }

  switch(state) {
    is(AXI4LiteInterconnectState.idle) {
      val selected: Axi4LiteSlaveIO = io.upstream(selectedMaster)

      val selectedHasRead  = selected.ar.valid
      val selectedHasWrite = selected.aw.valid || selected.w.valid

      when(selectedValid) {
        assert(!(selectedHasRead && selectedHasWrite))
      }

      when(selectedValid && selectedHasRead) {
        io.downstream.ar.valid := selected.ar.valid
        io.downstream.ar.bits  := selected.ar.bits
        selected.ar.ready      := io.downstream.ar.ready

        when(io.downstream.ar.fire) {
          ownerReg    := selectedMaster
          lastGranted := selectedMaster
          state       := AXI4LiteInterconnectState.waitReadResponse
        }
      }

      when(selectedValid && selectedHasWrite) {
        io.downstream.aw.valid := selected.aw.valid
        io.downstream.aw.bits  := selected.aw.bits
        selected.aw.ready      := io.downstream.aw.ready

        io.downstream.w.valid := selected.w.valid
        io.downstream.w.bits  := selected.w.bits
        selected.w.ready      := io.downstream.w.ready

        val awFire: Bool = io.downstream.aw.fire
        val wFire:  Bool = io.downstream.w.fire

        when(awFire || wFire) {
          ownerReg    := selectedMaster
          lastGranted := selectedMaster
          awSent      := awFire
          wSent       := wFire

          state := Mux(
            awFire && wFire,
            AXI4LiteInterconnectState.waitWriteResponse,
            AXI4LiteInterconnectState.sendWriteRequest
          )
        }
      }
    }
    is(AXI4LiteInterconnectState.sendWriteRequest) {
      val owner: Axi4LiteSlaveIO = io.upstream(ownerReg)

      io.downstream.aw.valid := owner.aw.valid && !awSent
      io.downstream.aw.bits  := owner.aw.bits
      owner.aw.ready         := io.downstream.aw.ready && !awSent

      io.downstream.w.valid := owner.w.valid && !wSent
      io.downstream.w.bits  := owner.w.bits
      owner.w.ready         := io.downstream.w.ready && !wSent

      val awComplete = awSent || io.downstream.aw.fire
      val wComplete  = wSent || io.downstream.w.fire

      when(io.downstream.aw.fire) {
        awSent := true.B
      }

      when(io.downstream.w.fire) {
        wSent := true.B
      }

      when(awComplete && wComplete) {
        state := AXI4LiteInterconnectState.waitWriteResponse
      }
    }
    is(AXI4LiteInterconnectState.waitReadResponse) {
      val owner = io.upstream(ownerReg)
      owner.r.valid         := io.downstream.r.valid
      owner.r.bits          := io.downstream.r.bits
      io.downstream.r.ready := owner.r.ready

      when(io.downstream.r.fire) {
        state := AXI4LiteInterconnectState.idle
      }
    }
    is(AXI4LiteInterconnectState.waitWriteResponse) {
      val owner = io.upstream(ownerReg)
      owner.b.valid         := io.downstream.b.valid
      owner.b.bits          := io.downstream.b.bits
      io.downstream.b.ready := owner.b.ready

      when(io.downstream.b.fire) {
        awSent := false.B
        wSent  := false.B
        state  := AXI4LiteInterconnectState.idle
      }
    }
  }
}