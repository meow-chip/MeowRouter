import data.MACAddr

import chisel3.core.VecInit
import chisel3._

object Consts {
  val LOCAL_MAC = Wire(new MACAddr)

  LOCAL_MAC.addr := VecInit(Seq(
    0xD8.U(8.W),
    0x71.U(8.W),
    0x20.U(8.W),
    0x6A.U(8.W),
    0xA4.U(8.W),
    0xF6.U(8.W)
  ))
}