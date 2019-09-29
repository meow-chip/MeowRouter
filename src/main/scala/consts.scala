import chisel3._
import chisel3.util._

object Consts {
  val LOCAL_MAC = Wire(UInt(48.W))

  LOCAL_MAC := Cat(
    0xD8.U(8.W),
    0x71.U(8.W),
    0x20.U(8.W),
    0x6A.U(8.W),
    0xA4.U(8.W),
    0xF6.U(8.W)
  )
}