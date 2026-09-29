package com.yahyafati.mnemo.core.anki.internal

/**
 * Just enough of the protobuf wire format to read the few fields Mnemo needs from Anki's newer
 * files (note type kind, template formats, deck description, package version, media names).
 * Unknown fields are skipped, so new Anki versions that add fields still parse.
 */
internal class ProtoReader(private val bytes: ByteArray) {
    private var pos = 0

    /** Every field as (number, value): a [Long] for varints and fixed ints, a [ByteArray] otherwise. */
    fun fields(): List<Pair<Int, Any>> {
        val out = mutableListOf<Pair<Int, Any>>()
        while (pos < bytes.size) {
            val key = varint()
            val number = (key ushr 3).toInt()
            val value: Any = when ((key and 7).toInt()) {
                WIRE_VARINT -> varint()
                WIRE_FIXED64 -> fixed(8)
                WIRE_LENGTH -> {
                    val length = varint().toInt()
                    if (length < 0 || pos + length > bytes.size) throw IllegalArgumentException("Truncated protobuf field $number")
                    bytes.copyOfRange(pos, pos + length).also { pos += length }
                }
                WIRE_FIXED32 -> fixed(4)
                else -> throw IllegalArgumentException("Unsupported protobuf wire type in field $number")
            }
            out += number to value
        }
        return out
    }

    private fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= bytes.size) throw IllegalArgumentException("Truncated protobuf varint")
            val b = bytes[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw IllegalArgumentException("Protobuf varint too long")
        }
    }

    private fun fixed(size: Int): Long {
        if (pos + size > bytes.size) throw IllegalArgumentException("Truncated protobuf fixed field")
        var result = 0L
        for (i in 0 until size) result = result or ((bytes[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += size
        return result
    }

    companion object {
        private const val WIRE_VARINT = 0
        private const val WIRE_FIXED64 = 1
        private const val WIRE_LENGTH = 2
        private const val WIRE_FIXED32 = 5

        fun parse(bytes: ByteArray?): List<Pair<Int, Any>> = if (bytes == null) emptyList() else ProtoReader(bytes).fields()
    }
}

internal fun List<Pair<Int, Any>>.long(number: Int): Long? = lastOrNull { it.first == number }?.second as? Long

internal fun List<Pair<Int, Any>>.bytes(number: Int): ByteArray? = lastOrNull { it.first == number }?.second as? ByteArray

internal fun List<Pair<Int, Any>>.string(number: Int): String? = bytes(number)?.decodeToString()

internal fun List<Pair<Int, Any>>.allBytes(number: Int): List<ByteArray> = filter { it.first == number }.mapNotNull { it.second as? ByteArray }
