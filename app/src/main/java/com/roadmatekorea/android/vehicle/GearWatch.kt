package com.roadmatekorea.android.vehicle

import com.roadmatekorea.android.drive.RawSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 기어를 한 번 읽은 결과(변경 콜백이나 주기적 다시 읽기). */
sealed interface GearReading {
    /** `android.car.VehicleGear` 값. */
    data class Value(val gear: Int) : GearReading

    data class Unavailable(val reason: String) : GearReading
}

/**
 * 기어 읽기 결과 → 화면 잠금에 쓰는 [RawSignal].
 *
 * - 읽기 결과가 [timeoutMs] 동안 하나도 오지 않으면(차량 서비스가 멈춰 다시 읽기도 막힘, 연결 끊김) 기어를 확인할 수 없는 것으로
 *   보고 [RawSignal.Vehicle.Unavailable]을 보낸다. 마지막 P로 잠금이 풀린 채 남지 않게 한다. 정상이면 주기적 다시 읽기가
 *   계속 오므로 오래 주차해 기어가 바뀌지 않아도 잠그지 않는다.
 * - 같은 상태가 반복되면 한 번만 보낸다(주기적 다시 읽기마다 기어 변경 이벤트가 나가지 않게). 읽기 불가의 이유가 달라도 같은 상태로 본다.
 */
fun Flow<GearReading>.toGearSignals(timeoutMs: Long): Flow<RawSignal> = channelFlow {
    val lock = Mutex()
    var last: RawSignal? = null
    var watchdog: Job? = null

    suspend fun emitIfChanged(signal: RawSignal) = lock.withLock {
        val same = when (val previous = last) {
            null -> false
            is RawSignal.Vehicle.Unavailable -> signal is RawSignal.Vehicle.Unavailable
            else -> previous == signal
        }
        if (!same) {
            last = signal
            send(signal)
        }
    }

    fun arm() {
        watchdog?.cancel()
        watchdog = launch {
            delay(timeoutMs)
            emitIfChanged(RawSignal.Vehicle.Unavailable("gear: not confirmed for ${timeoutMs}ms"))
        }
    }

    arm()
    collect { reading ->
        arm()
        emitIfChanged(
            when (reading) {
                is GearReading.Value -> RawSignal.Vehicle.Gear(reading.gear)
                is GearReading.Unavailable -> RawSignal.Vehicle.Unavailable("gear: ${reading.reason}")
            },
        )
    }
}
