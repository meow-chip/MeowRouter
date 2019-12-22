package forward

import chisel3._
import chisel3.util._
import chisel3.experimental._
import data._
import _root_.util.Consts


/**
 * Forward table with Cuckoo hashtable stored in external memory
 */
class CuckooFT(PORT_COUNT: Int) extends MultiIOModule {
  val io = IO(new Bundle {
    val input = Input(new Packet(PORT_COUNT))
    val status = Input(Status())

    val stall = Output(Bool())
    val pause = Input(Bool())

    val output = Output(new ForwardOutput(PORT_COUNT))
    val outputStatus = Output(Status())

    val axi = new AXI(64)
  })

  val ips = IO(Input(Vec(PORT_COUNT+1, UInt(32.W))))

  val working = Reg(new Packet(PORT_COUNT))
  val lookup = Reg(new ForwardLookup)
  val addr = Reg(UInt(32.W))
  val status = RegInit(Status.vacant)

  io.output.packet := working
  io.output.lookup := lookup
  io.outputStatus := status

  object State extends ChiselEnum {
    val idle, waiting = Value
  }

  val state = RegInit(State.idle)

  io.stall := state =/= State.idle

  class CuckooRow extends Bundle {
    val values = Vec(4, UInt(32.W))
    val keys = Vec(4, UInt(32.W))
  }

  val lines = Reg(Vec(2, Vec(4, UInt(64.W))))
  val cnts = RegInit(VecInit(Seq.fill(2)(0.U(log2Ceil(4).W))))
  val views = lines.asTypeOf(Vec(2, new CuckooRow()))
  val lineReady = RegInit(VecInit(Seq(false.B, false.B)))
  val sending = RegInit(0.U(2.W))
  val hashes = VecInit(CuckooFT.hash(working.ip.dest))

  io.axi := DontCare
  io.axi.AWVALID := false.B
  io.axi.WVALID := false.B
  io.axi.BREADY := false.B
  io.axi.ARVALID := false.B
  io.axi.RREADY := false.B

  switch(state) {
    is(State.idle) {
      when(!io.pause) {
        status := io.status
        working := io.input

        when(io.status =/= Status.normal) {
          state := State.idle
        }.elsewhen(io.input.eth.pactype =/= PacType.ipv4) {
          state := State.idle
          status := Status.toLocal
        }.elsewhen(
          io.input.ip.dest === ips(io.input.eth.vlan)
          || io.input.ip.dest.andR()
          || io.input.ip.dest(31, 24) === 224.U
        ) {
          state := State.idle
          status := Status.toLocal
          // lookup doesn't matter now
        }.otherwise {
          lineReady := VecInit(Seq(false.B, false.B))
          cnts := VecInit(Seq.fill(2)(0.U))
          sending := 0.U

          state := State.waiting
        }
      }
    }

    is(State.waiting) {
      assume(new CuckooRow().getWidth == 64 * 4)
      // AR
      io.axi.ARADDR := Consts.CUCKOO_ADDR_BASE.U + hashes(sending) * Consts.CUCKOO_LINE_WIDTH.U
      io.axi.ARBURST := AXI.Constants.Burst.INCR.U
      io.axi.ARCACHE := 0.U
      io.axi.ARID := sending
      io.axi.ARLEN := 3.U // 4 slot per line
      io.axi.ARPROT := 0.U
      io.axi.ARQOS := 0.U
      io.axi.ARREGION := 0.U
      io.axi.ARSIZE := AXI.Constants.Size.from(io.axi.DATA_WIDTH / 8).U
      io.axi.ARVALID := sending < 2.U

      assert(sending <= 2.U)

      when(io.axi.ARREADY && io.axi.ARVALID) {
        sending := sending + 1.U
      }

      // R
      io.axi.RREADY := true.B
      when(io.axi.RVALID) {
        val idx = io.axi.RID
        lines(idx)(cnts(idx)) := io.axi.RDATA
        cnts(idx) := cnts(idx) +% 1.U

        when(io.axi.RLAST) {
          lineReady(idx) := true.B
        }
      }

      when(lineReady.reduce(_ && _)) {
        // Start matching
        // TODO: filter broadcast & 0.0.0.0
        // TODO: match localhost
        val hits = views.map(row => VecInit(row.keys.map(_ === working.ip.dest)).asUInt().orR())
        val values = views.map(row => Mux1H(
          row.keys.zip(row.values).map({ case (k, v) => (k === working.ip.dest) -> v })
        ))

        val hit = VecInit(hits).asUInt.orR()
        val value = Mux1H(hits.zip(values))

        lookup.status := Mux(hit, ForwardLookup.forward, ForwardLookup.notFound)
        lookup.nextHop := value

        state := State.idle
      }
    }
  }
}

object CuckooFT {
  def hash(addr: UInt): Seq[UInt] = {
    val HASH_WIDTH = log2Ceil(Consts.CUCKOO_LINE_COUNT)
    val hash1 = addr(HASH_WIDTH-1, 0)
    val hash2 = addr(16 + HASH_WIDTH-1, 16)
    Seq(hash1, hash2)
  }
}
