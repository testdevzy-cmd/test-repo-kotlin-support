package com.example.embeddings

import java.util.List
import kotlin.collections.emptyList

/**
 * Single-file fixture for Kotlin embeddings E2E testing.
 * Each construct maps to a ChunkType — see KOTLIN_EMBEDDINGS_TEST.md.
 */
class SampleClass {
    val a: Int = 1
    val b: Int = 2
    val c: Int = 3

    fun memberFn(): List<String> {
        val x = a + b
        val y = x + c
        return emptyList()
    }
}

interface SampleInterface {
    fun read(): String
    fun write(value: String)
    val size: Int
}

enum class SampleMode {
    ON,
    OFF,
    STANDBY
}

data class SampleData(val id: Int, val label: String) {
    fun display(): String {
        val text = label
        val out = "$id:$text"
        return out
    }
}

sealed class SampleSealed {
    data class NodeA(val x: Int) : SampleSealed()
    data class NodeB(val y: String) : SampleSealed()
    object NodeC : SampleSealed()
}

object SampleObject {
    const val ID = "obj-1"
    fun ping(): String {
        val reply = "pong"
        val tag = ID
        return "$reply-$tag"
    }
}

class WithCompanion {
    val version: Int = 1

    companion object {
        fun companionFn(): String {
            val a = 1
            val b = 2
            return "${a + b}"
        }
    }
}

class WithSecondaryCtor(val name: String) {
    val tag: String = "primary"

    constructor(copy: WithSecondaryCtor) : this(copy.name) {
        val derived = copy.tag
        val marker = derived.uppercase()
        println(marker)
    }
}

/** Top-level function chunk — see KOTLIN_EMBEDDINGS_TEST.md */
fun topLevelFn(x: Int): Int {
    val step = 1
    val doubled = x * 2
    return doubled + step
}
