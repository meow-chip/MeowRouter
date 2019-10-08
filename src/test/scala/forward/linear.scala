package forward

import chisel3._
import chisel3.util._
import chisel3.iotesters.PeekPokeTester

import data._

class LLFTTestModule(PORT_COUNT: Int) extends Module {
    val io = IO(new Bundle {
        val cnt = Input(UInt(8.W))
        val idle = Output(Bool())
        val pass = Output(Bool())
    })

    val c = Module(new LLFT(PORT_COUNT))
    val INPUTS = VecInit(Seq(
        0x0A0001AA.U(32.W),
        0x0A000319.U(32.W)
    ))
    val OUTPUTS = VecInit(Seq(
        0x0A000102.U(32.W),
        0x0A000302.U(32.W)
    ))

    //val cnt = RegInit(0.U)

    c.io <> DontCare
    
    c.io.input.eth.pactype := PacType.ipv4
    c.io.input.ip.dest := INPUTS(io.cnt)
    c.io.status := Status.normal
    c.io.pause := 0.U

    io.idle := !c.io.stall
    io.pass := OUTPUTS(io.cnt) === c.io.output.lookup.nextHop
}

class LLFTTest(c: LLFTTestModule, PORT_COUNT: Int) extends PeekPokeTester(c) {
    for (cnt <- 0 until 2) {
        var done = false
        do {
            poke(c.io.cnt, cnt.U(8.W))
            step(1)
            done = peek(c.io.idle) == BigInt(1)
        } while (!done)
        expect(c.io.pass, true)
    }
}