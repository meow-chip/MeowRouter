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
    val status = Input(Status.normal.cloneType)

    val stall = Output(Bool())
    val pause = Input(Bool())

    val output = Output(new ARPOutput(PORT_COUNT))
    val outputStatus = Output(Status.normal.cloneType)
  })

  val MACS = VecInit(Consts.LOCAL_MACS)
  val IPS = VecInit(Consts.LOCAL_IPS)

  val storeInit = for(i <- (0 until SIZE)) yield ARPEntry()

  val store = RegInit(VecInit(storeInit))
  val ptr = RegInit(0.U(log2Ceil(SIZE).W))

  val (found, entry) = store.foldLeft((false.B, 0.U.asTypeOf(new ARPEntry)))((acc, cur) => {
    val found = cur.ip === io.input.lookup.nextHop

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
      when(io.input.packet.eth.pactype === PacType.arp && io.input.packet.arp.oper === ARP.OperReply) {
        store(ptr).valid := true.B
        store(ptr).ip := io.input.packet.arp.spa
        store(ptr).mac := io.input.packet.arp.sha
        store(ptr).at := io.input.packet.eth.vlan
        ptr := ptr + 1.U
        for(i <- (0 until SIZE)) {
          when(store(i).ip === io.input.packet.arp.spa && i.U =/= ptr) {
            store(i).valid := false.B
          }
        }
      }.elsewhen(io.input.packet.eth.pactype === PacType.arp
        && io.input.packet.arp.oper === ARP.OperRequest
        && io.input.packet.arp.tpa === IPS(io.input.packet.eth.vlan)
      ) {
        pipe.packet.eth.dest := io.input.packet.eth.sender
        pipe.packet.eth.sender := MACS(io.input.packet.eth.vlan)
        pipe.packet.arp.oper := ARP.OperReply
        pipe.packet.arp.tha := io.input.packet.arp.sha
        pipe.packet.arp.tpa := io.input.packet.arp.spa
        pipe.packet.arp.sha := MACS(io.input.packet.eth.vlan)
        pipe.packet.arp.spa := IPS(io.input.packet.eth.vlan)
      }.otherwise {
        pipe.arp.found := found
        pipe.arp.at := entry.at
        pipe.arp.mac := entry.mac
        pipe.packet.eth.vlan := entry.at
        pipe.packet.eth.dest := entry.mac
        pipe.packet.eth.sender := MACS(entry.at)
      }
    }
  }

  io.outputStatus := pipeStatus
  io.output := pipe
}