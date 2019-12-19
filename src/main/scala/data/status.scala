package data

import chisel3.util.Enum
import chisel3.experimental.ChiselEnum

/**
 * Pipeline status
 * vacant: No valid packet here
 * normal: This is a normal packet. Encoder should decide its
 *   behavior based on pactype and Forward Table/ARP lookup result
 * dropped: This is a dropped packet. Encoder should empty related IP content FIFO if needed
 */
object Status extends ChiselEnum {
  val vacant, normal, toLocal, dropped = Value
}
