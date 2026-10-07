package com.xnotes.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Rgba
import com.xnotes.ui.kit.inAppWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picker's pure rules (r2_selection_colour Frames 4-5, r3_text's wide picker). dp = 1, so the numbers are the mockup's. */
class ColourPickerLogicTest {

    private val window = IntSize(1280, 800)
    private val card = IntSize(340, 573)
    private val wide = IntSize(620, 346)

    // --- the live colour (SC 430) ---

    @Test fun aColourGivesItsOwnHueSaturationAndValue() {
        val hsv = keepHueThroughGreys(PickerHsv(10.0, 0.5, 0.5), Rgba(255, 0, 0))
        assertEquals(0.0, hsv.h, 1e-9)
        assertEquals(1.0, hsv.s, 1e-9)
        assertEquals(1.0, hsv.v, 1e-9)
    }

    @Test fun aGreyKeepsTheHueAndDropsTheSaturation() {
        val hsv = keepHueThroughGreys(PickerHsv(200.0, 0.8, 0.9), Rgba(128, 128, 128))
        assertEquals(200.0, hsv.h, 1e-9)
        assertEquals(0.0, hsv.s, 1e-9)
        assertEquals(128 / 255.0, hsv.v, 1e-9)
    }

    @Test fun blackKeepsHueAndSaturation() {
        val hsv = keepHueThroughGreys(PickerHsv(200.0, 0.8, 0.9), Rgba(0, 0, 0))
        assertEquals(200.0, hsv.h, 1e-9)
        assertEquals(0.8, hsv.s, 1e-9)
        assertEquals(0.0, hsv.v, 1e-9)
    }

    // --- the eyedropper: opaque only (SC 327) ---

    @Test fun anOpaquePixelPassesThrough() {
        assertEquals(Rgba(18, 52, 86), opaqueSample(0xFF123456.toInt(), Rgba(255, 253, 247)))
    }

    @Test fun aClearPixelShowsWhatIsUnderItOpaque() {
        assertEquals(Rgba(255, 253, 247), opaqueSample(0x00123456, Rgba(255, 253, 247, 40)))
    }

    @Test fun aHalfPixelBlendsOverWhatIsUnderIt() {
        // 0x80 = 128: red over white, 128/255 of the way.
        assertEquals(Rgba(255, 127, 127), opaqueSample(0x80FF0000.toInt(), Rgba(255, 255, 255)))
    }

    // --- the eyedropper: where the tap lands ---

    @Test fun aPointOnTheViewMapsToItsPixel() {
        assertEquals(IntOffset(0, 0), viewPixelAt(100, 64, 100, 64, 1180, 736))
        assertEquals(IntOffset(500, 300), viewPixelAt(600, 364, 100, 64, 1180, 736))
        assertEquals(IntOffset(1179, 735), viewPixelAt(1279, 799, 100, 64, 1180, 736))
    }

    @Test fun theRightAndBottomEdgesAreOffTheView() {
        assertNull(viewPixelAt(1280, 300, 100, 64, 1180, 736))
        assertNull(viewPixelAt(600, 800, 100, 64, 1180, 736))
    }

    @Test fun aPointLeftOrAboveTheViewIsOff() {
        assertNull(viewPixelAt(99, 300, 100, 64, 1180, 736))
        assertNull(viewPixelAt(600, 63, 100, 64, 1180, 736))
        assertNull(viewPixelAt(0, 0, 0, 0, 0, 0))
    }

    @Test fun theProbeIsOneViewportPixelOfThePage() {
        val p = eyedropperProbe(Pt(100.0, 50.0), zoom = 2.0)
        assertEquals(99.75, p.left, 1e-9)
        assertEquals(49.75, p.top, 1e-9)
        assertEquals(0.5, p.w, 1e-9)
        assertEquals(0.5, p.h, 1e-9)
    }

    // --- under a toolbar swatch (SC 476-477) ---

    private val swatch = IntRect(500, 20, 544, 80) // centre 522
    private val toolbar = IntRect(300, 14, 980, 74)

    @Test fun underASwatchStarts40BeforeItsCentreAnd10PastTheBar() {
        val p = pickerUnderSwatch(swatch, toolbar, window, card, PopoverSide.BELOW, 1f)
        assertEquals(482, p.x)
        assertEquals(84, p.y)
        assertEquals(40f / 340f, p.originX, 1e-6f)
        assertEquals(0f, p.originY, 1e-6f)
    }

