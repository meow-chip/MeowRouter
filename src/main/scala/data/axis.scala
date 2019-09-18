package data

import chisel3._

// AXI-S producer
class AXIS(val WIDTH: Int) extends Bundle {
  var tdata = Output(UInt(WIDTH.W))
  var tvalid = Output(Bool())
  var tlast = Output(Bool())
  var tready = Input(Bool())

  /*
  def split(n: Int) : List[AXIS] = {
    for(_ <- List(0 to n-1)) {
      val y = Wire(new AXIS(WIDTH))

      y.tdata := tdata
      y.tvalid := tvalid
      y.tlast := tlast

      tready := y.tlast || tready

      yield y
    }
  }
  */
}