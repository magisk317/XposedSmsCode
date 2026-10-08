package io.github.magisk317.smscode.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsViewModelPolicyTest {

    @Test
    fun resolvePreferredUpdateEvent_usesPlayOnlyForPlayFlavor() {
        assertEquals(SettingsEvent.StartPlayUpdate, resolvePreferredUpdateEvent(isPlayFlavor = true))
        assertEquals(null, resolvePreferredUpdateEvent(isPlayFlavor = false))
    }
}
