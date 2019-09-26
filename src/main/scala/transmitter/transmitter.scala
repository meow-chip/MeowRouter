package transmitter

import chisel3._
import data._
import _root_.util.AsyncReader
import encoder.EncoderUnit

class Transmitter extends Module {
  val io = IO(new Bundle {
    val reader = Flipped(new AsyncReader(new EncoderUnit))
    val tx = new AXIS(8)
  })
  
  io.tx.tdata := io.reader.data.data
  io.tx.tlast := io.reader.data.last
  io.tx.tvalid := !io.reader.empty
  io.reader.en := io.tx.tready
  io.reader.clk := this.clock
}