package npc.core

import chisel3._
import chisel3.util._
import npc.interface.{Dpic, Message}
import npc.common.{MemSize, TrapCause}

import scala.language.postfixOps

class MEM(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val in:  DecoupledIO[Message] = Flipped(Decoupled(new Message(xlen)))
    val out: DecoupledIO[Message] = Decoupled(new Message(xlen))
  })

  private val aluResult:   UInt = io.in.bits.aluResult
  private val valid:       Bool = io.in.bits.decode.valid
  private val memValid:    Bool = io.in.bits.decode.mem.valid
  private val memSize:     UInt = io.in.bits.decode.mem.size
  private val memUnsigned: Bool = io.in.bits.decode.mem.unsigned
  private val isStore:     Bool = io.in.bits.decode.mem.isStore
  private val rdata2:      UInt = io.in.bits.rs2Data

  private val memoryAddress:        UInt = aluResult
  private val memoryByteOffset:     UInt = memoryAddress(1, 0)
  private val alignedMemoryAddress: UInt = Cat(memoryAddress(xlen - 1, 2), 0.U(2.W))
  private val memoryShift:          UInt = memoryByteOffset << 3

  private val memoryMisaligned: Bool = memValid && MuxLookup(
    memSize,
    false.B
  )(
    Seq(
      MemSize.Byte -> false.B,
      MemSize.Half -> memoryAddress(0),
      MemSize.Word -> memoryAddress(1, 0).orR
    )
  )

  private val loadEnable:  Bool = !reset.asBool && io.in.fire && valid &&
    memValid && !isStore && !memoryMisaligned && !io.in.bits.exception.valid
  private val storeEnable: Bool = !reset.asBool && io.in.fire && valid &&
    memValid && isStore && !memoryMisaligned && !io.in.bits.exception.valid

  private val rawMemoryData:     UInt = Dpic.readData.callWithEnable(loadEnable, alignedMemoryAddress)
  private val shiftedMemoryData: UInt = rawMemoryData >> memoryShift
  private val byteLoadDAta:      UInt = Mux(
    memUnsigned,
    shiftedMemoryData(7, 0).pad(xlen),
    shiftedMemoryData(7, 0).asSInt.pad(xlen).asUInt
  )
  private val halfLoadData:      UInt = Mux(
    memUnsigned,
    shiftedMemoryData(15, 0).pad(xlen),
    shiftedMemoryData(15, 0).asSInt.pad(xlen).asUInt
  )
  private val formattedLoadData: UInt = MuxLookup(
    memSize,
    shiftedMemoryData
  )(
    Seq(
      MemSize.Byte -> byteLoadDAta,
      MemSize.Half -> halfLoadData,
      MemSize.Word -> shiftedMemoryData
    )
  )
  private val loadData:          UInt = Mux(loadEnable, formattedLoadData, 0.U)

  private val baseStoreMask: UInt = MuxLookup(
    memSize,
    0.U(8.W)
  )(
    Seq(
      MemSize.Byte -> "b00000001".U,
      MemSize.Half -> "b00000011".U,
      MemSize.Word -> "b00001111".U
    )
  )

  private val storeMask: UInt = (baseStoreMask << memoryByteOffset)(7, 0)
  private val storeData: UInt = (rdata2 << memoryShift)(xlen - 1, 0)
  Dpic.writeData.callWithEnable(storeEnable, alignedMemoryAddress, storeData, storeMask)

  private val next: Message = WireDefault(io.in.bits)
  next.memData := loadData

  when(memoryMisaligned) {
    next.exception.valid := true.B
    next.exception.cause := Mux(isStore, TrapCause.StoreAddrMisaligned, TrapCause.LoadAddrMisaligned)
  }

  io.out.bits  := next
  io.out.valid := io.in.valid
  io.in.ready  := io.out.ready
}
