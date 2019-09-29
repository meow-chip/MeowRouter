package util

import chisel3._
import chisel3.util._

object Consts {
  val LOCAL_MACS = Wire(Vec(5, UInt(48.W)))

  LOCAL_MACS(0) := Cat(
    0x000000.U(8.W),
    0x000000.U(8.W)
  )

  LOCAL_MACS(1) := Cat(
    0x000000.U(8.W),
    0x000001.U(8.W)
  )

  LOCAL_MACS(2) := Cat(
    0x000000.U(8.W),
    0x000002.U(8.W)
  )

  LOCAL_MACS(3) := Cat(
    0x000000.U(8.W),
    0x000003.U(8.W)
  )

  LOCAL_MACS(4) := Cat(
    0x000000.U(8.W),
    0x000004.U(8.W)
  )

  val LOCAL_IPS = Wire(Vec(5, UInt(32.W)))
  LOCAL_IPS := VecInit(Seq(
    0x0A010001.U(32.W),
    0x0A000101.U(32.W),
    0x0A000201.U(32.W),
    0x0A000301.U(32.W),
    0x0A000401.U(32.W)
  ))
}