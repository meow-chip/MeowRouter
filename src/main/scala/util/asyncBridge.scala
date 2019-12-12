package util
import chisel3._
import chisel3.core.withClock
import chisel3.util._
import chisel3.core.StringParam
import chisel3.core.IntParam
import chisel3.core.Reset
import scala.math.max

class InnerBridge(val width: Int, val depth: Int, val thresh: Int) extends BlackBox(Map(
  "FIFO_MEMORY_TYPE" -> StringParam("distributed"),
  "FIFO_WRITE_DEPTH" -> IntParam(depth),
  "WRITE_DATA_WIDTH" -> IntParam(width),
  "READ_DATA_WIDTH" -> IntParam(width),
  "READ_MODE" -> StringParam("fwft"),
  "FIFO_READ_LATENCY" -> IntParam(0),
  "PROG_FULL_THRESH" -> IntParam(thresh)
)) {
  val io = IO(new Bundle {
    val rst = Input(Reset())

    val wr_clk = Input(Clock())
    val wr_en = Input(Bool())
    val din = Input(UInt(width.W))
    val full = Output(Bool())

    val rd_clk = Input(Clock())
    val rd_en = Input(Bool())
    val dout = Output(UInt(width.W))
    val empty = Output(Bool())

    val prog_full = Output(Bool())
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

class AsyncWriter[+Type <: Data](t: Type) extends Bundle {
  val clk = Input(Clock())
  val en = Input(Bool())
  val data = Input(t)
  val full = Output(Bool())

  val progfull = Output(Bool())

  override def cloneType: this.type = new AsyncWriter(t).asInstanceOf[this.type]
}

class AsyncBridge[+Type <: Data](t: Type, depth: Int = 16, spaceThresh: Int = -1) extends Module {
  val io = IO(new Bundle {
    // Implict reset
    val write = new AsyncWriter(t.cloneType)
    val read = new AsyncReader(t)
  })

  def xpm_fifo_async_impl = {
    val thresh = if(spaceThresh < 0) { 7 } else { depth - spaceThresh + 1 }

    val width = t.getWidth
    val inner = Module(new InnerBridge(width, depth, thresh))
  
    inner.io.rst := this.reset
  
    inner.io.wr_clk := io.write.clk
    inner.io.wr_en := io.write.en
    inner.io.din := io.write.data.asUInt
    io.write.full := inner.io.full
    io.write.progfull := inner.io.prog_full
  
    inner.io.rd_clk := io.read.clk
    inner.io.rd_en := io.read.en
    io.read.data := inner.io.dout.asTypeOf(t)
    io.read.empty := inner.io.empty
  }

  def fake_register_impl = {
    // We ignore read.clk and write.clk, and use the default clock to make our life easier.

    var buf = Mem(depth, t)
    
    var start = RegInit(0.U(log2Ceil(depth ).W))
    val end = RegInit(0.U(log2Ceil(depth).W))

    // the maximum size is actually depth - 1 since we have to distinguish between the status of empty and full
    val full = start === end + 1.U
    val empty = start === end
    val size = end - start

    buf.write(end, io.write.data)
    io.write.full := full
    io.write.progfull := (size + max(0, spaceThresh).U) > (depth - 1).U || full // TODO: we haven't considered the overflow yet
    when(io.write.en && !full) {
      end := end + 1.U
    }

    io.read.data := buf.read(start)
    io.read.empty := empty
    when(io.read.en && !empty) {
      start := start + 1.U
    }
  }

  if (Configs.isTesting) {
    fake_register_impl
  } else {
    xpm_fifo_async_impl
  }
}
