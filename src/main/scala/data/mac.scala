package data

import chisel3._;

class MACAddr extends Bundle {
  var addr = Vec(6, UInt(8.W))
}