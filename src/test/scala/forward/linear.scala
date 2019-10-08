package forward

import chisel3._
import chisel3.util._
import chisel3.iotesters.PeekPokeTester

import data._

class LLFTTest(c: LLFT, PORT_COUNT: Int) extends PeekPokeTester(c) {
    def gen_packet(ip_seq: Seq[Int]): Packet = {
        val packet = new Packet(PORT_COUNT)
        poke(packet.eth.pactype, PacType.ipv4)

        val ipInt = ip_seq.map(_.U(8.W))
        val ipFirst :: ipRest = ipInt
        poke(packet.ip.src, Cat(ipFirst, ipRest: _*))

        packet
    }

    val packet = gen_packet(Seq[Int](10, 0, 3, 100))
    var done = false
    do {
        c.io.input := packet
        c.io.status := Status.normal
        poke(c.io.pause, 0)
        step(1)
        done = peek(c.io.stall) == BigInt(1)
    } while (!done)

    expect(c.io.outputStatus, ForwardLookup.forward)
    expect(c.io.output.lookup.nextHop, 0x0A000302.U)
}