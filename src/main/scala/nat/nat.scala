package nat

import chisel3._
import chisel3.util._
import data.{Packet, Status, PacType}

class Nat(val PORT_COUNT: Int) extends Module {
  val io = IO(new Bundle {
    val input = Input(new Packet(PORT_COUNT))
    val status = Input(Status.normal.cloneType)

    val output = Output(new Packet(PORT_COUNT))
    val outputStatus = Output(Status.normal.cloneType)

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
  val natTable = VecInit(Array.fill[NatTableEntry](200){
    val e = new NatTableEntry()
    e.valid := false.B
    e
  })
  
  // variables for the state machine
  val sIDLE :: sMATCHING :: Nil = Enum(2)
  val state = RegInit(sIDLE)

  // the working packet and its status
  val packet = Reg(new Packet(PORT_COUNT))
  val status = RegInit(Status.vacant)

  // connecting output wires
  io.stall := state =/= sIDLE
  io.output := packet
  io.outputStatus := status

  switch(state) {
    is(sIDLE) {
      when(!io.pause) {
        status := io.status
        packet := io.input
        when(io.status =/= Status.vacant) {
          // TODO:
        }
      }
    }
    is (sMATCHING) {
      // TODO:
    }
  }
}
