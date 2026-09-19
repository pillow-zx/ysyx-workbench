package npc.memory

import chisel3._
import chisel3.util._
import npc.interface.{MemoryMasterIO, MemorySlaveIO}

object MemorySubsystemState extends ChiselEnum {
  val idle, waitResponse = Value
}

class MemorySubsystem(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val imem:       MemorySlaveIO  = new MemorySlaveIO(addrWidth, dataWidth)
    val dmem:       MemorySlaveIO  = new MemorySlaveIO(addrWidth, dataWidth)
    val downstream: MemoryMasterIO = new MemoryMasterIO(addrWidth, dataWidth)
  })

  private val state:       MemorySubsystemState.Type = RegInit(MemorySubsystemState.idle)
  private val ownerReg:    Bool                      = RegInit(false.B)
  private val lastGranted: Bool                      = RegInit(false.B)

  private val imemPending:   Bool = io.imem.request.valid
  private val dmemPending:   Bool = io.dmem.request.valid
  private val selectedOwner: Bool = Mux(
    imemPending && dmemPending,
    !lastGranted,
    dmemPending
  )

  io.imem.request.ready  := false.B
  io.dmem.request.ready  := false.B
  io.imem.response.valid := false.B
  io.dmem.response.valid := false.B
  io.imem.response.bits  := io.downstream.response.bits
  io.dmem.response.bits  := io.downstream.response.bits

  io.downstream.request.valid  := false.B
  io.downstream.request.bits   := Mux(selectedOwner, io.dmem.request.bits, io.imem.request.bits)
  io.downstream.response.ready := false.B

  switch(state) {
    is(MemorySubsystemState.idle) {
      io.downstream.request.valid := !reset.asBool && (imemPending || dmemPending)
      io.imem.request.ready       := !reset.asBool && !selectedOwner && io.downstream.request.ready
      io.dmem.request.ready       := !reset.asBool && selectedOwner && io.downstream.request.ready

      when(io.downstream.request.fire) {
        ownerReg    := selectedOwner
        lastGranted := selectedOwner
        state       := MemorySubsystemState.waitResponse
      }
    }
    is(MemorySubsystemState.waitResponse) {
      io.imem.response.valid       := !ownerReg && io.downstream.response.valid
      io.dmem.response.valid       := ownerReg && io.downstream.response.valid
      io.downstream.response.ready := Mux(ownerReg, io.dmem.response.ready, io.imem.response.ready)

      when(io.downstream.response.fire) {
        state := MemorySubsystemState.idle
      }
    }
  }
}
