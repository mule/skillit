package fi.skillit.mathgen

import fi.skillit.engine.pack.ContentPack
import java.io.File

/** Developer entry point only. The generator/recomputer API itself performs no I/O. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Give the sample output path" }
    val text = ContentPack.encode(MathGen.samplePack())
    MathGen.parsePack(text) // same validation required of the #23 builder
    File(args[0]).writeText(text + "\n")
}
