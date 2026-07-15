package npc.core

import chisel3._
import chisel3.util._
import npc.common.{AluSrc1, AluSrc2, MemSize, NpcConfig, PcSel, TrapCause, WbSel}
import npc.interface.{CommitInfo, DebugInfo, DecodeResult, Dpic}
import npc.unit.{Alu, BranchUnit, Csr, Decode, RegFile}

class CoreIO(xlen: Int) extends Bundle {
  val commit: CommitInfo = Output(new CommitInfo(xlen))
  val debug:  DebugInfo  = Output(new DebugInfo(xlen))
}

class Core(config: NpcConfig = NpcConfig()) extends Module {
  require(config.xlen == 32, "the current DPI memory interface supports RV32 only")

  val io: CoreIO = IO(new CoreIO(config.xlen))

  private val pc:   UInt = RegInit(config.resetVector.U(config.xlen.W))
  private val inst: UInt = Dpic.fetchInst.call(pc)

  private val decode: Decode = Module(new Decode(config.xlen))
  decode.io.inst := inst
  private val decodeResult: DecodeResult = decode.io.result

  // Stage 1: operand selection.
  private val regs: RegFile = Module(new RegFile(config.xlen))
  regs.io.raddr1 := decodeResult.rs1
  regs.io.raddr2 := decodeResult.rs2
  private val rdata1: UInt = regs.io.rdata1
  private val rdata2: UInt = regs.io.rdata2

  private val operand1: UInt = MuxLookup(
    decodeResult.alu.src1,
    0.U(config.xlen.W)
  )(
    Seq(
      AluSrc1.Rs1  -> rdata1,
      AluSrc1.Pc   -> pc,
      AluSrc1.Zero -> 0.U(config.xlen.W)
    )
  )

  private val operand2: UInt = MuxLookup(
    decodeResult.alu.src2,
    0.U(config.xlen.W)
  )(
    Seq(
      AluSrc2.Rs2  -> rdata2,
      AluSrc2.Imm  -> decodeResult.imm,
      AluSrc2.Four -> 4.U(config.xlen.W)
    )
  )

  private val csrSource: UInt = Mux(decodeResult.csr.src.asBool, decodeResult.imm, rdata1)

  // Stage 2: execute and control-flow target generation.
  private val alu: Alu = Module(new Alu(config.xlen))
  alu.io.op   := decodeResult.alu.op
  alu.io.src1 := operand1
  alu.io.src2 := operand2
  private val aluResult: UInt = alu.io.out

  private val branchUnit: BranchUnit = Module(new BranchUnit(config.xlen))
  branchUnit.io.src1 := rdata1
  branchUnit.io.src2 := rdata2
  branchUnit.io.op   := decodeResult.branch.op

  private val pcPlus4:         UInt = pc + 4.U
  private val branchTarget:    UInt = pc + decodeResult.imm
  private val jalrTarget:      UInt = (rdata1 + decodeResult.imm) & (~1.U(config.xlen.W))
  private val branchTaken:     Bool = decodeResult.branch.valid && branchUnit.io.taken
  private val controlTransfer: Bool = branchTaken ||
    (decodeResult.pc.sel === PcSel.Jal) ||
    (decodeResult.pc.sel === PcSel.Jalr) ||
    (decodeResult.pc.sel === PcSel.Epc)

  private val csr: Csr = Module(new Csr(config.xlen))
  csr.io.addr := decodeResult.csr.addr
  csr.io.cmd  := decodeResult.csr.cmd
  csr.io.src  := csrSource

  private val selectedNextPc: UInt = MuxLookup(
    decodeResult.pc.sel,
    pcPlus4
  )(
    Seq(
      PcSel.Plus4  -> pcPlus4,
      PcSel.Branch -> Mux(branchTaken, branchTarget, pcPlus4),
      PcSel.Jal    -> branchTarget,
      PcSel.Jalr   -> jalrTarget,
      PcSel.Hold   -> pc,
      PcSel.Trap   -> csr.io.trapVector,
      PcSel.Epc    -> csr.io.epc
    )
  )

  // Stage 3: data memory access and load formatting.
  private val memoryAddress:        UInt = aluResult
  private val memoryByteOffset:     UInt = memoryAddress(1, 0)
  private val alignedMemoryAddress: UInt = Cat(memoryAddress(config.xlen - 1, 2), 0.U(2.W))
  private val memoryShift:          UInt = memoryByteOffset << 3

  private val memoryMisaligned: Bool = decodeResult.mem.valid && MuxLookup(
    decodeResult.mem.size,
    false.B
  )(
    Seq(
      MemSize.Byte -> false.B,
      MemSize.Half -> memoryAddress(0),
      MemSize.Word -> memoryAddress(1, 0).orR
    )
  )

