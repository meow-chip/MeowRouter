package util
import chisel3._
import chisel3.core.StringParam
import chisel3.core.IntParam
import chisel3.core.Reset

class InnerBridge(width: Int) extends BlackBox(Map(
    "FIFO_MEMORY_TYPE" -> StringParam("distributed"),
    "FIFO_WRITE_DEPTH" -> IntParam(4),
    "WRITE_DATA_WIDTH" -> IntParam(width),
    "READ_MODE" -> StringParam("fwft"),
    "FIFO_READ_LATENCY" -> IntParam(0),
    "READ_DATA_WIDTH" -> IntParam(width)
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
    })
}

class AsyncReader(width: Int) extends Bundle {
    val clk = Input(Clock())
    val en = Input(Bool())
    val data = Output(UInt(width.W))
    val empty = Output(Bool())
}

class AsyncWriter(width: Int) extends Bundle {
    val clk = Input(Clock())
    val en = Input(Bool())
    val data = Output(UInt(width.W))
    val full = Output(Bool())
}

class AsyncBridge(width: Int) extends Module {
    val io = IO(new Bundle {
        // Implict reset
        val write = new AsyncWriter(width)
        val read = new AsyncReader(width)
    })

    val inner = Module(new InnerBridge(width))

    inner.io.rst := this.reset

    inner.io.wr_clk := io.write.clk
    inner.io.wr_en := io.write.en
    inner.io.din := io.write.data
    io.write.full := inner.io.full

    inner.io.rd_clk := io.read.clk
    inner.io.rd_en := io.read.en
    io.read.data := inner.io.dout
    io.read.empty := inner.io.empty
}