package app.untilparking.data.vehicle

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.untilparking.domain.model.MirrorBasis
import app.untilparking.domain.model.RoofBox
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleProfile
import app.untilparking.domain.model.WeightBasis
import app.untilparking.domain.repository.VehicleProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vehicleDataStore: DataStore<Preferences> by preferencesDataStore(name = "vehicle_profile")

/** 차량 프로필을 DataStore에 원자적으로 저장한다. 저장마다 version이 1 오른다(스펙 R8). */
class DataStoreVehicleProfileRepository(context: Context) : VehicleProfileRepository {

    private val store = context.applicationContext.vehicleDataStore

    override val profile: Flow<VehicleProfile> = store.data.map { p ->
        VehicleProfile(
            heightMm = p[HEIGHT],
            widthMm = p[WIDTH],
            lengthMm = p[LENGTH],
            weightKg = p[WEIGHT],
            weightBasis = p[WEIGHT_BASIS].toEnumOrDefault(WeightBasis.UNKNOWN),
            mirrorBasis = p[MIRROR_BASIS].toEnumOrDefault(MirrorBasis.EXCLUDING),
            suv = p[SUV].toEnumOrDefault(SuvFlag.UNKNOWN),
            roofBox = p[ROOF_BOX].toEnumOrDefault(RoofBox.UNKNOWN),
            version = p[VERSION] ?: 0,
        )
    }

    override suspend fun save(profile: VehicleProfile) {
        store.edit { e ->
            e.put(HEIGHT, profile.heightMm)
            e.put(WIDTH, profile.widthMm)
            e.put(LENGTH, profile.lengthMm)
            e.put(WEIGHT, profile.weightKg)
            e[WEIGHT_BASIS] = profile.weightBasis.name
            e[MIRROR_BASIS] = profile.mirrorBasis.name
            e[SUV] = profile.suv.name
            e[ROOF_BOX] = profile.roofBox.name
            e[VERSION] = (e[VERSION] ?: 0) + 1
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.put(key: Preferences.Key<Int>, value: Int?) {
        if (value == null) remove(key) else set(key, value)
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private companion object {
        val HEIGHT = intPreferencesKey("height_mm")
        val WIDTH = intPreferencesKey("width_mm")
        val LENGTH = intPreferencesKey("length_mm")
        val WEIGHT = intPreferencesKey("weight_kg")
        val WEIGHT_BASIS = stringPreferencesKey("weight_basis")
        val MIRROR_BASIS = stringPreferencesKey("mirror_basis")
        val SUV = stringPreferencesKey("suv")
        val ROOF_BOX = stringPreferencesKey("roof_box")
        val VERSION = intPreferencesKey("version")
    }
}
