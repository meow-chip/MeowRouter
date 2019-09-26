package data

import chisel3.util.Enum

/**
 * Pipeline status
 * vacant: No valid packet here
 * normal: This is a normal packet. Encoder should decide its
 *   behavior based on pactype and Forward Table/ARP lookup result
 * dropped: This is a dropped packet. Encoder should empty related IP content FIFO if needed
 */
object Status {
  val vacant :: normal :: dropped :: Nil = Enum(3)
}