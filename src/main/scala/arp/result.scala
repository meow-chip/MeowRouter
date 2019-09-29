package arp

import chisel3._
import chisel3.util._
import forward.ForwardLookup
import data._

class ARPLookup(val PORT_COUNT: Int) extends Bundle {
  val found = Bool()
  val mac = UInt(48.W)
  val at = UInt(log2Ceil(PORT_COUNT+1).W)
}

class ARPOutput(val PORT_COUNT: Int) extends Bundle {
  val arp = new ARPLookup(PORT_COUNT)
  val forward = new ForwardLookup
  val packet = new Packet(PORT_COUNT)
}