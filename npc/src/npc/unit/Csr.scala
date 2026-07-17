package npc.unit

import chisel3._
import chisel3.util._
import npc.common.{CsrAddr, CsrCmd}

class Csr(xlen: Int) extends Module {
  val io: CsrIO = IO(new CsrIO(xlen))

  private val mstatus: UInt = RegInit(0x1800.U(xlen.W))
  private val mtvec:   UInt = RegInit(0.U(xlen.W))
  private val mepc:    UInt = RegInit(0.U(xlen.W))
  private val mcause:  UInt = RegInit(0.U(xlen.W))

  private val selectedData: UInt = MuxLookup(
    io.commit.addr,
    0.U(xlen.W)
  )(
    Seq(
      CsrAddr.Mstatus -> mstatus,
      CsrAddr.Mtvec   -> mtvec,
      CsrAddr.Mepc    -> mepc,
      CsrAddr.Mcause  -> mcause
    )
  )

  private val writeData: UInt = MuxLookup(
    io.commit.cmd,
    selectedData
  )(
    Seq(
      CsrCmd.Write -> io.commit.src,
      CsrCmd.Set   -> (selectedData | io.commit.src),
      CsrCmd.Clear -> (selectedData & ~io.commit.src)
    )
  )

  when(io.commit.trapReq.valid) {
    mepc   := io.commit.trapReq.pc
    mcause := io.commit.trapReq.cause
  }.elsewhen(io.commit.wen) {
    switch(io.commit.addr) {
      is(CsrAddr.Mstatus) { mstatus := writeData }
      is(CsrAddr.Mtvec) { mtvec := writeData }
      is(CsrAddr.Mepc) { mepc := writeData }
      is(CsrAddr.Mcause) { mcause := writeData }
    }
  }

  io.commit.rdata      := selectedData
  io.commit.trapVector := mtvec
  io.epc               := mepc
}
