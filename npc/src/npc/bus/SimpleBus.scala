package npc.bus

import chisel3._
import chisel3.util._
import npc.interface.Dpic

object SimpleBusState extends ChiselEnum {
  val idle, response = Value
}

class SimpleBus(xlen: Int) extends Module {
  val io: SimpleBusMasterIO = IO(Flipped(new SimpleBusMasterIO(xlen)))

  private val state:   SimpleBusState.Type = RegInit(SimpleBusState.idle)
  private val respReg: SimpleBusResp       = Reg(new SimpleBusResp(xlen))

  io.req.ready  := !reset.asBool && state === SimpleBusState.idle
  io.resp.valid := state === SimpleBusState.response
  io.resp.bits  := respReg

  private val requestFire: Bool = io.req.fire
  private val writeFire:   Bool = requestFire && io.req.bits.wen

  Dpic.writeData.callWithEnable(
    writeFire,
    io.req.bits.addr,
    io.req.bits.wdata,
    io.req.bits.wmask.pad(8)
  )

  switch(state) {
    is(SimpleBusState.idle) {
      when(requestFire) {
        respReg.error := false.B

        when(io.req.bits.wen) {
          respReg.rdata := 0.U
        }.otherwise {
          respReg.rdata := Dpic.readData.call(io.req.bits.addr)
        }
        state := SimpleBusState.response
      }
    }
    is(SimpleBusState.response) {
      when(io.resp.fire) {
        state := SimpleBusState.idle
      }
    }
  }
}
