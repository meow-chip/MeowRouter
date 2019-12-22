package acceptor;

import chisel3._;
import data._;
import chisel3.util._
import chisel3.experimental._
import _root_.util._
import encoder.EncoderUnit

class Acceptor(PORT_COUNT: Int) extends MultiIOModule {
  // Header Length = MAC * 2 + VLAN + EtherType
  val HEADER_LEN = 6 * 2 + 4 + 2

  val macs = IO(Input(Vec(PORT_COUNT+1, UInt(48.W))))

  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))

    val writer = Flipped(new AsyncWriter(new Packet(PORT_COUNT)))
    val payloadWriter = Flipped(new AsyncWriter(new EncoderUnit))
  })

  val cnt = RegInit(0.asUInt(12.W))

  object State extends ChiselEnum {
    val eth, ip, body = Value
  }

  val state = RegInit(State.eth)

  val header = Reg(Vec(HEADER_LEN, UInt(8.W)))
  val ip = Reg(Vec(IP.HeaderLength/8, UInt(8.W)))
  val fusedHeader = Wire(header.cloneType)
  fusedHeader := header
  fusedHeader(17.U - cnt) := io.rx.tdata

  val pactype = PacType.parse(header.slice(0, 2))
  val fusedPactype = PacType.parse(fusedHeader.slice(0, 2))

  val output = Wire(new Packet(PORT_COUNT))

  val dropped = RegInit(false.B)

  val emit = Wire(Bool())
  emit := false.B
  val emitted = RegNext(emit)

  io.rx.tready := true.B

  output.eth.sender := (header.asUInt >> (18 - 12) * 8)
  output.eth.dest := (header.asUInt >> (18 - 6) * 8)
  output.eth.vlan := header(2)
  output.eth.pactype := pactype
  output.ip := ip.asTypeOf(output.ip)
  val destMatch = (
    output.eth.dest === 0xFFFFFFFFFFFFl.U // Broadcast
    || output.eth.dest === macs(output.eth.vlan) // Unicast
    || output.eth.dest(47, 24) === 0x01005E.U // Multicast
  )

  io.payloadWriter.clk := this.clock
  io.payloadWriter.data := DontCare
  io.payloadWriter.en := false.B

  switch(state) {
    is(State.eth) {
      header(17.U - cnt) := io.rx.tdata

      when(io.rx.tvalid) {
        when(cnt < (HEADER_LEN-1).U) {
          cnt := cnt +% 1.U
        }.otherwise {
          when(fusedPactype === PacType.ipv4) {
            cnt := 0.U
            state := State.ip
          }.otherwise {
            emit := true.B
            dropped := io.writer.full || io.payloadWriter.progfull || !destMatch
            state := State.body
          }
        }
      }
    }

    is(State.ip) {
      ip((IP.HeaderLength/8 - 1).U - cnt) := io.rx.tdata

      when(io.rx.tvalid) {
        when(cnt < (IP.HeaderLength/8-1).U) {
          cnt := cnt +% 1.U
        }.otherwise {
          emit := true.B
          dropped := io.writer.full || io.payloadWriter.progfull || !destMatch
          state := State.body
        }
      }
    }

    is(State.body) {
      io.payloadWriter.en := io.rx.tvalid && !dropped
      io.payloadWriter.data.data := io.rx.tdata
      io.payloadWriter.data.last := io.rx.tlast

      when(io.rx.tvalid && io.rx.tlast) {
        state := State.eth
        cnt := 0.U
        dropped := false.B
      }
    }
  }

  io.writer.en := emitted && !dropped
  io.writer.data := output

  io.writer.clk := this.clock
}
