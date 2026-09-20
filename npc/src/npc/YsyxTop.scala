package npc

import chisel3._
import npc.axi4.AXI4Adapter
import npc.common.{Constants, NpcConfig}
import npc.core.CpuCore
import npc.interface.{CommitInfo, DebugInfo}
import npc.memory.{MemoryInterconnect, MemorySubsystem}
import npc.unit.Clint

class YsyxTopIO(xlen: Int) extends Bundle {
  val interrupt: Bool = Input(Bool())

  // The ysyx CPU interface uses flat names without Decoupled/bits prefixes.
  val master_awready: Bool = Input(Bool())
  val master_awvalid: Bool = Output(Bool())
  val master_awaddr:  UInt = Output(UInt(Constants.addrWidth.W))
  val master_awid:    UInt = Output(UInt(4.W))
  val master_awlen:   UInt = Output(UInt(8.W))
  val master_awsize:  UInt = Output(UInt(3.W))
  val master_awburst: UInt = Output(UInt(2.W))

  val master_wready: Bool = Input(Bool())
  val master_wvalid: Bool = Output(Bool())
  val master_wdata:  UInt = Output(UInt(Constants.dataWidth.W))
  val master_wstrb:  UInt = Output(UInt((Constants.dataWidth / 8).W))
  val master_wlast:  Bool = Output(Bool())

  val master_bready: Bool = Output(Bool())
  val master_bvalid: Bool = Input(Bool())
  val master_bresp:  UInt = Input(UInt(2.W))
  val master_bid:    UInt = Input(UInt(4.W))

  val master_arready: Bool = Input(Bool())
  val master_arvalid: Bool = Output(Bool())
  val master_araddr:  UInt = Output(UInt(Constants.addrWidth.W))
  val master_arid:    UInt = Output(UInt(4.W))
  val master_arlen:   UInt = Output(UInt(8.W))
  val master_arsize:  UInt = Output(UInt(3.W))
  val master_arburst: UInt = Output(UInt(2.W))

  val master_rready: Bool = Output(Bool())
  val master_rvalid: Bool = Input(Bool())
  val master_rresp:  UInt = Input(UInt(2.W))
  val master_rdata:  UInt = Input(UInt(Constants.dataWidth.W))
  val master_rlast:  Bool = Input(Bool())
  val master_rid:    UInt = Input(UInt(4.W))

  val slave_awready: Bool = Output(Bool())
  val slave_awvalid: Bool = Input(Bool())
  val slave_awaddr:  UInt = Input(UInt(Constants.addrWidth.W))
  val slave_awid:    UInt = Input(UInt(4.W))
  val slave_awlen:   UInt = Input(UInt(8.W))
  val slave_awsize:  UInt = Input(UInt(3.W))
  val slave_awburst: UInt = Input(UInt(2.W))

  val slave_wready: Bool = Output(Bool())
  val slave_wvalid: Bool = Input(Bool())
  val slave_wdata:  UInt = Input(UInt(Constants.dataWidth.W))
  val slave_wstrb:  UInt = Input(UInt((Constants.dataWidth / 8).W))
  val slave_wlast:  Bool = Input(Bool())

  val slave_bready: Bool = Input(Bool())
  val slave_bvalid: Bool = Output(Bool())
  val slave_bresp:  UInt = Output(UInt(2.W))
  val slave_bid:    UInt = Output(UInt(4.W))

  val slave_arready: Bool = Output(Bool())
  val slave_arvalid: Bool = Input(Bool())
  val slave_araddr:  UInt = Input(UInt(Constants.addrWidth.W))
  val slave_arid:    UInt = Input(UInt(4.W))
  val slave_arlen:   UInt = Input(UInt(8.W))
  val slave_arsize:  UInt = Input(UInt(3.W))
  val slave_arburst: UInt = Input(UInt(2.W))

