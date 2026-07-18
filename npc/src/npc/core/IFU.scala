package npc.core

import chisel3._
import chisel3.util._
import npc.interface.{Dpic, Message}

class IFU(xlen: Int, resetVector: BigInt) extends Module {
  val io = IO(new Bundle {
    val out:    DecoupledIO[Message] = Decoupled(new Message(xlen))
    val nextPc: DecoupledIO[UInt]    = Flipped(Decoupled(UInt(xlen.W)))
  })

  private val pc: UInt = RegInit(resetVector.U(xlen.W))

  private val msgReg:   Message = Reg(new Message(xlen))
  private val validReg: Bool    = RegInit(false.B)

  when(!validReg) {
    msgReg.pc   := pc
    msgReg.inst := Dpic.fetchInst.call(pc)
    validReg    := true.B
  }

  when(io.nextPc.fire) {
    pc       := io.nextPc.bits
    validReg := false.B
  }

  io.out.bits       := msgReg
  io.out.valid      := !reset.asBool && validReg
  io.nextPc.ready := true.B
}
