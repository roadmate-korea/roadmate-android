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
    private var arrived = false
    private var currentSpeedLimit: Int? = null

    /** 차량 속도(km/h). 읽을 수 없으면 null이고, 그때는 속도에 따른 안내를 하지 않는다. */
    private var vehicleSpeed: Int? = null

    /** 비어 있으면 경로 안내 정보가 없는 것으로 본다. */
    private var turns: List<TurnInstruction> = emptyList()
    private var rightTurnNear = false
    private var leftTurnNear = false

    private val announcedCautionIds = mutableSetOf<String>()
    private val announcedCameraIds = mutableSetOf<String>()
    private var schoolZone: SchoolZoneContext? = null

    private val isParked get() = gear == Gear.PARK

    /**
     * 한 이벤트에서 나온 안내는 안전 경고를 먼저 둔다(나머지는 원래 순서). 음성 큐는 비어 있으면 첫 항목을 바로
     * 재생하므로, 같은 묶음 안의 순서가 곧 재생 순서다.
     */
    fun onEvent(event: DriveEvent): List<Guidance> = route(event).let { list ->
        val (safety, rest) = list.partition { it.priority == Priority.SAFETY }
        safety + rest
    }

    private fun route(event: DriveEvent): List<Guidance> = when (event) {
        DriveEvent.SessionStarted -> greet()
        is DriveEvent.GearChanged -> onGear(event.gear)
        is DriveEvent.TurnSignalChanged -> onTurnSignal(event.signal)
        is DriveEvent.VehicleSpeedChanged -> onVehicleSpeed(event.kmh)
        is DriveEvent.SpeedLimitChanged -> onSpeedLimit(event.kmh)
        is DriveEvent.CautionPointsUpdated -> onCautionPoints(event.points)
        is DriveEvent.CameraAlertsUpdated -> onCameras(event.cameras)
        is DriveEvent.TurnInstructionsUpdated -> onTurns(event.turns)
        DriveEvent.DestinationArrived -> arrive()
    }

    private fun arrive(): List<Guidance> {
        if (arrived) return emptyList()
        arrived = true
        return listOf(Guidance(GuidanceId.ARRIVED))
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
        if (isParked) {
            // 정차 중에 받은 근접 회전은 안내하지 않은 것으로 두어, 출발 뒤 다음 갱신에서 다시 판단한다.
            rightTurnNear = false
            leftTurnNear = false
            return emptyList()
        }
        val rightNear = hasNearTurn(TurnDirection.RIGHT)
        val leftNear = hasNearTurn(TurnDirection.LEFT)
        // 같은 회전이 다가오며 계속 갱신되므로, 가까워지는 순간에만 낸다.
        val enteredRight = rightNear && !rightTurnNear
        val enteredLeft = leftNear && !leftTurnNear
        rightTurnNear = rightNear
        leftTurnNear = leftNear
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
        // 같은 구역 지점이 계속 들어오면 아직 그 구역 근처다(신호 대기 등으로 오래 걸려도 구역을 유지)
        schoolZone?.let { zone ->
            if (zone.cautionId != null && points.any { it.id == zone.cautionId }) schoolZone = zone.copy(lastEvidenceMs = clock.nowMillis())
        }
        return schoolZoneAhead(points) + busLaneAhead(points)
    }

    private fun schoolZoneAhead(points: List<CautionPoint>): List<Guidance> {
        val inRange = points.filter {
            it.kind == CautionKind.SCHOOL_ZONE && it.remainMeters in 1..SCHOOL_ZONE_AHEAD_METERS
        }
        val fresh = inRange.filter { it.id !in announcedCautionIds }
        if (fresh.isEmpty()) return emptyList()
        // 한꺼번에 여러 곳이 들어오면 가장 가까운 곳 하나만 말한다.
        // 나머지는 완료 처리하지 않으므로 다음 갱신에서 가장 가까운 곳이 되면 안내한다.
        val nearest = fresh.minBy { it.remainMeters }
        announcedCautionIds += nearest.id
        schoolZone = SchoolZoneContext(lastEvidenceMs = clock.nowMillis(), speedLimitBefore = currentSpeedLimit, cautionId = nearest.id)
        return buildList {
            add(Guidance(GuidanceId.SCHOOL_ZONE_AHEAD, listOf(roundDistance(nearest.remainMeters))))
            if (scope.detail == DetailLevel.DETAILED) addIfAllowed(GuidanceId.SCHOOL_ZONE_CROSSWALK)
        }
    }

    // ---- 버스전용차로 (상세 안내 대상) ----

    private fun busLaneAhead(points: List<CautionPoint>): List<Guidance> {
        if (isParked || scope.detail != DetailLevel.DETAILED) return emptyList()
        val nearest = points
            .filter { it.kind == CautionKind.BUS_LANE && it.remainMeters in 1..BUS_LANE_AHEAD_METERS && it.id !in announcedCautionIds }
            .minByOrNull { it.remainMeters } ?: return emptyList()
        // 실제로 말한 지점만 완료로 둔다. 간격 제한에 걸린 지점은 범위 안에 남아 있으면 다음 갱신에서 다시 판단한다.
        if (!cooldown.tryAcquire(GuidanceId.BUS_LANE_AHEAD)) return emptyList()
        announcedCautionIds += nearest.id
        return listOf(Guidance(GuidanceId.BUS_LANE_AHEAD, listOf(roundDistance(nearest.remainMeters))))
    }

    private fun onCameras(cameras: List<CameraAlert>): List<Guidance> = schoolZoneCamera(cameras) + sectionCheck(cameras)

    private fun schoolZoneCamera(cameras: List<CameraAlert>): List<Guidance> {
        activeSchoolZone()?.let { zone ->
            if (cameras.any { it.kind == CameraKind.SCHOOL_ZONE && it.speedLimitKmh == zone.zoneLimitKmh }) {
                schoolZone = zone.copy(lastEvidenceMs = clock.nowMillis())
            }
        }
        val fresh = cameras.filter {
            it.kind == CameraKind.SCHOOL_ZONE && it.speedLimitKmh > 0 && it.id !in announcedCameraIds
        }
        if (fresh.isEmpty()) return emptyList()
        val zone = activeSchoolZone()
        // 카메라에는 구역 정보가 없다. 같은 구역인지 알 수 없으므로, 방금 같은 제한 속도를 말한 경우에만 건너뛴다.
        // 건너뛴 카메라는 완료 처리하지 않는다(구역이 끝난 뒤 다시 들어오면 안내한다).
        val camera = fresh
            .filter { zone?.zoneLimitKmh != it.speedLimitKmh }
            .minByOrNull { it.remainMeters }
            ?: return emptyList()
        announcedCameraIds += camera.id
        schoolZone = (zone ?: SchoolZoneContext(clock.nowMillis(), currentSpeedLimit))
            .copy(zoneLimitKmh = camera.speedLimitKmh, lastEvidenceMs = clock.nowMillis(), overSpeedWarned = false)
        return listOf(schoolZoneLimit(camera.speedLimitKmh))
    }

    // ---- 구간 단속: 시작 설명(상세 안내 대상), 평균 속도 초과(모든 사용자) ----

    private fun sectionCheck(cameras: List<CameraAlert>): List<Guidance> {
        if (isParked) return emptyList()
        return buildList {
            if (scope.detail == DetailLevel.DETAILED) {
                cameras.filter { it.kind == CameraKind.SECTION_START && it.speedLimitKmh > 0 && it.id !in announcedCameraIds }
                    .minByOrNull { it.remainMeters }
                    ?.let { start ->
                        if (cooldown.tryAcquire(GuidanceId.SECTION_START)) {
                            announcedCameraIds += start.id
                            add(Guidance(GuidanceId.SECTION_START, listOf(start.speedLimitKmh)))
                        }
                    }
            }
            cameras.firstOrNull { it.kind == CameraKind.SECTION_AVERAGE && it.speedLimitKmh > 0 && it.sectionAverageKmh > it.speedLimitKmh }
                ?.let { over ->
                    if (cooldown.tryAcquire(GuidanceId.SECTION_OVER_AVERAGE)) {
                        add(Guidance(GuidanceId.SECTION_OVER_AVERAGE, listOf(over.sectionAverageKmh, over.speedLimitKmh)))
                    }
                }
        }
    }

    /**
     * 도로 제한 속도(NaviHelper, 같은 값이 반복해서 온다)로 구역 진입, 구역 안 제한 변경, 구역 이탈을 판단한다.
     * - 아직 구역 제한을 모르면: 접근 경고 전 도로 제한보다 내려간 값을 구역 제한으로 본다.
     * - 구역 제한과 같으면: 구역 안에 들어왔다(진입 확인, 구역 유지).
     * - 구역 제한보다 낮으면: 구역 안에서 제한이 더 내려갔다. 새 값으로 바꾸고 다시 안내한다.
     * - 구역 제한보다 높으면: 진입한 뒤라면 구역을 벗어난 것이다. 진입 전(카메라가 앞으로의 제한을 먼저 알려 준 경우)이면
     *   아직 지금 도로의 제한이 오는 것이므로 무시한다.
     */
    private fun onSpeedLimit(kmh: Int): List<Guidance> {
        currentSpeedLimit = kmh.takeIf { it > 0 }
        val zone = activeSchoolZone() ?: return emptyList()
        if (kmh <= 0) return emptyList()
        val now = clock.nowMillis()
        val zoneLimit = zone.zoneLimitKmh
        return when {
            zoneLimit == null -> {
                val before = zone.speedLimitBefore
                if (before != null && kmh >= before) return emptyList()
                schoolZone = zone.copy(zoneLimitKmh = kmh, entered = true, lastEvidenceMs = now)
                listOf(schoolZoneLimit(kmh))
            }
            kmh == zoneLimit -> {
                schoolZone = zone.copy(entered = true, lastEvidenceMs = now)
                emptyList()
            }
            kmh < zoneLimit -> {
                schoolZone = zone.copy(zoneLimitKmh = kmh, entered = true, lastEvidenceMs = now, overSpeedWarned = false)
                listOf(schoolZoneLimit(kmh))
            }
            zone.entered -> {
                schoolZone = null
                emptyList()
            }
            else -> emptyList()
        }
    }

    /**
     * 어린이보호구역 제한 속도를 알게 된 순간의 안내. 이미 제한보다 빠르면 "지금 몇 km, 제한 몇"으로 바꿔 말하고,
     * 아니면 제한 속도만 말한다. 제한 속도 자체는 항상 NaviHelper 값이다.
     */
    private fun schoolZoneLimit(limit: Int): Guidance {
        val speed = vehicleSpeed
        if (speed != null && !isParked && isOver(speed, limit) && cooldown.tryAcquire(GuidanceId.SCHOOL_ZONE_OVER_SPEED)) {
            schoolZone = schoolZone?.copy(overSpeedWarned = true)
            return Guidance(GuidanceId.SCHOOL_ZONE_OVER_SPEED, listOf(speed, limit))
        }
        return Guidance(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT, listOf(limit))
    }

    /**
     * 구역 안에서 제한을 넘는 순간 한 번 경고한다. 계속 빠른 동안에는 다시 말하지 않고(잔소리 금지),
     * 제한 아래로 줄였다가 다시 넘으면 다시 경고한다. 제한보다 느리면 아무 말도 하지 않는다.
     */
    private fun onVehicleSpeed(speed: Int?): List<Guidance> {
        // 읽을 수 없게 되면 지난 값을 쓰지 않는다
        val kmh = speed?.coerceAtLeast(0)
        vehicleSpeed = kmh
        if (kmh == null) return emptyList()
        val zone = activeSchoolZone() ?: return emptyList()
        val limit = zone.zoneLimitKmh ?: return emptyList()
        if (kmh <= limit) {
            if (zone.overSpeedWarned) schoolZone = zone.copy(overSpeedWarned = false)
            return emptyList()
        }
        if (zone.overSpeedWarned || isParked || !isOver(kmh, limit)) return emptyList()
        if (!cooldown.tryAcquire(GuidanceId.SCHOOL_ZONE_OVER_SPEED)) return emptyList()
        schoolZone = zone.copy(overSpeedWarned = true)
        return listOf(Guidance(GuidanceId.SCHOOL_ZONE_OVER_SPEED, listOf(kmh, limit)))
    }

    private fun isOver(speed: Int, limit: Int) = speed > limit + OVER_SPEED_MARGIN_KMH

    /**
     * 마지막 구역 근거(접근 지점, 같은 제한의 카메라, 구역 제한과 같은 도로 제한)를 받은 뒤
     * [SCHOOL_ZONE_LIMIT_WINDOW_MS]가 지나면 구역이 끝난 것으로 본다.
     */
    private fun activeSchoolZone(): SchoolZoneContext? =
        schoolZone?.takeIf { clock.nowMillis() - it.lastEvidenceMs <= SCHOOL_ZONE_LIMIT_WINDOW_MS }

    // ---- 공통 ----

    private fun MutableList<Guidance>.addIfAllowed(id: GuidanceId) {
        if (cooldown.tryAcquire(id)) add(Guidance(id))
    }

    private data class SchoolZoneContext(
        /** 마지막으로 구역 근거를 받은 시각 */
        val lastEvidenceMs: Long,
        val speedLimitBefore: Int?,
        /** 접근 경고를 낸 주의 지점 ID. 카메라로 시작한 구역이면 null */
        val cautionId: String? = null,
        /** 지금 구역 제한 속도(항상 이미 말한 값). null이면 아직 모름 */
        val zoneLimitKmh: Int? = null,
        /** 도로 제한이 구역 제한 이하로 들어와 구역 안에 있음이 확인됨 */
        val entered: Boolean = false,
        /** 지금 제한을 넘은 상태로 이미 경고했는지. 제한 아래로 내려오면 false로 돌아간다. */
        val overSpeedWarned: Boolean = false,
    )

    companion object {
        /** 이 거리 안에 같은 방향 회전이 있으면 회전으로 본다. */
        const val TURN_NEAR_METERS = 150

        /** 어린이보호구역 접근 경고 거리. */
        const val SCHOOL_ZONE_AHEAD_METERS = 300

        /** 버스전용차로 안내 거리. */
        const val BUS_LANE_AHEAD_METERS = 300

        /** 구역 근거가 이 시간 동안 없으면 구역이 끝난 것으로 본다(접근 경고 뒤 제한 속도를 기다리는 시간이기도 하다). */
        const val SCHOOL_ZONE_LIMIT_WINDOW_MS = 90_000L

        /** 속도계 오차를 생각해 제한보다 이만큼 넘게 빠를 때만 과속으로 본다. */
        const val OVER_SPEED_MARGIN_KMH = 2

        /** 음성으로 읽기 좋게 50m 단위로 반올림한다(최소 50m). */
        fun roundDistance(meters: Int): Int = maxOf(50, (meters / 50.0).roundToInt() * 50)
    }
}
