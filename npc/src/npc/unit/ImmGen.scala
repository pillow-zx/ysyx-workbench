package npc.unit

import chisel3._
import chisel3.util._

object ImmSel {
  val I:     UInt = 0.U
  val S:     UInt = 1.U
  val B:     UInt = 2.U
  val U:     UInt = 3.U
  val J:     UInt = 4.U
  val Z:     UInt = 5.U
  val Width: Int  = log2Ceil(6)
}

class ImmGen(xlen: Int) extends Module {
  val io: ImmGenIO = IO(new ImmGenIO(xlen))

  private val iImm: SInt = io.inst(31, 20).asSInt.pad(xlen)

  private val sImm: SInt = Cat(
    io.inst(31, 25),
    io.inst(11, 7)
  ).asSInt.pad(xlen)

  private val bImm: SInt = Cat(
    io.inst(31),
    io.inst(7),
    io.inst(30, 25),
    io.inst(11, 8),
    0.U(1.W)
  ).asSInt.pad(xlen)

  private val uImm: SInt = Cat(
    io.inst(31, 12),
    0.U(12.W)
  ).asSInt.pad(xlen)

  private val jImm: SInt = Cat(
    io.inst(31),
    io.inst(19, 12),
    io.inst(20),
    io.inst(30, 21),
    0.U(1.W)
  ).asSInt.pad(xlen)

  private val zImm: SInt = io.inst(19, 15).zext.pad(xlen)

  io.imm := MuxLookup(
    io.sel,
    0.S(xlen.W)
  )(
    Seq(
      ImmSel.I -> iImm,
      ImmSel.S -> sImm,
      ImmSel.B -> bImm,
      ImmSel.U -> uImm,
      ImmSel.J -> jImm,
      ImmSel.Z -> zImm
    )
  ).asUInt
}
