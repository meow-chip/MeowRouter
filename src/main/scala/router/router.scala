package top

import chisel3._;

import acceptor.Acceptor
import encoder.Encoder
import encoder.EncoderUnit
import chisel3.core.withClock
import data._
import ch.qos.logback.core.helpers.Transform
import _root_.util.AsyncBridge
import transmitter.Transmitter
import forward._
import arp.ARPTable
import _root_.util.Consts
import adapter._

/**
 * The router module
 * Pipeline:
 * (Eth & CPU) -> Forward Table Lookup -> ARP Cache Lookup -> Encoder -> (Eth)
 *                                                    |
 *                                                    ---> CPU (Forward table miss, ARP cache miss, or dest === localhost)
 */
class Router(PORT_NUM: Int) extends Module {
  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val tx_clk = Input(Clock())

    val rx = Flipped(new AXIS(8))
    val tx = new AXIS(8)

    val buf = new BufPort

    val cmd = Input(new Cmd)

    val axi = new AXI(64)
  })

  val acceptorBridge = Module(new AsyncBridge(new Packet(PORT_NUM)))
  acceptorBridge.io.read.clk := this.clock

  val transmitterBridge = Module(new AsyncBridge(new EncoderUnit))
  transmitterBridge.io.write.clk := this.clock
  transmitterBridge.io.write.progfull := DontCare

  val payloadBridge = Module(new AsyncBridge(new EncoderUnit, Consts.PAYLOAD_BUF, Consts.MAX_MTU))

  val ctrl = Module(new Ctrl())
  acceptorBridge.io.read.en := !ctrl.io.inputWait
  ctrl.cmd := io.cmd

  withClock(io.rx_clk) {
    val acceptor = Module(new Acceptor(PORT_NUM))

    acceptor.io.rx <> io.rx
    acceptorBridge.io.write <> acceptor.io.writer
    acceptor.macs := RegNext(RegNext(ctrl.macs))
    payloadBridge.io.write <> acceptor.io.payloadWriter
  }

  val forward = Module(new CuckooFT(PORT_NUM))
  forward.ips := ctrl.ips
  ctrl.io.forward.stall <> forward.io.stall
  ctrl.io.forward.pause <> forward.io.pause
  forward.io.input := acceptorBridge.io.read.data
  forward.io.status := Mux(acceptorBridge.io.read.empty, Status.vacant, Status.normal)
  forward.io.axi <> io.axi
  
  val arp = Module(new ARPTable(PORT_NUM, 8))
  arp.ips := ctrl.ips
  arp.macs := ctrl.macs
  arp.cmd := io.cmd
  ctrl.io.arp.stall <> arp.io.stall
  ctrl.io.arp.pause <> arp.io.pause
  forward.io.output <> arp.io.input
  forward.io.outputStatus <> arp.io.status

  val encoder = Module(new Encoder(PORT_NUM))
  ctrl.io.encoder.stall <> encoder.io.stall
  ctrl.io.encoder.pause <> encoder.io.pause

  val packet = acceptorBridge.io.read.data

  encoder.io.input := arp.io.output
  encoder.io.status := arp.io.outputStatus
  encoder.io.writer <> transmitterBridge.io.write
  encoder.io.payloadReader <> payloadBridge.io.read

  val adapter = Module(new Adapter)
  adapter.toBuf <> io.buf
  encoder.toAdapter <> adapter.fromEnc
  adapter.toEnc <> encoder.fromAdapter

  withClock(io.tx_clk) {
    val transmitter = Module(new Transmitter)
    transmitter.io.reader <> transmitterBridge.io.read
    transmitter.io.tx <> io.tx
  }
}
