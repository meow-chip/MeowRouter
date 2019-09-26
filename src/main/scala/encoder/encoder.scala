package encoder

import chisel3._
import chisel3.util.Enum
import data._
import _root_.util.AsyncWriter

class EncoderUnit extends Bundle {
  val data = UInt(8.W)
  val last = Bool()
}

class Encoder(PORT_COUNT: Int) extends Module {
  val io = IO(new Bundle{
    val input = Input(new Packet(PORT_COUNT))
    val status = Input(UInt())

    val stall = Output(Bool())
    val writer = Flipped(new AsyncWriter(new EncoderUnit))
  })

  val writing = RegInit(false.B)
  val cnt = RegInit(0.U)

  val sIDLE :: sETH :: sARP :: sIP :: sIPPIPE :: Nil = Enum(5)
  val state = RegInit(sIDLE)

  val sending = Reg(new Packet(PORT_COUNT))
  val header = sending.eth.asVec

  io.writer.data.last := false.B
  io.writer.data.data := 0.asUInt.asTypeOf(io.writer.data.data)
  io.writer.en := false.B
  io.writer.clk := this.clock

  when(state === sIDLE) {
    when(io.status === Status.normal) {
      state := sETH
      sending := io.input
      cnt := 27.U
      header := io.input.eth.asVec
    }
  } .elsewhen(state === sETH) {
    // Sending ETH packet
    io.writer.data.data := header(cnt)
    io.writer.en := true.B

    when(!io.writer.full) {
      when(cnt > 0.U) {
        cnt := cnt - 1.U
      } .otherwise {
        state := sIDLE
      }
    }
  }

  io.stall := false.B
}