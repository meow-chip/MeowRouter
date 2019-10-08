package forward

import chisel3._
import chisel3.iotesters.{Driver, TesterOptionsManager}

object Main {
  def main(args: Array[String]): Unit = {
    if (!Driver(() => new LLFT(4))(c => new LLFTTest(c, 4))) System.exit(1)
  }
}
