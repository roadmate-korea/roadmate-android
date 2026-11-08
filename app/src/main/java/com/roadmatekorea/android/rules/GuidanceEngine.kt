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
    //
    // 구역 제한(zoneLimitKmh)은 NaviHelper 값이다. 도로 제한이 그 값 이하로 들어오면 "진입 확인"이고,
    // 진입한 뒤에는 도로 제한이 기준이다(앞쪽 카메라의 다른 값으로 덮어쓰지 않는다). 과속 경고는 진입 확인 뒤에만 한다.

    private fun onCautionPoints(points: List<CautionPoint>): List<Guidance> {
        // 같은 구역 지점이 계속(또는 끊겼다가 다시) 들어오면 아직 그 구역 근처다
        schoolZone?.let { zone ->
            if (zone.cautionId != null && points.any { it.id == zone.cautionId }) schoolZone = revive(zone)
        }
        return schoolZoneAhead(points) + busLaneAhead(points)
    }

    private fun schoolZoneAhead(points: List<CautionPoint>): List<Guidance> {
        val inRange = points.filter {
            it.kind == CautionKind.SCHOOL_ZONE && it.remainMeters in 1..SCHOOL_ZONE_AHEAD_METERS
        }
        val fresh = inRange.filter { it.id !in announcedCautionIds }
        if (fresh.isEmpty()) {
            // 이미 안내한 지점이 다시 왔는데 지금 감시 중인 구역이 없다(같은 구역 재진입, 앞 구역을 벗어난 뒤 다음 구역):
            // 다시 말하지 않고 감시만 시작한다
            if (activeSchoolZone() == null) {
                inRange.minByOrNull { it.remainMeters }?.let { point ->
                    schoolZone = SchoolZoneContext(lastEvidenceMs = clock.nowMillis(), speedLimitBefore = currentSpeedLimit, cautionId = point.id)
                }
            }
            return emptyList()
        }
        // 한꺼번에 여러 곳이 들어오면 가장 가까운 곳 하나만 말한다.
        // 나머지는 완료 처리하지 않으므로 다음 갱신에서 가장 가까운 곳이 되면 안내한다.
        val nearest = fresh.minBy { it.remainMeters }
        announcedCautionIds += nearest.id
        // 카메라나 도로 제한으로 이미 알고 있는 구역이면 그 제한과 진입 상태를 그대로 둔다(접근 지점이 늦게 오는 경우)
        val now = clock.nowMillis()
        schoolZone = activeSchoolZone()?.copy(cautionId = nearest.id, lastEvidenceMs = now)
            ?: SchoolZoneContext(lastEvidenceMs = now, speedLimitBefore = currentSpeedLimit, cautionId = nearest.id)
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
        val schoolCameras = cameras.filter { it.kind == CameraKind.SCHOOL_ZONE && it.speedLimitKmh > 0 }
        schoolZone?.let { zone ->
            val active = activeSchoolZone() != null
            // 지금 구역과 같은 제한의 카메라는 구역 근거다. 만료된 구역은 같은 카메라가 다시 올 때만 말없이 되살린다.
            val evidence = if (active) schoolCameras.any { it.speedLimitKmh == zone.zoneLimitKmh }
            else zone.cameraId != null && schoolCameras.any { it.id == zone.cameraId }
            if (evidence) schoolZone = revive(zone)
        }
        val known = schoolCameras.filter { it.id in announcedCameraIds }.minByOrNull { it.remainMeters }
        val current = activeSchoolZone()
        if (known != null && schoolCameras.none { it.id !in announcedCameraIds } && current?.zoneLimitKmh == null) {
            // 이미 안내한 카메라가 다시 왔는데 감시 중인 구역이 없거나, 접근 지점만 알고 제한을 아직 모른다
            // (같은 구역 재진입 등): 다시 말하지 않고 그 카메라 제한으로 감시를 시작하고 지금 속도로 판단한다
            schoolZone = (current ?: SchoolZoneContext(clock.nowMillis(), currentSpeedLimit)).copy(
                zoneLimitKmh = known.speedLimitKmh,
                cameraId = known.id,
                lastEvidenceMs = clock.nowMillis(),
                entry = entryFor(known.speedLimitKmh),
                overSpeedWarned = false,
            )
            return checkOverSpeed()
        }
        val zone = activeSchoolZone()
        // 도로 제한으로 진입이 확인된 구역에서는 그 제한이 기준이다. 앞쪽 카메라의 다른 값으로 바꾸지 않는다.
        // (카메라로만 추정한 구역이면 다른 제한의 카메라를 새 구역으로 본다.)
        if (zone?.entry == Entry.ROAD) return emptyList()
        // 카메라에는 구역 정보가 없다. 같은 구역인지 알 수 없으므로, 방금 같은 제한 속도를 말한 경우에만 건너뛴다.
        // 건너뛴 카메라는 완료 처리하지 않는다(구역이 끝난 뒤 다시 들어오면 안내한다).
        val camera = schoolCameras
            .filter { it.id !in announcedCameraIds && zone?.zoneLimitKmh != it.speedLimitKmh }
            .minByOrNull { it.remainMeters }
            ?: return emptyList()
        announcedCameraIds += camera.id
        val limit = camera.speedLimitKmh
        schoolZone = (zone ?: SchoolZoneContext(clock.nowMillis(), currentSpeedLimit)).copy(
            zoneLimitKmh = limit,
            cameraId = camera.id,
            lastEvidenceMs = clock.nowMillis(),
            // 도로 제한을 알면 그 값이 확인해 줄 때까지 기다리고(앞쪽 카메라일 수 있다), 모르면 카메라 값으로 추정한다
            entry = entryFor(limit),
            overSpeedWarned = false,
        )
        return listOf(schoolZoneLimit(limit))
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
     * - 구역 제한과 같으면: 구역 안에 들어왔다(진입 확인, 구역 유지). 처음 진입하는 순간 지금 속도를 판단한다.
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
                schoolZone = zone.copy(zoneLimitKmh = kmh, entry = Entry.ROAD, lastEvidenceMs = now)
                listOf(schoolZoneLimit(kmh))
            }
            kmh == zoneLimit -> {
                schoolZone = zone.copy(entry = Entry.ROAD, lastEvidenceMs = now)
                if (zone.entry == Entry.ROAD) emptyList() else checkOverSpeed()
            }
            kmh < zoneLimit -> {
                schoolZone = zone.copy(zoneLimitKmh = kmh, entry = Entry.ROAD, lastEvidenceMs = now, overSpeedWarned = false)
                listOf(schoolZoneLimit(kmh))
            }
            zone.entry == Entry.ROAD -> {
                schoolZone = null
                emptyList()
            }
            // 카메라로만 추정했는데 실제 도로 제한이 더 높다: 아직 앞쪽 카메라에 다가가는 중이다(이탈이 아니다)
            else -> {
                if (zone.entry == Entry.CAMERA_ESTIMATE) schoolZone = zone.copy(entry = Entry.NONE, overSpeedWarned = false)
                emptyList()
            }
        }
    }

    /**
     * 어린이보호구역 제한 속도를 알게 된 순간의 안내. 구역 안이고 이미 제한보다 빠르면 "지금 몇 km, 제한 몇"으로 바꿔 말하고,
     * 아니면 제한 속도만 말한다(앞쪽 카메라의 사전 안내는 늘 제한 속도만). 제한 속도 자체는 항상 NaviHelper 값이다.
     */
    private fun schoolZoneLimit(limit: Int): Guidance {
        val zone = schoolZone
        val speed = vehicleSpeed
        if (zone != null && zone.entry != Entry.NONE && speed != null && !isParked && isOver(speed, limit) &&
            cooldown.tryAcquire(GuidanceId.SCHOOL_ZONE_OVER_SPEED)
        ) {
            schoolZone = zone.copy(overSpeedWarned = true, lastOverWarnMs = clock.nowMillis())
            return Guidance(GuidanceId.SCHOOL_ZONE_OVER_SPEED, listOf(speed, limit))
        }
        return Guidance(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT, listOf(limit))
    }

    private fun onVehicleSpeed(speed: Int?): List<Guidance> {
        // 읽을 수 없게 되면 지난 값을 쓰지 않는다
        vehicleSpeed = speed?.coerceAtLeast(0)
        return checkOverSpeed()
    }

    /**
     * 진입이 확인된 구역에서 제한을 넘으면 경고한다. 넘는 순간 한 번, 계속 넘는 동안에는 [OVER_SPEED_REPEAT_MS]마다 다시
     * (음성 큐에서 버려졌어도 지금 속도로 다시 만든다). 제한 아래로 줄였다가 다시 넘으면 최소 간격(쿨다운) 뒤 다시 경고한다.
     * 제한보다 느리면 아무 말도 하지 않는다.
     */
    private fun checkOverSpeed(): List<Guidance> {
        val kmh = vehicleSpeed ?: return emptyList()
        val zone = activeSchoolZone() ?: return emptyList()
        val limit = zone.zoneLimitKmh ?: return emptyList()
        if (zone.entry == Entry.NONE) return emptyList()
        if (kmh <= limit) {
            if (zone.overSpeedWarned) schoolZone = zone.copy(overSpeedWarned = false)
            return emptyList()
        }
        if (isParked || !isOver(kmh, limit)) return emptyList()
        val now = clock.nowMillis()
        if (zone.overSpeedWarned && now - zone.lastOverWarnMs < OVER_SPEED_REPEAT_MS) return emptyList()
        if (!cooldown.tryAcquire(GuidanceId.SCHOOL_ZONE_OVER_SPEED)) return emptyList()
        schoolZone = zone.copy(overSpeedWarned = true, lastOverWarnMs = now)
        return listOf(Guidance(GuidanceId.SCHOOL_ZONE_OVER_SPEED, listOf(kmh, limit)))
    }

    private fun isOver(speed: Int, limit: Int) = speed > limit + OVER_SPEED_MARGIN_KMH

    /**
     * 구역 제한 [limit]을 카메라로 알게 됐거나 구역을 되살릴 때의 진입 상태.
     * 지금 도로 제한이 같으면 도로가 확인해 준 것, 모르면 카메라 값으로 추정, 다르면 도로 제한이 확인해 줄 때까지 기다린다
     * (더 높으면 앞쪽 카메라에 다가가는 중, 더 낮으면 다음 도로 제한에서 그 값으로 바뀐다).
     */
    private fun entryFor(limit: Int): Entry = when (currentSpeedLimit) {
        null -> Entry.CAMERA_ESTIMATE
        limit -> Entry.ROAD
        else -> Entry.NONE
    }

    /**
     * 지금 어린이보호구역에서 제한보다 빠르면 지금 값으로 만든 과속 경고, 아니면 null.
     * DriveSession이 아직 말하지 않은 과속 경고를 버리거나 지금 값으로 바꾸는 데 쓴다(쿨다운과 상관없는 상태 조회).
     */
    fun overSpeedNow(): Guidance? {
        val zone = activeSchoolZone() ?: return null
        val limit = zone.zoneLimitKmh ?: return null
        val speed = vehicleSpeed ?: return null
        if (zone.entry == Entry.NONE || isParked || !isOver(speed, limit)) return null
        return Guidance(GuidanceId.SCHOOL_ZONE_OVER_SPEED, listOf(speed, limit))
    }

    /** 구역 근거를 다시 받았다. 만료됐던 구역이면 진입 여부를 지금 도로 제한으로 다시 정하고 경고 상태를 지운다. */
    private fun revive(zone: SchoolZoneContext): SchoolZoneContext {
        val now = clock.nowMillis()
        if (activeSchoolZone() != null) return zone.copy(lastEvidenceMs = now)
        return zone.copy(lastEvidenceMs = now, entry = zone.zoneLimitKmh?.let(::entryFor) ?: Entry.NONE, overSpeedWarned = false)
    }

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

    /** 구역 안에 있는지: 모름(접근 중), 카메라로 추정(도로 제한 정보 없음), 도로 제한으로 확인. 과속 경고는 NONE이 아닐 때만. */
    private enum class Entry { NONE, CAMERA_ESTIMATE, ROAD }

    private data class SchoolZoneContext(
        /** 마지막으로 구역 근거를 받은 시각 */
        val lastEvidenceMs: Long,
        val speedLimitBefore: Int?,
        /** 접근 경고를 낸 주의 지점 ID. 카메라로 시작한 구역이면 null */
        val cautionId: String? = null,
        /** 구역 제한을 알려 준 카메라 ID */
        val cameraId: String? = null,
        /** 지금 구역 제한 속도(항상 이미 말한 값). null이면 아직 모름 */
        val zoneLimitKmh: Int? = null,
        val entry: Entry = Entry.NONE,
        /** 지금 제한을 넘은 상태로 이미 경고했는지. 제한 아래로 내려오면 false로 돌아간다. */
        val overSpeedWarned: Boolean = false,
        val lastOverWarnMs: Long = 0,
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

        /** 구역 안에서 계속 제한을 넘고 있으면 이 간격으로 다시 경고한다. */
        const val OVER_SPEED_REPEAT_MS = 30_000L

        /** 음성으로 읽기 좋게 50m 단위로 반올림한다(최소 50m). */
        fun roundDistance(meters: Int): Int = maxOf(50, (meters / 50.0).roundToInt() * 50)
    }
}
