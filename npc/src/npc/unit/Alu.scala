package npc.unit

import chisel3._
import chisel3.util._
import npc.common.AluOp

class Alu(xlen: Int) extends Module {
  val io: AluIO = IO(new AluIO(xlen))

  private val result: UInt = WireDefault(0.U(xlen.W))
  private val shamtWidth = log2Ceil(xlen)

  switch(io.op) {
    is(AluOp.Add) {
      result := io.src1 + io.src2
    }
    is(AluOp.Subtract) {
      result := io.src1 - io.src2
    }
    is(AluOp.ShiftLeftLogical) {
      result := io.src1 << io.src2(shamtWidth - 1, 0)
    }
    is(AluOp.LessThanSigned) {
      result := (io.src1.asSInt < io.src2.asSInt).asUInt
    }
    is(AluOp.LessThanUnsigned) {
      result := (io.src1 < io.src2).asUInt
    }
    is(AluOp.BitwiseAnd) {
      result := io.src1 & io.src2
    }
    is(AluOp.BitwiseOr) {
      result := io.src1 | io.src2
    }
    is(AluOp.BitwiseXor) {
      result := io.src1 ^ io.src2
    }
    is(AluOp.ShiftRightLogical) {
      result := io.src1 >> io.src2(shamtWidth - 1, 0)
    }
    is(AluOp.ShiftRightArithmetic) {
      result := (io.src1.asSInt >> io.src2(shamtWidth - 1, 0)).asUInt
    }
    is(AluOp.PassThroughSrc1) {
      result := io.src1
    }
    is(AluOp.PassThroughSrc2) {
      result := io.src2
    }
  }

  io.out := result
}
