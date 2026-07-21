package npc.core

import chisel3._
import npc.bus.axi4lite.AXI4LiteBus
import npc.common.{Constants, NpcConfig}
import npc.interface.{CommitInfo, DebugInfo}
import npc.unit.{Csr, DpicMemoryAxi4Lite, RegFile}

class CoreIO(xlen: Int) extends Bundle {
  val commit: CommitInfo = Output(new CommitInfo(xlen))
  val debug:  DebugInfo  = Output(new DebugInfo(xlen))
}

class Core(config: NpcConfig = NpcConfig()) extends Module {
  require(config.xlen == 32, "the current DPI memory interface supports RV32 only")

  val io: CoreIO = IO(new CoreIO(config.xlen))

  private val regs: RegFile = Module(new RegFile(config.xlen))
  private val csr:  Csr     = Module(new Csr(config.xlen))

  private val ifuBus:         AXI4LiteBus        = Module(new AXI4LiteBus(Constants.addrWidth, Constants.dataWidth))
  private val isuBus:         AXI4LiteBus        = Module(new AXI4LiteBus(Constants.dataWidth, Constants.dataWidth))
  private val axi4LiteIfuBus: DpicMemoryAxi4Lite = Module(
    new DpicMemoryAxi4Lite(Constants.addrWidth, Constants.dataWidth)
  )
  private val axi4LiteIsuBus: DpicMemoryAxi4Lite = Module(
    new DpicMemoryAxi4Lite(Constants.addrWidth, Constants.dataWidth)
  )

  private val ifu: IFU = Module(new IFU(config.xlen, config.resetVector))
  private val idu: IDU = Module(new IDU(config.xlen))
  private val exu: EXU = Module(new EXU(config.xlen))
  private val lsu: LSU = Module(new LSU(config.xlen))
  private val wbu: WBU = Module(new WBU(config.xlen))

  ifu.io.bus <> ifuBus.io.upstream
  ifuBus.io.downstream <> axi4LiteIfuBus.io
  idu.io.in <> ifu.io.out
  idu.io.regs <> regs.io.read
  exu.io.in <> idu.io.out
  exu.io.epc := csr.io.epc
  lsu.io.bus <> isuBus.io.upstream
  isuBus.io.downstream <> axi4LiteIsuBus.io
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
