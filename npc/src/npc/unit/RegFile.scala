package npc.unit

import chisel3._
import npc.common.Constants

class RegFile(xlen: Int) extends Module {
  val io: RegFileIO = IO(new RegFileIO(xlen))

  private val regs: Vec[UInt] = RegInit(VecInit.fill(Constants.RegCount)(0.U(xlen.W)))

  io.read.rdata1 := Mux(io.read.raddr1.orR, regs(io.read.raddr1), 0.U)
  io.read.rdata2 := Mux(io.read.raddr2.orR, regs(io.read.raddr2), 0.U)

  when(io.write.wen && io.write.waddr.orR) {
    regs(io.write.waddr) := io.write.wdata
  }

  io.debugGpr := regs
}
