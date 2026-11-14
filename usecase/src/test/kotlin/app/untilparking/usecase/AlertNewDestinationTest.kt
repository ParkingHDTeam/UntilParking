package app.untilparking.usecase

import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.repository.DestinationAlertRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** 목적지 알림 스펙 N1. */
class AlertNewDestinationTest {

    private class FakeAlerts : DestinationAlertRepository {
        val calls = mutableListOf<String>()
        override fun show(destination: Destination) { calls += "show:${destination.name}" }
        override fun clear() { calls += "clear" }
    }

    private val alerts = FakeAlerts()
    private val alert = AlertNewDestinationImpl(alerts)

    @Test
    fun `앱이 화면에 없을 때 새 방문 목적지가 잡히면 알린다`() {
        alert(DestinationChange.NEW_VISIT, DEST, appVisible = false)
        assertEquals(listOf("show:서림문화센터"), alerts.calls)
    }

    @Test
    fun `앱이 화면에 있으면 알리지 않는다`() {
        alert(DestinationChange.NEW_VISIT, DEST, appVisible = true)
        assertEquals(emptyList<String>(), alerts.calls)
    }

    @Test
    fun `같은 목적지나 앱 자신의 경로 변경에는 알리지 않는다`() {
        alert(DestinationChange.SAME, DEST, appVisible = false)
        alert(DestinationChange.OWN_REROUTE, DEST, appVisible = false)
        assertEquals(emptyList<String>(), alerts.calls)
    }

    @Test
    fun `목적지가 없어지면 알림을 지운다`() {
        alert(DestinationChange.NEW_VISIT, null, appVisible = false)
        alert(DestinationChange.NEW_VISIT, null, appVisible = true)
        assertEquals(listOf("clear", "clear"), alerts.calls)
    }
}
