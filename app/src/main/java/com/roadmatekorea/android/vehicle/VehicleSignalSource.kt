package com.roadmatekorea.android.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.BufferOverflow

/**
 * Vehicle SDK의 기어와 방향지시등 → [RawSignal].
 *
 * 수집을 시작하면 `initialize` → 리스너 등록 → 현재 값 한 번 읽기,
 * 수집을 멈추면 리스너 해제 → `release`. 권한: 기어 `CAR_DRIVE`, 방향지시등 `CAR_LIGHTS`.
 */
class VehicleSignalSource(context: Context) : DriveSignalSource {

    private val appContext = context.applicationContext

    override fun signals(): Flow<RawSignal> = callbackFlow {
        val vehicle = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Vehicle initialize failed", e)
            trySend(RawSignal.Vehicle.Unavailable(e.javaClass.simpleName))
            close()
            return@callbackFlow
        }

        val gearListener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                gear?.let { trySend(RawSignal.Vehicle.Gear(it)) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "gear listener failed", e)
                trySend(RawSignal.Vehicle.Unavailable("gear: ${e.javaClass.simpleName}"))
            }
        }
        val turnListener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                value?.let { trySend(RawSignal.Vehicle.TurnSignal(it)) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "turn signal listener failed", e)
            }
        }
        val turnRegistered = AtomicBoolean(false)

        runCatching {
            val gear = vehicle.getGear()
            gear.registerGearSelection(gearListener)
            gear.getCurrentGearSelectionState(
                { gear -> gear?.let { trySend(RawSignal.Vehicle.Gear(it)) } },
                { e -> Log.w(TAG, "current gear failed", e) },
            )
        }.onFailure { e ->
            Log.w(TAG, "gear register failed", e)
            trySend(RawSignal.Vehicle.Unavailable("gear: ${e.javaClass.simpleName}"))
        }

        runCatching {
            val turn = vehicle.getTurnSignal()
            // capability로 읽기가 되는지 확인한 뒤에만 등록한다
            turn.checkTurnSignalStateCapabilities(
                { config ->
                    val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
                    if (readable && !isClosedForSend) {
                        runCatching {
                            turn.registerTurnSignalState(turnListener)
                            turnRegistered.set(true)
                            turn.getCurrentTurnSignalState(
                                { value -> value?.let { trySend(RawSignal.Vehicle.TurnSignal(it)) } },
                                { e -> Log.w(TAG, "current turn signal failed", e) },
                            )
                        }.onFailure { e -> Log.w(TAG, "turn signal register failed", e) }
                    } else {
                        Log.w(TAG, "turn signal not readable: ${config.accessType}")
                    }
                },
                { e -> Log.w(TAG, "turn signal capability failed", e) },
            )
        }.onFailure { e -> Log.w(TAG, "turn signal check failed", e) }

        awaitClose {
            runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
            if (turnRegistered.get()) runCatching { vehicle.getTurnSignal().unregisterTurnSignalState(turnListener) }
            runCatching { vehicle.release() }
        }
    }.buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "RoadMateVehicle"
    }
}
