package cuckoo

import chisel3._
import chisel3.util._
import chisel3.iotesters.{Driver, TesterOptionsManager, PeekPokeTester}

class TestCuckoo(c: HashTable, KeySize: Int, ValueSize: Int) extends PeekPokeTester(c) {
  val keys = List(312, 232, 32323, 7889000, 890)
  val vals = List(1, 2, 3, 233, 4)

  def waitWhenStalled(): Unit = {
    var cnt = 0
    while (peek(c.io.stall) == BigInt(1)) {      
      cnt = cnt + 1
      assert(cnt <= 10)
      step(1)
    }
  }

  // insert
  for (i <- 0 until keys.length) {
    waitWhenStalled()
    poke(c.io.key, keys(i))
    poke(c.io.value, vals(i))
    poke(c.io.setEn, true)
    poke(c.io.queryEn, false)
    waitWhenStalled()
    println(peek(c.io.answerValue).toString)
  }

  // query
  for (i <- 0 until keys.length) {
    waitWhenStalled()
    poke(c.io.key, keys(i))
    poke(c.io.setEn, false)
    poke(c.io.queryEn, true)
    step(1)
    waitWhenStalled()
    println(peek(c.io.answerValue).toString)
    assert(peek(c.io.answerEn) == BigInt(1))
    assert(peek(c.io.answerFound) == BigInt(1))
    assert(peek(c.io.answerValue) == BigInt(vals(i)))
  }
}

object TestCuckooMain {
  def main(args: Array[String]): Unit = {
    if (!Driver(() => new HashTable(32, 8))(c => new TestCuckoo(c, 32, 8))) System.exit(1)
  }
}
  