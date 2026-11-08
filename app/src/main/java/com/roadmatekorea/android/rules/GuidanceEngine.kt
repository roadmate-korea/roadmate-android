package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.DriverProfile
import kotlin.math.roundToInt

/**
 * 주행 이벤트를 받아 낼 안내 목록을 정한다. 주행 세션 하나에 엔진 하나를 쓴다.
 *
 * 실제 SDK와 시연 모드가 같은 엔진을 탄다. 시연용 분기를 여기 넣지 않는다.
 * 스레드 안전하지 않으므로 이벤트는 한 곳에서 순서대로 넣는다.
 */
class GuidanceEngine(
    val scope: GuidanceScope,
    private val clock: Clock = Clock.SYSTEM,
    private val cooldown: CooldownPolicy = CooldownPolicy(clock),
) {
    constructor(profile: DriverProfile, clock: Clock = Clock.SYSTEM) :
        this(GuidancePolicy.scopeFor(profile), clock)

    private var gear = Gear.UNKNOWN
    private var turnSignal = TurnSignal.OFF
    private var greeted = false
    private var currentSpeedLimit: Int? = null

    /** 비어 있으면 경로 안내 정보가 없는 것으로 본다. */
    private var turns: List<TurnInstruction> = emptyList()
    private var rightTurnNear = false
    private var leftTurnNear = false

    private val announcedCautionIds = mutableSetOf<String>()
    private val announcedCameraIds = mutableSetOf<String>()
    private var schoolZone: SchoolZoneContext? = null

    private val isParked get() = gear == Gear.PARK

    fun onEvent(event: DriveEvent): List<Guidance> = when (event) {
        DriveEvent.SessionStarted -> greet()
        is DriveEvent.GearChanged -> onGear(event.gear)
        is DriveEvent.TurnSignalChanged -> onTurnSignal(event.signal)
        is DriveEvent.SpeedLimitChanged -> onSpeedLimit(event.kmh)
        is DriveEvent.CautionPointsUpdated -> onCautionPoints(event.points)
        is DriveEvent.CameraAlertsUpdated -> onCameras(event.cameras)
        is DriveEvent.TurnInstructionsUpdated -> onTurns(event.turns)
        DriveEvent.DestinationArrived -> emit(GuidanceId.ARRIVED)
    }

    // ---- 시작 안내 ----

    private fun greet(): List<Guidance> {
        if (greeted) return emptyList()
        greeted = true
        val welcome = when {
            scope.rightSideAdaptation -> GuidanceId.WELCOME_RIGHT_SIDE
            scope.detail == DetailLevel.DETAILED -> GuidanceId.WELCOME_FIRST_TIME
            else -> GuidanceId.WELCOME_EXPERIENCED
        }
        return buildList {
            add(Guidance(welcome))
            if (scope.leftHandDriveAdaptation) add(Guidance(GuidanceId.LHD_INTRO))
        }
    }

    private fun onGear(newGear: Gear): List<Guidance> {
        gear = newGear
        // 주행 시작(P가 아닌 기어로 처음 바뀜)도 시작 안내 시점이다.
        return if (newGear != Gear.PARK && newGear != Gear.UNKNOWN) greet() else emptyList()
    }

    // ---- 회전, 차선 변경 ----

    private fun onTurnSignal(signal: TurnSignal): List<Guidance> {
        val previous = turnSignal
        turnSignal = signal
        if (signal == TurnSignal.OFF || signal == previous || isParked) return emptyList()

        val direction = if (signal == TurnSignal.LEFT) TurnDirection.LEFT else TurnDirection.RIGHT
        // 경로 안내 중인데 같은 방향 회전이 가까이 없으면 차선 변경으로 본다.
        // 경로 안내 정보가 없으면 회전인지 알 수 없으므로 회전으로 본다.
        val isLaneChange = turns.isNotEmpty() && !hasNearTurn(direction)
        return if (isLaneChange) laneChange() else turn(direction)
    }

    private fun onTurns(newTurns: List<TurnInstruction>): List<Guidance> {
        turns = newTurns
        val rightNear = hasNearTurn(TurnDirection.RIGHT)
        val leftNear = hasNearTurn(TurnDirection.LEFT)
        // 같은 회전이 다가오며 계속 갱신되므로, 가까워지는 순간에만 낸다.
        val enteredRight = rightNear && !rightTurnNear
        val enteredLeft = leftNear && !leftTurnNear
        rightTurnNear = rightNear
        leftTurnNear = leftNear
        if (isParked) return emptyList()
        return buildList {
            if (enteredRight) addAll(turn(TurnDirection.RIGHT))
            if (enteredLeft) addAll(turn(TurnDirection.LEFT))
        }
    }

    private fun hasNearTurn(direction: TurnDirection) =
        turns.any { it.direction == direction && it.distanceMeters in 0..TURN_NEAR_METERS }

    private fun turn(direction: TurnDirection): List<Guidance> = buildList {
        val detailed = scope.detail == DetailLevel.DETAILED
        when (direction) {
            TurnDirection.RIGHT -> if (detailed) addIfAllowed(GuidanceId.RIGHT_TURN_RULE)
            TurnDirection.LEFT -> {
                if (detailed) addIfAllowed(GuidanceId.LEFT_TURN_RULE)
                if (scope.rightSideAdaptation) addIfAllowed(GuidanceId.RIGHT_SIDE_LEFT_TURN)
                if (scope.leftHandDriveAdaptation) addIfAllowed(GuidanceId.LHD_MIRROR_CHECK)
            }
            TurnDirection.OTHER -> Unit
        }
    }

    private fun laneChange(): List<Guidance> = buildList {
        if (scope.leftHandDriveAdaptation) addIfAllowed(GuidanceId.LHD_MIRROR_CHECK)
    }

    // ---- 어린이보호구역 (모든 사용자) ----

    private fun onCautionPoints(points: List<CautionPoint>): List<Guidance> {
        val inRange = points.filter {
            it.kind == CautionKind.SCHOOL_ZONE && it.remainMeters in 1..SCHOOL_ZONE_AHEAD_METERS
        }
        val fresh = inRange.filter { it.id !in announcedCautionIds }
        if (fresh.isEmpty()) return emptyList()
        // 한꺼번에 여러 곳이 들어오면 가장 가까운 곳 하나만 말한다.
        announcedCautionIds += fresh.map { it.id }
        val nearest = fresh.minBy { it.remainMeters }
        schoolZone = SchoolZoneContext(startedAtMs = clock.nowMillis(), speedLimitBefore = currentSpeedLimit)
        return buildList {
            add(Guidance(GuidanceId.SCHOOL_ZONE_AHEAD, listOf(roundDistance(nearest.remainMeters))))
            if (scope.detail == DetailLevel.DETAILED) addIfAllowed(GuidanceId.SCHOOL_ZONE_CROSSWALK)
        }
    }

    private fun onCameras(cameras: List<CameraAlert>): List<Guidance> {
        val fresh = cameras.filter {
            it.kind == CameraKind.SCHOOL_ZONE && it.speedLimitKmh > 0 && it.id !in announcedCameraIds
        }
        if (fresh.isEmpty()) return emptyList()
        announcedCameraIds += fresh.map { it.id }
        val zone = schoolZone
        if (zone != null && zone.limitAnnounced) return emptyList()
        val limit = fresh.minBy { it.remainMeters }.speedLimitKmh
        schoolZone = (zone ?: SchoolZoneContext(clock.nowMillis(), currentSpeedLimit)).copy(limitAnnounced = true)
        return listOf(Guidance(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT, listOf(limit)))
    }

    private fun onSpeedLimit(kmh: Int): List<Guidance> {
        currentSpeedLimit = kmh.takeIf { it > 0 }
        val zone = schoolZone ?: return emptyList()
        if (zone.limitAnnounced || kmh <= 0) return emptyList()
        if (clock.nowMillis() - zone.startedAtMs > SCHOOL_ZONE_LIMIT_WINDOW_MS) return emptyList()
        // 접근 경고 뒤 제한 속도가 내려가면 그 값을 어린이보호구역 제한 속도로 본다.
        val before = zone.speedLimitBefore
        if (before != null && kmh >= before) return emptyList()
        schoolZone = zone.copy(limitAnnounced = true)
        return listOf(Guidance(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT, listOf(kmh)))
    }

    // ---- 공통 ----

    private fun emit(id: GuidanceId): List<Guidance> =
        if (cooldown.tryAcquire(id)) listOf(Guidance(id)) else emptyList()

    private fun MutableList<Guidance>.addIfAllowed(id: GuidanceId) {
        if (cooldown.tryAcquire(id)) add(Guidance(id))
    }

    private data class SchoolZoneContext(
        val startedAtMs: Long,
        val speedLimitBefore: Int?,
        val limitAnnounced: Boolean = false,
    )

    companion object {
        /** 이 거리 안에 같은 방향 회전이 있으면 회전으로 본다. */
        const val TURN_NEAR_METERS = 150

        /** 어린이보호구역 접근 경고 거리. */
        const val SCHOOL_ZONE_AHEAD_METERS = 300

        /** 접근 경고 뒤 이 시간 안에 들어온 제한 속도를 어린이보호구역 제한 속도로 본다. */
        const val SCHOOL_ZONE_LIMIT_WINDOW_MS = 90_000L

        /** 음성으로 읽기 좋게 50m 단위로 반올림한다(최소 50m). */
        fun roundDistance(meters: Int): Int = maxOf(50, (meters / 50.0).roundToInt() * 50)
    }
}
