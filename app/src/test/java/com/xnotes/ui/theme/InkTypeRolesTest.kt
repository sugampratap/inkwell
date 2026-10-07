package com.xnotes.ui.theme

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Part 9 swaps the inline `InkType.meta.copy(fontSize = 12.5.sp …)` copies for roles; nothing may render differently. */
class InkTypeRolesTest {

    @Test
    fun hintIsMetaAtTwelveAndAHalf() {
        assertEquals(InkType.meta.copy(fontSize = 12.5.sp), InkType.hint)
        assertEquals(InkType.meta.copy(fontSize = 12.5.sp, lineHeight = 18.sp), InkType.hint)
    }

    @Test
    fun captionIsMetaAtTwelveAndAHalfOnASeventeenLine() {
        assertEquals(InkType.meta.copy(fontSize = 12.5.sp, lineHeight = 17.sp), InkType.caption)
    }
}
