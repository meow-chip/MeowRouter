package data

import chisel3._;

object ARP {
  val HtypeEth = 0x0001.U(16.W)
  val PtypeIPV4 = 0x0800.U(16.W)

  val OperRequest = 0x0001.asUInt(16.W)
  val OperReply = 0x0002.asUInt(16.W)
}

// Reversed due to endian
class ARP(val HASIZE: Int, val PASIZE: Int) extends Bundle {
  var htype = UInt(16.W)
  var ptype = UInt(16.W)

  var hlen = UInt(8.W)
  var plen = UInt(8.W)

  var oper = UInt(16.W)

  var sha = UInt(HASIZE.W)
  var spa = UInt(PASIZE.W)

  var tha = UInt(HASIZE.W)
  var tpa = UInt(PASIZE.W)
}
