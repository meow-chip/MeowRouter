package cuckoo

import chisel3._
import chisel3.util._
import _root_.util.CRC

class HashTable(val KeySize: Int, val ValueSize: Int) extends Module {
  class KeyValuePair(val KeySize: Int, val ValueSize: Int) extends Bundle {
    val key = UInt(KeySize.W)
    val value = UInt(ValueSize.W)
    val valid = Bool()
  }

  class Bucket() extends Bundle {
    val pairs = Vec(4, new KeyValuePair(KeySize, ValueSize))
  }

  val io = IO(new Bundle {
    val key = Input(UInt(KeySize.W))
    val value = Input(UInt(ValueSize.W))
    // Both of setEn and queryEn are true is the undefined behavior
    // when setEn is true, this module will set the "key" to "the value"
    // when queryEn is true, this module will return the value associted to the "key"
    val setEn = Input(Bool()) 
    val queryEn = Input(Bool())

    val answerValue = Output(UInt(ValueSize.W))
    val answerFound = Output(Bool())
    val setSucc = Output(Bool()) // is set operation successful
    val stall = Output(Bool())
    val dbg = Output(new Bucket())
    val dbg_keyhash = Output(UInt(KeySize.W))
  });

  val mem = Mem(1024, new Bucket())
  val sIDLE :: sReading :: sComparing :: sSetting :: sSetting1 :: sSetting2 :: sSetting3 :: Nil = Enum(7)
  val state = RegInit(sIDLE)
  
  val key = Reg(UInt(KeySize.W)) // querying on key
  val keyHash = Reg(UInt(log2Ceil(1024).W))
  val bucket = Reg(new Bucket())
  val p = RegInit(0.asUInt(8.W))
  val pair = Reg(new KeyValuePair(KeySize, ValueSize))
  val found = RegInit(false.B)
  val setSucc = RegInit(false.B)

  // connecting io wires
  io.answerValue := pair.value
  io.answerFound := found
  io.setSucc := setSucc
  io.stall := state =/= sIDLE
  io.dbg := bucket
  io.dbg_keyhash := keyHash

  switch (state) {
    is (sIDLE) {
      key := io.key
      keyHash := CRC(CRC.CRC_8F_6, io.key, KeySize)
      setSucc := false.B
      found := false.B
      p := 0.U
      when (io.queryEn) {
        state := sReading
      } .elsewhen (io.setEn) {
        pair.key := io.key
        pair.value := io.value
        pair.valid := true.B
        state := sSetting
      }
    }

    // *******
    // * the states for query
    // *******
    is (sReading) {
      bucket := mem.read(keyHash)
      state := sComparing
    }
    is (sComparing) {
      for (i <- 0 until 4) {
        when (bucket.pairs(i).key === key) {
          found := true.B
          pair := bucket.pairs(i)
        }
      }
      state := sIDLE
    }

    // *******
    // * the state for insert
    // *******
    is (sSetting) {
      bucket := mem.read(keyHash)
      state := sSetting1
    }
    is (sSetting1) {
      // !!! This doesn't work. The behavior is that when using the second slot (pairs(1)), it will set all slots to pair, i.e. firstEmptys are all true.
      val firstEmptys = Array.tabulate(4) { bucket.pairs(_).valid === false.B }
      for (i <- 1 until 4) {
        for (j <- 0 until i) {
          firstEmptys(i) = firstEmptys(i) && bucket.pairs(j).valid
        }
      }
      for (i <- 0 until 4) {
        when (firstEmptys(i)) {
          bucket.pairs(i) := pair
          setSucc := true.B
        }
      }
      state := sSetting2
    }
    is (sSetting2) {
      mem.write(keyHash, bucket)
      state := sIDLE
    }
  }
}