package npc.axi4

import chisel3._
import chisel3.util._
import npc.interface._

object AXI4AdapterState extends ChiselEnum {
  val idle, sendReadAddress, waitReadResponse, sendWriteRequest, waitWriteResponse, sendResponse = Value
}

class AXI4Adapter(addrWidth: Int, dataWidth: Int, idWidth: Int = 4) extends Module {
  require(dataWidth >= 8 && isPow2(dataWidth), "AXI4 data width must be a power of two")

  val io = IO(new Bundle {
    val memory: MemorySlaveIO = new MemorySlaveIO(addrWidth, dataWidth)
    val bus:    AXI4IO        = new AXI4IO(addrWidth, dataWidth, idWidth)
  })

  private val state:       AXI4AdapterState.Type = RegInit(AXI4AdapterState.idle)
  private val requestReg:  MemoryRequest         = Reg(new MemoryRequest(addrWidth, dataWidth))
  private val responseReg: MemoryResponse        = Reg(new MemoryResponse(dataWidth))
  private val awSent:      Bool                  = RegInit(false.B)
  private val wSent:       Bool                  = RegInit(false.B)

  private val bytesPerBeat:     Int  = dataWidth / 8
  private val offsetWidth:      Int  = log2Ceil(bytesPerBeat)
  private val byteOffset:       UInt = requestReg.address(offsetWidth - 1, 0)
  private val bitShift:         UInt = byteOffset << 3
  private val shiftedWriteData: UInt = (requestReg.writeData << bitShift)(dataWidth - 1, 0)
  private val shiftedWriteMask: UInt = (requestReg.writeMask << byteOffset)(bytesPerBeat - 1, 0)

  io.memory.request.ready  := !reset.asBool && state === AXI4AdapterState.idle
  io.memory.response.valid := state === AXI4AdapterState.sendResponse
  io.memory.response.bits  := responseReg

  io.bus.aw.valid      := state === AXI4AdapterState.sendWriteRequest && !awSent
  io.bus.aw.bits.addr  := requestReg.address
  io.bus.aw.bits.id    := 0.U
  io.bus.aw.bits.len   := 0.U
  io.bus.aw.bits.size  := requestReg.size.asUInt.pad(3)
  io.bus.aw.bits.burst := 1.U
  io.bus.w.valid       := state === AXI4AdapterState.sendWriteRequest && !wSent
  io.bus.w.bits.data   := shiftedWriteData
  io.bus.w.bits.strb   := shiftedWriteMask
  io.bus.w.bits.last   := true.B
  io.bus.b.ready       := state === AXI4AdapterState.waitWriteResponse

  io.bus.ar.valid      := state === AXI4AdapterState.sendReadAddress
  io.bus.ar.bits.addr  := requestReg.address
  io.bus.ar.bits.id    := 0.U
  io.bus.ar.bits.len   := 0.U
  io.bus.ar.bits.size  := requestReg.size.asUInt.pad(3)
  io.bus.ar.bits.burst := 1.U
  io.bus.r.ready       := state === AXI4AdapterState.waitReadResponse

  private def memoryResponseCode(axiResponse: UInt): MemoryResponseCode.Type = Mux(
    axiResponse === 0.U,
    MemoryResponseCode.okay,
    Mux(axiResponse === 3.U, MemoryResponseCode.decodeError, MemoryResponseCode.accessFault)
  )

  switch(state) {
    is(AXI4AdapterState.idle) {
      when(io.memory.request.fire) {
        requestReg := io.memory.request.bits
        awSent     := false.B
        wSent      := false.B
        state      := Mux(
          io.memory.request.bits.operation === MemoryOperation.write,
          AXI4AdapterState.sendWriteRequest,
          AXI4AdapterState.sendReadAddress
        )
      }
    }
    is(AXI4AdapterState.sendReadAddress) {
      when(io.bus.ar.fire) {
        state := AXI4AdapterState.waitReadResponse
      }
    }
    is(AXI4AdapterState.waitReadResponse) {
      when(io.bus.r.fire) {
        responseReg.readData     := io.bus.r.bits.data >> bitShift
        responseReg.responseCode := memoryResponseCode(io.bus.r.bits.resp)
        state                    := AXI4AdapterState.sendResponse
      }
    }
    is(AXI4AdapterState.sendWriteRequest) {
      when(io.bus.aw.fire) {
        awSent := true.B
      }
      when(io.bus.w.fire) {
        wSent := true.B
      }
      when((awSent || io.bus.aw.fire) && (wSent || io.bus.w.fire)) {
        state := AXI4AdapterState.waitWriteResponse
      }
    }
    is(AXI4AdapterState.waitWriteResponse) {
      when(io.bus.b.fire) {
        responseReg.readData     := 0.U
        responseReg.responseCode := memoryResponseCode(io.bus.b.bits.resp)
        state                    := AXI4AdapterState.sendResponse
      }
    }
    is(AXI4AdapterState.sendResponse) {
      when(io.memory.response.fire) {
        state := AXI4AdapterState.idle
      }
    }
  }
}
