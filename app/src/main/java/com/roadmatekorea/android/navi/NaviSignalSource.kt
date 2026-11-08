package com.roadmatekorea.android.navi

import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * NaviHelper 주행 안내 이벤트 → [RawSignal].
 * 수집을 시작하면 `initialize` 성공 뒤 리스너 등록, 수집을 멈추면 리스너 해제 → `release`.
 * 위치나 경로 원본은 앱 밖으로 보내지 않는다.
 * Vehicle과 마찬가지로 [com.roadmatekorea.android.drive.DriveSignalHub]에서 프로세스 수명 동안 한 번만 수집한다.
 */
class NaviSignalSource(context: Context) : DriveSignalSource {

    private val appContext = context.applicationContext

    override fun signals(): Flow<RawSignal> = callbackFlow {
        val navi = NaviHelper(appContext)
        val listener = object : NaviHelperEventListener {
            override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
                trySend(RawSignal.Navi.SpeedLimit(speedLimit))
            }

            override fun onCautionPointInfo(points: List<CautionPoint>) {
                trySend(RawSignal.Navi.CautionPoints(points))
            }

            override fun onCameraAlert(cameras: List<CameraAlert>) {
                trySend(RawSignal.Navi.CameraAlerts(cameras))
            }

            override fun onTBTInfo(turns: List<TBTInfo>) {
                trySend(RawSignal.Navi.Tbt(turns))
            }

            override fun onDestinationArrived(info: DestinationArrivedInfo) {
                trySend(RawSignal.Navi.Arrived)
            }
        }
        // 리스너 등록(initialize 성공 콜백)과 종료(awaitClose)를 같은 잠금으로 묶는다
        val lock = Any()
        var closed = false
        var added = false

        try {
            navi.initialize(
                {
                    synchronized(lock) {
                        if (!closed) {
                            runCatching {
                                navi.addListener(listener)
                                added = true
                            }.onFailure { e ->
                                Log.w(TAG, "addListener failed", e)
                                trySend(RawSignal.Navi.Unavailable(e.javaClass.simpleName))
                            }
                        }
                    }
                },
                { e ->
                    Log.w(TAG, "NaviHelper initialize failed", e)
                    trySend(RawSignal.Navi.Unavailable(e.javaClass.simpleName))
                },
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "NaviHelper initialize threw", e)
            trySend(RawSignal.Navi.Unavailable(e.javaClass.simpleName))
        }

        awaitClose {
            synchronized(lock) {
                closed = true
                if (added) runCatching { navi.removeListener(listener) }
            }
            runCatching { navi.release() }
        }
    }.buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "RoadMateNavi"
    }
}
