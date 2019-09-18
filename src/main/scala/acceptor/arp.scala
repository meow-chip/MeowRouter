package acceptor

import chisel3._;
import data._;

class ARPAcceptor extends Module {
  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))
    val output = Output(new ARP(48, 32))
    val start = Input(Bool())
    val finished = Output(Bool())
  })

  val buf = Reg(Vec(28, UInt(8.W)))
  val cnt = RegInit(0.U(5.W))
  val reading = RegInit(false.B)

  when(io.start) {
    cnt := 0.U
    reading := true.B
  } .otherwise {
    when(io.rx.tvalid && reading) {
      when(cnt < 28.U) {
        buf(cnt) := io.rx.tdata
        cnt := cnt +% 1.U
      }

      when(cnt === 28.U && (RegNext(cnt) =/= 28.U)) {
        reading := false.B
      }
    }
  }

  io.output := (new ARP(48, 32)).fromBits(buf.toBits)
  io.finished := !reading
  io.rx.tready := true.B
}