package npc.unit

import chisel3._
import chisel3.util._
import npc.common.BranchOp

class BranchUnit(xlen: Int) extends Module {
  val io: BranchUnitIO = IO(new BranchUnitIO(xlen))

  io.taken := MuxLookup(
    io.op,
    false.B
  )(
    Seq(
      BranchOp.Eq  -> (io.src1 === io.src2),
      BranchOp.Ne  -> (io.src1 =/= io.src2),
      BranchOp.Lt  -> (io.src1.asSInt < io.src2.asSInt),
      BranchOp.Ge  -> (io.src1.asSInt >= io.src2.asSInt),
      BranchOp.Ltu -> (io.src1 < io.src2),
      BranchOp.Geu -> (io.src1 >= io.src2)
    )
  )
}
