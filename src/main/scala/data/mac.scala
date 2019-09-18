package data

import chisel3._;

class MACAddr extends Bundle {
  var addr = UInt(48.W)
}