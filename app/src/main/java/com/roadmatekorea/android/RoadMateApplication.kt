package com.roadmatekorea.android

import android.app.Application
import android.content.pm.PackageManager
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.RealDriveSignalSource
import com.roadmatekorea.android.drive.SavedSessionRestorer
import com.roadmatekorea.android.i18n.AppLocales
import com.roadmatekorea.android.i18n.withLanguage
import com.roadmatekorea.android.interpreter.DemoTranslationClient
import com.roadmatekorea.android.interpreter.HttpTranslationClient
import com.roadmatekorea.android.interpreter.InterpreterController
import com.roadmatekorea.android.interpreter.NoTranslationClient
import com.roadmatekorea.android.interpreter.Phrases
import com.roadmatekorea.android.interpreter.TranslationClient
import com.roadmatekorea.android.navi.NaviSignalSource
import com.roadmatekorea.android.profile.ProfileRepository
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.ui.drive.DriveController
import com.roadmatekorea.android.ui.drive.createDriveController
import com.roadmatekorea.android.vehicle.VehicleSignalSource
import com.roadmatekorea.android.vehicle.VehicleSpeedSource
import com.roadmatekorea.android.voice.PleosSpeechInput
import com.roadmatekorea.android.voice.SpeechInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 앱 전체에서 하나만 쓰는 객체를 둔다(간단한 수동 의존성 주입). */
class RoadMateApplication : Application() {

    /** 프로세스 수명 동안 살아 있는 범위. 신호는 메인 스레드에서 처리한다. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }

    /** 차량 속도 권한(`CAR_SPEED`)이 허용되었는지. 화면에서 허용을 받으면 true로 바꾼다. */
    val speedPermissionGranted: MutableStateFlow<Boolean> by lazy { MutableStateFlow(hasSpeedPermission()) }

    /** 이 프로세스에서 권한 창(속도, 알림)을 이미 띄웠는지. 화면이 다시 만들어져도 유지된다(한 번만 묻는다). */
    var permissionsAsked = false

    fun hasSpeedPermission(): Boolean = checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED

    /** 마이크 권한(음성 질문). 허용되면 주행 화면에 마이크 버튼이 보인다. */
    val micPermissionGranted: MutableStateFlow<Boolean> by lazy { MutableStateFlow(hasMicPermission()) }

    fun hasMicPermission(): Boolean = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** 실제 Vehicle·NaviHelper 신호. 처음 쓸 때 한 번 시작하고 프로세스가 끝날 때까지 수집한다. */
    val signalHub: DriveSignalHub by lazy {
        DriveSignalHub(
            RealDriveSignalSource(VehicleSignalSource(this), NaviSignalSource(this), VehicleSpeedSource(this, speedPermissionGranted)),
            appScope,
        ).also { it.start() }
    }

    /** 안내 세션(판단, 음성). 화면과 상관없이 프로세스 수명 동안 하나. */
    val driveController: DriveController by lazy { createDriveController(this) }

    /** 음성 인식 엔진(앱에 하나). 인식 서비스는 결과를 마지막으로 만든 엔진에만 보내므로 음성 질문과 통역이 같이 쓴다. */
    val speechInput: SpeechInput by lazy { PleosSpeechInput(this) }

    /** 정차 중 통역. 번역은 서버(서버 주소가 없으면 debug는 시연용 가짜 서버, release는 문장 카드만). */
    val interpreter: InterpreterController by lazy {
        InterpreterController(
            speech = speechInput,
            client = translationClient(),
            say = driveController::sayInterpreter,
            prepareToListen = driveController::prepareForInterpreter,
            driverLocale = { AppLocales.localeOf(driveController.uiState.value.profile?.language ?: AppLanguage.EN) },
            scope = appScope,
        ).also { controller ->
            // 화면이 백그라운드여도 기어가 P를 벗어나면 듣기와 기다리던 번역을 멈춘다
            appScope.launch {
                driveController.vehicle.map { it.locked }.distinctUntilChanged().filter { it }.collect { controller.leave() }
            }
        }
    }

    private fun translationClient(): TranslationClient = when {
        BuildConfig.SERVER_URL.isNotBlank() -> HttpTranslationClient(BuildConfig.SERVER_URL)
        BuildConfig.DEBUG -> DemoTranslationClient(
            // 모든 언어의 문장 카드(운전자 언어 문장 → 한국어)
            AppLanguage.entries.flatMap { language ->
                val res = withLanguage(language).resources
                Phrases.all.map { res.getString(it.driverText) to res.getString(it.korean) }
            }.toMap(),
        )
        else -> NoTranslationClient
    }

    private val restorer by lazy { SavedSessionRestorer(appScope, profileRepository.profile, driveController::ensureSession) }

    /**
     * 저장된 프로필로 안내 세션을 연다(시작 인사 없이). 화면 없이 서비스만 다시 만들어진 경우(프로세스가 죽은 뒤
     * START_STICKY 재시작)에도 안전 경고가 나가게 한다. 온보딩 전이거나 저장소를 읽지 못하면 기다리기만 한다.
     * 화면이 먼저 세션을 열었으면 ensureSession은 아무 일도 하지 않는다.
     */
    fun restoreSavedSession() = restorer.restore()

    /** 사용자가 안내를 끝냈다(최근 앱에서 제거): 기다리던 복원을 버리고 세션·시연·음성을 끝낸다. */
    fun endGuidance() {
        restorer.cancel()
        driveController.endSession()
    }

    companion object {
        /** Android Automotive 차량 속도 권한. Pleos 권한이 아닌 표준 권한이라 문자열로 둔다. */
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
