package adapter

import chisel3._
import _root_.data._
import chisel3.util.log2Ceil

/* Data plane -> CPU */
class Incoming(BUF_COUNT: Int) extends Module {
  val TOT_SIZE = BUF_COUNT * 4096
  val ADDR_WIDTH = log2Ceil(TOT_SIZE)

  val io = IO(new Bundle{
    val input = Flipped(new AXIS(8))
    val external = new AXI(64, 32, 4)
  })

  val cnt = RegInit(0.U(12.W))
  val target = RegInit(1.U(log2Ceil(BUF_COUNT).W)) // Ignore zero
  val buf = Vec(8, UInt(8.W))

  val sRESET :: sPOLL :: sPOLL_WAIT :: sBUF :: sWRITE_REQ :: sWRITE :: sFINALIZE_REQ :: sFINALIZE :: nil = Enum(8)
  val state = RegInit(sPOLL)

  io.input.tready := false.B

  io.external <> 0.U.asTypeOf(io.external)

  io.external.ARVALID := false.B
  io.external.AWVALID := false.B
  io.external.WVALID := false.B
  io.external.RREADY := false.B
  io.external.BREADY := false.B

  io.external.ARLEN := 0.U
  io.external.ARSIZE := AXI.Constants.Size.S4 // Only read state
  io.external.ARBURST := AXI.Constants.Burst.INCR

  io.external.AWLEN := 0.U
  io.external.AWSIZE := AXI.Constants.Size.S8
  io.external.AWBURST := AXI.Constants.Burst.INCR

  val isLast = RegInit(false.B)

  switch(state) {
    is(sRESET) {
      // TODO: impl
    }

    is(sPOLL) {
      io.external.ARADDR := ((target + 1.U) << 12) - 4.U // Read state
      io.external.ARVALID := true.B

      when(io.external.ARREADY) {
        state := sPOLL_WAIT
      }
    }

    is(sPOLL_WAIT) {
      when(io.external.RVALID) {
        when(io.external.RDATA(31, 0) === 0.U) {
          cnt := 0.U
          isLast := false.B
          state := sBUF
        }
      }
    }

    is(sBUF) {
      io.input.tready := true.B
      when(io.input.tvalid) {
        buf(cnt) := io.input.tdata

        when(io.input.tlast) {
          isLast := true.B
          state := sWRITE_REQ
        }.elsewhen(cnt(2, 0).andR()) { // Buf full
          state := sWRITE_REQ
        }.otherwise {
          cnt := cnt + 1.U
        }
      }
    }

    is(sWRITE_REQ) {
      io.external.AWADDR := (target << 12) | cnt(11, 2) // Align
      io.external.AWVALID := true.B
      when(io.external.AWREADY) {
        state := sWRITE
      }
    }

    is(sWRITE) {
      io.external.WDATA := buf.asUInt
      io.external.WLAST := true.B
      io.external.WVALID := true.B
      io.external.WSTRB := (1.S(4.W) << cnt) - 1.S

      when(io.external.WREADY) {
        cnt := cnt + 1
        when(isLast) {
          state := sFINALIZE_REQ
        }.otherwise {
          state := sBUF
        }
      }
    }

    is(sFINALIZE_REQ) {
      io.external.AWADDR := ((target + 1.U) << 12) - 8.U // Read state
      io.external.AWVALID := true.B

      when(io.external.AWREADY) {
        state := sFINALIZE
      }
    }

    is(sFINALIZE) {
      io.external.WDATA := (cnt << 32) ## 1.U(32.W)
      io.external.WVALID := true.B
      io.external.WLAST := true.B
      io.external.WSTRB := (-1).S(8.W).asUInt

      when(io.external.WREADY) {
        target := target + 1.U
        state := sPOLL
      }
    }
  }
}
