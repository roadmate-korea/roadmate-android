package com.roadmatekorea.android.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.take

/**
 * 차량 속도 → [RawSignal.Vehicle.Speed].
 *
 * Pleos Vehicle SDK에는 속도 API가 없어 Android Automotive 표준 `CarPropertyManager`의
 * `PERF_VEHICLE_SPEED`(m/s)를 읽는다. 권한은 `android.car.permission.CAR_SPEED`(사용자 허용이 필요한 권한).
 *
 * 속도는 있으면 좋은 정보다. 읽지 못해도(권한 거부, 차량 서비스 없음) 다른 안내는 그대로 나가야 하므로
 * 실패해도 Unavailable 신호를 내지 않고 조용히 끝낸다(기어 오류처럼 화면을 잠그지 않는다).
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
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private fun speed(): Flow<RawSignal> = callbackFlow {
        val car = try {
            Car.createCar(appContext)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Car service unavailable", e)
            null
        } catch (e: NoClassDefFoundError) {
            Log.w(TAG, "android.car library missing", e)
            null
        }
        val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (car == null || manager == null) {
            car?.disconnect()
            close()
            return@callbackFlow
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
                (value.value as? Float)?.let { trySend(RawSignal.Vehicle.Speed(it)) }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "speed error event")
            }
        }
        val registered = runCatching {
            manager.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        }.onFailure { e -> Log.w(TAG, "speed register failed", e) }.getOrDefault(false)
        if (!registered) Log.w(TAG, "speed not registered")
        awaitClose {
            if (registered) runCatching { manager.unregisterCallback(callback) }
            runCatching { car.disconnect() }
        }
    }
        // 센서는 초당 여러 번 값을 준다. 판단 로직에는 km/h 정수가 바뀔 때만 넘긴다.
        .distinctUntilChanged { a, b -> (a as RawSignal.Vehicle.Speed).kmh == (b as RawSignal.Vehicle.Speed).kmh }
        .buffer(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "RoadMateSpeed"
    }
}
