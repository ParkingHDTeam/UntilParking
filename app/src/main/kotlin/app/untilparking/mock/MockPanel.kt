package app.untilparking.mock

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.untilparking.domain.model.DrivingState

/** mock 플레이버에서만 보이는 띠. 주행 상태·내비 목적지·만차·음성 입력을 가상으로 바꾼다. */
@Composable
fun MockPanel(controls: MockControls) {
    if (!controls.available) return
    var open by remember { mutableStateOf(false) }
    var driving by remember { mutableStateOf(DrivingState.PARKED) }
    var destination by remember { mutableStateOf(true) }
    var full by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    val spoken by controls.spoken.collectAsState(initial = null)

    Surface(color = Color(0xFF2B1F3F), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MOCK 환경", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD9B8FF))
                Spacer(Modifier.width(12.dp))
                Text(spoken?.let { "TTS: $it" } ?: "TTS 대기", fontSize = 12.sp, color = Color(0xFFBFB5CC), modifier = Modifier.weight(1f))
                TextButton(onClick = { open = !open }) { Text(if (open) "접기" else "펼치기", fontSize = 12.sp) }
            }
            if (open) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("주행 상태", fontSize = 12.sp, color = Color(0xFFBFB5CC))
                    DrivingState.entries.forEach { s ->
                        FilterChip(selected = driving == s, onClick = { driving = s; controls.setDriving(s) }, label = { Text(s.name, fontSize = 12.sp) })
                    }
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        selected = destination,
                        onClick = { destination = !destination; controls.setDestinationPresent(destination) },
                        label = { Text("내비 목적지 있음", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = full,
                        onClick = { full = !full; controls.setForcedFull("pk-001", full) },
                        label = { Text("서림 공영 만차", fontSize = 12.sp) },
                    )
                    OutlinedTextField(
                        value = voiceText,
                        onValueChange = { voiceText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp),
                        placeholder = { Text("음성 입력 (듣는 중일 때 전송)", fontSize = 12.sp) },
                        modifier = Modifier.width(320.dp),
                    )
                    TextButton(onClick = { controls.submitVoice(voiceText); voiceText = "" }) { Text("전송", fontSize = 12.sp) }
                    // 에뮬레이터 키보드로 한글을 넣기 어려워 자주 쓰는 말을 버튼으로 둔다
                    listOf("기계식 빼줘", "두 번째", "거리순", "안내 시작").forEach { phrase ->
                        FilterChip(selected = false, onClick = { controls.submitVoice(phrase) }, label = { Text(phrase, fontSize = 12.sp) })
                    }
                }
            }
        }
    }
}
