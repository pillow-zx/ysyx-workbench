package npc.core

import chisel3._
import npc.bus.simplebus.SimpleBus
import npc.common.NpcConfig
import npc.interface.{CommitInfo, DebugInfo}
import npc.unit.{Csr, DpicMemory, RegFile}

class CoreIO(xlen: Int) extends Bundle {
  val commit: CommitInfo = Output(new CommitInfo(xlen))
  val debug:  DebugInfo  = Output(new DebugInfo(xlen))
}

class Core(config: NpcConfig = NpcConfig()) extends Module {
  require(config.xlen == 32, "the current DPI memory interface supports RV32 only")

  val io: CoreIO = IO(new CoreIO(config.xlen))

  private val regs:    RegFile    = Module(new RegFile(config.xlen))
  private val csr:     Csr        = Module(new Csr(config.xlen))
  private val busIfu:  SimpleBus  = Module(new SimpleBus(config.xlen))
  private val busIsu:  SimpleBus  = Module(new SimpleBus(config.xlen))
  private val dpicIfu: DpicMemory = Module(new DpicMemory(config.xlen))
  private val dpicIsu: DpicMemory = Module(new DpicMemory(config.xlen))

  private val ifu: IFU = Module(new IFU(config.xlen, config.resetVector))
  private val idu: IDU = Module(new IDU(config.xlen))
  private val exu: EXU = Module(new EXU(config.xlen))
  private val lsu: LSU = Module(new LSU(config.xlen))
  private val wbu: WBU = Module(new WBU(config.xlen))

  ifu.io.bus <> busIfu.io.upstream
  busIfu.io.downstream <> dpicIfu.io
  idu.io.in <> ifu.io.out
  idu.io.regs <> regs.io.read
  exu.io.in <> idu.io.out
  exu.io.epc := csr.io.epc
  lsu.io.bus <> busIsu.io.upstream
  busIsu.io.downstream <> dpicIsu.io
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
