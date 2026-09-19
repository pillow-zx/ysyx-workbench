package npc.memory

import chisel3._
import chisel3.util._
import npc.interface.{MemoryMasterIO, MemorySlaveIO}

object MemoryInterconnectState extends ChiselEnum {
  val idle, waitResponse = Value
}

class MemoryInterconnect(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val upstream: MemorySlaveIO  = new MemorySlaveIO(addrWidth, dataWidth)
    val memory:   MemoryMasterIO = new MemoryMasterIO(addrWidth, dataWidth)
    val clint:    MemoryMasterIO = new MemoryMasterIO(addrWidth, dataWidth)
  })

  private val state:       MemoryInterconnectState.Type = RegInit(MemoryInterconnectState.idle)
  private val targetClint: Bool                         = RegInit(false.B)

  private val decodedClint: Bool = SimulationAddressMap.Clint.contains(io.upstream.request.bits.address)
  private val requestReady: Bool = Mux(decodedClint, io.clint.request.ready, io.memory.request.ready)
  private val responseValid: Bool = Mux(targetClint, io.clint.response.valid, io.memory.response.valid)
  private val responseBits = Mux(targetClint, io.clint.response.bits, io.memory.response.bits)

  io.upstream.request.ready  := false.B
  io.upstream.response.valid := false.B
  io.upstream.response.bits  := responseBits

  io.memory.request.valid  := false.B
  io.memory.request.bits   := io.upstream.request.bits
  io.memory.response.ready := false.B

  io.clint.request.valid  := false.B
  io.clint.request.bits   := io.upstream.request.bits
  io.clint.response.ready := false.B

  switch(state) {
    is(MemoryInterconnectState.idle) {
      io.upstream.request.ready := !reset.asBool && requestReady

      when(io.upstream.request.valid) {
        io.memory.request.valid := !reset.asBool && !decodedClint
        io.clint.request.valid  := !reset.asBool && decodedClint
      }

      when(io.upstream.request.fire) {
        targetClint := decodedClint
        state       := MemoryInterconnectState.waitResponse
      }
    }
    is(MemoryInterconnectState.waitResponse) {
      io.upstream.response.valid := responseValid
      io.memory.response.ready   := io.upstream.response.ready && !targetClint
      io.clint.response.ready    := io.upstream.response.ready && targetClint

      when(io.upstream.response.fire) {
        state := MemoryInterconnectState.idle
      }
    }
  }
}
