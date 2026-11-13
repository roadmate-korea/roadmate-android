package com.roadmatekorea.android.vehicle

import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 기어 → [RawSignal.Vehicle.Gear], 읽을 수 없으면 [RawSignal.Vehicle.Unavailable](화면을 잠근다).
 *
 * Android Automotive 표준 `CarPropertyManager`의 `GEAR_SELECTION`을 읽는다([carPropertyEvents]).
 * Pleos 공개 문서의 Vehicle SDK에는 기어 API가 없고, SDK API 정책이 기어 같은 표준 차량 정보는 AAOS Car API로 읽게 한다.
 * 권한은 `android.car.permission.CAR_POWERTRAIN`(설치 때 주는 권한).
 *
 * 앱은 이 Flow를 [com.roadmatekorea.android.drive.DriveSignalHub]에서 프로세스 수명 동안 한 번만 수집한다.
 */
class VehicleGearSource(context: Context) : DriveSignalSource {

    private val appContext = context.applicationContext

    override fun signals(): Flow<RawSignal> =
        carPropertyEvents(appContext, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE, TAG)
            .map { event ->
                when (event) {
                    is CarPropertyEvent.Value -> {
                        val gear = event.value.value as? Int
                        if (event.value.status == CarPropertyValue.STATUS_AVAILABLE && gear != null) {
                            RawSignal.Vehicle.Gear(gear)
                        } else {
                            RawSignal.Vehicle.Unavailable("gear: status ${event.value.status}")
                        }
                    }
                    is CarPropertyEvent.Unavailable -> RawSignal.Vehicle.Unavailable("gear: ${event.reason}")
                }
            }

    private companion object {
        const val TAG = "RoadMateGear"
    }
}
