package encoder

import chisel3._
import chisel3.util._
import data._
import _root_.util.AsyncWriter
import _root_.util.AsyncReader
import arp.ARPOutput
import _root_.util.Consts
import chisel3.experimental.ChiselEnum
import forward.ForwardLookup
import adapter.AdapterReq

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

    val req = Output(AdapterReq())

    val stall = Input(Bool())
  })

  val fromAdapter = IO(new Bundle {
    val writer = new AsyncWriter(new EncoderUnit)
  })

  val writing = RegInit(false.B)
  val cnt = RegInit(0.U)

  object State extends ChiselEnum {
    val idle, eth, ip, ipPipe, ipDrop, local, localIp, localPipe, localSend = Value
  }

  val state = RegInit(State.idle)

  val sending = Reg(new ARPOutput(PORT_COUNT))
  val ipView = sending.packet.ip.asUInt.asTypeOf(Vec(IP.HeaderLength/8, UInt(8.W)))
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

  val localReq = Reg(AdapterReq())
  toAdapter.req := localReq

  fromAdapter.writer.full := true.B
  fromAdapter.writer.progfull := true.B

  switch(state) {
    is(State.idle) {
      when(fromAdapter.writer.en) {
        state := State.localSend
      }.elsewhen(
        !io.pause
        && io.status === Status.normal
        && io.input.arp.found
        && io.input.forward.status === ForwardLookup.forward
      ) {
        sending := io.input
        state := State.eth
        cnt := 17.U
      }.elsewhen(!io.pause && (io.status =/= Status.dropped && io.status =/= Status.vacant)) {
        sending := io.input
        state := State.local
        cnt := 17.U

        when(io.status === Status.toLocal) {
          localReq := AdapterReq.incoming
        }.elsewhen(io.input.forward.status === ForwardLookup.notFound) {
          localReq := AdapterReq.forwardMiss
        }.elsewhen(!io.input.arp.found) {
          localReq := AdapterReq.arpMiss
        }.otherwise {
          localReq := AdapterReq.unknown
        }
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
      toAdapter.input := headerView(cnt)
      toAdapter.valid := true.B
      toAdapter.last := false.B

      when(!toAdapter.stall) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        }.elsewhen(sending.packet.eth.pactype === PacType.ipv4) {
          state := State.localIp
          cnt := (IP.HeaderLength/8-1).U
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
          state := State.ipPipe
        }
      }
    }

    is(State.localIp) {
      toAdapter.input := ipView(cnt)
      toAdapter.valid := true.B
      toAdapter.last := false.B

      when(!toAdapter.stall) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .otherwise {
          state := State.localPipe
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

    // TODO: ipDrop + localSend

    is(State.localSend) {
      io.writer <> fromAdapter.writer
      io.writer.clk := this.clock // Avoid clock mux
      when(io.writer.en && io.writer.data.last && !io.writer.full) {
        state := State.idle
      }
    }
  }

  io.stall := state =/= State.idle
}
