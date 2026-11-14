package app.untilparking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.untilparking.feature.parking.ParkingRoute
import app.untilparking.feature.parking.ParkingViewModel
import app.untilparking.feature.parking.UntilParkingTheme
import app.untilparking.mock.MockControls
import app.untilparking.mock.MockPanel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 단일 Activity. 화면 전환은 ViewModel 상태의 screen이 정한다(기획서 27절). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** mock 플레이버에서만 패널이 보인다. pleos 플레이버는 아무것도 그리지 않는 구현을 받는다. */
    @Inject
    lateinit var mockControls: MockControls

    private val viewModel: ParkingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 이 앱이 쓰는 Pleos 권한(NAVI_*, STT·TTS)은 실행 중 요청이 필요한 dangerous 권한이 아니다.
        // 알림 권한만 묻는다(목적지 알림 스펙 N1). 거절해도 앱 동작은 같고, 두 번 거절하면 시스템이 더 묻지 않는다.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            UntilParkingTheme {
                Column(Modifier.fillMaxSize()) {
                    MockPanel(mockControls)
                    ParkingRoute(viewModel, Modifier.weight(1f))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppVisible(true)
    }

    override fun onStop() {
        viewModel.onAppVisible(false)
        super.onStop()
    }
}
