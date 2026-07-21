package npc.bus.axi4lite

import chisel3._

class AXI4LiteBus(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val upstream:   Axi4LiteSlaveIO  = new Axi4LiteSlaveIO(addrWidth, dataWidth)
    val downstream: Axi4LiteMasterIO = new Axi4LiteMasterIO(addrWidth, dataWidth)
  })

  io.downstream.aw.valid := io.upstream.aw.valid
  io.downstream.aw.bits  := io.upstream.aw.bits
  io.upstream.aw.ready   := io.downstream.aw.ready

  io.downstream.w.valid := io.upstream.w.valid
  io.downstream.w.bits  := io.upstream.w.bits
  io.upstream.w.ready   := io.downstream.w.ready

  io.upstream.b.valid   := io.downstream.b.valid
  io.upstream.b.bits    := io.downstream.b.bits
  io.downstream.b.ready := io.upstream.b.ready

  io.downstream.ar.valid := io.upstream.ar.valid
  io.downstream.ar.bits  := io.upstream.ar.bits
  io.upstream.ar.ready   := io.downstream.ar.ready

  io.upstream.r.valid   := io.downstream.r.valid
  io.upstream.r.bits    := io.downstream.r.bits
  io.downstream.r.ready := io.upstream.r.ready
}
