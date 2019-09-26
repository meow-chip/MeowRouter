package util
import chisel3._
import chisel3.core.StringParam
import chisel3.core.IntParam
import chisel3.core.Reset

class InnerBridge(val width: Int, val depth: Int, val countWidth: Int = 16) extends BlackBox(Map(
  "FIFO_MEMORY_TYPE" -> StringParam("distributed"),
  "FIFO_WRITE_DEPTH" -> IntParam(depth),
  "WRITE_DATA_WIDTH" -> IntParam(width),
  "READ_DATA_WIDTH" -> IntParam(width),
  "READ_MODE" -> StringParam("fwft"),
  "FIFO_READ_LATENCY" -> IntParam(0),
  "RD_DATA_COUNT_WIDTH" -> IntParam(countWidth),
  "WR_DATA_COUNT_WIDTH" -> IntParam(countWidth)
)) {
  val io = IO(new Bundle {
    val rst = Input(Bool())

    val wr_clk = Input(Clock())
    val wr_en = Input(Bool())
    val din = Input(UInt(width.W))
    val full = Output(Bool())

    val rd_clk = Input(Clock())
    val rd_en = Input(Bool())
    val dout = Output(UInt(width.W))
    val empty = Output(Bool())

    val wr_data_count = Output(UInt(countWidth.W))
    val rd_data_count = Output(UInt(countWidth.W))
  })

  override def desiredName: String = "xpm_fifo_async"
}

class AsyncReader[+Type <: Data](t: Type) extends Bundle {
  val clk = Input(Clock())
  val en = Input(Bool())
  val data = Output(t)
  val empty = Output(Bool())

  override def cloneType: this.type = new AsyncReader(t).asInstanceOf[this.type]
}

class AsyncWriter[+Type <: Data](t: Type, countWidth: Int = 16) extends Bundle {
  val clk = Input(Clock())
  val en = Input(Bool())
  val data = Input(t)
  val full = Output(Bool())

  val space = Output(UInt(countWidth.W))

  override def cloneType: this.type = new AsyncWriter(t, countWidth).asInstanceOf[this.type]
}

class AsyncBridge[+Type <: Data](t: Type, depth: Int = 16) extends Module {
  val io = IO(new Bundle {
    // Implict reset
    val write = new AsyncWriter(t.cloneType)
    val read = new AsyncReader(t)
  })

  val width = t.getWidth
  println("WIDTH:")
  println(width)
  val inner = Module(new InnerBridge(width, depth))

  inner.io.rst := this.reset

  inner.io.wr_clk := io.write.clk
  inner.io.wr_en := io.write.en
  inner.io.din := io.write.data.asUInt
  io.write.full := inner.io.full
  io.write.space := depth.U + inner.io.rd_data_count - inner.io.wr_data_count

  inner.io.rd_clk := io.read.clk
  inner.io.rd_en := io.read.en
  io.read.data := inner.io.dout.asTypeOf(t)
  io.read.empty := inner.io.empty
}