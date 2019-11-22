import chisel3.iotesters.Driver
import forward.{LLFTTestModule, LLFTTest}
import acceptor.{AcceptorWrapper, AcceptorTest}
import cuckoo.{HashTable, TestCuckoo}


object TestMain {
  def main(args: Array[String]): Unit = {
    println("🧪 LLFTTest...")
    if (!Driver(() => new LLFTTestModule(4))(c => new LLFTTest(c, 4))) System.exit(1)
    println("🧪 AcceptorTest...")
    if (!Driver(() => new AcceptorWrapper(4))(c => new AcceptorTest(4, c))) System.exit(1)
    println("🧪 TestCuckoo...")
    if (!Driver(() => new HashTable(32, 8))(c => new TestCuckoo(c, 32, 8))) System.exit(1)
    println("🧪 TopTest...")
    if (!Driver(() => new TopWrap())(c => new TopTest(c))) System.exit(1)
  }
}
