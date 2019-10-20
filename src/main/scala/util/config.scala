package util

object Configs {
    var isTesting: Boolean = sys.env.getOrElse("CHISEL_TYPE", "RELEASE").toUpperCase == "TEST"
    var isRelease: Boolean = !isTesting
}