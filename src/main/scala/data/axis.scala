package data

import chisel3._

// AXI-S producer
class AXIS(val WIDTH: Int) extends Bundle {
  var tdata = Output(UInt(WIDTH.W))
  var tvalid = Output(Bool())
  var tlast = Output(Bool())
  var tready = Input(Bool())

  def split(n: Int) : List[AXIS] = {
    var ready = false.B
    val coll = for(_ <- (0 until n)) yield {
      val y = Wire(new AXIS(WIDTH))

      y.tdata := tdata
      y.tvalid := tvalid
      y.tlast := tlast

      ready = y.tlast || ready

      y
    }

    tready := ready

    coll.toList
  }
}