    @Test fun underASwatchStaysInTheWindow() {
        val p = pickerUnderSwatch(IntRect(1228, 20, 1272, 80), IntRect(900, 14, 1276, 74), window, card, PopoverSide.BELOW, 1f)
        assertEquals(1280 - 8 - 340, p.x)
    }

    @Test fun overABottomBarItOpensAbove() {
        val bottomBar = IntRect(300, 726, 980, 786)
        val p = pickerUnderSwatch(IntRect(500, 732, 544, 780), bottomBar, window, card, PopoverSide.ABOVE, 1f)
        assertEquals(726 - 10 - 573, p.y)
        assertEquals(PopoverSide.ABOVE, p.side)
    }

    // --- with no card or bar: under the dot (Settings, a sheet) ---

    @Test fun withNothingToHangBesideItHangsUnderTheDot() {
        val p = pickerUnderAnchor(IntRect(600, 300, 640, 340), window, IntSize(340, 300), 1f)
        assertEquals(592, p.x)
        assertEquals(348, p.y)
    }

    // --- beside its card (SC 478, TO 592) ---

    private val plus = IntRect(380, 300, 408, 328) // centre y 314

    @Test fun besideItsCardItOpens12OffTheCardsEndTopAligned() {
        val p = pickerBesideCard(plus, IntRect(100, 84, 440, 714), window, card, 1f)
        assertEquals(452, p.x)
        assertEquals(84, p.y)
        assertEquals(PopoverSide.END, p.side)
        assertEquals(0f, p.originX, 1e-6f)
        assertEquals((314f - 84f) / 573f, p.originY, 1e-6f)
    }

    @Test fun withNoRoomAfterTheCardItOpensBeforeIt() {
        val p = pickerBesideCard(IntRect(1180, 300, 1208, 328), IntRect(900, 84, 1240, 714), window, card, 1f)
        assertEquals(900 - 12 - 340, p.x)
        assertEquals(PopoverSide.START, p.side)
        assertEquals(1f, p.originX, 1e-6f)
    }

    @Test fun withNoRoomEitherSideItIsClampedInsideTheWindow() {
        val p = pickerBesideCard(plus, IntRect(200, 84, 540, 714), IntSize(700, 800), card, 1f)
        assertEquals(700 - 8 - 340, p.x)
    }

    @Test fun aLowCardStillKeepsThePickerOnScreen() {
        val p = pickerBesideCard(IntRect(380, 600, 408, 628), IntRect(100, 400, 440, 760), window, card, 1f)
        assertEquals(800 - 8 - 573, p.y)
    }

    // --- the wide picker above the format pill (TX 892-895) ---

    private val pill = IntRect(300, 700, 980, 764)
    private val button = IntRect(500, 710, 544, 754) // centre 522

    @Test fun aboveThePillItIsCentredOnItsButton() {
        val p = pickerAbovePill(button, pill, null, window, wide, 1f)
        assertEquals(212, p.x)
        assertEquals(344, p.y)
        assertEquals(0.5f, p.originX, 1e-6f)
        assertEquals(1f, p.originY, 1e-6f)
        assertEquals(PopoverSide.ABOVE, p.side)
    }

    @Test fun whenCentredWouldCoverTheSelectionItStepsBesideIt() {
        val p = pickerAbovePill(button, pill, IntRect(150, 300, 450, 400), window, wide, 1f)
        assertEquals(466, p.x)
        assertEquals(56f / 620f, p.originX, 1e-6f)
    }

    @Test fun whenEverySpotCoversItStaysCentred() {
        assertEquals(212, pickerAbovePill(button, pill, IntRect(150, 300, 1100, 400), window, wide, 1f).x)
    }

    @Test fun aSelectionWellAboveThePickerIsNotInTheWay() {
        assertEquals(212, pickerAbovePill(button, pill, IntRect(150, 100, 450, 300), window, wide, 1f).x)
    }

    // --- beside the selection bar: Change style's + (SC 606-608) ---

    private val bar = IntRect(200, 150, 800, 214)

    @Test fun besideTheBarItOpens12OffItsEndAtTheGivenTop() {
        val p = pickerBesideBar(IntRect(380, 286, 408, 314), bar, 118, window, card, 1f)
        assertEquals(812, p.x)
        assertEquals(118, p.y)
        assertEquals(0f, p.originX, 1e-6f)
        assertEquals(182f / 573f, p.originY, 1e-6f)
    }

    @Test fun besideTheBarItStopsAtTheWindowsEnd() {
        assertEquals(932, pickerBesideBar(IntRect(380, 286, 408, 314), IntRect(500, 150, 1100, 214), 118, window, card, 1f).x)
    }

