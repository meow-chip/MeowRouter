import chisel3._;

import data.AXIS;
import acceptor.Acceptor
import chisel3.core.withClock

class Router(PORT_NUM: Int) extends Module {
  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val rx = Flipped(new AXIS(8))
    val tx = new AXIS(8)
  })

  withClock(io.rx_clk) {
    val acceptor = Module(new Acceptor(PORT_NUM))

    acceptor.io.rx <> io.rx

    acceptor.io.pactype := DontCare
    acceptor.io.dest := DontCare
    acceptor.io.sender := DontCare
    acceptor.io.arp := DontCare
    acceptor.io.port := DontCare

    io.tx.tdata := 0.U
    io.tx.tvalid := acceptor.io.emit
    io.tx.tlast := false.B
    io.tx.tready := DontCare
  }
}