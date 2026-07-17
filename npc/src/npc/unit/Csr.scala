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
    io.addr,
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
    io.cmd,
    selectedData
  )(
    Seq(
      CsrCmd.Write -> io.src,
      CsrCmd.Set   -> (selectedData | io.src),
      CsrCmd.Clear -> (selectedData & ~io.src)
    )
  )

  when(io.trapReq.valid) {
    mepc   := io.trapReq.pc
    mcause := io.trapReq.cause
  }.elsewhen(io.wen) {
    switch(io.addr) {
      is(CsrAddr.Mstatus) { mstatus := writeData }
      is(CsrAddr.Mtvec) { mtvec := writeData }
      is(CsrAddr.Mepc) { mepc := writeData }
      is(CsrAddr.Mcause) { mcause := writeData }
    }
  }

  io.rdata      := selectedData
  io.trapVector := mtvec
  io.epc        := mepc
}
