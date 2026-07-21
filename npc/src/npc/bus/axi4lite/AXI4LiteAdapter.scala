package npc.bus.axi4lite

import chisel3._
import chisel3.util._
import npc.interface.{MemoryMasterIO, MemoryOperation, MemoryRequest, MemoryResponse, MemoryResponseCode, MemorySlaveIO}

object Axi4LiteMasterAdapterState extends ChiselEnum {
  val idle, sendReadAddress, waitReadResponse, sendWriteRequest, waitWriteResponse, sendResponse = Value
}

class Axi4LiteMasterAdapter(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val memory: MemorySlaveIO    = new MemorySlaveIO(addrWidth, dataWidth)
    val bus:    Axi4LiteMasterIO = new Axi4LiteMasterIO(addrWidth, dataWidth)
  })

  private val state:       Axi4LiteMasterAdapterState.Type = RegInit(Axi4LiteMasterAdapterState.idle)
  private val requestReg:  MemoryRequest                   = Reg(new MemoryRequest(addrWidth, dataWidth))
  private val responseReg: MemoryResponse                  = Reg(new MemoryResponse(dataWidth))
  private val awSent:      Bool                            = RegInit(false.B)
  private val wSent:       Bool                            = RegInit(false.B)

  io.memory.request.ready  := !reset.asBool && state === Axi4LiteMasterAdapterState.idle
  io.memory.response.valid := state === Axi4LiteMasterAdapterState.sendResponse
  io.memory.response.bits  := responseReg

  io.bus.aw.valid     := state === Axi4LiteMasterAdapterState.sendWriteRequest && !awSent
  io.bus.aw.bits.addr := requestReg.address
  io.bus.aw.bits.prot := 0.U
  io.bus.w.valid      := state === Axi4LiteMasterAdapterState.sendWriteRequest && !wSent
  io.bus.w.bits.data  := requestReg.writeData
  io.bus.w.bits.strb  := requestReg.writeMask
  io.bus.b.ready      := state === Axi4LiteMasterAdapterState.waitWriteResponse

  io.bus.ar.valid     := state === Axi4LiteMasterAdapterState.sendReadAddress
  io.bus.ar.bits.addr := requestReg.address
  io.bus.ar.bits.prot := 0.U
  io.bus.r.ready      := state === Axi4LiteMasterAdapterState.waitReadResponse

  private def memoryResponseCode(axiResponse: UInt): MemoryResponseCode.Type = {
    Mux(
      axiResponse === 0.U,
      MemoryResponseCode.okay,
      Mux(axiResponse === 3.U, MemoryResponseCode.decodeError, MemoryResponseCode.accessFault)
    )
  }

  switch(state) {
    is(Axi4LiteMasterAdapterState.idle) {
      when(io.memory.request.fire) {
        requestReg := io.memory.request.bits
        awSent     := false.B
        wSent      := false.B
        state      := Mux(
          io.memory.request.bits.operation === MemoryOperation.write,
          Axi4LiteMasterAdapterState.sendWriteRequest,
          Axi4LiteMasterAdapterState.sendReadAddress
        )
      }
    }
    is(Axi4LiteMasterAdapterState.sendReadAddress) {
      when(io.bus.ar.fire) {
        state := Axi4LiteMasterAdapterState.waitReadResponse
      }
    }
    is(Axi4LiteMasterAdapterState.waitReadResponse) {
      when(io.bus.r.fire) {
        responseReg.readData := io.bus.r.bits.data
        responseReg.code     := memoryResponseCode(io.bus.r.bits.resp)
        state                := Axi4LiteMasterAdapterState.sendResponse
      }
    }
    is(Axi4LiteMasterAdapterState.sendWriteRequest) {
      when(io.bus.aw.fire) {
        awSent := true.B
      }
      when(io.bus.w.fire) {
        wSent := true.B
      }
      when((awSent || io.bus.aw.fire) && (wSent || io.bus.w.fire)) {
        state := Axi4LiteMasterAdapterState.waitWriteResponse
      }
    }
    is(Axi4LiteMasterAdapterState.waitWriteResponse) {
      when(io.bus.b.fire) {
        responseReg.readData := 0.U
        responseReg.code     := memoryResponseCode(io.bus.b.bits.resp)
        state                := Axi4LiteMasterAdapterState.sendResponse
      }
    }
    is(Axi4LiteMasterAdapterState.sendResponse) {
      when(io.memory.response.fire) {
        state := Axi4LiteMasterAdapterState.idle
      }
    }
  }
}

object Axi4LiteSlaveAdapterState extends ChiselEnum {
  val receiveRequest, sendRequest, waitResponse, sendWriteResponse, sendReadResponse = Value
}

