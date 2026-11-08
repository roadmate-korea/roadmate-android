package com.roadmatekorea.android.demo

import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * [DemoRoute]를 재생하는 신호 입력원. 실제 SDK 입력원과 같은 [DriveSignalSource]다.
 *
 * 각 단계 전에 [awaitIdle]로 앞 안내가 끝나기를 기다린다. 화면 녹화에서 안내가 겹치거나 유효 시간이 지나
 * 버려지지 않게 하려는 재생 속도 조절일 뿐, 어떤 안내를 낼지는 판단 로직이 정한다.
 */
class DemoSignalSource(
    private val steps: List<DemoStep> = DemoRoute.DEFAULT,
    private val awaitIdle: suspend () -> Unit = {},
) : DriveSignalSource {

    override fun signals(): Flow<RawSignal> = flow {
        steps.forEach { step ->
            delay(step.delayMs)
            awaitIdle()
            step.signals.forEach { emit(it) }
        }
    }
}
