import data.MACAddr

import chisel3.core.VecInit
import chisel3._

object Consts {
    val LOCAL_MAC = new MACAddr

    LOCAL_MAC.addr := VecInit(Seq(
        0xD8.U,
        0x71.U,
        0x20.U,
        0x6A.U,
        0xA4.U,
        0xF6.U
    ))
}