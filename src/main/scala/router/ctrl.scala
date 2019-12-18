package top

import chisel3._
import chisel3.experimental._
import chisel3.util._
import _root_.data._

/**
 * Stall signal from/to a single stage
 * 
 * stall: The stage request a stall
 * pause: The stage should hold its output and pause
 *   This means that this stage is probably stalled by the succeeding stage(s)
 */
class StageStall extends Bundle {
  val stall = Input(Bool())
  val pause = Output(Bool())
}

/**
 * Command interface
 * 
 * A command sent by the CPU
 * 
 * Command is applied on the raising edge of op
 * All fields are given in reversed order comparing to the software repr
 * because we are using big-endian here.
 */
class Cmd extends Bundle {
  val data = UInt(48.W)
  val idx = UInt(8.W)
  val op = Op()

  def fired() = RegNext(op) === Op.nop && op =/= Op.nop
}

object Op extends ChiselEnum {
  val nop = Value(0.U)
  val setIP = Value(1.U)
  val setMAC = Value(2.U) // Actually, we don't know if MAC can be altered during runtime
  val writeNCEntIP = Value(3.U) // NCEnt = Neighboor cache entry
  val writeNCEntMAC = Value(4.U)
  val writeNCEntPort = Value(5.U)
  val enableNCEnt = Value(6.U)
  val disableNCEnt = Value(7.U)

  val inval = Value(0xFF.U) // Pad op length to 8
}

/**
 * Stall signal controller.
 * Currently only presents a naive impl:
 * Pause the entire pipeline if any stage is stalled
 */
class Ctrl extends MultiIOModule {
  val io = IO(new Bundle {
    val inputWait = Output(Bool()) // controls acceptor.io.read.en
    val forward = new StageStall
    val arp = new StageStall
    val encoder = new StageStall
  });

  val anyStalled = io.forward.stall || io.arp.stall || io.encoder.stall
  io.inputWait := anyStalled
  io.forward.pause := anyStalled
  io.arp.pause := anyStalled
  io.encoder.pause := anyStalled

  val macStore = RegInit(VecInit(Seq(
    0x000000000000L.U(48.W),
    0x000000000001L.U(48.W),
    0x000000000002L.U(48.W),
    0x000000000003L.U(48.W),
    0x000000000004L.U(48.W)
  )))

  val ipStore = RegInit(VecInit(Seq(
    0x0A010001.U(32.W),
    0x0A000101.U(32.W),
    0x0A000201.U(32.W),
    0x0A000301.U(32.W),
    0x0A000401.U(32.W)
  )))

  val macs = IO(Output(Vec(macStore.length, UInt(48.W))))
  macs := macStore

  val ips = IO(Output(Vec(ipStore.length, UInt(32.W))))
  ips := ipStore

  val cmd = IO(Input(new Cmd))

  assert(cmd.getWidth == 64)
  assert(cmd.op.getWidth == 8)

  when(cmd.fired()) {
    switch(cmd.op) {
      is(Op.setIP) {
        ipStore(cmd.idx) := cmd.data
      }

      is(Op.setMAC) {
        macStore(cmd.idx) := cmd.data
      }
    }
  }
}
