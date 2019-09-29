package data

import chisel3._;
import chisel3.util.log2Ceil;
import chisel3.util.Cat

class Eth(val VLAN_COUNT: Int) extends Bundle {
  val dest = UInt(48.W)
  val sender = UInt(48.W)
  val pactype = PacType()
  val vlan = UInt(log2Ceil(VLAN_COUNT).W)

  def toBits() : UInt = Cat(
    dest,
    sender,
    0x810000.U(24.W),
    vlan.asTypeOf(UInt(8.W)),
    PacType.serialize(pactype)
  )

  def asVec : Vec[UInt] = toBits().asTypeOf(Vec(18, UInt(8.W)))
}