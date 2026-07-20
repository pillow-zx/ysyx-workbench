package npc.bus.simplebus

import chisel3._

class SimpleBus(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val upstream:   SimpleBusSlaveIO  = new SimpleBusSlaveIO(xlen)
    val downstream: SimpleBusMasterIO = new SimpleBusMasterIO(xlen)
  })

  io.downstream.req.valid := io.upstream.req.valid
  io.downstream.req.bits  := io.upstream.req.bits
  io.upstream.req.ready   := io.downstream.req.ready

  io.upstream.resp.valid   := io.downstream.resp.valid
  io.upstream.resp.bits    := io.downstream.resp.bits
  io.downstream.resp.ready := io.upstream.resp.ready
}
