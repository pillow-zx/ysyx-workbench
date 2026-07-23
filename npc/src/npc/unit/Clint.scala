package npc.unit

import chisel3._
import chisel3.util._
import npc.bus.axi4lite.MMIOAddressMap
import npc.interface.{MemoryOperation, MemoryResponse, MemoryResponseCode, MemorySlaveIO}

class Clint(addrWidth: Int, dataWidth: Int) extends Module {
  require(dataWidth == 32, "the current Clint implementation requires a 32-bit data bus")

  val io: MemorySlaveIO = IO(new MemorySlaveIO(addrWidth, dataWidth))

  private val mtime:           UInt           = RegInit(0.U(64.W))
  private val responsePending: Bool           = RegInit(false.B)
  private val responseReg:     MemoryResponse = Reg(new MemoryResponse(dataWidth))

  private val mtimeLowAddress:  UInt = MMIOAddressMap.CLINT.base.U(addrWidth.W)
  private val mtimeHighAddress: UInt = (MMIOAddressMap.CLINT.base + 4).U(addrWidth.W)

  private def mergeBytes(oldData: UInt, newData: UInt, byteMask: UInt): UInt = {
    val bitMask: UInt = FillInterleaved(8, byteMask)
    (oldData & ~bitMask) | (newData & bitMask)
  }

  io.request.ready  := !reset.asBool && !responsePending
  io.response.valid := responsePending
  io.response.bits  := responseReg

  mtime := mtime + 1.U

  when(io.request.fire) {
    responsePending      := true.B
    responseReg.readData := 0.U
    responseReg.code     := MemoryResponseCode.okay

    when(io.request.bits.address === mtimeLowAddress) {
      when(io.request.bits.operation === MemoryOperation.read) {
        responseReg.readData := mtime(31, 0)
      }.otherwise {
        mtime := Cat(
          mtime(63, 32),
          mergeBytes(mtime(31, 0), io.request.bits.writeData, io.request.bits.writeMask)
        )
      }
    }.elsewhen(io.request.bits.address === mtimeHighAddress) {
      when(io.request.bits.operation === MemoryOperation.read) {
        responseReg.readData := mtime(63, 32)
      }.otherwise {
        mtime := Cat(
          mergeBytes(mtime(63, 32), io.request.bits.writeData, io.request.bits.writeMask),
          mtime(31, 0)
        )
      }
    }.otherwise {
      responseReg.code := MemoryResponseCode.decodeError
    }
  }.elsewhen(io.response.fire) {
    responsePending := false.B
  }
}
