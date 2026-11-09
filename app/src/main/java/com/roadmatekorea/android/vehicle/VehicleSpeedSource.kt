package com.roadmatekorea.android.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.take

/**
 * 차량 속도 → [RawSignal.Vehicle.Speed], 읽을 수 없으면 [RawSignal.Vehicle.SpeedUnavailable].
 *
 * Pleos Vehicle SDK에는 속도 API가 없어 Android Automotive 표준 `CarPropertyManager`의
 * `PERF_VEHICLE_SPEED`(m/s)를 읽는다. 권한은 `android.car.permission.CAR_SPEED`(사용자 허용이 필요한 권한).
 *
 * 속도는 있으면 좋은 정보다. 읽지 못해도(권한 거부, 차량 서비스 없음·끊김) 다른 안내는 그대로 나가야 한다.
 * - 차량 서비스에는 연결 상태 리스너를 붙여 연결한다. 리스너 없이 연결하면 서비스가 끊길 때 플랫폼이 앱 프로세스를 끝낸다
 *   (AOSP Car.java finishClient). 끊기면 속도를 지우고, 다시 연결되면 다시 등록한다.
 * - 기어 오류와 달리 화면을 잠그지 않는다.
 *
 * 센서는 초당 여러 번 값을 준다. km/h 정수가 바뀌거나 마지막으로 보낸 지 [HEARTBEAT_MS]가 지나면 보낸다.
 * 같은 속도라도 주기적으로 보내야 엔진이 쿨다운이 끝난 뒤 과속을 다시 판단할 수 있다.
 *
 * @param permissionGranted 권한이 허용되면 true가 되는 값. 허용될 때까지 기다렸다가 붙는다.
 */
class VehicleSpeedSource(
    context: Context,
    private val permissionGranted: StateFlow<Boolean>,
) : DriveSignalSource {

    private val appContext = context.applicationContext

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun signals(): Flow<RawSignal> =
        permissionGranted.filter { it }.take(1).flatMapLatest { if (supported()) speed() else emptyFlow() }

    private fun supported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private fun speed(): Flow<RawSignal> = callbackFlow {
        val main = Handler(Looper.getMainLooper())
        var lastKmh: Int? = null
        var lastSentAt = 0L

        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val mps = value.value as? Float
                if (value.status != CarPropertyValue.STATUS_AVAILABLE || mps == null) {
                    if (lastKmh != null) {
                        lastKmh = null
                        trySend(RawSignal.Vehicle.SpeedUnavailable)
                    }
                    return
                }
                val signal = RawSignal.Vehicle.Speed(mps)
                val now = SystemClock.elapsedRealtime()
                if (signal.kmh != lastKmh || now - lastSentAt >= HEARTBEAT_MS) {
                    lastKmh = signal.kmh
                    lastSentAt = now
                    trySend(signal)
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "speed error event")
                lastKmh = null
                trySend(RawSignal.Vehicle.SpeedUnavailable)
            }
        }

        // 연결 상태 콜백과 종료(awaitClose)는 모두 메인 스레드에서 돈다
        var manager: CarPropertyManager? = null
        var closed = false

        fun unregister() {
            manager?.let { m -> runCatching { m.unregisterCallback(callback) } }
            manager = null
        }

        val car = try {
            Car.createCar(appContext, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready ->
                if (closed) return@createCar
                unregister()
                if (!ready) {
                    Log.w(TAG, "car service disconnected")
                    lastKmh = null
                    trySend(RawSignal.Vehicle.SpeedUnavailable)
                    return@createCar
                }
                val m = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return@createCar
                val ok = runCatching {
                    m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
                }.onFailure { e -> Log.w(TAG, "speed register failed", e) }.getOrDefault(false)
                if (ok) manager = m else Log.w(TAG, "speed not registered")
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Car service unavailable", e)
            null
        } catch (e: NoClassDefFoundError) {
            Log.w(TAG, "android.car library missing", e)
            null
        }
        if (car == null) {
            close()
            return@callbackFlow
        }
        awaitClose {
            main.post {
                closed = true
                unregister()
                runCatching { car.disconnect() }
            }
        }
    }.buffer(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "RoadMateSpeed"

        /** 같은 속도라도 이 간격으로 다시 보낸다(쿨다운 뒤 재경고 판단용). */
        const val HEARTBEAT_MS = 1_000L
    }
}
