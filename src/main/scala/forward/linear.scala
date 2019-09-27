package forward

import chisel3._
import chisel3.util._
import data._

/**
 * LLFT stands for Linear Lookup Forward Table
 * Using chisel memory here (becaues, reasons)
 * 
 * This impl doesn't support NAT. so io.output.lookup.status is never natInbound or natOutbound
 */
class LLFT(PORT_COUNT: Int) extends Module {
  private class Entry extends Bundle {
    val prefix = UInt(32.W)
    val len = UInt(log2Ceil(32+1).W)
    val dest = UInt(32.W)
  }

  private object Entry {
    def apply(ip: List[Int], len: Int, dest: List[Int]) : Entry = {
      val output = Wire(new Entry)

      val pFirst :: pRest = ip.map(_.U(8.W))
      val dFirst :: dRest = dest.map(_.U(8.W))

      output.prefix := (Cat(pFirst, pRest :_*)) >> (32 - len)
      output.len := len.U
      output.dest := Cat(dFirst, dRest :_*)

      output
    }
  }

  val io = IO(new Bundle {
    val input = Input(new Packet(PORT_COUNT))
    val status = Input(Status.normal.cloneType)

    val stall = Output(Bool())
    val pause = Input(Bool())

    val output = Output(new ForwardOutput(PORT_COUNT))
    val outputStatus = Output(Status.normal.cloneType)
  })

  private val store = VecInit(Array(
    Entry(List(10, 0, 1, 3), 32, List(10, 2, 0, 1)),
    Entry(List(10, 0, 1, 0), 24, List(10, 1, 0, 1))
  ))

  val cnt = Reg(UInt(log2Ceil(store.length + 1).W))
  val shiftCnt = Reg(UInt(log2Ceil(32+1).W))

  val working = Reg(new Packet(PORT_COUNT))
  val lookup = Reg(new ForwardLookup)
  val addr = Reg(UInt(32.W))

  io.output.packet := working
  io.output.lookup := lookup
  io.outputStatus := Status.normal // Forward table currently doesn't cause a packet drop

  val sIDLE :: sMATCHING :: Nil = Enum(2)
  val state = RegInit(sIDLE)

  io.stall := state =/= sIDLE

  switch(state) {
    is(sIDLE) {
      when(!io.pause && io.status =/= Status.vacant) {
        working := io.input
        when(io.input.eth.pactype === PacType.ipv4) {
          addr := io.input.ip.dest
          cnt := 0.U
          shiftCnt := 32.U
          state := sMATCHING
        } .otherwise {
          lookup.status := ForwardLookup.invalid
        }
      }
    }

    is(sMATCHING) {
      when(cnt === store.length.U) {
        state := sIDLE
        lookup.status := ForwardLookup.notFound
      } .elsewhen(shiftCnt =/= store(cnt).len) {
        shiftCnt := shiftCnt - 1.U
        addr := addr >> 1
      } .otherwise {
        when(store(cnt).prefix === addr) {
          lookup.status := ForwardLookup.forward
          lookup.nextHop := store(cnt).dest
          state := sIDLE
        } .otherwise {
          cnt := cnt + 1.U
        }
      }
    }
  }
}