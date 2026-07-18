package npc.core

import chisel3._
import chisel3.util._
import npc.common.WbSel
import npc.interface.{CommitInfo, Message}
import npc.unit.{CsrCommitIO, RegFileWriteIO}

class WBU(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val in:       DecoupledIO[Message] = Flipped(Decoupled(new Message(xlen)))
    val regs:     RegFileWriteIO       = Flipped(new RegFileWriteIO(xlen))
    val csr:      CsrCommitIO          = Flipped(new CsrCommitIO(xlen))
    val nextPc:   DecoupledIO[UInt]    = Decoupled(UInt(xlen.W))
    val commit:   CommitInfo           = Output(new CommitInfo(xlen))
  })

  private val pc:             UInt = io.in.bits.pc
  private val valid:          Bool = io.in.bits.decode.valid
  private val imm:            UInt = io.in.bits.decode.imm
  private val rdata1:         UInt = io.in.bits.rs1Data
  private val wbSel:          UInt = io.in.bits.decode.wb.sel
  private val wbWen:          Bool = io.in.bits.decode.wb.wen
  private val rd:             UInt = io.in.bits.decode.rd
  private val memData:        UInt = io.in.bits.memData
  private val aluResult:      UInt = io.in.bits.aluResult
  private val trapValid:      Bool = io.in.bits.exception.valid
  private val trapCause:      UInt = io.in.bits.exception.cause
  private val selectedPc:     UInt = io.in.bits.selectedNextPc
  private val commitFire:     Bool = !reset.asBool && io.in.fire
  private val regWriteEnable: Bool = commitFire && valid && wbWen && !trapValid
  private val pcPlus4:        UInt = pc + 4.U
  private val csrSrc:         Bool = io.in.bits.decode.csr.src.asBool
  private val csrAddr:        UInt = io.in.bits.decode.csr.addr
  private val csrCmd:         UInt = io.in.bits.decode.csr.cmd
  private val csrWen:         Bool = io.in.bits.decode.csr.wen
  private val csrRdata:       UInt = io.csr.rdata

  private val csrSource: UInt = Mux(csrSrc, imm, rdata1)

  io.csr.addr := csrAddr
  io.csr.cmd  := csrCmd
  io.csr.src  := csrSource

  io.csr.trapReq.valid := commitFire && trapValid
  io.csr.trapReq.pc    := pc
  io.csr.trapReq.cause := trapCause
  io.csr.wen           := commitFire && valid && csrWen && !trapValid

  private val writebackData: UInt = MuxLookup(
    wbSel,
    0.U(xlen.W)
  )(
    Seq(
      WbSel.Alu -> aluResult,
      WbSel.Mem -> memData,
      WbSel.Pc4 -> pcPlus4,
      WbSel.Csr -> csrRdata
    )
  )

  private val nextPc: UInt = Mux(
    trapValid,
    io.csr.trapVector,
    selectedPc
  )
  io.nextPc.valid := commitFire
  io.nextPc.bits  := nextPc

  private val commitInfo: CommitInfo = WireDefault(0.U.asTypeOf(new CommitInfo(xlen)))
  commitInfo.valid        := commitFire && (valid || trapValid)
  commitInfo.pc           := pc
  commitInfo.inst         := io.in.bits.inst
  commitInfo.nextPc       := nextPc
  commitInfo.rd           := rd
  commitInfo.rfWen        := regWriteEnable && rd.orR
  commitInfo.rfWdata      := writebackData
  commitInfo.trap         := commitFire && trapValid
  commitInfo.trapCause    := Mux(trapValid, trapCause, 0.U)
  commitInfo.isEbreak     := commitFire && valid && io.in.bits.decode.system.ebreak
  commitInfo.skipDifftest := false.B
  io.commit               := commitInfo

  io.in.ready := true.B

  io.regs.wen   := regWriteEnable
  io.regs.waddr := rd
  io.regs.wdata := writebackData
}