class Axi4LiteSlaveAdapter(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val bus:    Axi4LiteSlaveIO = new Axi4LiteSlaveIO(addrWidth, dataWidth)
    val memory: MemoryMasterIO  = new MemoryMasterIO(addrWidth, dataWidth)
  })

  private val state:          Axi4LiteSlaveAdapterState.Type = RegInit(Axi4LiteSlaveAdapterState.receiveRequest)
  private val requestReg:     MemoryRequest                  = Reg(new MemoryRequest(addrWidth, dataWidth))
  private val responseReg:    MemoryResponse                 = Reg(new MemoryResponse(dataWidth))
  private val awAddr:         UInt                           = Reg(UInt(addrWidth.W))
  private val awPending:      Bool                           = RegInit(false.B)
  private val wData:          UInt                           = Reg(UInt(dataWidth.W))
  private val wMask:          UInt                           = Reg(UInt((dataWidth / 8).W))
  private val wPending:       Bool                           = RegInit(false.B)
  private val requestIsWrite: Bool                           = RegInit(false.B)

  private val receiving: Bool = state === Axi4LiteSlaveAdapterState.receiveRequest

  io.bus.aw.ready := !reset.asBool && receiving && !awPending
  io.bus.w.ready  := !reset.asBool && receiving && !wPending
  io.bus.ar.ready := !reset.asBool && receiving && !awPending && !wPending && !io.bus.aw.valid && !io.bus.w.valid

  private val axiResponse: UInt = Mux(
    responseReg.code === MemoryResponseCode.okay,
    0.U,
    Mux(responseReg.code === MemoryResponseCode.decodeError, 3.U, 2.U)
  )

  io.bus.b.valid     := state === Axi4LiteSlaveAdapterState.sendWriteResponse
  io.bus.b.bits.resp := axiResponse
  io.bus.r.valid     := state === Axi4LiteSlaveAdapterState.sendReadResponse
  io.bus.r.bits.data := responseReg.readData
  io.bus.r.bits.resp := axiResponse

  io.memory.request.valid  := state === Axi4LiteSlaveAdapterState.sendRequest
  io.memory.request.bits   := requestReg
  io.memory.response.ready := state === Axi4LiteSlaveAdapterState.waitResponse

  switch(state) {
    is(Axi4LiteSlaveAdapterState.receiveRequest) {
      val awAvailable: Bool = awPending || io.bus.aw.fire
      val wAvailable:  Bool = wPending || io.bus.w.fire

      when(io.bus.aw.fire) {
        awAddr    := io.bus.aw.bits.addr
        awPending := true.B
      }
      when(io.bus.w.fire) {
        wData    := io.bus.w.bits.data
        wMask    := io.bus.w.bits.strb
        wPending := true.B
      }
      when(awAvailable && wAvailable) {
        requestReg.address   := Mux(io.bus.aw.fire, io.bus.aw.bits.addr, awAddr)
        requestReg.operation := MemoryOperation.write
        requestReg.writeData := Mux(io.bus.w.fire, io.bus.w.bits.data, wData)
        requestReg.writeMask := Mux(io.bus.w.fire, io.bus.w.bits.strb, wMask)
        requestIsWrite       := true.B
        awPending            := false.B
        wPending             := false.B
        state                := Axi4LiteSlaveAdapterState.sendRequest
      }.elsewhen(io.bus.ar.fire) {
        requestReg.address   := io.bus.ar.bits.addr
        requestReg.operation := MemoryOperation.read
        requestReg.writeData := 0.U
        requestReg.writeMask := 0.U
        requestIsWrite       := false.B
        state                := Axi4LiteSlaveAdapterState.sendRequest
      }
    }
    is(Axi4LiteSlaveAdapterState.sendRequest) {
      when(io.memory.request.fire) {
        state := Axi4LiteSlaveAdapterState.waitResponse
      }
    }
    is(Axi4LiteSlaveAdapterState.waitResponse) {
      when(io.memory.response.fire) {
        responseReg := io.memory.response.bits
        state       := Mux(
          requestIsWrite,
          Axi4LiteSlaveAdapterState.sendWriteResponse,
          Axi4LiteSlaveAdapterState.sendReadResponse
        )
      }
    }
    is(Axi4LiteSlaveAdapterState.sendWriteResponse) {
      when(io.bus.b.fire) {
        state := Axi4LiteSlaveAdapterState.receiveRequest
      }
    }
    is(Axi4LiteSlaveAdapterState.sendReadResponse) {
      when(io.bus.r.fire) {
        state := Axi4LiteSlaveAdapterState.receiveRequest
      }
    }
  }
}
