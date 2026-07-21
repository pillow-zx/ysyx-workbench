package npc.core

import chisel3._
import chisel3.util._
import npc.common.{Constants, MemSize, TrapCause}
import npc.interface.{MemoryMasterIO, MemoryOperation, MemoryResponseCode, Message}

object LsuState extends ChiselEnum {
  val sendReq, waitReadResp, waitWriteResp, output = Value
}

class LSU(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val in:  DecoupledIO[Message] = Flipped(Decoupled(new Message(xlen)))
    val out: DecoupledIO[Message] = Decoupled(new Message(xlen))
    val memory = new MemoryMasterIO(Constants.addrWidth, Constants.dataWidth)
  })

  private val state:         LsuState.Type = RegInit(LsuState.sendReq)
  private val messageReg:    Message       = Reg(new Message(xlen))
  private val byteOffsetReg: UInt          = Reg(UInt(2.W))

  private val valid:           Bool = io.in.bits.decode.valid
  private val isStore:         Bool = io.in.bits.decode.mem.isStore
  private val memSize:         UInt = io.in.bits.decode.mem.size
  private val memValid:        Bool = io.in.bits.decode.mem.valid
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

  private val inputIsMemoryRequest: Bool = inputIsLoad || inputIsStore

  private val baseStoreMask: UInt = MuxLookup(
    memSize,
    0.U((xlen / 8).W)
  )(
    Seq(
      MemSize.Byte -> 1.U((xlen / 8).W),
      MemSize.Half -> 3.U((xlen / 8).W),
      MemSize.Word -> 15.U((xlen / 8).W)
    )
  )
  private val storeMask:     UInt = (baseStoreMask << inputByteOffset)(xlen / 8 - 1, 0)
  private val storeData:     UInt = (rdata2 << inputShift)(xlen - 1, 0)

  io.in.ready  := false.B
  io.out.valid := false.B
  io.out.bits  := messageReg

  io.memory.request.valid          := false.B
  io.memory.request.bits.address   := alignedAddress
  io.memory.request.bits.operation := Mux(inputIsStore, MemoryOperation.write, MemoryOperation.read)
  io.memory.request.bits.writeData := storeData
  io.memory.request.bits.writeMask := storeMask
  io.memory.response.ready         := false.B

  switch(state) {
    is(LsuState.sendReq) {
      val next = WireDefault(io.in.bits)

      when(memoryMisaligned && !trapValid) {
        next.exception.valid := true.B
        next.exception.cause := Mux(
          isStore,
          TrapCause.StoreAddrMisaligned,
          TrapCause.LoadAddrMisaligned
        )
      }

      when(!inputIsMemoryRequest) {
        io.out.valid := io.in.valid
        io.out.bits  := next
        io.in.ready  := io.out.ready
      }

      when(inputIsLoad) {
        io.memory.request.valid := io.in.valid
        io.in.ready             := io.memory.request.ready

        when(io.memory.request.fire) {
          messageReg    := next
          byteOffsetReg := inputByteOffset
          state         := LsuState.waitReadResp
        }
      }

      when(inputIsStore) {
        io.memory.request.valid := io.in.valid
        io.in.ready             := io.memory.request.ready

        when(io.memory.request.fire) {
          messageReg := next
          state      := LsuState.waitWriteResp
        }
      }
    }
    is(LsuState.waitReadResp) {
      io.memory.response.ready := true.B

      when(io.memory.response.fire) {
        val shiftedMemoryData: UInt = io.memory.response.bits.readData >> (byteOffsetReg << 3)
        val byteLoadData:      UInt = Mux(
          messageReg.decode.mem.unsigned,
          shiftedMemoryData(7, 0).pad(xlen),
          shiftedMemoryData(7, 0).asSInt.pad(xlen).asUInt
        )
        val halfLoadData:      UInt = Mux(
          messageReg.decode.mem.unsigned,
          shiftedMemoryData(15, 0).pad(xlen),
          shiftedMemoryData(15, 0).asSInt.pad(xlen).asUInt
        )
        val formattedLoadData: UInt = MuxLookup(
          messageReg.decode.mem.size,
          shiftedMemoryData
        )(
          Seq(
            MemSize.Byte -> byteLoadData,
            MemSize.Half -> halfLoadData,
            MemSize.Word -> shiftedMemoryData
          )
        )

        val response = WireDefault(messageReg)
        response.memData         := formattedLoadData
        response.exception.valid := io.memory.response.bits.code =/= MemoryResponseCode.okay
        response.exception.cause := TrapCause.LoadAccessFault
        messageReg               := response
        state                    := LsuState.output
      }
    }
    is(LsuState.waitWriteResp) {
      io.memory.response.ready := true.B

      when(io.memory.response.fire) {
        val response = WireDefault(messageReg)
        response.exception.valid := io.memory.response.bits.code =/= MemoryResponseCode.okay
        response.exception.cause := TrapCause.StoreAccessFault
        messageReg               := response
        state                    := LsuState.output
      }
    }
    is(LsuState.output) {
      io.out.valid := true.B
      io.out.bits  := messageReg

      when(io.out.fire) {
        state := LsuState.sendReq
      }
    }
  }
}
