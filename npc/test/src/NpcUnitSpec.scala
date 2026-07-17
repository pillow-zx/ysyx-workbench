package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import npc.common.{AluSrc1, BranchOp, CsrAddr, CsrCmd, PcSel, WbSel}
import npc.unit.{BranchUnit, Csr, Decode}
import org.scalatest.flatspec.AnyFlatSpec

class NpcUnitSpec extends AnyFlatSpec {
  behavior.of("BranchUnit")

  it should "distinguish signed and unsigned comparisons" in {
    simulate(new BranchUnit(32)) { dut =>
      dut.io.src1.poke("hffffffff".U)
      dut.io.src2.poke(1.U)

      dut.io.op.poke(BranchOp.Lt)
      dut.io.taken.expect(true.B)

      dut.io.op.poke(BranchOp.Ltu)
      dut.io.taken.expect(false.B)

      dut.io.op.poke(BranchOp.Ne)
      dut.io.taken.expect(true.B)
    }
  }

  behavior.of("Decode")

  it should "decode writeback and system control signals" in {
    simulate(new Decode(32)) { dut =>
      dut.io.inst.poke("h123450b7".U)
      dut.io.result.valid.expect(true.B)
      dut.io.result.alu.src1.expect(AluSrc1.Zero)
      dut.io.result.wb.wen.expect(true.B)
      dut.io.result.wb.sel.expect(WbSel.Alu)
      dut.io.result.imm.expect("h12345000".U)

      dut.io.inst.poke("h008000ef".U)
      dut.io.result.valid.expect(true.B)
      dut.io.result.pc.sel.expect(PcSel.Jal)
      dut.io.result.wb.sel.expect(WbSel.Pc4)
      dut.io.result.imm.expect(8.U)

      dut.io.inst.poke("h00100073".U)
      dut.io.result.valid.expect(true.B)
      dut.io.result.system.illegal.expect(false.B)
      dut.io.result.system.ebreak.expect(true.B)

      dut.io.inst.poke("h30200073".U)
      dut.io.result.valid.expect(true.B)
      dut.io.result.pc.sel.expect(PcSel.Epc)
      dut.io.result.system.mret.expect(true.B)

      dut.io.inst.poke("hffffffff".U)
      dut.io.result.valid.expect(false.B)
      dut.io.result.system.illegal.expect(true.B)
    }
  }

  behavior.of("Csr")

  it should "write CSRs and capture trap state" in {
    simulate(new Csr(32)) { dut =>
      dut.reset.poke(true.B)
      dut.io.commit.addr.poke(CsrAddr.Mtvec)
      dut.io.commit.cmd.poke(CsrCmd.None)
      dut.io.commit.src.poke(0.U)
      dut.io.commit.wen.poke(false.B)
      dut.io.commit.trapReq.valid.poke(false.B)
      dut.io.commit.trapReq.pc.poke(0.U)
      dut.io.commit.trapReq.cause.poke(0.U)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.commit.cmd.poke(CsrCmd.Write)
      dut.io.commit.src.poke("h80000100".U)
      dut.io.commit.wen.poke(true.B)
      dut.clock.step()
      dut.io.commit.wen.poke(false.B)
      dut.io.commit.rdata.expect("h80000100".U)

      dut.io.commit.trapReq.valid.poke(true.B)
      dut.io.commit.trapReq.pc.poke("h80000020".U)
      dut.io.commit.trapReq.cause.poke(2.U)
      dut.io.commit.trapVector.expect("h80000100".U)
      dut.clock.step()
      dut.io.commit.trapReq.valid.poke(false.B)

      dut.io.commit.addr.poke(CsrAddr.Mepc)
      dut.io.commit.rdata.expect("h80000020".U)
      dut.io.epc.expect("h80000020".U)

      dut.io.commit.addr.poke(CsrAddr.Mcause)
      dut.io.commit.rdata.expect(2.U)
    }
  }
}
