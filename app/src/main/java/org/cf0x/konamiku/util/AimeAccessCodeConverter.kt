package org.cf0x.konamiku.util

import java.math.BigInteger

object AimeAccessCodeConverter {

    private val LONG_MAX   = BigInteger.valueOf(Long.MAX_VALUE)
    private val TWO_POW_64 = BigInteger.ONE.shiftLeft(64)

    sealed class Result {
        data class Single(val value: String) : Result()
        data class Ambiguous(val positive: String, val negative: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun idmToAccessCode(idm: String): Result {
        if (idm.isEmpty())
            return Result.Failure("IDm cannot be empty")
        if (idm.length > 16)
            return Result.Failure("IDm must be at most 16 hex characters")
        if (idm.any { it !in '0'..'9' && it !in 'A'..'F' && it !in 'a'..'f' })
            return Result.Failure("IDm contains invalid hex characters")
        return runCatching {
            val padded  = idm.padStart(16, '0').uppercase()
            var longVal = BigInteger(padded, 16)
            if (longVal > LONG_MAX) longVal = longVal - TWO_POW_64
            Result.Single(longVal.abs().toString().padStart(20, '0'))
        }.getOrElse { Result.Failure("Conversion failed: ${it.message}") }
    }

    fun accessCodeToIdm(accessCode: String): Result {
        val digits = accessCode.filter { it.isDigit() }
        if (digits.isEmpty()) return Result.Failure("Access Code cannot be empty")
        if (digits.length > 20) return Result.Failure("Access Code must be at most 20 digits")
        return runCatching {
            val value = BigInteger(digits)
            if (value > LONG_MAX) {
                Result.Single(
                    (TWO_POW_64 - value).toString(16).uppercase().padStart(16, '0')
                )
            } else {
                val positiveIdm = value.toString(16).uppercase().padStart(16, '0')
                val negativeIdm = if (value > BigInteger.ZERO)
                    (TWO_POW_64 - value).toString(16).uppercase().padStart(16, '0')
                else positiveIdm
                if (positiveIdm == negativeIdm) Result.Single(positiveIdm)
                else Result.Ambiguous(positiveIdm, negativeIdm)
            }
        }.getOrElse { Result.Failure("Conversion failed: ${it.message}") }
    }

    fun formatAccessCode(raw: String): String =
        raw.filter { it.isDigit() }.padStart(20, '0').chunked(4).joinToString("-")

    fun normalizeAccessCode(input: String): String = input.filter { it.isDigit() }

    fun selfTest(): Boolean {
        val t1 = idmToAccessCode("012E456789ABCDEF")
        if (t1 !is Result.Single || t1.value != "00081234123412341234") return false
        val t2 = accessCodeToIdm("00081234123412341234")
        val t2p = when (t2) {
            is Result.Single    -> t2.value
            is Result.Ambiguous -> t2.positive
            else                -> return false
        }
        if (t2p != "012E456789ABCDEF") return false
        if (formatAccessCode("00081234123412341234") != "0008-1234-1234-1234-1234") return false
        return true
    }
}