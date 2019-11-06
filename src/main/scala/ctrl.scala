import chisel3._

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
 * Stall signal controller.
 * Currently only presents a naive impl:
 * Pause the entire pipeline if any stage is stalled
 */
class Ctrl extends Module {
  val io = IO(new Bundle {
    val inputWait = Output(Bool()) // controls acceptor.io.read.en
    val nat = new StageStall
    val forward = new StageStall
    val arp = new StageStall
    val encoder = new StageStall
  });

  val anyStalled = io.nat.stall || io.forward.stall || io.arp.stall || io.encoder.stall
  io.inputWait := anyStalled
  io.nat.pause := anyStalled
  io.forward.pause := anyStalled
  io.arp.pause := anyStalled
  io.encoder.pause := anyStalled
}