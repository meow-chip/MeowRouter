package top

import chisel3._

class Top extends Module {
  val io = IO(new Bundle {
    // Clk and rst are implicit

    val rx_clk = Input(Clock())
    val tx_clk = Input(Clock())

    val rx_tdata = Input(UInt(8.W))
    val rx_tvalid = Input(Bool())
    val rx_tlast = Input(Bool())
    // Ignore user

    val tx_tdata = Output(UInt(8.W))
    val tx_tvalid = Output(Bool())
    val tx_tlast = Output(Bool())
    val tx_tready = Input(Bool())
    val tx_tuser = Output(Bool())

    val buf_clk = Output(Clock())
    val buf_addr = Output(UInt(32.W))
    val buf_din = Output(UInt(8.W))
    val buf_dout = Input(UInt(8.W))
    val buf_we = Output(UInt(1.W))

    val cmd = Input(UInt(64.W))
  })

  val router = Module(new Router(4))

  router.io.rx_clk := io.rx_clk
  router.io.tx_clk := io.tx_clk

  router.io.rx.tdata := io.rx_tdata
  router.io.rx.tvalid := io.rx_tvalid
  router.io.rx.tlast := io.rx_tlast
  router.io.rx.tready := DontCare

  io.tx_tdata := router.io.tx.tdata;
  io.tx_tlast := router.io.tx.tlast;
  io.tx_tvalid := router.io.tx.tvalid;
  router.io.tx.tready := io.tx_tready

  io.buf_addr := router.io.buf.addr
  io.buf_clk := router.io.buf.clk
  io.buf_din := router.io.buf.din
  router.io.buf.dout := io.buf_dout
  io.buf_we := router.io.buf.we.asUInt

  io.tx_tuser := false.B

  router.io.cmd := io.cmd.asTypeOf(router.io.cmd)
}
