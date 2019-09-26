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
}