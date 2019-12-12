import chisel3._
import chisel3.experimental._
import chisel3.experimental.ChiselEnum
import chisel3.util._
import _root_.util.AsyncWriter
import _root_.util.Consts
import data._
import encoder.EncoderUnit

class Adapter extends MultiIOModule {
  val toBuf = IO(new Bundle {
    val clk = Output(Clock())
    val addr = Output(UInt(32.W))
    val din = Output(UInt(8.W))
    val dout = Input(UInt(8.W))
    val we = Output(Bool())
  })

  val fromExec = IO(new Bundle {
    val input = Input(UInt(8.W))
    val valid = Input(Bool())
    val last = Input(Bool())

    val stall = Output(Bool())
  })

  val toExec = IO(new Bundle {
    val writer = Flipped(new AsyncWriter(new EncoderUnit))
  })

  object State extends ChiselEnum {
    val rst, pollHead, pollZero, incoming, outgoing = Value
  }

  object Status extends ChiselEnum {
    val idle = Value(0.U)
    val incoming = Value(1.U)
    val outgoing = Value(2.U)
  }

  toBuf.clk := this.clock
  // By default, don't write
  toBuf.we := false.B
  toBuf.din := DontCare

  // By default
  fromExec.stall := true.B

  val state = RegInit(State.rst)
  val nstate = Wire(State())

  val head = RegInit(1.U(log2Ceil(Consts.CPUBUF_COUNT).W))
  val tail = RegInit(1.U(log2Ceil(Consts.CPUBUF_COUNT).W))

  val bufAddrShift = log2Ceil(Consts.CPUBUF_SIZE)
  val statusOffset = (Consts.CPUBUF_SIZE - 1).U(bufAddrShift.W)
  val lenOffset = (Consts.CPUBUF_SIZE - 4).U(bufAddrShift.W)

  val sendingSlot = Reg(UInt(log2Ceil(Consts.CPUBUF_COUNT).W))
  val dropping = RegInit(false.B)

  val cnt = RegInit(0.U(bufAddrShift.W))
  val totCnt = RegInit(0.U(bufAddrShift.W))

  object TransferState extends ChiselEnum {
    val payload, status, cntLo, cntHi, fin = Value
  }

  val transferState = RegInit(TransferState.payload)

  val raddr = Wire(UInt(32.W))
  toBuf.addr := raddr
  // Compute raddr

  switch(state) {
    is(State.rst) {
      nstate := State.pollZero
      raddr := statusOffset
    }

    is(State.pollZero) {
      when(toBuf.dout === Status.outgoing.asUInt()) {
        nstate := State.outgoing
        raddr := 0.U
        cnt := 0.U
        sendingSlot := 0.U
      }.otherwise {
        nstate := State.pollHead
        raddr := head ## statusOffset
      }
    }

    is(State.pollHead) {
      when(toBuf.dout === Status.outgoing.asUInt()) {
        nstate := State.outgoing
        sendingSlot := head
        raddr := head << bufAddrShift
        cnt := 0.U
      }.elsewhen(fromExec.valid && !dropping) {
        when(tail +% 1.U =/= head) {
          nstate := State.incoming
          transferState := TransferState.payload
          cnt := 0.U
          // Recv slot is always tail
        }.otherwise {
          dropping := true.B
          nstate := State.pollZero
          raddr := statusOffset
        }
      }.otherwise {
        nstate := State.pollZero
        raddr := statusOffset
      }
      
      when(toBuf.dout === Status.idle.asUInt()) {
        when(head =/= tail) { // Ring buf not empty
          head := head +% 1.U
        }
      }
    }

    is(State.incoming) {
      switch(transferState) {
        is(TransferState.payload) {
          raddr := tail ## cnt
          toBuf.din := fromExec.input

          when(fromExec.valid) {
            toBuf.we := true.B
            cnt := cnt +% 1.U

            when(fromExec.last) {
              transferState := TransferState.cntLo
            }
          }
        }

        is(TransferState.cntLo) {
          raddr := tail ## lenOffset
          toBuf.we := true.B
          toBuf.din := cnt(7, 0)
          transferState := TransferState.cntHi
        }

        is(TransferState.cntHi) {
          raddr := tail ## lenOffset + 1.U
          toBuf.we := true.B
          toBuf.din := cnt >> 8
          transferState := TransferState.status
        }

        is(TransferState.status) {
          raddr := tail ## statusOffset
          toBuf.we := true.B
          toBuf.din := Status.incoming.asUInt()
          transferState := TransferState.fin
        }

        is(TransferState.fin) {
          tail := tail +% 1.U

          nstate := State.pollZero
          raddr := statusOffset
        }
      }
    }

    // TODO: impl outgoing
  }

  when(dropping) {
    fromExec.stall := false.B
    when(fromExec.valid && fromExec.last) {
      dropping := false.B
    }
  }
}
