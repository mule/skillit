package fi.skillit.mathgen

import fi.skillit.engine.pack.ContentPack
import java.io.File

/** Developer entry point only. The generator/recomputer API itself performs no I/O. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Give the sample output path" }
    File(args[0]).writeText(ContentPack.encode(MathGen.samplePack()) + "\n")
}
