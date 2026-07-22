package npc.unit

import chisel3._
import npc.interface.{MemoryOperation, MemoryResponseCode, MemorySlaveIO}

class Uart(addrWidth: Int, dataWidth: Int) extends Module {
  val io: MemorySlaveIO = IO(new MemorySlaveIO(addrWidth, dataWidth))

  private val responsePending: Bool = RegInit(false.B)

  private val acceptWrite: Bool = io.request.fire &&
      io.request.bits.operation === MemoryOperation.write &&
      io.request.bits.writeMask(0)

  io.request.ready := !reset.asBool && !responsePending
  io.response.valid := responsePending

  io.response.bits.readData := 0.U(dataWidth.W)
  io.response.bits.code     := MemoryResponseCode.okay


  when (acceptWrite) {
    printf("%c", io.request.bits.writeData(7, 0))
  }

  when(io.request.fire) {
    responsePending := true.B
  }.elsewhen(io.response.fire) {
    responsePending := false.B
  }
}
