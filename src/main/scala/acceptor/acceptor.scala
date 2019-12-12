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
    val payloadWriter = Flipped(new AsyncWriter(new EncoderUnit))
  })

  val ipAcceptor = Module(new IPAcceptor)
  ipAcceptor.io.rx <> io.rx
  ipAcceptor.io.payloadWriter <> io.payloadWriter

  val cnt = RegInit(0.asUInt(12.W))
  val header = Reg(Vec(HEADER_LEN, UInt(8.W)))
  val pactype = PacType.parse(header.slice(0, 2))

  val output = Wire(new Packet(PORT_COUNT))

  val opaque = RegInit(false.B)
  val opaqueRecv = Wire(Bool())
  opaqueRecv := opaque

  output.eth.sender := (header.asUInt >> (18 - 12) * 8)
  output.eth.dest := (header.asUInt >> (18 - 6) * 8)
  output.eth.vlan := header(2)
  output.eth.pactype := pactype

  when(io.rx.tvalid) {
    when(io.rx.tlast) {
      cnt := 0.U
      opaque := false.B
    } .otherwise {
      cnt := cnt +% 1.U
    }
  }

  when(io.rx.tvalid) {
    when(cnt < HEADER_LEN.U) {
      header(17.U - cnt) := io.rx.tdata
    }.elsewhen(opaqueRecv) {
      io.payloadWriter.en := true.B
      io.payloadWriter.data.data := io.rx.tdata
      io.payloadWriter.full := io.rx.tlast
    }
  }

  val destMatch = output.eth.dest === 0xFFFFFFFFFFFFl.U || output.eth.dest === MACS(output.eth.vlan)

  val headerEnd = cnt === HEADER_LEN.U && RegNext(cnt) =/= HEADER_LEN.U

  val isOpaque = pactype =/= PacType.ipv4 && destMatch && headerEnd
  when(headerEnd && isOpaque && !io.payloadWriter.progfull) {
    opaque := true.B
    opaqueRecv := true.B

    // FIXME: check fifo space
  }

  ipAcceptor.io.start := pactype === PacType.ipv4 && destMatch && headerEnd

  val ipEmit = pactype === PacType.ipv4 && ipAcceptor.io.headerFinished && !RegNext(ipAcceptor.io.headerFinished)
  val ipIgnore = pactype === PacType.ipv4 && ipAcceptor.io.ignored

  output.ip := ipAcceptor.io.ip
  output.icmp := ipAcceptor.io.icmp

  io.writer.en := isOpaque || (ipEmit && !ipIgnore)
  io.writer.data := output
  io.writer.full := DontCare
  io.writer.progfull := DontCare

  io.writer.clk := this.clock
}
