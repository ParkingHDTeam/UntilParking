package app.untilparking.domain.model

import java.time.Instant

/** 운전자가 도착해서 고르는 혼잡도. 점수가 클수록 붐빈다. 평균은 이 점수로 낸다(혼잡도 스펙 C4). */
enum class CongestionLevel(val score: Int) { SMOOTH(1), NORMAL(2), CONGESTED(3) }

/** 운전자 한 명이 남긴 입력(혼잡도 스펙 C3). */
data class CongestionReport(val parkingId: String, val level: CongestionLevel, val reportedAt: Instant)

/** 최근 입력의 평균(혼잡도 스펙 C4). 입력이 없으면 이 값 자체가 null이다. */
data class Congestion(val level: CongestionLevel, val reportCount: Int)

/** 도착 후보: 안내 중이던 주차장과 내비 목적지가 없어진 시각(혼잡도 스펙 C1). */
data class PendingArrival(val parkingId: String, val parkingName: String, val at: Instant)

/** 도착 후보를 두고 지금 혼잡도를 물을지(혼잡도 스펙 C2). */
enum class ArrivalPrompt { ASK, WAIT, DROP }
