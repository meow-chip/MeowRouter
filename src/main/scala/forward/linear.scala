package forward

import chisel3._
import chisel3.util._
import data._
import _root_.util.Consts

/**
 * LLFT stands for Linear Lookup Forward Table
 * Using chisel memory here (becaues, reasons)
 * 
 * This impl doesn't support NAT. so io.output.lookup.status is never natInbound or natOutbound
 */
class LLFT(PORT_COUNT: Int) extends MultiIOModule {
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
    val status = Input(Status())

    val stall = Output(Bool())
    val pause = Input(Bool())

    val output = Output(new ForwardOutput(PORT_COUNT))
    val outputStatus = Output(Status())
  })

  val ips = IO(Input(Vec(PORT_COUNT+1, UInt(32.W))))

  private val store = VecInit(Array(
    Entry(List(10, 0, 1, 0), 24, List(10, 0, 1, 2)),
    Entry(List(10, 0, 2, 0), 24, List(10, 0, 2, 2)),
    Entry(List(10, 0, 3, 0), 24, List(10, 0, 3, 2)),
    Entry(List(10, 0, 4, 0), 24, List(10, 0, 4, 2))
  ))

  val cnt = Reg(UInt(log2Ceil(store.length + 1).W))
  val shiftCnt = Reg(UInt(log2Ceil(32+1).W))

  val working = Reg(new Packet(PORT_COUNT))
  val lookup = Reg(new ForwardLookup)
  val addr = Reg(UInt(32.W))
  val status = RegInit(Status.vacant)

  io.output.packet := working
  io.output.lookup := lookup
  io.outputStatus := status

  val sIDLE :: sMATCHING :: Nil = Enum(2)
  val state = RegInit(sIDLE)

  io.stall := state =/= sIDLE

  switch(state) {
    is(sIDLE) {
      when(!io.pause) {
        status := io.status
        working := io.input
        when(io.status =/= Status.vacant) {
          when(io.input.eth.pactype === PacType.ipv4) {
            when(io.input.ip.dest === ips(io.input.eth.vlan)) {
              status := Status.toLocal
              lookup.status := ForwardLookup.invalid
            }.otherwise {
              addr := io.input.ip.dest
              cnt := 0.U
              shiftCnt := 32.U
              state := sMATCHING
            }
          } .otherwise {
            status := Status.toLocal
            lookup.status := ForwardLookup.invalid
          }
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
