package acceptor

import chisel3._;
import data._;
import chisel3.util.log2Ceil
import _root_.util.AsyncWriter
import encoder.EncoderUnit

class IPAcceptor extends Module {
  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))
    val output = Output(new IP)
    val start = Input(Bool())
    val headerFinished = Output(Bool())

    val payloadWriter = Flipped(new AsyncWriter(new EncoderUnit))
  })

  val HeaderByteLen = IP.HeaderLength / 8
  val buf = Reg(Vec(HeaderByteLen, UInt(8.W)))

  val cnt = RegInit(0.U(log2Ceil(2048).W)) // MTU ~= 1500
  val reading = RegInit(false.B)
  val header = RegInit(false.B)

  io.payloadWriter.clk := this.clock
  io.payloadWriter.data.data := io.rx.tdata
  io.payloadWriter.data.last := false.B
  io.payloadWriter.en := false.B

  when(io.start) {
    reading := true.B
    header := true.B
  }

  when(io.rx.tvalid && (io.start || reading)) {
    when(cnt < HeaderByteLen.U) {
      buf(19.U - cnt) := io.rx.tdata
      cnt := cnt +% 1.U
    } .otherwise {
      io.payloadWriter.en := true.B
    }

    when(cnt === HeaderByteLen.U && (RegNext(cnt) =/= HeaderByteLen.U)) {
      header := false.B
    }

    when(io.rx.tlast) {
      io.payloadWriter.data.last := true.B
      reading := false.B
      cnt := 0.U
    }
  }

  io.output := (new IP).fromBits(buf.toBits)
  io.headerFinished := !header
  io.rx.tready := true.B
}