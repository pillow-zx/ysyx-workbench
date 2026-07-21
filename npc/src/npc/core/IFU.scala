package npc.core

import chisel3._
import chisel3.util._
import npc.common.{Constants, TrapCause}
import npc.interface.{MemoryMasterIO, MemoryOperation, MemoryResponseCode, Message}

object IFUState extends ChiselEnum {
  val sendReq, waitResp, output = Value
}

class IFU(xlen: Int, resetVector: BigInt) extends Module {
  val io = IO(new Bundle {
    val out:    DecoupledIO[Message] = Decoupled(new Message(xlen))
    val nextPc: DecoupledIO[UInt]    = Flipped(Decoupled(UInt(xlen.W)))
    val memory: MemoryMasterIO       = new MemoryMasterIO(Constants.addrWidth, Constants.dataWidth)
  })

  private val pc:    UInt          = RegInit(resetVector.U(xlen.W))
  private val state: IFUState.Type = RegInit(IFUState.sendReq)

  private val msgReg: Message = Reg(new Message(xlen))

  io.memory.request.valid          := !reset.asBool && state === IFUState.sendReq
  io.memory.request.bits.address   := pc
  io.memory.request.bits.operation := MemoryOperation.read
  io.memory.request.bits.writeData := 0.U
  io.memory.request.bits.writeMask := 0.U
  io.memory.response.ready         := state === IFUState.waitResp
  io.out.valid                     := !reset.asBool && state === IFUState.output
  io.out.bits                      := msgReg
  io.nextPc.ready                  := state === IFUState.output

  switch(state) {
    is(IFUState.sendReq) {
      when(io.memory.request.fire) {
        state := IFUState.waitResp
      }
    }
    is(IFUState.waitResp) {
      when(io.memory.response.fire) {
        msgReg.pc              := pc
        msgReg.inst            := io.memory.response.bits.readData
        msgReg.exception.valid := io.memory.response.bits.code =/= MemoryResponseCode.okay
        msgReg.exception.cause := TrapCause.InstAccessFault
        state                  := IFUState.output
      }
    }
    is(IFUState.output) {
      when(io.nextPc.fire) {
        pc    := io.nextPc.bits
        state := IFUState.sendReq
      }
    }
  }
}