  private val loadEnable:  Bool = !reset.asBool &&
    decodeResult.valid &&
    decodeResult.mem.valid &&
    !decodeResult.mem.isStore &&
    !memoryMisaligned
  private val storeEnable: Bool = !reset.asBool &&
    decodeResult.valid &&
    decodeResult.mem.valid &&
    decodeResult.mem.isStore &&
    !memoryMisaligned

  private val rawMemoryData:     UInt = Dpic.readData.callWithEnable(loadEnable, alignedMemoryAddress)
  private val shiftedMemoryData: UInt = rawMemoryData >> memoryShift
  private val byteLoadData:      UInt = Mux(
    decodeResult.mem.unsigned,
    shiftedMemoryData(7, 0).pad(config.xlen),
    shiftedMemoryData(7, 0).asSInt.pad(config.xlen).asUInt
  )
  private val halfLoadData:      UInt = Mux(
    decodeResult.mem.unsigned,
    shiftedMemoryData(15, 0).pad(config.xlen),
    shiftedMemoryData(15, 0).asSInt.pad(config.xlen).asUInt
  )
  private val formattedLoadData: UInt = MuxLookup(
    decodeResult.mem.size,
    shiftedMemoryData
  )(
    Seq(
      MemSize.Byte -> byteLoadData,
      MemSize.Half -> halfLoadData,
      MemSize.Word -> shiftedMemoryData
    )
  )
  private val loadData:          UInt = Mux(loadEnable, formattedLoadData, 0.U)

  private val baseStoreMask: UInt = MuxLookup(
    decodeResult.mem.size,
    0.U(8.W)
  )(
    Seq(
      MemSize.Byte -> "b00000001".U,
      MemSize.Half -> "b00000011".U,
      MemSize.Word -> "b00001111".U
    )
  )
  private val storeMask:     UInt = (baseStoreMask << memoryByteOffset)(7, 0)
  private val storeData:     UInt = (rdata2 << memoryShift)(config.xlen - 1, 0)
  Dpic.writeData.callWithEnable(storeEnable, alignedMemoryAddress, storeData, storeMask)

  // Stage 4: trap resolution, writeback, and next-PC selection.
  private val instructionMisaligned: Bool = controlTransfer && selectedNextPc(1, 0).orR
  private val trapValid:             Bool = decodeResult.system.illegal ||
    decodeResult.system.ecall ||
    memoryMisaligned ||
    instructionMisaligned
  private val trapCause:             UInt = MuxCase(
    TrapCause.IllegalInst,
    Seq(
      instructionMisaligned                          -> TrapCause.InstAddrMisaligned,
      (memoryMisaligned && decodeResult.mem.isStore) -> TrapCause.StoreAddrMisaligned,
      memoryMisaligned                               -> TrapCause.LoadAddrMisaligned,
      decodeResult.system.ecall                      -> TrapCause.EcallM
    )
  )

  csr.io.trapReq.valid := !reset.asBool && trapValid
  csr.io.trapReq.pc    := pc
  csr.io.trapReq.cause := trapCause
  csr.io.wen           := !reset.asBool &&
    decodeResult.valid &&
    decodeResult.csr.wen &&
    !trapValid

  private val writebackData:       UInt = MuxLookup(
    decodeResult.wb.sel,
    0.U(config.xlen.W)
  )(
    Seq(
      WbSel.Alu -> aluResult,
      WbSel.Mem -> loadData,
      WbSel.Pc4 -> pcPlus4,
      WbSel.Csr -> csr.io.rdata
    )
  )
  private val registerWriteEnable: Bool = !reset.asBool &&
    decodeResult.valid &&
    decodeResult.wb.wen &&
    !trapValid

  regs.io.wen   := registerWriteEnable
  regs.io.waddr := decodeResult.rd
  regs.io.wdata := writebackData

  private val nextPc: UInt = Mux(trapValid, csr.io.trapVector, selectedNextPc)
  pc := nextPc

  io.commit.valid        := !reset.asBool && (decodeResult.valid || trapValid)
  io.commit.pc           := pc
  io.commit.inst         := inst
  io.commit.nextPc       := nextPc
  io.commit.rd           := decodeResult.rd
  io.commit.rfWen        := registerWriteEnable && decodeResult.rd.orR
  io.commit.rfWdata      := writebackData
  io.commit.trap         := trapValid
  io.commit.trapCause    := Mux(trapValid, trapCause, 0.U)
  io.commit.isEbreak     := decodeResult.valid && decodeResult.system.ebreak
  io.commit.skipDifftest := false.B

  io.debug.pc  := pc
  io.debug.gpr := regs.io.debugGpr
}
