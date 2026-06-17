package org.cf0x.konamiku.util

import java.math.BigInteger

object AimeAccessCodeConverter {

    private val TWO_POW_64 = BigInteger("18446744073709551616")
    private val LONG_MAX   = BigInteger("9223372036854775807")

    sealed class Result {
        data class Single(val value: String) : Result()
        data class Ambiguous(val positive: String, val negative: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun idmToAccessCode(idm: String, context: android.content.Context): Result {
        if (idm.isEmpty())
            return Result.Failure(context.getString(org.cf0x.konamiku.R.string.tools_err_idm_empty))
        if (idm.length > 16)
            return Result.Failure(context.getString(org.cf0x.konamiku.R.string.tools_err_idm_length))
        if (idm.any { it !in '0'..'9' && it !in 'A'..'F' && it !in 'a'..'f' })
            return Result.Failure(context.getString(org.cf0x.konamiku.R.string.tools_err_idm_hex))
        return runCatching {
            val padded  = idm.padStart(16, '0').uppercase()
            var longVal = BigInteger(padded, 16)
            if (longVal > LONG_MAX) longVal = longVal - TWO_POW_64
            Result.Single(longVal.abs().toString().padStart(20, '0'))
        }.getOrElse { Result.Failure("${context.getString(org.cf0x.konamiku.R.string.tools_err_generic)}: ${it.message}") }
    }

    fun accessCodeToIdm(accessCode: String, context: android.content.Context): Result {
        val digits = accessCode.filter { it.isDigit() }
        if (digits.isEmpty()) return Result.Failure(context.getString(org.cf0x.konamiku.R.string.tools_err_ac_empty))
        if (digits.length > 20) return Result.Failure(context.getString(org.cf0x.konamiku.R.string.tools_err_ac_length))
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
        }.getOrElse { Result.Failure("${context.getString(org.cf0x.konamiku.R.string.tools_err_generic)}: ${it.message}") }
    }

    fun formatAccessCode(raw: String): String =
        raw.filter { it.isDigit() }.padStart(20, '0').chunked(4).joinToString("-")
}
