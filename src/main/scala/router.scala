import chisel3._;

import data.AXIS;
import acceptor.Acceptor
import encoder.Encoder
import encoder.EncoderUnit
import chisel3.core.withClock
import data._
import ch.qos.logback.core.helpers.Transform
import _root_.util.AsyncBridge
import transmitter.Transmitter

class Router(PORT_NUM: Int) extends Module {
  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val tx_clk = Input(Clock())

    val rx = Flipped(new AXIS(8))
    val tx = new AXIS(8)
  })

  val acceptorBridge = Module(new AsyncBridge(new Packet(PORT_NUM)))
  acceptorBridge.io.read.clk := this.clock

  val transmitterBridge = Module(new AsyncBridge(new EncoderUnit))
  transmitterBridge.io.write.clk := this.clock
  transmitterBridge.io.write.space := DontCare

  val ipBridge = Module(new AsyncBridge(new EncoderUnit, 2048))

  withClock(io.rx_clk) {
    val acceptor = Module(new Acceptor(PORT_NUM))

    acceptor.io.rx <> io.rx
    acceptorBridge.io.write <> acceptor.io.writer
    ipBridge.io.write <> acceptor.io.ipWriter
  }

  val encoder = Module(new Encoder(PORT_NUM))

  val packet = acceptorBridge.io.read.data
  val status = Mux(acceptorBridge.io.read.empty, Status.vacant, Status.normal)
  acceptorBridge.io.read.en := !encoder.io.stall

  val masked = Wire(new Packet(PORT_NUM))
  masked := packet
  masked.eth.dest := packet.eth.sender
  masked.eth.sender := Consts.LOCAL_MAC

  encoder.io.input := masked
  encoder.io.status := status
  encoder.io.stall := DontCare
  encoder.io.writer <> transmitterBridge.io.write
  encoder.io.ipReader <> ipBridge.io.read

  withClock(io.tx_clk) {
    val transmitter = Module(new Transmitter)
    transmitter.io.reader <> transmitterBridge.io.read
    transmitter.io.tx <> io.tx
  }
}