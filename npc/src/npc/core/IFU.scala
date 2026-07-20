package npc.core

import chisel3._
import chisel3.util._
import npc.bus.simplebus.SimpleBusMasterIO
import npc.interface.Message

object IFUState extends ChiselEnum {
  val sendReq, waitResp, output = Value
}

class IFU(xlen: Int, resetVector: BigInt) extends Module {
  val io = IO(new Bundle {
    val out:    DecoupledIO[Message] = Decoupled(new Message(xlen))
    val nextPc: DecoupledIO[UInt]    = Flipped(Decoupled(UInt(xlen.W)))
    val bus:    SimpleBusMasterIO    = new SimpleBusMasterIO(xlen)
  })

  private val pc:    UInt          = RegInit(resetVector.U(xlen.W))
  private val state: IFUState.Type = RegInit(IFUState.sendReq)

  private val msgReg: Message = Reg(new Message(xlen))

  io.bus.req.valid      := !reset.asBool && state === IFUState.sendReq
  io.bus.req.bits.addr  := pc
  io.bus.req.bits.wen   := false.B
  io.bus.req.bits.wdata := 0.U
  io.bus.req.bits.wmask := 0.U
  io.bus.resp.ready     := state === IFUState.waitResp
  io.out.valid          := !reset.asBool && (state === IFUState.output)
  io.out.bits           := msgReg
  io.nextPc.ready       := state === IFUState.output

  switch(state) {
    is(IFUState.sendReq) {
      when(io.bus.req.fire) {
        state := IFUState.waitResp
      }
    }
    is(IFUState.waitResp) {
      when(io.bus.resp.fire) {
        msgReg.pc   := pc
        msgReg.inst := io.bus.resp.bits.rdata
        state       := IFUState.output
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
