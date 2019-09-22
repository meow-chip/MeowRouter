package acceptor

import chisel3._
import chisel3.util.log2Ceil

class IPFIFO(CAPACITY: Int, WIDTH: Int) extends Module {
    val ADDR_LEN = log2Ceil(CAPACITY+1)
    val io = IO(new Bundle {
        val size = Output(UInt(ADDR_LEN.W))
        val full = Output(Bool())
        val empty = Output(Bool())

        val push = Input(Bool())
        val pop = Input(Bool())

        val front = Output(UInt(WIDTH.W))
        val wdata = Input(UInt(WIDTH.W))
    })

    val mem = RegInit(Vec(CAPACITY, UInt(WIDTH.W)))
    val fptr = RegInit(0.asUInt(ADDR_LEN.W))
    val tptr = RegInit(0.asUInt(ADDR_LEN.W))

    val count = RegInit(0.asUInt(ADDR_LEN.W))

    io.size := count
    io.empty := count === 0.U
    io.full := count === CAPACITY.U
    io.front := mem(fptr)

    when(io.push && ~io.full) {
        tptr := Mux(tptr === (CAPACITY - 1).U, 0.U, tptr + 1.U)
        mem(tptr) := io.wdata
        count := count + 1.U
    }

    when(io.pop && ~io.empty) {
        fptr := Mux(fptr === (CAPACITY - 1).U, 0.U, fptr + 1.U)
        count := count - 1.U
    }

    when(io.push && ~io.full && io.pop && ~io.empty) {
        count := count
    }
}