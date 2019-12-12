package util

import chisel3._
import chisel3.util._

object Consts {
  val PAYLOAD_BUF = 8192
  val MAX_MTU = 1600
  val LOCAL_MACS = Seq(
    0x000000000000L.U(48.W),
    0x000000000001L.U(48.W),
    0x000000000002L.U(48.W),
    0x000000000003L.U(48.W),
    0x000000000004L.U(48.W)
  )

  val LOCAL_IPS = Seq(
    0x0A010001.U(32.W),
    0x0A000101.U(32.W),
    0x0A000201.U(32.W),
    0x0A000301.U(32.W),
    0x0A000401.U(32.W)
  )
  
  val CPUBUF_SIZE = 2048
  val CPUBUF_COUNT = 8
}
