package app.untilparking.domain

/** 저장소 구현이 SDK·네트워크 오류를 이 어휘로 바꿔 던진다. 원인은 cause에 남긴다. */
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NavigationFailed(reason: String, cause: Throwable? = null) : AppError("내비 요청 실패: $reason", cause)
    class ParkingSourceFailed(reason: String, cause: Throwable? = null) : AppError("주차장 정보 조회 실패: $reason", cause)
    class VoiceFailed(reason: String, cause: Throwable? = null) : AppError("음성 처리 실패: $reason", cause)
    class CongestionFailed(reason: String, cause: Throwable? = null) : AppError("혼잡도 처리 실패: $reason", cause)
}
