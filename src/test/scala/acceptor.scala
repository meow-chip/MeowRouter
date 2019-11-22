package acceptor

import chisel3._
import chisel3.util._
import chisel3.iotesters.{Driver, PeekPokeTester}

import data.Packet
import data.AXIS

class AcceptorWrapper(PORT_COUNT: Int) extends Module {
  val io = IO(new Bundle {
    val rx = Flipped(new AXIS(8))

    val packet = Output(new Packet(PORT_COUNT))
    val valid = Output(new Bool())
  })

  val c = Module(new Acceptor(PORT_COUNT))
  c.io.rx <> io.rx
  c.io.writer <> DontCare
  c.io.ipWriter <> DontCare
  io.valid := c.io.writer.en
  io.packet := c.io.writer.data
}

class AcceptorTest(PORT_COUNT: Int, c: AcceptorWrapper) extends PeekPokeTester(c) {
  val packetsStr = List(
    // ARP request, local IP for port 1 is 10.0.1.1, expected to emit an ARP Response
    "FF FF FF FF FF FF 00 12 17 CD DD 12 81 00 00 01 08 06 00 01 08 00 06 04 00 01 00 12 17 CD DD 12 0A 00 01 02 00 00 00 00 00 00 0A 00 01 01 01 00 00 00 00 00 00 00 00 00 00 00 00 00 61 84 A4 4D",
    // IP ping 10.0.1.2 -> 10.0.2.3 (With incorrect FCS). This should route to 10.0.2.2, and thus cause a ARP miss. Expected to emit four ARP Request
    "00 00 00 00 00 01 00 12 17 CD DD 12 81 00 00 01 08 00 45 00 00 54 32 22 00 00 40 01 34 85 0A 00 01 02 0A 00 02 03 08 00 80 D5 7B 43 00 03 5C BF EC 2E 00 03 C7 EF 08 09 0A 0B 0C 0D 0E 0F 10 11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F 30 31 32 33 34 35 36 37 26 7F 6C E4",
    // ARP response from 10.0.3.1
    "00 00 00 00 00 03 06 05 04 03 02 01 81 00 00 03 08 06 00 01 08 00 06 04 00 02 06 05 04 03 02 01 0A 00 03 02 00 00 00 00 00 03 0A 00 03 01 01 00 00 00 00 00 00 00 00 00 00 00 00 00 61 84 A4 4D",
    // IP ping 10.0.1.2 -> 10.0.3.6 (With incorrect FCS). This should route to 10.0.3.2
    "00 00 00 00 00 01 00 12 17 CD DD 12 81 00 00 01 08 00 45 00 00 54 32 22 00 00 40 01 34 85 0A 00 01 02 0A 00 03 06 08 00 80 D5 7B 43 00 03 5C BF EC 2E 00 03 C7 EF 08 09 0A 0B 0C 0D 0E 0F 10 11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F 30 31 32 33 34 35 36 37 26 7F 6C E4",
    // Very large IP packet (Fake size), should drop
    "00 00 00 00 00 01 00 12 17 CD DD 12 81 00 00 01 08 00 45 00 FF FF 32 22 00 00 40 01 34 85 0A 00 01 02 0A 00 03 06 08 00 80 D5 7B 43 00 03 5C BF EC 2E 00 03 C7 EF 08 09 0A 0B 0C 0D 0E 0F 10 11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F 30 31 32 33 34 35 36 37 26 7F 6C E4"
  )

  val results = List(
    2, // ARP
    1, // IP
    2, // ARP
    1, // IP
    -1 // error packet
  )

  for (p_id <- 0 until packetsStr.length) {
    val p = packetsStr(p_id)
    val sendBuf = p.split(" ").map(x => Integer.parseInt(x, 16))
    var packetType = BigInt(-1)
    println("sending packet: " + p)
    for (i <- 0 until sendBuf.length) {
      poke(c.io.rx.tdata, sendBuf(i))
      poke(c.io.rx.tvalid, true)
      poke(c.io.rx.tlast, i == sendBuf.length - 1)
      step(1)
      //println("packtype: " + peek(c.io.packet.eth.pactype))
      //println("iplen: " + peek(c.io.packet.ip.len))
      //println("ttl: " + peek(c.io.packet.ip.ttl))
      if (peek(c.io.valid) == BigInt(1)) {
        packetType = peek(c.io.packet.eth.pactype)
      }
    }
    assert(packetType.toInt == results(p_id))
  }
}
