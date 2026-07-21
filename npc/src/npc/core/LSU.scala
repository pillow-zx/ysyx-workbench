package npc.core

import chisel3._
import chisel3.util._
import npc.bus.axi4lite.Axi4LiteMasterIO
import npc.common.{Constants, MemSize, TrapCause}
import npc.interface.Message

object LsuState extends ChiselEnum {
  val sendReq, waitReadResp, waitWriteResp, output = Value
}

class LSU(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val in:  DecoupledIO[Message] = Flipped(Decoupled(new Message(xlen)))
    val out: DecoupledIO[Message] = Decoupled(new Message(xlen))
    val bus = new Axi4LiteMasterIO(Constants.addrWidth, Constants.dataWidth)
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

  // read
  io.bus.ar.valid     := false.B
  io.bus.ar.bits.addr := alignedAddress
  io.bus.ar.bits.prot := DontCare
  io.bus.r.ready      := false.B

  // write
  io.bus.aw.valid     := false.B
  io.bus.w.valid      := false.B
  io.bus.aw.bits.addr := alignedAddress
  io.bus.aw.bits.prot := DontCare
  io.bus.w.bits.data  := storeData
  io.bus.w.bits.strb  := storeMask
  io.bus.b.ready      := false.B

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
        io.bus.ar.valid := io.in.valid
        io.in.ready     := io.bus.ar.ready

        when(io.bus.ar.fire) {
          messageReg    := next
          byteOffsetReg := inputByteOffset
          state         := LsuState.waitReadResp
        }
      }

      when(inputIsStore) {
        io.bus.aw.valid := io.in.valid
        io.bus.w.valid  := io.in.valid
        io.in.ready     := io.bus.aw.ready && io.bus.w.ready

        when(io.bus.aw.fire && io.bus.w.fire) {
          messageReg := next
          state      := LsuState.waitWriteResp
        }
      }
    }
    is(LsuState.waitReadResp) {
      io.bus.r.ready := true.B

      when(io.bus.r.fire) {
        val shiftedMemoryData: UInt = io.bus.r.bits.data >> (byteOffsetReg << 3)
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
        response.memData := formattedLoadData
        messageReg       := response
        state            := LsuState.output
      }
    }
    is(LsuState.waitWriteResp) {
      io.bus.b.ready := true.B

      when(io.bus.b.fire) {
        state := LsuState.output
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
