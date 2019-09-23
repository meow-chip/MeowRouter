package encoder

import chisel3._
import data._

class Encoder(PORT_COUNT: Int) extends Module {
    val io = IO(new Bundle{
        val eth = Input(new Eth(PORT_COUNT + 1))
        val arp = Input(new ARP(48, 32))
        val ip = Input(new IP)
        val fire = Input(Bool())

        val tx = new AXIS(8)
    })

    val writing = RegInit(false.B)
    val cnt = RegInit(0.U)

    val header = Reg(Vec(28, UInt(8.W)))

    io.tx.tvalid := false.B
    io.tx.tlast := false.B

    when(io.fire && ~writing) {
        cnt := 27.U
        writing := true.B
        header := io.eth.asVec
    } .elsewhen(writing) {
        io.tx.tdata := header(io.tx.tdata)
        io.tx.tvalid := true.B

        when(io.tx.tready) {
            when(cnt > 0.U) {
                cnt := cnt - 1.U
            } .otherwise {
                writing := false.B
            }
        }
    }
}