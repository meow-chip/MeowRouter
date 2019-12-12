package encoder

import chisel3._
import chisel3.util._
import data._
import _root_.util.AsyncWriter
import _root_.util.AsyncReader
import arp.ARPOutput
import _root_.util.Consts
import chisel3.experimental.ChiselEnum

class EncoderUnit extends Bundle {
  val data = UInt(8.W)
  val last = Bool()
}

class Encoder(PORT_COUNT: Int) extends MultiIOModule {
  val io = IO(new Bundle{
    val input = Input(new ARPOutput(PORT_COUNT))
    val status = Input(Status())

    val stall = Output(Bool())
    val pause = Input(Bool())

    val writer = Flipped(new AsyncWriter(new EncoderUnit))
    val payloadReader = Flipped(new AsyncReader(new EncoderUnit))
  })

  val toAdapter = IO(new Bundle {
    val input = Output(UInt(8.W))
    val valid = Output(Bool())
    val last = Output(Bool())

    val stall = Input(Bool())
  })

  // TODO: impl: fromAdapter

  val MACS = VecInit(Consts.LOCAL_MACS)
  val IPS = VecInit(Consts.LOCAL_IPS)

  val writing = RegInit(false.B)
  val cnt = RegInit(0.U)

  object State extends ChiselEnum {
    val idle, eth, ip, icmp, ipPipe, ipDrop, local, localPipe = Value
  }

  val state = RegInit(State.idle)

  val sending = Reg(new ARPOutput(PORT_COUNT))
  val ipView = sending.packet.ip.asUInt.asTypeOf(Vec(IP.HeaderLength/8, UInt(8.W)))
  val icmpView = sending.packet.icmp.asUInt.asTypeOf(Vec(ICMP.HeaderLength/8, UInt(8.W)))
  val headerView = sending.packet.eth.asVec

  io.payloadReader.clk := this.clock
  io.payloadReader.en := false.B

  io.writer.data.last := false.B
  io.writer.data.data := 0.asUInt.asTypeOf(io.writer.data.data)
  io.writer.en := false.B
  io.writer.clk := this.clock

  toAdapter.input := DontCare
  toAdapter.last := DontCare
  toAdapter.valid := false.B

  switch(state) {
    is(State.idle) {
      when(!io.pause && io.status === Status.normal) {
        sending := io.input
        state := State.idle
        cnt := 17.U
      }.elsewhen(!io.pause && io.status === Status.toLocal) {
        sending := io.input
        state := State.local
        cnt := 17.U
      }
    }

    is(State.eth) {
      // Sending ETH packet
      io.writer.data.data := headerView(cnt)
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        }.otherwise {
          // For all packets other than IP, local is asserted
          assert(sending.packet.eth.pactype === PacType.ipv4)
          // Is IP
          state := State.ip
          cnt := (IP.HeaderLength/8-1).U
        }
      }
    }

    is(State.local) {
      io.writer.data.data := headerView(cnt)
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        }.otherwise {
          state := State.localPipe
        }
      }
    }

    is(State.ip) {
      io.writer.data.data := ipView(cnt)
      io.writer.data.last := false.B
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .otherwise {
          when (sending.packet.ip.proto === IP.ICMP_PROTO.U) {
            cnt := (ICMP.HeaderLength/8-1).U
            state := State.icmp
          } .otherwise {
            state := State.ipPipe
          }
        }
      }
    }

    is(State.icmp) {
      io.writer.data.data := icmpView(cnt)
      io.writer.data.last := false.B
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .otherwise {
          state := State.ipPipe
        }
      }
    }

    is(State.ipPipe) {
      io.writer.data := io.payloadReader.data
      val transfer = (!io.payloadReader.empty) && (!io.writer.full)
      io.writer.en := transfer
      io.payloadReader.en := transfer

      when(io.payloadReader.data.last && transfer) {
        state := State.idle
      }
    }

    is(State.localPipe) {
      toAdapter.input := io.payloadReader.data.data
      toAdapter.valid := !io.payloadReader.empty
      toAdapter.last := io.payloadReader.data.last
      io.payloadReader.en := !io.payloadReader.empty && !toAdapter.stall

      when(io.payloadReader.data.last && io.payloadReader.en) {
        state := State.idle
      }
    }

    is(State.ipDrop) {
      io.payloadReader.en := true.B
      when(io.payloadReader.data.last) { state := State.idle }
    }
  }

  io.stall := state =/= State.idle
}
