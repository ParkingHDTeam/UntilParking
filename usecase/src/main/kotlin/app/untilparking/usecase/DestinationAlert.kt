package app.untilparking.usecase

import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.repository.DestinationAlertRepository
import app.untilparking.domain.usecase.AlertNewDestination
import app.untilparking.domain.usecase.DismissDestinationAlert

/** 목적지 알림 스펙 N1. 새 방문인지는 화면이 R7 분류로 정해서 넘긴다. */
class AlertNewDestinationImpl(private val alerts: DestinationAlertRepository) : AlertNewDestination {

    override fun invoke(change: DestinationChange, next: Destination?, appVisible: Boolean) {
        if (change != DestinationChange.NEW_VISIT) return
        // 목적지가 없어졌으면 떠 있는 알림은 낡은 것이다
        if (next == null) alerts.clear() else if (!appVisible) alerts.show(next)
    }
}

class DismissDestinationAlertImpl(private val alerts: DestinationAlertRepository) : DismissDestinationAlert {
    override fun invoke() = alerts.clear()
}
