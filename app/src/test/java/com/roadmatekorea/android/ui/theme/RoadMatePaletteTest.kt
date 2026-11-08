package com.roadmatekorea.android.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/** Pleos 디자인 가이드: 글자와 아이콘은 바탕 대비 4.5:1 이상. 실제로 쓰는 조합을 두 모드 모두 고정한다. */
class RoadMatePaletteTest {

    private val pairs: List<Triple<String, (RoadMatePalette) -> Long, (RoadMatePalette) -> Long>> = listOf(
        Triple("본문/화면", { it.ink }, { it.canvas }),
        Triple("본문/카드", { it.ink }, { it.surface }),
        Triple("보조/화면", { it.inkSecondary }, { it.canvas }),
        Triple("보조/카드", { it.inkSecondary }, { it.surface }),
        Triple("흐린 글자/화면", { it.inkTertiary }, { it.canvas }),
        Triple("흐린 글자/카드", { it.inkTertiary }, { it.surface }),
        Triple("본문/보조 칸", { it.ink }, { it.sunken }),
        Triple("먹색 바탕 글자", { it.onInk }, { it.ink }),
        Triple("노랑 위 글자", { it.onCenterline }, { it.centerline }),
        Triple("빨간 버튼 글자", { it.onDanger }, { it.danger }),
        Triple("안전 경고 머리글", { it.danger }, { it.dangerSurface }),
        Triple("안전 경고 본문", { it.ink }, { it.dangerSurface }),
        Triple("표지판 숫자", { it.onCenterline }, { it.paint }),
    )

    @Test
    fun usedPairs_meetPleosContrast_inBothModes() {
        for ((mode, palette) in listOf("Light" to RoadMatePalette.Light, "Dark" to RoadMatePalette.Dark)) {
            for ((name, fg, bg) in pairs) {
                val ratio = RoadMatePalette.contrast(fg(palette), bg(palette))
                assertTrue("$mode $name 대비 ${"%.2f".format(ratio)}", ratio >= 4.5)
            }
        }
    }

    @Test
    fun centerlineYellow_isTheSameInBothModes() {
        // 대표 색은 하나다(노란 중앙선). 모드에 따라 바뀌지 않는다.
        assertTrue(RoadMatePalette.Light.centerline == RoadMatePalette.Dark.centerline)
    }
}
