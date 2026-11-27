package com.hypai.fion.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroSplitTest {
    @Test
    fun `tabletop horizontal fold places hero above hinge and stats below hinge`() {
        val normal = heroSplit(minHeight = 800, topHeight = 48, statsHeight = 360, minHeroHeight = 108, gap = 10)
        assertEquals(HeroSplit(heroHeight = 372, statsY = 440, totalHeight = 800), normal)

        val tabletop = heroSplit(minHeight = 800, topHeight = 48, statsHeight = 360, minHeroHeight = 108, gap = 10, foldTop = 380, foldBottom = 400)
        assertEquals(HeroSplit(heroHeight = 322, statsY = 440, totalHeight = 800), tabletop)

        val tallStats = heroSplit(minHeight = 800, topHeight = 48, statsHeight = 450, minHeroHeight = 108, gap = 10, foldTop = 380, foldBottom = 400)
        assertEquals(HeroSplit(heroHeight = 322, statsY = 400, totalHeight = 850), tallStats)
    }
}
