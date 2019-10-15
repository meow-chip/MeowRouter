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
    val icmp = Output(new ICMP)
    val start = Input(Bool())
    val headerFinished = Output(Bool())
    val ignored = Output(Bool())

    val payloadWriter = Flipped(new AsyncWriter(new EncoderUnit))
  })

  val IPHeaderByteLen = IP.HeaderLength / 8
  val ICMPHeaderByteLen = ICMP.HeaderLength / 8

  val ipBuf = Reg(Vec(IPHeaderByteLen, UInt(8.W)))
  val icmpBuf = Reg(Vec(ICMPHeaderByteLen, UInt(8.W)))

  val cnt = RegInit(0.U(log2Ceil(2048).W)) // MTU ~= 1500
  val reading = RegInit(false.B)
  val header = RegInit(false.B)
  val ignored = RegInit(false.B)

  io.payloadWriter.clk := this.clock
  io.payloadWriter.data.data := io.rx.tdata
  io.payloadWriter.data.last := false.B
  io.payloadWriter.en := false.B

  val sIP :: sICMP :: Nil = Enum(2)
  val state = RegInit(sIP)

  when(io.start) {
    reading := true.B
    header := true.B
    ignored := false.B
    state := sIP
  }


  when(io.rx.tvalid && (io.start || reading)) {
    switch(state) {
      // reading the ip header
      is(sIP) {
        // filling ipBuf
        when(cnt < IPHeaderByteLen.U) {
          // convert the endianness to little endian
          ipBuf((IPHeaderByteLen-1).U - cnt) := io.rx.tdata
          cnt := cnt +% 1.U
        }
        
        when(cnt === (IPHeaderByteLen-1).U && (RegNext(cnt) =/= (IPHeaderByteLen-1).U)) {
          // Does continue to read?
          // If it's a icmp packet, continue.
          // Otherwise, finishedHeader = true
          when (io.ip.proto === IP.ICMP_PROTO.U) {
            cnt := 0.U
            state := sICMP
          } .otherwise {
            header := false.B
          }

          //TODO: need Xiayi to confirm this. Do we also need to put this statement into the processing of ICMP
          ignored := io.payloadWriter.progfull || io.ip.len > Consts.MAX_MTU.U
        }
      }

      // reading the icmp header
      is(sICMP) {
        // filling icmpBuf
        when (cnt < (ICMPHeaderByteLen).U) {
          icmpBuf((ICMPHeaderByteLen-1).U - cnt) := io.rx.tdata
          cnt := cnt +% 1.U
        }

        // finishedHeader = !header = true
        when ((cnt === (ICMPHeaderByteLen - 1).U) && (RegNext(cnt) =/= (ICMPHeaderByteLen - 1).U)) {
          header := false.B
        }
      }
    }

    // drop this packet?
    // TODO: need Xiayi to confirm this
    io.payloadWriter.en := !ignored
      
    // no more data from the upper stage
    when(io.rx.tlast) {
      io.payloadWriter.data.last := true.B
      reading := false.B
      cnt := 0.U
    }
  }

  io.ip := ipBuf.asUInt.asTypeOf(io.ip)
  io.icmp := icmpBuf.asUInt.asTypeOf(io.icmp)
  io.ignored := ignored
  io.headerFinished := !header
  io.rx.tready := true.B
}