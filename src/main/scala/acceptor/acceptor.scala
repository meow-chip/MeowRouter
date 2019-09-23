package acceptor;

import chisel3._;
import data._;
import chisel3.util.log2Ceil

class Acceptor(PORT_COUNT: Int) extends Module {
  // Header Length = MAC * 2 + VLAN + EtherType
  val HEADER_LEN = 6 * 2 + 4 + 2

  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))
    val eth = Output(new Eth(PORT_COUNT + 1)) // Port = 0 should refer to localhost
    val arp = Output(new ARP(48, 32))
    val ip = Output(new IP)
    val emit = Output(Bool())
  })

  val cnt = RegInit(0.asUInt(12.W))
  val header = Reg(Vec(HEADER_LEN, UInt(8.W)))

  // TODO: put payload into ring buffer
  io.eth.sender.addr := header.asUInt >> (18 - 12) * 8
  io.eth.dest.addr := header.asUInt >> (18 - 6) * 8
  io.eth.vlan := header(12)
  io.eth.pactype := PacType.parse(header.slice(2, 0))

  when(io.rx.tvalid) {
    when(io.rx.tlast) {
      cnt := 0.U
    } .otherwise {
      cnt := cnt +% 1.U
    }
  }

  when(io.rx.tvalid) {
    when(cnt < HEADER_LEN.U) {
      header(17.U - cnt) := io.rx.tdata
    }
  }

  val arpAcceptor = Module(new ARPAcceptor)
  val ipAcceptor = Module(new IPAcceptor)

  val arpRx :: ipRx :: Nil = io.rx.split(2)
  arpAcceptor.io.rx <> arpRx
  ipAcceptor.io.rx <> ipRx

  val headerEnd = cnt === HEADER_LEN.U && RegNext(cnt) =/= HEADER_LEN.U
  arpAcceptor.io.start := io.eth.pactype === PacType.arp && headerEnd
  ipAcceptor.io.start := io.eth.pactype === PacType.ipv4 && headerEnd

  val arpEmit = io.eth.pactype === PacType.arp && arpAcceptor.io.finished && !RegNext(arpAcceptor.io.finished)
  val ipEmit = io.eth.pactype === PacType.ipv4 && ipAcceptor.io.headerFinished && !RegNext(ipAcceptor.io.headerFinished)

  io.emit := arpEmit || ipEmit
  io.arp := arpAcceptor.io.output
  io.ip := ipAcceptor.io.output
}