package com.xnotes.ui

import com.xnotes.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What each Insert tile does and says (mockup §6). */
class InsertCardTest {

    @Test fun everyInsertKindHasItsTile() {
        for (kind in InsertKind.entries) assertEquals(kind, insertKindOf(InsertTile.valueOf(kind.name)))
    }

    @Test fun stickyNoteAndTableAreCallbacksNotKinds() {
        assertNull(insertKindOf(InsertTile.STICKY_NOTE))
        assertNull(insertKindOf(InsertTile.TABLE))
    }

    @Test fun theVoiceTileOffersStopWhileRecording() {
        assertEquals(R.string.insert_menu_voice_recording, insertTileLabel(InsertTile.VOICE, recording = false))
        assertEquals(R.string.insert_menu_stop_recording, insertTileLabel(InsertTile.VOICE, recording = true))
    }

    @Test fun noOtherTileChangesWhileRecording() {
        for (tile in InsertTile.entries) {
            if (tile == InsertTile.VOICE) continue
            assertEquals(tile.name, insertTileLabel(tile, recording = false), insertTileLabel(tile, recording = true))
        }
    }

    @Test fun tilesKeepTheMenusWording() {
        assertEquals(R.string.insert_menu_pdf, insertTileLabel(InsertTile.PDF, false))
        assertEquals(R.string.insert_menu_image_item, insertTileLabel(InsertTile.IMAGE, false))
        assertEquals(R.string.insert_menu_camera, insertTileLabel(InsertTile.CAMERA, false))
        assertEquals(R.string.insert_menu_document_scan, insertTileLabel(InsertTile.SCAN, false))
        assertEquals(R.string.insert_menu_audio_file, insertTileLabel(InsertTile.AUDIO_FILE, false))
        assertEquals(R.string.insert_menu_sticky_note, insertTileLabel(InsertTile.STICKY_NOTE, false))
        assertEquals(R.string.insert_menu_table, insertTileLabel(InsertTile.TABLE, false))
    }
}
