package npc.core

import chisel3._
import chisel3.util._
import npc.bus.axi4lite.Axi4LiteMasterIO
import npc.bus.simplebus.SimpleBusMasterIO
import npc.common.Constants
import npc.interface.Message

object IFUState extends ChiselEnum {
  val sendReq, waitResp, output = Value
}

class IFU(xlen: Int, resetVector: BigInt) extends Module {
  val io = IO(new Bundle {
    val out:    DecoupledIO[Message] = Decoupled(new Message(xlen))
    val nextPc: DecoupledIO[UInt]    = Flipped(Decoupled(UInt(xlen.W)))
    val bus:    Axi4LiteMasterIO     = new Axi4LiteMasterIO(Constants.addrWidth, Constants.dataWidth)
  })

  private val pc:    UInt          = RegInit(resetVector.U(xlen.W))
  private val state: IFUState.Type = RegInit(IFUState.sendReq)

  private val msgReg: Message = Reg(new Message(xlen))

  io.bus.aw := DontCare
  io.bus.w  := DontCare
  io.bus.b  := DontCare

  io.bus.ar.valid     := !reset.asBool && state === IFUState.sendReq
  io.bus.ar.bits.addr := pc
  io.bus.ar.bits.prot := DontCare
  io.bus.r.ready      := state === IFUState.waitResp
  io.out.valid        := !reset.asBool && state === IFUState.output
  io.out.bits         := msgReg
  io.nextPc.ready     := state === IFUState.output

  switch(state) {
    is(IFUState.sendReq) {
      when(io.bus.ar.fire) {
        state := IFUState.waitResp
      }
    }
    is(IFUState.waitResp) {
      when(io.bus.r.fire) {
        msgReg.pc   := pc
        msgReg.inst := io.bus.r.bits.data
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
