package nat

import chisel3._
import chisel3.util._
import data.{Packet, Status, PacType, IP}

class Nat(val PORT_COUNT: Int) extends Module {
  val io = IO(new Bundle {
    val input = Input(new Packet(PORT_COUNT))
    val status = Input(Status())

    val output = Output(new Packet(PORT_COUNT))
    val outputStatus = Output(Status())

    val pause = Input(Bool())
    val stall = Output(Bool())
  })

  // See more: https://tools.ietf.org/html/rfc2663
  // (priIP, priPort) is the address of the private realm
  // (extIP, extPort) is the address of the public realm
  // For a packet from (priIP, priPort) to (extIP, extPort), Nat should replace (priIP, priPort) with (bindIP, bindPort)
  // For a packet from (extIP, extPort) to (bindIP, bindPort), Nat should replace (bindIP, bindPort) with (priIP, priPort)
  // "Port" depends on the protocol. It can be query ID for icmp, or port for UDP and TCP.
  class NatTableEntry extends Bundle {
    val priIP = UInt(32.W)
    val priPort = UInt(16.W)
    val extIP = UInt(32.W)
    val extPort = UInt(16.W)
    val bindIP = UInt(32.W)
    val bindPort = UInt(16.W)
    val pactype = PacType()
    val valid = Bool()
  }

  // create a nat table
  val tableSize = 200
  // TODO: initialize natTable
  val natTable = Reg(Vec(tableSize, new NatTableEntry()));
  
  // variables for the state machine
  val sIDLE :: sMATCHING :: Nil = Enum(2)
  val state = RegInit(sIDLE)
  // used for linear search
  val searchingP = RegInit(0.U(log2Ceil(tableSize+1).W))

  // the working packet and its status
  val packet = Reg(new Packet(PORT_COUNT))
  val status = RegInit(Status.vacant)
  // the packet details
  val srcIP = packet.ip.src
  val srcPort = packet.icmp.id
  val dstIP = packet.ip.dest
  val dstPort = packet.icmp.id

  // connecting output wires
  io.stall := state =/= sIDLE
  io.output := packet
  io.outputStatus := status

  switch(state) {
    is(sIDLE) {
      when(!io.pause) {
        status := io.status
        packet := io.input
        when(io.status === Status.normal) {
          when (io.input.eth.pactype === PacType.ipv4 && io.input.ip.proto === IP.ICMP_PROTO.U) {
            state := sMATCHING
            // It's like a bubble. Although the controller will pause the succeeding statge anyway.
            // It may be useful after we rewrite the controller.
            status := Status.vacant
            searchingP := 0.U
          }
        }
      }
    }
    is (sMATCHING) {
      // TODO: we haven't initialize the nat table yet. In case the valid is true and cause a bug, we && false at there.
      val outbound = false.B &&
                    srcIP === natTable(searchingP).priIP && srcPort === natTable(searchingP).priPort &&
                    dstIP === natTable(searchingP).extIP && dstPort === natTable(searchingP).extPort &&
                    natTable(searchingP).valid
      val inbound = false.B &&
                    srcIP === natTable(searchingP).extIP && srcPort === natTable(searchingP).extPort &&
                    dstIP === natTable(searchingP).bindIP && dstPort === natTable(searchingP).bindPort &&
                    natTable(searchingP).valid
      when (outbound) {
        srcIP := natTable(searchingP).bindIP
        srcPort := natTable(searchingP).bindPort
      } .elsewhen (inbound) {
        dstIP := natTable(searchingP).priIP
        dstPort := natTable(searchingP).priPort
      } .otherwise {
        when (searchingP + 1.U < tableSize.U) {
          searchingP := searchingP + 1.U
        } .otherwise {
          state := sIDLE
          status := Status.normal
        }
      }

      when (outbound || inbound) {
        state := sIDLE
        status := Status.normal
      }
    }
  }
}
