package com.example.graph

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.List as KList
import kotlin.math.*

/**
 * Fixture exercising every EntityType + RelationshipType the Kotlin code-graph
 * extractor can produce. See KOTLIN_CODE_GRAPH_TEST.md for the expected counts
 * and Cypher verification queries.
 *
 * Coverage map (extractor emits):
 *   EntityType : class, interface, enum, method, function, variable
 *                (Kotlin `const val` → entity type 'variable', metadata.kind='constant')
 *   RelType    : IMPORTS (named + wildcard + alias), CONTAINS, EXTENDS, IMPLEMENTS,
 *                CALLS, INSTANTIATES, DECORATES, USES_TYPE
 */

annotation class Marker

annotation class Tagged(val value: String)

interface Greeter {
    fun greet(name: String): String
}

interface Sized {
    val size: Int
}

@Marker
open class Animal(open val species: String) {
    open fun describe(): String = species
}

@Tagged("dog")
class Dog(val nickname: String) : Animal("canine"), Greeter, Sized {
    override val size: Int = 1

    override fun describe(): String {
        val base = super.describe()
        return "$base/$nickname"
    }

    override fun greet(name: String): String {
        val tag = nickname
        val msg = "Woof $name from $tag"
        return msg
    }
}

class Box<T : Marker>(val value: T) {
    fun unbox(): T = value
}

internal class GraphFactory {
    fun makeDog(nickname: String): Dog {
        val d = Dog(nickname)
        val u = UUID.randomUUID()
        val tag = u.toString()
        return d
    }

    fun newCounter(): AtomicLong {
        val c = AtomicLong(0L)
        return c
    }
}

object MathUtil {
    const val MAX_RETRIES: Int = 5

    fun joinNumbers(sep: String, vararg nums: Int): String {
        val list = nums.toList()
        val first = list.first()
        return "$first$sep$list"
    }

    fun padWidth(width: Int = MAX_RETRIES): Int {
        val base = width
        val out = base + 1
        return out
    }
}

suspend fun fetchSomething(id: String): String {
    val prefix = "id="
    val out = prefix + id
    return out
}

fun topLevelMaker(): Dog {
    val d = Dog("rex")
    val f = GraphFactory()
    val d2 = f.makeDog("buddy")
    return d2
}
