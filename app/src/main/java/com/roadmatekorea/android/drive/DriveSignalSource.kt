package com.roadmatekorea.android.drive

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * 주행 신호 입력원. 실제 SDK([RealDriveSignalSource])와 시연 모드가 이 인터페이스를 구현한다.
 * 수집을 시작하면 SDK에 붙고, 수집을 멈추면 리스너를 해제하고 SDK를 놓는다.
 */
fun interface DriveSignalSource {
    fun signals(): Flow<RawSignal>
}

/** 실제 차량(Vehicle, 차량 속도)과 내비(NaviHelper) 신호를 합친다. */
class RealDriveSignalSource(
    private vararg val sources: DriveSignalSource,
) : DriveSignalSource {
    override fun signals(): Flow<RawSignal> = merge(*sources.map { it.signals() }.toTypedArray())
}
