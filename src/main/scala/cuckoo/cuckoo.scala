package cuckoo

import chisel3._
import chisel3.util._

class HashTable(val KeySize: Int, val ValueSize: Int) extends Module {
  val io = IO(new Bundle {
    val key = Input(UInt(KeySize.W))
    val value = Input(UInt(ValueSize.W))
    // Both of setEn and queryEn are true is the undefined behavior
    // when setEn is true, this module will set the "key" to "the value"
    // when queryEn is true, this module will return the value associted to the "key"
    val setEn = Input(Bool()) 
    val queryEn = Input(Bool())

    val answerValue = Output(UInt(ValueSize.W))
    val answerEn = Output(Bool())
    val answerFound = Output(Bool())
    val stall = Output(Bool())
  });

  class KeyValuePair(val KeySize: Int, val ValueSize: Int) extends Bundle {
    val key = UInt(KeySize.W)
    val value = UInt(ValueSize.W)
    val valid = Bool()
  }

  val mem = Mem(1024, (new KeyValuePair(KeySize, ValueSize)).cloneType)
  val sIDLE :: sReading :: sComparing :: sSetting :: Nil = Enum(4)
  val state = RegInit(sIDLE)
  
  val key = Reg(UInt(KeySize.W)) // querying on key
  val keyHash = Reg(UInt(log2Ceil(1024).W))
  val readPair = Reg(new KeyValuePair(KeySize, ValueSize))
  val found = RegInit(false.B)
  val answerEn = RegInit(false.B)

  // connecting io wires
  io.answerValue := readPair.value
  io.answerEn := answerEn
  io.answerFound := readPair.key === key && readPair.valid
  io.stall := state =/= sIDLE

  switch (state) {
    is (sIDLE) {
      answerEn := false.B
      key := io.key
      keyHash := io.key // TODO: This is just a mod operation for now. Use a real hash function instead.
      when (io.queryEn) {
        state := sReading
      } .elsewhen (io.setEn) {
        readPair.key := io.key
        readPair.value := io.value
        readPair.valid := true.B
        state := sSetting
      }
    }

    // *******
    // * the states for query
    // *******
    is (sReading) {
      readPair := mem.read(keyHash)
      state := sComparing
    }
    is (sComparing) {
      answerEn := true.B
      state := sIDLE
    }

    // *******
    // * the state for insert
    // *******
    is (sSetting) {
      mem.write(keyHash, readPair)
      state := sIDLE
    }
  }
}