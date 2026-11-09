# release는 R8을 켜되, 개인정보 로그 제거에만 쓴다.
# 이름 바꾸기와 코드 줄이기는 하지 않는다(Pleos SDK와 직렬화가 리플렉션을 쓰므로 모든 클래스를 그대로 둔다).
-dontobfuscate
-keep class ** { *; }
-keepattributes *

# Pleos STT 3.3.1의 자체 로거는 인식한 문장(운전자 질문)을 그대로 logcat에 남긴다
# (SttViewModel.notifySttChanged → Log.c → android.util.Log.i). release에서는 이 로거 호출을 지운다.
# 시스템 인식 서비스(GAAS)가 남기는 로그는 앱 밖이라 여기서 막을 수 없다.
-assumenosideeffects class ai.pleos.playground.stt.utils.Log {
    public void a(java.lang.String, java.lang.String);
    public void b(java.lang.String, java.lang.String);
    public void c(java.lang.String, java.lang.String);
    public void d(java.lang.String, java.lang.String);
}

# 라이브러리가 참조하지만 앱에서 쓰지 않는 선택 의존성(R8 missing_rules.txt)
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn javax.lang.model.element.Modifier
-dontwarn org.joda.time.Instant
