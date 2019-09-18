package data

import chisel3._;

object ARP {
  val HtypeEth = 0x0100.U(2.W)
  val PtypeIPV4 = 0x8000.U(2.W)

  val OperRequest = 0x0100.asUInt(2.W)
  val OperReply = 0x0200.asUInt(2.W)
}

// Reversed due to endian
class ARP(val HASIZE: Int, val PASIZE: Int) extends Bundle {
  var tpa = UInt(PASIZE.W)
  var tha = UInt(HASIZE.W)

  var spa = UInt(PASIZE.W)
  var sha = UInt(HASIZE.W)

  var oper = UInt(16.W)

  var plen = UInt(8.W)
  var hlen = UInt(8.W)

  var ptype = UInt(16.W)
  var htype = UInt(16.W)
}