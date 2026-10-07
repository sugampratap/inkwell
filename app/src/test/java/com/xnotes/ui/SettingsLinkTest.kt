package com.xnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsLinkTest {
    @Test fun aRequestedSettingIsTakenOnce() {
        SettingsLink.request(SettingId.KEEP_DELETED)
        assertEquals(SettingId.KEEP_DELETED, SettingsLink.pending)
        assertEquals(SettingId.KEEP_DELETED, SettingsLink.take())
        assertNull(SettingsLink.take())
        assertNull(SettingsLink.pending)
    }

    @Test fun aRequestSettingsTakesInTimeIsKept() {
        SettingsLink.request(SettingId.KEEP_DELETED, now = 1_000L)
        assertEquals(SettingId.KEEP_DELETED, SettingsLink.take(now = 1_000L + SettingsLink.TTL_MS))
    }

    @Test fun aRequestSettingsNeverTookExpires() {
        // Trash asked, but Settings did not open then; opening it much later must not jump to the row.
        SettingsLink.request(SettingId.KEEP_DELETED, now = 1_000L)
        assertNull(SettingsLink.take(now = 1_001L + SettingsLink.TTL_MS))
        assertNull(SettingsLink.pending)
    }
}
