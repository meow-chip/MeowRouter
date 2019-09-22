package acceptor;

import chisel3._;
import data._;
import chisel3.util.log2Ceil

class Acceptor(PORT_COUNT: Int) extends Module {
  // Header Length = MAC * 2 + VLAN + EtherType
  val HEADER_LEN = 6 * 2 + 4 + 2

  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))

    val dest = Output(new MACAddr)
    val sender = Output(new MACAddr)
    val pactype = Output(PacType())
    val vlan = Output(UInt(log2Ceil(PORT_COUNT+1).W)) // Port = 0 should refer to localhost

    val arp = Output(new ARP(48, 32))

    val emit = Output(Bool())
  })

  val cnt = RegInit(0.asUInt(12.W))
  val header = Reg(Vec(HEADER_LEN, UInt(8.W)))

  // TODO: put payload into ring buffer
  io.sender.addr := header.asUInt >> 48
  io.dest.addr := header.asUInt
  io.vlan := header(15)
  io.pactype := PacType.parse(header.slice(16, 18))

  when(io.rx.tvalid) {
    when(io.rx.tlast) {
      cnt := 0.U
    } .otherwise {
      cnt := cnt +% 1.U
    }
  }

  when(io.rx.tvalid) {
    when(cnt < HEADER_LEN.U) {
      header(cnt) := io.rx.tdata
    }
  }

  val arpAcceptor = Module(new ARPAcceptor())
  arpAcceptor.io.rx <> io.rx
  arpAcceptor.io.start := io.rx.tvalid && cnt === (HEADER_LEN-1).U
  val arpEmit = Wire(Bool())
  arpEmit := io.pactype === PacType.arp && arpAcceptor.io.finished && !RegNext(arpAcceptor.io.finished)
  io.emit := arpEmit
  io.arp := arpAcceptor.io.output
}