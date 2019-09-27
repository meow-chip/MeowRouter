package forward

import chisel3._
import chisel3.util._
import data._

object ForwardLookup {
  val invalid :: notFound :: forward :: natOutbound :: natInbound :: Nil = Enum(5)
}

/**
 * The forward table lookup result
 * nextHop is valid only if status =/= ForwardLookup.notFound && status =/= ForwardLookup.invalid
 * 
 * status = ForwardLookup.invalid if the input packet is not an IP packet (ARP, etc.)
 * (For now)
 * 
 */
class ForwardLookup extends Bundle {
  val status = ForwardLookup.notFound.cloneType
  val nextHop = UInt(32.W)
}

/**
 * Output for the forward table stage.
 * Contains the lookup result and the packet.
 * 
 * 
 * For nat packets, TCP should be asserted
 * If lookup.status === ForwardLookup.natOutbound, then the packet besides TCP checksum is modified
 * If lookup.status === ForwardLookup.natInbound, then the packet besides TCP checksum is
 *   also modified, and it's guaranteed that lookup.nextHop === packet.ip.dest
 * TCP checksum computation is delayed until the TCP Checksum stage
 */
class ForwardOutput(val PORT_COUNT: Int) extends Bundle {
  val packet = new Packet(PORT_COUNT)
  val lookup = new ForwardLookup
}