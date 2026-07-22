package npc.bus.axi4lite

import chisel3._
import chisel3.util._

object MMIOAddressMap {
  val SERIAL: UInt = "ha00003f8".U(32.W)
}

object AXI4LiteInterconnectState extends ChiselEnum {
  val idle, sendWriteRequest, waitReadResponse, waitWriteResponse = Value
}

class AXI4LiteInterconnect(addrWidth: Int, dataWidth: Int, masterCount: Int, slaveCount: Int) extends Module {
  require(masterCount == 2, "the current interconnect supports exactly IFU and LSU")
  require(slaveCount == 2, "the current interconnect supports exactly Uart and Dpic")
  val io = IO(new Bundle {
    val upstream:   Vec[Axi4LiteSlaveIO]  = Vec(masterCount, new Axi4LiteSlaveIO(addrWidth, dataWidth))
    val downstream: Vec[Axi4LiteMasterIO] = Vec(slaveCount, new Axi4LiteMasterIO(addrWidth, dataWidth))
  })
  private val ownerWidth:  Int = math.max(1, log2Ceil(masterCount))
  private val ownerReg:    UInt = RegInit(0.U(ownerWidth.W))
  private val targetWidth: Int  = math.max(1, log2Ceil(slaveCount))
  private val targetReg:   UInt = RegInit(0.U(targetWidth.W))

  private val state:       AXI4LiteInterconnectState.Type = RegInit(AXI4LiteInterconnectState.idle)
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

  for (slave <- io.downstream) {
    slave.aw.valid := false.B
    slave.aw.bits  := 0.U.asTypeOf(slave.aw.bits)

    slave.w.valid := false.B
    slave.w.bits  := 0.U.asTypeOf(slave.w.bits)

    slave.ar.valid := false.B
    slave.ar.bits  := 0.U.asTypeOf(slave.ar.bits)

    slave.b.ready := false.B
    slave.r.ready := false.B
  }

  private def decode(addr: UInt): UInt = {
    Mux(addr === MMIOAddressMap.SERIAL, 1.U, 0.U)
  }

  private def hasRequest(master: Axi4LiteSlaveIO): Bool = {
    master.aw.valid || master.ar.valid
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
      val owner: Axi4LiteSlaveIO = io.upstream(selectedMaster)

      val selectedHasRead:  Bool = owner.ar.valid
      val selectedHasWrite: Bool = owner.aw.valid

      when(selectedValid) {
        assert(!(selectedHasRead && selectedHasWrite))
      }

      when(selectedValid && selectedHasRead) {
        val selectedSlave: UInt = decode(owner.ar.bits.addr)
        val target: Axi4LiteMasterIO = io.downstream(selectedSlave)

        target.ar.valid := owner.ar.valid
        target.ar.bits  := owner.ar.bits
        owner.ar.ready := target.ar.ready

        when(target.ar.fire) {
          ownerReg    := selectedMaster
          lastGranted := selectedMaster
          targetReg   := selectedSlave
          state       := AXI4LiteInterconnectState.waitReadResponse
        }
      }

      when(selectedValid && selectedHasWrite) {
        val selectedSlave = decode(owner.aw.bits.addr)
        val target: Axi4LiteMasterIO = io.downstream(selectedSlave)

        target.aw.valid := owner.aw.valid
        target.aw.bits  := owner.aw.bits
        owner.aw.ready := target.aw.ready

        when(target.aw.fire) {
          ownerReg    := selectedMaster
          lastGranted := selectedMaster
          targetReg   := selectedSlave
          state       := AXI4LiteInterconnectState.sendWriteRequest
        }
      }
    }
    is(AXI4LiteInterconnectState.sendWriteRequest) {
      val owner:  Axi4LiteSlaveIO  = io.upstream(ownerReg)
      val target: Axi4LiteMasterIO = io.downstream(targetReg)


      target.w.valid := owner.w.valid
      target.w.bits  := owner.w.bits
      owner.w.ready  := target.w.ready

      when(target.w.fire) {
        state := AXI4LiteInterconnectState.waitWriteResponse
      }
    }
    is(AXI4LiteInterconnectState.waitReadResponse) {
      val owner:  Axi4LiteSlaveIO  = io.upstream(ownerReg)
      val target: Axi4LiteMasterIO = io.downstream(targetReg)
      owner.r.valid  := target.r.valid
      owner.r.bits   := target.r.bits
      target.r.ready := owner.r.ready

      when(target.r.fire) {
        state := AXI4LiteInterconnectState.idle
      }
    }
    is(AXI4LiteInterconnectState.waitWriteResponse) {
      val owner:  Axi4LiteSlaveIO  = io.upstream(ownerReg)
      val target: Axi4LiteMasterIO = io.downstream(targetReg)
      owner.b.valid  := target.b.valid
      owner.b.bits   := target.b.bits
      target.b.ready := owner.b.ready

      when(target.b.fire) {
        state  := AXI4LiteInterconnectState.idle
      }
    }
  }
}
