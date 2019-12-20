package util

import chisel3._
import chisel3.util._

object Consts {
  val PAYLOAD_BUF = 8192
  val MAX_MTU = 1600

  val CPUBUF_SIZE = 2048
  val CPUBUF_COUNT = 8

  val CUCKOO_LINE_COUNT = 1024
  val CUCKOO_ADDR_BASE = 0x400000
}
