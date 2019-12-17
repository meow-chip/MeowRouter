package top

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
import nat.Nat
import forward.LLFT
import arp.ARPTable
import _root_.util.Consts
import adapter._

/**
 * The router module
 * Pipeline:
 * (Eth & CPU) -> NAT -> Forward Table Lookup -> ARP Cache Lookup -> Encoder -> (Eth)
 *                                                    |
 *                                                    ---> CPU (Forward table miss, ARP cache miss, or dest === localhost)
 * 
 * TCP Checksum stage is intended for NAT packets.
 * Currently we do not support hardward NAT, so all NAT packets are sent to CPU
 *   (Which we also dont have at the moment)
 */
class Router(PORT_NUM: Int) extends Module {
  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val tx_clk = Input(Clock())

    val rx = Flipped(new AXIS(8))
    val tx = new AXIS(8)

    val buf = new BufPort

    val cmd = Input(new Cmd)
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
    acceptor.macs := ctrl.macs
    payloadBridge.io.write <> acceptor.io.payloadWriter
  }

  val nat = Module(new Nat(PORT_NUM))
  ctrl.io.nat.stall := nat.io.stall
  nat.io.pause := ctrl.io.nat.pause
  nat.io.input := acceptorBridge.io.read.data
  nat.io.status := Mux(acceptorBridge.io.read.empty, Status.vacant, Status.normal)

  val forward = Module(new LLFT(PORT_NUM))
  forward.ips := ctrl.ips
  ctrl.io.forward.stall <> forward.io.stall
  ctrl.io.forward.pause <> forward.io.pause
  forward.io.input := nat.io.output
  forward.io.status := nat.io.outputStatus
  
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
