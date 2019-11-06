package data

import chisel3._;

object ICMP {
    val HeaderLength = 48
    
    // The ICMP packets with these types have the identifier
    val ECHO_REPLY = 0
    val ECHO_REQUEST = 8
    val TIME_EXCEEDED = 11
}

// We presume the ICMP packets contains id, which is only for specific types.
class ICMP() extends Bundle {
    val id = UInt(16.W)
    val checksum = UInt(16.W)
    val code = UInt(8.W)
    val imcpType = UInt(8.W)
}
