package org.cf0x.konamiku.util

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CardIdConverter {

    private val RAW_KEY = "?I'llB2c.YouXXXeMeHaYpy!".toByteArray(Charsets.ISO_8859_1)
    private val PROCESSED_KEY = ByteArray(RAW_KEY.size) {
        (RAW_KEY[it].toInt() and 0xFF).shl(1).and(0xFF).toByte()
    }
    private val IV_SPEC = IvParameterSpec(ByteArray(8))
    private const val ALPHABET = "0123456789ABCDEFGHJKLMNPRSTUWXYZ"

    private fun encDes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(PROCESSED_KEY, "DESede"), IV_SPEC)
        return cipher.doFinal(data)
    }

    private fun decDes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(PROCESSED_KEY, "DESede"), IV_SPEC)
        return cipher.doFinal(data)
    }

    private fun pack5(data: IntArray): ByteArray {
        var bits = data.joinToString("") { it.and(0x1F).toString(2).padStart(5, '0') }
        val rem  = bits.length % 8
        if (rem != 0) bits += "0".repeat(8 - rem)
        return ByteArray(bits.length / 8) { i ->
            bits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        }
    }

    private fun unpack5(data: ByteArray): IntArray {
        var bits = data.joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') }
        val rem  = bits.length % 5
        if (rem != 0) bits += "0".repeat(5 - rem)
        return IntArray(bits.length / 5) { i ->
            bits.substring(i * 5, i * 5 + 5).toInt(2)
        }
    }

    private fun checksum(data: IntArray): Int {
        var chk = (0 until 15).sumOf { i -> data[i] * (i % 3 + 1) }
        while (chk > 31) chk = (chk shr 5) + (chk and 31)
        return chk
    }

    sealed class Result {
        data class Success(val value: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun toKonamiId(uid: String): Result {
        if (uid.length != 16)
            return Result.Failure("UID must be exactly 16 hex characters")
        val upper = uid.uppercase()
        if (upper.any { it !in '0'..'9' && it !in 'A'..'F' })
            return Result.Failure("UID contains invalid hex characters")
        val cardType = when {
            upper.startsWith("E004") -> 1
            upper.startsWith("0")   -> 2
            else -> return Result.Failure("UID must start with E004 or 0")
        }
        return runCatching {
            val kidBytes = ByteArray(8) { i ->
                upper.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val encrypted = encDes(kidBytes.reversedArray())
            val unpacked  = unpack5(encrypted).take(13).toIntArray()
            val out       = IntArray(16)
            for (i in 0 until 13) out[i] = unpacked[i]
            out[0]  = out[0] xor cardType
            out[13] = 1
            for (i in 1 until 14) out[i] = out[i] xor out[i - 1]
            out[14] = cardType
            out[15] = checksum(out)
            Result.Success(out.joinToString("") { ALPHABET[it].toString() })
        }.getOrElse { Result.Failure("Conversion failed: ${it.message}") }
    }

    fun toUid(konamiId: String): Result {
        if (konamiId.length != 16)
            return Result.Failure("Konami ID must be exactly 16 characters")
        if (konamiId.any { it !in ALPHABET })
            return Result.Failure("Konami ID contains invalid characters")
        return runCatching {
            val card     = IntArray(16) { i -> ALPHABET.indexOf(konamiId[i]) }
            val cardType = when (konamiId[14]) {
                '1'  -> 1
                '2'  -> 2
                else -> return Result.Failure("Invalid card type at position 14")
            }
            if (card[11] % 2 != card[12] % 2) return Result.Failure("Parity check failed")
            if (card[13] != (card[12] xor 1))  return Result.Failure("Encoding check failed")
            if (card[15] != checksum(card))     return Result.Failure("Checksum mismatch")
            for (i in 13 downTo 1) card[i] = card[i] xor card[i - 1]
            card[0] = card[0] xor cardType
            val packed    = pack5(card.copyOf(13))
            val decrypted = decDes(packed.copyOf(8)).reversedArray()
            val uid       = decrypted.joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            }.uppercase()
            when (cardType) {
                1 -> if (!uid.startsWith("E004"))
                    return Result.Failure("Post-decode check failed: expected E004 prefix")
                2 -> if (!uid.startsWith("0"))
                    return Result.Failure("Post-decode check failed: expected 0 prefix")
            }
            Result.Success(uid)
        }.getOrElse { Result.Failure("Conversion failed: ${it.message}") }
    }

    fun selfTest(): Boolean {
        val t1 = toKonamiId("0000000000000000")
        if (t1 !is Result.Success || t1.value != "FW5331K31WT1ZY2U") return false
        val t2 = toUid("FW5331K31WT1ZY2U")
        if (t2 !is Result.Success || t2.value != "0000000000000000") return false
        val t3 = toKonamiId("000000100200F000")
        if (t3 !is Result.Success) return false
        val t4 = toUid(t3.value)
        if (t4 !is Result.Success || t4.value != "000000100200F000") return false
        return true
    }
}