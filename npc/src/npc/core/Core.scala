package npc.core

import chisel3._
import npc.bus.axi4lite.{AXI4LiteBus, Axi4LiteMasterAdapter, Axi4LiteSlaveAdapter}
import npc.common.{Constants, NpcConfig}
import npc.interface.{CommitInfo, DebugInfo}
import npc.unit.{Csr, DpicMemory, RegFile}

class CoreIO(xlen: Int) extends Bundle {
  val commit: CommitInfo = Output(new CommitInfo(xlen))
  val debug:  DebugInfo  = Output(new DebugInfo(xlen))
}

class Core(config: NpcConfig = NpcConfig()) extends Module {
  require(config.xlen == 32, "the current DPI memory interface supports RV32 only")

  val io: CoreIO = IO(new CoreIO(config.xlen))

  private val regs: RegFile = Module(new RegFile(config.xlen))
  private val csr:  Csr     = Module(new Csr(config.xlen))

  private val ifuMasterAdapter: Axi4LiteMasterAdapter = Module(
    new Axi4LiteMasterAdapter(Constants.addrWidth, Constants.dataWidth)
  )
  private val ifuBus:           AXI4LiteBus           = Module(new AXI4LiteBus(Constants.addrWidth, Constants.dataWidth))
  private val ifuSlaveAdapter:  Axi4LiteSlaveAdapter  = Module(
    new Axi4LiteSlaveAdapter(Constants.addrWidth, Constants.dataWidth)
  )
  private val ifuMemory:        DpicMemory            = Module(new DpicMemory(config.xlen))

  private val lsuMasterAdapter: Axi4LiteMasterAdapter = Module(
    new Axi4LiteMasterAdapter(Constants.addrWidth, Constants.dataWidth)
  )
  private val lsuBus:           AXI4LiteBus           = Module(new AXI4LiteBus(Constants.addrWidth, Constants.dataWidth))
  private val lsuSlaveAdapter:  Axi4LiteSlaveAdapter  = Module(
    new Axi4LiteSlaveAdapter(Constants.addrWidth, Constants.dataWidth)
  )
  private val lsuMemory:        DpicMemory            = Module(new DpicMemory(config.xlen))

  private val ifu: IFU = Module(new IFU(config.xlen, config.resetVector))
  private val idu: IDU = Module(new IDU(config.xlen))
  private val exu: EXU = Module(new EXU(config.xlen))
  private val lsu: LSU = Module(new LSU(config.xlen))
  private val wbu: WBU = Module(new WBU(config.xlen))

  ifu.io.memory <> ifuMasterAdapter.io.memory
  ifuMasterAdapter.io.bus <> ifuBus.io.upstream
  ifuBus.io.downstream <> ifuSlaveAdapter.io.bus
  ifuSlaveAdapter.io.memory <> ifuMemory.io
  idu.io.in <> ifu.io.out
  idu.io.regs <> regs.io.read
  exu.io.in <> idu.io.out
  exu.io.epc := csr.io.epc
  lsu.io.memory <> lsuMasterAdapter.io.memory
  lsuMasterAdapter.io.bus <> lsuBus.io.upstream
  lsuBus.io.downstream <> lsuSlaveAdapter.io.bus
  lsuSlaveAdapter.io.memory <> lsuMemory.io
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
