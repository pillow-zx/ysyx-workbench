package npc.core

import chisel3._
import chisel3.util._
import npc.interface.{Dpic, Message}

class IFU(xlen: Int, resetVector: BigInt) extends Module {
  val io = IO(new Bundle {
    val out:      DecoupledIO[Message] = Decoupled(new Message(xlen))
    val pcUpdate: ValidIO[UInt]        = Flipped(Valid(UInt(xlen.W)))
  })

  private val pc:   UInt = RegInit(resetVector.U(xlen.W))
  private val inst: UInt = Dpic.fetchInst.call(pc)

  when(io.pcUpdate.valid) {
    pc := io.pcUpdate.bits
  }

  private val message: Message = WireDefault(0.U.asTypeOf(new Message(xlen)))
  message.pc   := pc
  message.inst := inst

  io.out.bits  := message
  io.out.valid := !reset.asBool
}
