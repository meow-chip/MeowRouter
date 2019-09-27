package data
import chisel3._

object IP {
  val HeaderLength = 160
}

/**
 * TODO: add TCP Source/Target port
 */
class IP extends Bundle {
  val version = UInt(4.W)
  val ihl = UInt(4.W)
  val dscp = UInt(6.W)
  val ecn = UInt(2.W)

  val len = UInt(16.W)

  val id = UInt(16.W)
  val flags = UInt(3.W)
  val foff = UInt(13.W)

  val ttl = UInt(8.W)
  val proto = UInt(8.W)

  val chksum = UInt(16.W)
  val src = UInt(32.W)
  val dest = UInt(32.W)
}