package npc.core

import chisel3._
import chisel3.util.{Decoupled, DecoupledIO}
import npc.common.TrapCause
import npc.interface.Message
import npc.unit.{Decode, RegFileReadIO}

class IDU(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val in:   DecoupledIO[Message] = Flipped(Decoupled(new Message(xlen)))
    val out:  DecoupledIO[Message] = Decoupled(new Message(xlen))
    val regs: RegFileReadIO        = Flipped(new RegFileReadIO(xlen))
  })

  private val decode: Decode = Module(new Decode(xlen))
  decode.io.inst := io.in.bits.inst

  private val systemIllegal: Bool = decode.io.result.system.illegal
  private val systemEcall:   Bool = decode.io.result.system.ecall
  private val trapValid:     Bool = systemIllegal || systemEcall

  io.regs.raddr1 := decode.io.result.rs1
  io.regs.raddr2 := decode.io.result.rs2

  private val next: Message = WireDefault(io.in.bits)
  next.rs1Data         := io.regs.rdata1
  next.rs2Data         := io.regs.rdata2
  next.decode          := decode.io.result
  next.exception.valid := trapValid
  next.exception.cause := Mux(systemEcall, TrapCause.EcallM, Mux(systemIllegal, TrapCause.IllegalInst, 0.U(xlen.W)))

  io.out.bits  := next
  io.out.valid := io.in.valid
  io.in.ready  := io.out.ready
}
