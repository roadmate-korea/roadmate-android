package com.roadmatekorea.android.rules

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 판단 로직(rules/)과 프로필 모델(profile/model/)은 순수 Kotlin이어야 한다.
 * Android, Pleos SDK, 시연 코드에 기대지 않는지 소스를 직접 검사한다.
 */
class RulesPurityTest {

    private val root = File("src/main/java/com/roadmatekorea/android")
    private val forbidden = listOf(
        "import android.",
        "import androidx.",
        "import ai.pleos.",
        "import com.roadmatekorea.android.demo",
    )

    @Test
    fun rulesAndProfileModel_haveNoPlatformOrDemoImports() {
        val files = listOf("rules", "profile/model").flatMap { dir ->
            File(root, dir).walkTopDown().filter { it.extension == "kt" }.toList()
        }
        assertTrue("검사할 파일이 없음: 테스트 작업 디렉터리 확인", files.isNotEmpty())
        val violations = files.flatMap { file ->
            file.readLines().filter { line -> forbidden.any { line.trim().startsWith(it) } }
                .map { "${file.name}: ${it.trim()}" }
        }
        assertTrue("금지된 import:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