    @Test fun itGrowsFromThePlusButNeverFromItsVeryTop() {
        val p = pickerBesideBar(IntRect(380, 86, 408, 114), bar, 118, window, card, 1f)
        assertEquals(20f / 573f, p.originY, 1e-6f)
    }

    @Test fun withNoRoomPastTheBarsEndItOpensBesideTheChangeStyleCard() {
        // A right-hand pane: the bar ends at 1240, so off its end would be clamped back over the card at 800..1140.
        val styleCard = IntRect(800, 230, 1140, 600)
        val p = pickerBesideBar(IntRect(1000, 300, 1028, 328), IntRect(700, 150, 1240, 214), 118, window, card, 1f, styleCard)
        assertEquals(pickerBesideCard(IntRect(1000, 300, 1028, 328), styleCard, window, card, 1f), p)
        assertEquals(800 - 12 - 340, p.x)
        assertEquals(PopoverSide.START, p.side)
    }

    @Test fun withRoomPastTheBarsEndTheCardIsNotConsulted() {
        val styleCard = IntRect(200, 230, 540, 600)
        assertEquals(812, pickerBesideBar(IntRect(380, 286, 408, 314), bar, 118, window, card, 1f, styleCard).x)
    }

    // --- gate fixes: window px, a dot with no room under it, a Change style card past the bar, dark-look rings ---

    @Test fun aCardInItsOwnPopupIsMeasuredInTheAppWindowSoThePickerOpensBesideIt() {
        // The card's Popup window starts at its corner, so window bounds read 0,0. On screen it is at 100,84, in an app
        // window that starts 0,0 (full screen) or 40,30 (a moved window): the card comes back where the picker is placed.
        val onScreen = IntRect(140, 114, 480, 744)
        val card = inAppWindow(onScreen, IntOffset(40, 30))
        assertEquals(IntRect(100, 84, 440, 714), card)
        val plus = inAppWindow(IntRect(420, 330, 448, 358), IntOffset(40, 30))
        val p = pickerBesideCard(plus, card, window, this.card, 1f)
        assertEquals(452, p.x)
        assertEquals(84, p.y)
        assertTrue("never over its card", p.x >= card.right)
        assertEquals(onScreen, inAppWindow(onScreen, IntOffset.Zero))
    }

    @Test fun aDotWithNoRoomUnderOrOverItOpensBesideItNotOverIt() {
        // Halfway down a sheet: 573 fits neither under the dot (380 + 8 + 573 > 792) nor over it (352 - 8 - 573 < 8).
        val dot = IntRect(600, 352, 630, 382)
        val p = pickerUnderAnchor(dot, window, card, 1f)
        assertEquals(642, p.x)
        assertEquals(PopoverSide.END, p.side)
        assertTrue("clear of the dot", p.x >= dot.right || p.x + card.width <= dot.left)
    }

    @Test fun aDotWithRoomUnderItStillHangsUnderIt() {
        val p = pickerUnderAnchor(IntRect(600, 60, 640, 100), window, card, 1f)
        assertEquals(592, p.x)
        assertEquals(108, p.y)
        assertEquals(PopoverSide.BELOW, p.side)
    }

    @Test fun whenOffTheBarsEndWouldStillCoverTheChangeStyleCardItOpensBesideTheCard() {
        // A short bar (200..600): 12 off its end is 612, but the Change style card reaches 420..760.
        val styleCard = IntRect(420, 230, 760, 600)
        val p = pickerBesideBar(IntRect(700, 300, 728, 328), IntRect(200, 150, 600, 214), 118, window, card, 1f, styleCard)
        assertEquals(pickerBesideCard(IntRect(700, 300, 728, 328), styleCard, window, card, 1f), p)
        assertEquals(772, p.x)
    }

    @Test fun inTheDarkLooksANavyFavouriteGetsALightRingAndABrightOneDoesNot() {
        val darkCard = 0.017f // about #262626
        assertTrue(needsLightRing(Rgba(0x1F, 0x2A, 0x44), darkCard, dark = true))
        assertTrue(needsLightRing(Rgba(0, 0, 0), 0f, dark = true))
        assertFalse(needsLightRing(Rgba(0xFA, 0xCC, 0x15), darkCard, dark = true))
        assertFalse("a light look keeps its own ring", needsLightRing(Rgba(0xFF, 0xFF, 0xFF), 1f, dark = false))
    }
}
