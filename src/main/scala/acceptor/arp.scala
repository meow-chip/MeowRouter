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
    reading := true.B
  }

  when(io.rx.tvalid && (io.start || reading)) {
    when(cnt < 28.U) {
      buf(27.U - cnt) := io.rx.tdata
      cnt := cnt +% 1.U
    }

    when(cnt === 28.U && (RegNext(cnt) =/= 28.U)) {
      reading := false.B
      cnt := 0.U
    }
  }

  io.output := (new ARP(48, 32)).fromBits(buf.toBits)
  io.finished := !reading
  io.rx.tready := true.B
}