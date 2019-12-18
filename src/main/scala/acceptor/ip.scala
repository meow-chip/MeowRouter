package acceptor

import chisel3._;
import data._;
import chisel3.util._
import _root_.util.AsyncWriter
import encoder.EncoderUnit
import _root_.util.Consts

class IPAcceptor extends Module {
  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))
    val ip = Output(new IP)
    val start = Input(Bool())
    val headerFinished = Output(Bool())
    val ignored = Output(Bool())

    val payloadWriter = Flipped(new AsyncWriter(new EncoderUnit))
  })

  val IPHeaderByteLen = IP.HeaderLength / 8

  val ipBuf = Reg(Vec(IPHeaderByteLen, UInt(8.W)))

  val cnt = RegInit(0.U(log2Ceil(2048).W)) // MTU ~= 1500
  val reading = RegInit(false.B)
  val ignored = RegInit(false.B)

  val sIP :: sBody :: Nil = Enum(3)
  val state = RegInit(sIP)

  when(io.start) {
    reading := true.B
    ignored := false.B
    state := sIP
  }

  // feed data into FIFO
  io.payloadWriter.clk := this.clock
  io.payloadWriter.data.data := io.rx.tdata
  io.payloadWriter.data.last := false.B
  io.payloadWriter.en := false.B

  when(io.rx.tvalid && (io.start || reading)) {
    switch(state) {
      // reading the ip header
      is(sIP) {
        // fill ipBuf
        ipBuf((IPHeaderByteLen-1).U - cnt) := io.rx.tdata
        // state transition
        when(cnt < (IPHeaderByteLen-1).U) {
          // convert the endianness to little endian
          cnt := cnt +% 1.U
        } .otherwise {
          state := sBody
          ignored := io.payloadWriter.progfull || io.ip.len > Consts.MAX_MTU.U
        }
      }

      is(sBody) {
        // if this packet is illegal, we drop this packet
        // otherwise, keep it's enable
        io.payloadWriter.en := !ignored
      }
    }

    // no more data from the upper stage
    when(io.rx.tlast) {
      io.payloadWriter.data.last := true.B
      reading := false.B
      cnt := 0.U
      state := sIP
    }
  }

  io.ip := ipBuf.asUInt.asTypeOf(io.ip)
  io.ignored := ignored
  io.headerFinished := state === sBody
  io.rx.tready := true.B
}
