package org.cf0x.konamiku.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardIdConverterTest {

    // ── toKonamiId ──

    @Test
    fun `toKonamiId converts E004-prefix card`() {
        val result = CardIdConverter.toKonamiId("E004123456789ABC")
        assertTrue(result is CardIdConverter.Result.Success)
        val kid = (result as CardIdConverter.Result.Success).value
        assertEquals(16, kid.length)
        assertTrue(kid.all { it in CardIdConverter.ALPHABET })
    }

    @Test
    fun `toKonamiId converts 0-prefix card`() {
        val result = CardIdConverter.toKonamiId("012E000000114514")
        assertTrue(result is CardIdConverter.Result.Success)
        val kid = (result as CardIdConverter.Result.Success).value
        assertEquals(16, kid.length)
    }

    @Test
    fun `toKonamiId rejects wrong-length UID`() {
        val result = CardIdConverter.toKonamiId("1234")
        assertTrue(result is CardIdConverter.Result.Failure)
    }

    @Test
    fun `toKonamiId rejects non-hex UID`() {
        val result = CardIdConverter.toKonamiId("ZZZZZZZZZZZZZZZZ")
        assertTrue(result is CardIdConverter.Result.Failure)
    }

    @Test
    fun `toKonamiId rejects invalid prefix`() {
        val result = CardIdConverter.toKonamiId("FFFF123456789ABC")
        assertTrue(result is CardIdConverter.Result.Failure)
    }

    // ── toUid ──

    @Test
    fun `toUid roundtrip matches toKonamiId`() {
        val originalIdm = "E004123456789ABC"
        val kidResult = CardIdConverter.toKonamiId(originalIdm)
        assertTrue(kidResult is CardIdConverter.Result.Success)

        val uidResult = CardIdConverter.toUid((kidResult as CardIdConverter.Result.Success).value)
        assertTrue(uidResult is CardIdConverter.Result.Success)
        assertEquals(originalIdm, (uidResult as CardIdConverter.Result.Success).value)
    }

    @Test
    fun `toUid roundtrip for 0-prefix card`() {
        val originalIdm = "012E000000114514"
        val kidResult = CardIdConverter.toKonamiId(originalIdm)
        assertTrue(kidResult is CardIdConverter.Result.Success)

        val uidResult = CardIdConverter.toUid((kidResult as CardIdConverter.Result.Success).value)
        assertTrue(uidResult is CardIdConverter.Result.Success)
        assertEquals(originalIdm, (uidResult as CardIdConverter.Result.Success).value)
    }

    @Test
    fun `toUid rejects wrong-length KID`() {
        val result = CardIdConverter.toUid("SHORT")
        assertTrue(result is CardIdConverter.Result.Failure)
    }

    @Test
    fun `toUid rejects invalid characters`() {
        val result = CardIdConverter.toUid("OOOOOOOOOOOOOOOO")  // O (letter O) not in alphabet
        assertTrue(result is CardIdConverter.Result.Failure)
    }

    @Test
    fun `toUid rejects checksum mismatch`() {
        // Tweak one character of a valid KID to break checksum
        val validKid = (CardIdConverter.toKonamiId("E004123456789ABC") as CardIdConverter.Result.Success).value
        val tampered = "A" + validKid.drop(1)  // change first char
        val result = CardIdConverter.toUid(tampered)
        assertTrue(result is CardIdConverter.Result.Failure)
    }
}
