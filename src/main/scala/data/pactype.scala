package data

import chisel3._;
import chisel3.util.Enum;

object PacType {
  val unknown :: ipv4 :: arp :: Nil = Enum(3)

  def apply() = PacType.unknown.cloneType

  def parse(v: IndexedSeq[UInt]) : UInt = {
    var result = Wire(PacType.unknown.cloneType)

    when((v(1) === 8.U) && (v(0) === 0.U)) {
      result := PacType.ipv4
    }.elsewhen((v(1) === 8.U) && (v(0) === 6.U)) {
      result := PacType.arp
    }.otherwise {
      result := PacType.unknown
    }

    result
  }

  def serialize(v: UInt): UInt = {
    val result = Wire(UInt())
    when(v === PacType.ipv4) {
      result := 0x0800.U(16.W)
    } .elsewhen(v === PacType.arp) {
      result := 0x0806.U(16.W)
    } .otherwise {
      result := 0.U(16.W)
    }

    result
  }
}