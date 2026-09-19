package npc.interface

import chisel3._
import chisel3.util._

object MemoryOperation extends ChiselEnum {
  val read, write = Value
}

object MemorySize extends ChiselEnum {
  val byte, half, word = Value
}

object MemoryResponseCode extends ChiselEnum {
  val okay, accessFault, decodeError = Value
}

class MemoryRequest(addrWidth: Int, dataWidth: Int) extends Bundle {
  val address:   UInt                 = UInt(addrWidth.W)
  val operation: MemoryOperation.Type = MemoryOperation()
  val size:      MemorySize.Type      = MemorySize()
  val writeData: UInt                 = UInt(dataWidth.W)
  val writeMask: UInt                 = UInt((dataWidth / 8).W)
}

class MemoryResponse(dataWidth: Int) extends Bundle {
  val readData:     UInt                    = UInt(dataWidth.W)
  val responseCode: MemoryResponseCode.Type = MemoryResponseCode()
}

class MemoryMasterIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val request:  DecoupledIO[MemoryRequest]  = Decoupled(new MemoryRequest(addrWidth, dataWidth))
  val response: DecoupledIO[MemoryResponse] = Flipped(Decoupled(new MemoryResponse(dataWidth)))
}

class MemorySlaveIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val request:  DecoupledIO[MemoryRequest]  = Flipped(Decoupled(new MemoryRequest(addrWidth, dataWidth)))
  val response: DecoupledIO[MemoryResponse] = Decoupled(new MemoryResponse(dataWidth))
}
