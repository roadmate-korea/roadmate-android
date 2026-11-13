package com.roadmatekorea.android.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.util.Log
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.BufferOverflow

/**
 * Vehicle SDK의 방향지시등 → [RawSignal.Vehicle.TurnSignal].
 *
 * 수집을 시작하면 `initialize` → capability 확인 → 리스너 등록 → 현재 값 한 번 읽기,
 * 수집을 멈추면 리스너 해제 → `release`. 권한: `CAR_LIGHTS`.
 * 기어는 [VehicleGearSource]가 읽는다(공개 문서의 Vehicle SDK에 기어 API가 없다).
 * 방향지시등은 안내에 쓰는 신호라 읽지 못해도 화면을 잠그지 않는다(로그만 남긴다).
 *
 * 주의: Vehicle SDK 2.3.2의 `release()`는 `initialize()`가 시작한 비동기 연결 작업을 취소하지 않는다.
 * 연결 중에 수집을 멈추면 해제 뒤 서비스에 다시 붙을 수 있으므로, 앱은 이 Flow를 [DriveSignalHub]에서
 * 프로세스 수명 동안 한 번만 수집하고 중간에 끊지 않는다.
 */
class VehicleSignalSource(context: Context) : DriveSignalSource {

    private val appContext = context.applicationContext

    override fun signals(): Flow<RawSignal> = callbackFlow {
        val vehicle = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Vehicle initialize failed", e)
            close()
            return@callbackFlow
        }

        val turnListener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                value?.let { trySend(RawSignal.Vehicle.TurnSignal(it)) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "turn signal listener failed", e)
            }
        }
        // 방향지시등 등록(capability 응답 스레드)과 종료(awaitClose)를 같은 잠금으로 묶는다.
        // 종료 뒤에는 등록하지 않고, 등록이 끝났으면 종료 때 반드시 해제한다.
        val lock = Any()
        var closed = false
        var turnRegistered = false

        runCatching {
            val turn = vehicle.getTurnSignal()
            // capability로 읽기가 되는지 확인한 뒤에만 등록한다
            turn.checkTurnSignalStateCapabilities(
                { config ->
                    val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
                    if (!readable) {
                        Log.w(TAG, "turn signal not readable: ${config.accessType}")
                    } else {
                        synchronized(lock) {
                            if (!closed) {
                                runCatching {
                                    turn.registerTurnSignalState(turnListener)
                                    turnRegistered = true
                                }.onFailure { e -> Log.w(TAG, "turn signal register failed", e) }
                            }
                        }
                        if (turnRegistered) {
                            turn.getCurrentTurnSignalState(
                                { value -> value?.let { trySend(RawSignal.Vehicle.TurnSignal(it)) } },
                                { e -> Log.w(TAG, "current turn signal failed", e) },
                            )
                        }
                    }
                },
                { e -> Log.w(TAG, "turn signal capability failed", e) },
            )
        }.onFailure { e -> Log.w(TAG, "turn signal check failed", e) }

        awaitClose {
            synchronized(lock) {
                closed = true
                if (turnRegistered) runCatching { vehicle.getTurnSignal().unregisterTurnSignalState(turnListener) }
            }
            runCatching { vehicle.release() }
        }
    }.buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "RoadMateVehicle"
    }
}
