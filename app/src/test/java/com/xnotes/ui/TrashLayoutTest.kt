package com.xnotes.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Trash's header: the search takes the room left, and a line of its own when there is too little. */
class TrashLayoutTest {
    @Test fun aWideHeaderKeepsTheFullSearch() {
        assertEquals(260f, TrashLayout.searchWidth(900.dp)!!.value, 0.01f)
        assertEquals(260f, TrashLayout.searchWidth(620.dp)!!.value, 0.01f)
    }

    @Test fun aMiddlingHeaderShrinksTheSearchToTheRoomLeft() {
        assertEquals(240f, TrashLayout.searchWidth(600.dp)!!.value, 0.01f)
        assertEquals(200f, TrashLayout.searchWidth(560.dp)!!.value, 0.01f)
    }

    @Test fun aNarrowHeaderPutsTheSearchOnItsOwnLine() {
        assertNull(TrashLayout.searchWidth(559.dp))
        assertNull(TrashLayout.searchWidth(360.dp))
    }
}
