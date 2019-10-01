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

  val sIDLE :: sETH :: sARP :: sIP :: sIPPIPE :: sARPMISS :: sIPDROP :: Nil = Enum(7)
  val state = RegInit(sIDLE)

  val sending = Reg(new ARPOutput(PORT_COUNT))
  val arpView = sending.packet.arp.asUInt.asTypeOf(Vec(28, UInt(8.W)))
  val ipView = sending.packet.ip.asUInt.asTypeOf(Vec(20, UInt(8.W)))
  val headerView = sending.packet.eth.asVec

  io.ipReader.clk := this.clock
  io.ipReader.en := false.B

  io.writer.data.last := false.B
  io.writer.data.data := 0.asUInt.asTypeOf(io.writer.data.data)
  io.writer.en := false.B
  io.writer.clk := this.clock

  // For ARPMISS
  val arpEth = Wire(new Eth(PORT_COUNT))
  arpEth.pactype := PacType.arp
  arpEth.dest := (-1).S(48.W).asUInt // Broadcast

  val arpReq = Wire(new ARP(48, 32))
  arpReq.htype := ARP.HtypeEth
  arpReq.ptype := ARP.PtypeIPV4
  arpReq.hlen := 6.U
  arpReq.plen := 4.U
  arpReq.oper := ARP.OperRequest
  arpReq.tpa := sending.forward.nextHop
  arpReq.tha := 0.U

  val port = RegInit(1.U(log2Ceil(PORT_COUNT+1).W))
  arpEth.vlan := port
  arpEth.sender := MACS(port)
  arpReq.sha := MACS(port)
  arpReq.spa := IPS(port)

  val arpMissPayload = Cat(arpEth.toBits(), arpReq.asUInt()).asTypeOf(Vec(18 + 28, UInt(8.W)))

  switch(state) {
    is(sIDLE) {
      when(!io.pause && io.status === Status.normal) {
        sending := io.input
        when(io.input.packet.eth.pactype =/= PacType.arp && !io.input.arp.found) {
          state := sARPMISS
          port := 1.U
          cnt := (18 + 28 -1).U
        }.elsewhen(io.input.packet.eth.pactype =/= PacType.arp
          || io.input.packet.eth.sender === MACS(io.input.packet.eth.vlan)
        ) {
          state := sETH
          cnt := 17.U
        }
      }
    }

    is(sARPMISS) {
      io.writer.data.data := arpMissPayload(cnt)
      io.writer.data.last := cnt === 0.U
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .elsewhen(port < PORT_COUNT.U) {
          cnt := (18 + 28 -1).U
          port := port + 1.U
        } .otherwise {
          state := sIPDROP
        }
      }
    }

    is(sETH) {
      // Sending ETH packet
      io.writer.data.data := headerView(cnt)
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .elsewhen(sending.packet.eth.pactype === PacType.arp) {
          // Is ARP
          state := sARP
          cnt := 27.U
        } .otherwise {
          // Is IP
          state := sIP
          cnt := 19.U
        }
      }
    }

    is(sARP) {
      io.writer.data.data := arpView(cnt)
      io.writer.data.last := cnt === 0.U
      io.writer.en := true.B

      when(!io.writer.full) {
        when(cnt > 0.U) {
          cnt := cnt - 1.U
        } .otherwise {
          state := sIDLE
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