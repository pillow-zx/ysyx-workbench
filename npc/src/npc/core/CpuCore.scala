package npc.core

import chisel3._
import npc.common.{Constants, NpcConfig}
import npc.interface.{CommitInfo, DebugInfo, MemoryMasterIO}
import npc.unit.{Csr, RegFile}

class CpuCoreIO(xlen: Int) extends Bundle {
  val imem:   MemoryMasterIO = new MemoryMasterIO(Constants.addrWidth, Constants.dataWidth)
  val dmem:   MemoryMasterIO = new MemoryMasterIO(Constants.addrWidth, Constants.dataWidth)
  val commit: CommitInfo     = Output(new CommitInfo(xlen))
  val debug:  DebugInfo      = Output(new DebugInfo(xlen))
}

class CpuCore(config: NpcConfig = NpcConfig()) extends Module {
  val io: CpuCoreIO = IO(new CpuCoreIO(config.xlen))

  private val regs: RegFile = Module(new RegFile(config.xlen))
  private val csr:  Csr     = Module(new Csr(config.xlen))

  private val ifu: IFU = Module(new IFU(config.xlen, config.resetVector))
  private val idu: IDU = Module(new IDU(config.xlen))
  private val exu: EXU = Module(new EXU(config.xlen))
  private val lsu: LSU = Module(new LSU(config.xlen))
  private val wbu: WBU = Module(new WBU(config.xlen))

  ifu.io.memory <> io.imem
  lsu.io.memory <> io.dmem

  idu.io.in <> ifu.io.out
  idu.io.regs <> regs.io.read
  exu.io.in <> idu.io.out
  exu.io.epc := csr.io.epc
  lsu.io.in <> exu.io.out
  wbu.io.in <> lsu.io.out
  wbu.io.csr <> csr.io.commit
  wbu.io.regs <> regs.io.write
  ifu.io.nextPc <> wbu.io.nextPc

  private val architecturalPc: UInt = RegInit(config.resetVector.U(config.xlen.W))
  when(wbu.io.commit.valid) {
    architecturalPc := wbu.io.commit.nextPc
  }

  io.commit    := wbu.io.commit
  io.debug.pc  := architecturalPc
  io.debug.gpr := regs.io.debugGpr
}
