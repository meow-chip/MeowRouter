import chisel3._;

import data.AXIS;
import acceptor.Acceptor
import encoder.Encoder
import chisel3.core.withClock
import data._
import ch.qos.logback.core.helpers.Transform

class Router(PORT_NUM: Int) extends Module {
  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val rx = Flipped(new AXIS(8))
    val tx = new AXIS(8)
  })

  withClock(io.rx_clk) {
    val acceptor = Module(new Acceptor(PORT_NUM))
    val encoder = Module(new Encoder(PORT_NUM))

    acceptor.io.rx <> io.rx

    val arp = new ARP(48, 32)
    val ip = new IP
    val header = new Eth(PORT_NUM + 1)

    arp := acceptor.io.arp 
    ip := acceptor.io.ip
    header := acceptor.io.eth
    header.dest := acceptor.io.eth.sender
    header.sender := Consts.LOCAL_MAC

    encoder.io.arp := arp
    encoder.io.ip := ip
    encoder.io.eth := header
    encoder.io.fire := acceptor.io.emit
    encoder.io.tx <> io.tx
  }
}