package cuckoo

import chisel3._
import chisel3.util._
import chisel3.iotesters.{Driver, TesterOptionsManager, PeekPokeTester}

class TestCuckoo(c: HashTable, KeySize: Int, ValueSize: Int) extends PeekPokeTester(c) {
  // gerenate keys and val
  // TODO: When the #keys is larger than the 250, the hash table may fail
  val keys = for (i <- 0 until 200) yield scala.util.Random.nextInt(2047483647)
  val vals = for (i <- 0 until 200) yield scala.util.Random.nextInt(255)

  def waitWhenStalled(): Unit = {
    var cnt = 0
    while (peek(c.io.stall) == BigInt(1)) {      
      cnt = cnt + 1
      assert(cnt <= 10)
      step(1)
    }
    // println(s"Waited $cnt steps")
  }

  // insert
  for (i <- 0 until keys.length) {
    poke(c.io.key, keys(i))
    poke(c.io.value, vals(i))
    poke(c.io.setEn, true)
    poke(c.io.queryEn, false)
    step(1)
    waitWhenStalled()
    println("i = " + i + " key_hash = " + peek(c.io.dbg_keyhash) + " key = " + keys(i))
    // for (j <- 0 until 4) {
    //   println("key = " + peek(c.io.dbg.pairs(j).key) + ", value = " + peek(c.io.dbg.pairs(j).value) + ", valid = " + peek(c.io.dbg.pairs(j).valid))
    // }
    assert(peek(c.io.setSucc) == BigInt(1))
  }

  // query
  for (i <- 0 until keys.length) {
    poke(c.io.key, keys(i))
    poke(c.io.setEn, false)
    poke(c.io.queryEn, true)
    step(1)
    waitWhenStalled()
    //println(peek(c.io.answerValue).toString)
    assert(peek(c.io.answerFound) == BigInt(1))
    assert(peek(c.io.answerValue) == BigInt(vals(i)))
  }
}
