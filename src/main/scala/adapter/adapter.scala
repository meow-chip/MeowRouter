package adapter

import chisel3._
import chisel3.experimental._
import chisel3.experimental.ChiselEnum
import chisel3.util._
import _root_.util.AsyncWriter
import _root_.util.Consts
import data._
import encoder.EncoderUnit

class BufPort extends Bundle {
  val clk = Output(Clock())
  val addr = Output(UInt(32.W))
  val din = Output(UInt(8.W))
  val dout = Input(UInt(8.W))
  val we = Output(Bool())
}

object AdapterReq extends ChiselEnum {
  val incoming, arpMiss, forwardMiss, unknown = Value
}

class Adapter extends MultiIOModule {
  val toBuf = IO(new BufPort)

  val fromEnc = IO(new Bundle {
    val input = Input(UInt(8.W))
    val valid = Input(Bool())
    val last = Input(Bool())

    val req = Input(AdapterReq())

    val stall = Output(Bool())
  })

  val toEnc = IO(new Bundle {
    val writer = Flipped(new AsyncWriter(new EncoderUnit))
  })

  object State extends ChiselEnum {
    val rst, pollHead, pollZero, incoming, outgoing = Value
  }

  object Status extends ChiselEnum {
    val idle = Value(0.U)
    val incoming = Value(1.U)
    val outgoing = Value(2.U)
    val forwardMiss = Value(3.U)
    val arpMiss = Value(4.U)
  }

  toBuf.clk := this.clock
  // By default, don't write
  toBuf.we := false.B
  toBuf.din := DontCare

  fromEnc.stall := true.B
  toEnc.writer := DontCare
  toEnc.writer.en := false.B
  toEnc.writer.clk := this.clock

  val state = RegInit(State.rst)
  val nstate = Wire(State())
  nstate := state
  state := nstate

  val head = RegInit(1.U(log2Ceil(Consts.CPUBUF_COUNT).W))
  val tail = RegInit(1.U(log2Ceil(Consts.CPUBUF_COUNT).W))

  def step(sig: UInt): UInt = {
    Mux(sig === (Consts.CPUBUF_COUNT-1).U, 1.U, sig +% 1.U)
  }

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
  raddr := DontCare
  toBuf.addr := raddr

  switch(state) {
    is(State.rst) {
      nstate := State.pollZero
      raddr := statusOffset
    }

    is(State.pollZero) {
      when(toBuf.dout === Status.outgoing.asUInt()) {
        nstate := State.outgoing
        transferState := TransferState.cntLo
        raddr := lenOffset
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
        transferState := TransferState.cntLo
        sendingSlot := head
        raddr := head ## lenOffset
        cnt := 0.U
      }.elsewhen(fromEnc.valid && !dropping) {
        when(step(tail) =/= head) {
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
          head := step(head)
        }
      }
    }

    is(State.incoming) {
      switch(transferState) {
        is(TransferState.payload) {
          raddr := tail ## cnt
          toBuf.din := fromEnc.input
          fromEnc.stall := false.B

          when(fromEnc.valid) {
            toBuf.we := true.B
            cnt := cnt +% 1.U

            when(fromEnc.last) {
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

          when(fromEnc.req === AdapterReq.arpMiss) {
            toBuf.din := Status.arpMiss.asUInt()
          }.elsewhen(fromEnc.req === AdapterReq.forwardMiss) {
            toBuf.din := Status.forwardMiss.asUInt()
          }
          transferState := TransferState.fin
        }

        is(TransferState.fin) {
          tail := step(tail)

          nstate := State.pollZero
          raddr := statusOffset
        }
      }
    }

    is(State.outgoing) {
      switch(transferState) {
        is(TransferState.cntLo) {
          raddr := sendingSlot ## lenOffset + 1.U
          transferState := TransferState.cntHi
          totCnt := toBuf.dout
        }

        is(TransferState.cntHi) {
          raddr := sendingSlot ## cnt
          transferState := TransferState.payload
          totCnt := toBuf.dout ## totCnt(7, 0)
        }

        is(TransferState.payload) {
          val isLast = (cnt +% 1.U) === totCnt
          toEnc.writer.data.data := toBuf.dout
          toEnc.writer.data.last := isLast
          toEnc.writer.en := true.B

          raddr := sendingSlot ## cnt

          when(toEnc.writer.en && !toEnc.writer.full) {
            cnt := cnt +% 1.U
            raddr := sendingSlot ## (cnt +% 1.U)

            when(isLast) {
              transferState := TransferState.status
            }
          }
        }

        is(TransferState.status) {
          raddr := sendingSlot ## statusOffset
          toBuf.we := true.B
          toBuf.din := Status.idle.asUInt()
          transferState := TransferState.fin
        }

        is(TransferState.fin) {
          head := step(head)

          nstate := State.pollZero
          raddr := statusOffset
        }
      }
    }
  }

  when(dropping) {
    fromEnc.stall := false.B
    when(fromEnc.valid && fromEnc.last) {
      dropping := false.B
    }
  }
}
