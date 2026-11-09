package app.untilparking.data.driving

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.repository.DrivingStateRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 주행·정차는 앱이 판정하지 않는다. 플랫폼이 보낸 주행 규제 상태를 그대로 따른다(스펙 R9).
 * 차량 서비스가 없는 기기에서는 UNKNOWN이다. 신호 불가를 정차로 간주하지 않는다.
 */
class CarUxDrivingStateRepository(private val context: Context) : DrivingStateRepository {

    override val state: Flow<DrivingState> = callbackFlow {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            trySend(DrivingState.UNKNOWN)
            awaitClose()
            return@callbackFlow
        }
        // createCar는 차량 서비스에 못 붙으면 null을 돌려준다(API 문서 "otherwise null"). 그때도 정차가 아니라 UNKNOWN이다.
        val car = Car.createCar(context.applicationContext)
        val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (car == null || manager == null) {
            trySend(DrivingState.UNKNOWN)
            awaitClose { car?.disconnect() }
            return@callbackFlow
        }
        trySend(manager.currentCarUxRestrictions?.toState() ?: DrivingState.UNKNOWN)
        manager.registerListener { restrictions -> trySend(restrictions.toState()) }
        awaitClose {
            manager.unregisterListener()
            car.disconnect()
        }
    }.distinctUntilChanged()

    private fun CarUxRestrictions.toState(): DrivingState =
        if (isRequiresDistractionOptimization) DrivingState.MOVING else DrivingState.PARKED
}
