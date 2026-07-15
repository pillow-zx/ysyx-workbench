package npc.unit

import chisel3._
import npc.common.Constants

class RegFile(xlen: Int) extends Module {
  val io: RegFileIO = IO(new RegFileIO(xlen))

  private val regs: Vec[UInt] = RegInit(VecInit.fill(Constants.RegCount)(0.U(xlen.W)))

  io.rdata1 := Mux(io.raddr1.orR, regs(io.raddr1), 0.U)
  io.rdata2 := Mux(io.raddr2.orR, regs(io.raddr2), 0.U)

  when(io.wen && io.waddr.orR) {
    regs(io.waddr) := io.wdata
  }

  io.debugGpr := regs
}
