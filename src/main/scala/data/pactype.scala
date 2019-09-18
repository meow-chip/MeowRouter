package data

import chisel3._;
import chisel3.util.Enum;

object PacType {
  val unknown :: ipv4 :: arp :: Nil = Enum(3)

  def apply() = PacType.unknown.cloneType

  def parse(v: IndexedSeq[UInt]) : UInt = {
    var result = Wire(PacType.unknown.cloneType)

    when((v(0) === 8.U) && (v(1) === 0.U)) {
      result := PacType.ipv4
    }.elsewhen((v(0) === 8.U) && (v(1) === 6.U)) {
      result := PacType.arp
    }.otherwise {
      result := PacType.unknown
    }

    result
  }
}