  val slave_rready: Bool = Input(Bool())
  val slave_rvalid: Bool = Output(Bool())
  val slave_rresp:  UInt = Output(UInt(2.W))
  val slave_rdata:  UInt = Output(UInt(Constants.dataWidth.W))
  val slave_rlast:  Bool = Output(Bool())
  val slave_rid:    UInt = Output(UInt(4.W))

  val commit: CommitInfo = Output(new CommitInfo(xlen))
  val debug:  DebugInfo  = Output(new DebugInfo(xlen))
}

class ysyx_00000000(config: NpcConfig = NpcConfig()) extends Module {
  val io: YsyxTopIO = IO(new YsyxTopIO(config.xlen))

  private val core:         CpuCore            = Module(new CpuCore(config))
  private val memory:       MemorySubsystem    = Module(new MemorySubsystem(Constants.addrWidth, Constants.dataWidth))
  private val adapter:      AXI4Adapter        = Module(new AXI4Adapter(Constants.addrWidth, Constants.dataWidth))
  private val interconnect: MemoryInterconnect = Module(
    new MemoryInterconnect(Constants.addrWidth, Constants.dataWidth)
  )
  private val clint:        Clint              = Module(new Clint(Constants.addrWidth, Constants.dataWidth))

  core.io.imem <> memory.io.imem
  core.io.dmem <> memory.io.dmem
  memory.io.downstream <> interconnect.io.upstream
  interconnect.io.memory <> adapter.io.memory
  interconnect.io.clint <> clint.io

  io.master_awvalid       := adapter.io.bus.aw.valid
  io.master_awaddr        := adapter.io.bus.aw.bits.addr
  io.master_awid          := adapter.io.bus.aw.bits.id
  io.master_awlen         := adapter.io.bus.aw.bits.len
  io.master_awsize        := adapter.io.bus.aw.bits.size
  io.master_awburst       := adapter.io.bus.aw.bits.burst
  adapter.io.bus.aw.ready := io.master_awready

  io.master_wvalid       := adapter.io.bus.w.valid
  io.master_wdata        := adapter.io.bus.w.bits.data
  io.master_wstrb        := adapter.io.bus.w.bits.strb
  io.master_wlast        := adapter.io.bus.w.bits.last
  adapter.io.bus.w.ready := io.master_wready

  io.master_bready           := adapter.io.bus.b.ready
  adapter.io.bus.b.valid     := io.master_bvalid
  adapter.io.bus.b.bits.resp := io.master_bresp
  adapter.io.bus.b.bits.id   := io.master_bid

  io.master_arvalid       := adapter.io.bus.ar.valid
  io.master_araddr        := adapter.io.bus.ar.bits.addr
  io.master_arid          := adapter.io.bus.ar.bits.id
  io.master_arlen         := adapter.io.bus.ar.bits.len
  io.master_arsize        := adapter.io.bus.ar.bits.size
  io.master_arburst       := adapter.io.bus.ar.bits.burst
  adapter.io.bus.ar.ready := io.master_arready

  io.master_rready           := adapter.io.bus.r.ready
  adapter.io.bus.r.valid     := io.master_rvalid
  adapter.io.bus.r.bits.resp := io.master_rresp
  adapter.io.bus.r.bits.data := io.master_rdata
  adapter.io.bus.r.bits.last := io.master_rlast
  adapter.io.bus.r.bits.id   := io.master_rid

  dontTouch(io.interrupt)
  io.slave_awready := false.B
  io.slave_wready  := false.B
  io.slave_arready := false.B
  io.slave_bvalid  := false.B
  io.slave_bresp   := 0.U
  io.slave_bid     := 0.U
  io.slave_rvalid  := false.B
  io.slave_rresp   := 0.U
  io.slave_rdata   := 0.U
  io.slave_rlast   := false.B
  io.slave_rid     := 0.U
  io.commit        := core.io.commit
  io.debug         := core.io.debug
}
