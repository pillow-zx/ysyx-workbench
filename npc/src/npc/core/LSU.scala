package npc.core

import chisel3._
import chisel3.util._
import npc.interface.{Dpic, Message}
import npc.common.{MemSize, TrapCause}

import scala.language.postfixOps

object LsuState extends ChiselEnum {
  val idle, waitLoad = Value
}

class LSU(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val in:  DecoupledIO[Message] = Flipped(Decoupled(new Message(xlen)))
    val out: DecoupledIO[Message] = Decoupled(new Message(xlen))
  })

  private val state:      LsuState.Type = RegInit(LsuState.idle)
  private val messageReg: Message       = Reg(new Message(xlen))

  private val valid:           Bool = io.in.bits.decode.valid
  private val isStore:         Bool = io.in.bits.decode.mem.isStore
  private val memSize:         UInt = io.in.bits.decode.mem.size
  private val memValid:        Bool = io.in.bits.decode.mem.valid
  private val memUnsigned:     Bool = io.in.bits.decode.mem.unsigned
  private val trapValid:       Bool = io.in.bits.exception.valid
  private val rdata2:          UInt = io.in.bits.rs2Data
  private val inputAddress:    UInt = io.in.bits.aluResult
  private val inputByteOffset: UInt = inputAddress(1, 0)
  private val inputShift:      UInt = inputByteOffset << 3
  private val alignedAddress:  UInt = Cat(inputAddress(xlen - 1, 2), 0.U(2.W))

  private val memoryMisaligned: Bool = memValid && MuxLookup(
    memSize,
    false.B
  )(
    Seq(
      MemSize.Byte -> false.B,
      MemSize.Half -> inputAddress(0),
      MemSize.Word -> inputAddress(1, 0).orR
    )
  )

  private val inputIsLoad:  Bool = !reset.asBool && valid &&
    memValid && !isStore && !memoryMisaligned && !trapValid
  private val inputIsStore: Bool = !reset.asBool && valid &&
    memValid && isStore && !memoryMisaligned && !trapValid

  private val loadFire: Bool = state === LsuState.idle && io.in.fire && inputIsLoad

  io.in.ready  := false.B
  io.out.valid := false.B
  io.out.bits  := messageReg

  switch(state) {
    is(LsuState.idle) {
      val next = WireDefault(io.in.bits)

      when(memoryMisaligned && !trapValid) {
        next.exception.valid := true.B
        next.exception.cause := Mux(
          isStore,
          TrapCause.StoreAddrMisaligned,
          TrapCause.LoadAddrMisaligned
        )
      }

      io.in.ready := inputIsLoad || io.out.ready

      when(inputIsLoad && io.in.fire) {
        val rawMemoryData:     UInt = Dpic.readData.callWithEnable(loadFire, alignedAddress)
        val shiftedMemoryData: UInt = rawMemoryData >> inputShift
        val byteLoadDAta:      UInt = Mux(
          memUnsigned,
          shiftedMemoryData(7, 0).pad(xlen),
          shiftedMemoryData(7, 0).asSInt.pad(xlen).asUInt
        )
        val halfLoadData:      UInt = Mux(
          memUnsigned,
          shiftedMemoryData(15, 0).pad(xlen),
          shiftedMemoryData(15, 0).asSInt.pad(xlen).asUInt
        )
        val formattedLoadData: UInt = MuxLookup(
          memSize,
          shiftedMemoryData
        )(
          Seq(
            MemSize.Byte -> byteLoadDAta,
            MemSize.Half -> halfLoadData,
            MemSize.Word -> shiftedMemoryData
          )
        )
        val loadResponse      = WireDefault(next)
        loadResponse.memData := formattedLoadData
        messageReg           := loadResponse
        state                := LsuState.waitLoad
      }.otherwise {
        io.out.bits := next
        io.out.valid := io.in.valid
      }

      when(inputIsStore && io.in.fire) {
        val baseStoreMask: UInt = MuxLookup(
          memSize,
          0.U(8.W)
        )(
          Seq(
            MemSize.Byte -> "b00000001".U,
            MemSize.Half -> "b00000011".U,
            MemSize.Word -> "b00001111".U
          )
        )
        val storeMask:     UInt = (baseStoreMask << inputByteOffset)(7, 0)
        val storeData:     UInt = (rdata2 << inputShift)(xlen - 1, 0)
        Dpic.writeData.callWithEnable(
          true.B,
          alignedAddress,
          storeData,
          storeMask
        )
      }

    }
    is(LsuState.waitLoad) {
      io.in.ready  := false.B
      io.out.bits  := messageReg
      io.out.valid := true.B

      when(io.out.fire) {
        state := LsuState.idle
      }
    }
  }
}
