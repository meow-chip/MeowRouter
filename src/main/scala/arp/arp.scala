package arp
import chisel3._
import chisel3.util._
import forward.ForwardOutput
import data._
import _root_.util.Consts

class ARPTable(PORT_COUNT: Int, SIZE: Int) extends Module {
  class ARPEntry extends Bundle {
    val ip = UInt(32.W)

    val valid = Bool()

    val mac = UInt(48.W)
    val at = UInt(log2Ceil(PORT_COUNT+1).W)

    def |(that: ARPEntry) : ARPEntry = (this.asUInt() | that.asUInt()).asTypeOf(this)
  }

  object ARPEntry {
    def apply(): ARPEntry = 0.U.asTypeOf(new ARPEntry)
  }

  val io = IO(new Bundle {
    val input = Input(new ForwardOutput(PORT_COUNT))
    val status = Input(Status())

    val stall = Output(Bool())
    val pause = Input(Bool())

    val output = Output(new ARPOutput(PORT_COUNT))
    val outputStatus = Output(Status())
  })

  val MACS = VecInit(Consts.LOCAL_MACS)
  val IPS = VecInit(Consts.LOCAL_IPS)

  val storeInit = for(i <- (0 until SIZE)) yield ARPEntry()

  val store = RegInit(VecInit(storeInit))
  val ptr = RegInit(0.U(log2Ceil(SIZE).W))

  val (found, entry) = store.foldLeft((false.B, 0.U.asTypeOf(new ARPEntry)))((acc, cur) => {
    val found = cur.valid && cur.ip === io.input.lookup.nextHop

    (found || acc._1, Mux(found, cur, 0.U.asTypeOf(cur)) | acc._2)
  })

  io.stall := false.B // ARPTable never stalls

  val pipe = Reg(new ARPOutput(PORT_COUNT))
  val pipeStatus = RegInit(Status.vacant)

  when(!io.pause) {
    pipeStatus := io.status
    pipe.packet := io.input.packet
    pipe.forward := io.input.lookup
    pipe.arp := DontCare

    when(io.status === Status.normal) {
      pipe.arp.found := found
      pipe.arp.at := entry.at
      pipe.arp.mac := entry.mac
      pipe.packet.eth.vlan := entry.at
      pipe.packet.eth.dest := entry.mac
      pipe.packet.eth.sender := MACS(entry.at)
    }
  }

  io.outputStatus := pipeStatus
  io.output := pipe
}
