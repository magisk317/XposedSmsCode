package io.github.magisk317.smscode.receiver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KillSelfControlReceiverTest {
    @Test
    fun normalizeDelayMs_clampsExtremeValues() {
        assertEquals(0L, KillSelfControlReceiver.normalizeDelayMs(-1L))
        assertEquals(80L, KillSelfControlReceiver.normalizeDelayMs(80L))
        assertEquals(5_000L, KillSelfControlReceiver.normalizeDelayMs(60_000L))
    }
}
