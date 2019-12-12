package encoder

import chisel3._
import chisel3.util._
import data._
import _root_.util.AsyncWriter
import _root_.util.AsyncReader
import arp.ARPOutput
import _root_.util.Consts

class EncoderUnit extends Bundle {
  val data = UInt(8.W)
  val last = Bool()
}

class Encoder(PORT_COUNT: Int) extends Module {
  val io = IO(new Bundle{
    val input = Input(new ARPOutput(PORT_COUNT))
    val status = Input(UInt())

    val stall = Output(Bool())
    val pause = Input(Bool())

    val writer = Flipped(new AsyncWriter(new EncoderUnit))
    val ipReader = Flipped(new AsyncReader(new EncoderUnit))
  })

  val MACS = VecInit(Consts.LOCAL_MACS)
  val IPS = VecInit(Consts.LOCAL_IPS)

  val writing = RegInit(false.B)
  val cnt = RegInit(0.U)

  val sIDLE :: sETH :: sIP :: sICMP :: sIPPIPE :: sIPDROP :: Nil = Enum(8)
  val state = RegInit(sIDLE)

  val sending = Reg(new ARPOutput(PORT_COUNT))
  val ipView = sending.packet.ip.asUInt.asTypeOf(Vec(IP.HeaderLength/8, UInt(8.W)))
  val icmpView = sending.packet.icmp.asUInt.asTypeOf(Vec(ICMP.HeaderLength/8, UInt(8.W)))
  val headerView = sending.packet.eth.asVec

  io.ipReader.clk := this.clock
  io.ipReader.en := false.B

  io.writer.data.last := false.B
  io.writer.data.data := 0.asUInt.asTypeOf(io.writer.data.data)
  io.writer.en := false.B
  io.writer.clk := this.clock

  switch(state) {
    is(sIDLE) {
      when(!io.pause && io.status === Status.normal) {
        sending := io.input
        state := sETH
        cnt := 17.U
      }
    }

    is(sETH) {
      // Sending ETH packet
      io.writer.data.data := headerView(cnt)
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .elsewhen(sending.packet.eth.pactype === PacType.ipv4) {
          // Is IP
          state := sIP
          cnt := (IP.HeaderLength/8-1).U
        } .otherwise {
          // FIXME: opaque packets, send to CPU
        }
      }
    }

    is(sIP) {
      io.writer.data.data := ipView(cnt)
      io.writer.data.last := false.B
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .otherwise {
          when (sending.packet.ip.proto === IP.ICMP_PROTO.U) {
            cnt := (ICMP.HeaderLength/8-1).U
            state := sICMP
          } .otherwise {
            state := sIPPIPE
          }
        }
      }
    }

    is(sICMP) {
      io.writer.data.data := icmpView(cnt)
      io.writer.data.last := false.B
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .otherwise {
          state := sIPPIPE
        }
      }
    }

    is(sIPPIPE) {
      io.writer.data := io.ipReader.data
      val transfer = (!io.ipReader.empty) && (!io.writer.full)
      io.writer.en := transfer
      io.ipReader.en := transfer

      when(io.ipReader.data.last && transfer) {
        state := sIDLE
      }
    }

    is(sIPDROP) {
      io.ipReader.en := true.B
      when(io.ipReader.data.last) { state := sIDLE }
    }
  }

  io.stall := state =/= sIDLE
}
