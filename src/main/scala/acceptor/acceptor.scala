package acceptor;

import chisel3._;
import data._;
import chisel3.util.log2Ceil
import _root_.util._
import encoder.EncoderUnit

class Acceptor(PORT_COUNT: Int) extends Module {
  // Header Length = MAC * 2 + VLAN + EtherType
  val HEADER_LEN = 6 * 2 + 4 + 2
  val MACS = VecInit(Consts.LOCAL_MACS)

  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))

    val writer = Flipped(new AsyncWriter(new Packet(PORT_COUNT)))
    val ipWriter = Flipped(new AsyncWriter(new EncoderUnit))
  })

  val cnt = RegInit(0.asUInt(12.W))
  val header = Reg(Vec(HEADER_LEN, UInt(8.W)))
  val pactype = PacType.parse(header.slice(0, 2))

  val output = Wire(new Packet(PORT_COUNT))

  // TODO: put payload into ring buffer
  output.eth.sender := (header.asUInt >> (18 - 12) * 8)
  output.eth.dest := (header.asUInt >> (18 - 6) * 8)
  output.eth.vlan := header(2)
  output.eth.pactype := pactype

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

  val destMatch = output.eth.dest === 0xFFFFFFFFFFFFl.U || output.eth.dest === MACS(output.eth.vlan)

  val arpAcceptor = Module(new ARPAcceptor)
  val ipAcceptor = Module(new IPAcceptor)

  val arpRx :: ipRx :: Nil = io.rx.split(2)
  arpAcceptor.io.rx <> arpRx
  ipAcceptor.io.rx <> ipRx

  ipAcceptor.io.payloadWriter <> io.ipWriter

  val headerEnd = cnt === HEADER_LEN.U && RegNext(cnt) =/= HEADER_LEN.U
  arpAcceptor.io.start := pactype === PacType.arp && destMatch && headerEnd
  ipAcceptor.io.start := pactype === PacType.ipv4 && destMatch && headerEnd

  val arpEmit = pactype === PacType.arp && arpAcceptor.io.finished && !RegNext(arpAcceptor.io.finished)
  val ipEmit = pactype === PacType.ipv4 && ipAcceptor.io.headerFinished && !RegNext(ipAcceptor.io.headerFinished)
  val ipIgnore = pactype === PacType.ipv4 && ipAcceptor.io.ignored

  output.ip := ipAcceptor.io.output
  output.arp := arpAcceptor.io.output

  io.writer.en := arpEmit || (ipEmit && !ipIgnore)
  io.writer.data := output
  io.writer.full := DontCare
  io.writer.progfull := DontCare
  // TODO: skip body on drop
  io.writer.clk := this.clock
}
