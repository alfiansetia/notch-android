package app.notch.android.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.notch.android.actions.DndController
import app.notch.android.data.NotchSettings
import app.notch.android.data.SettingsRepository
import app.notch.android.media.MediaObserver
import app.notch.android.overlay.NotchService
import app.notch.android.timer.TimerAlarm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, NotchSettings())
    fun setHidden(v: Boolean) = viewModelScope.launch { repo.setHidden(v) }
    fun setBoot(v: Boolean) = viewModelScope.launch { repo.setStartAtBoot(v) }
    fun setPill(v: Int) = viewModelScope.launch { repo.setPillSize(v) }
    fun setY(v: Int) = viewModelScope.launch { repo.setYOffset(v) }
    fun setCollapse(v: Int) = viewModelScope.launch { repo.setAutoCollapse(v) }
    fun setInbox(v: Boolean) = viewModelScope.launch { repo.setInboxEnabled(v) }
    fun setRetention(d: Int) = viewModelScope.launch { repo.setInboxRetention(d) }
    fun setTodo(v: Boolean) = viewModelScope.launch { repo.setTodoEnabled(v) }
    fun setHydrate(v: Boolean) = viewModelScope.launch { repo.setHydrateEnabled(v) }
    fun setHydrateCad(m: Int) = viewModelScope.launch { repo.setHydrateCadence(m) }
    fun setEye(v: Boolean) = viewModelScope.launch { repo.setEyeEnabled(v) }
    fun setEyeCad(m: Int) = viewModelScope.launch { repo.setEyeCadence(m) }
}

class SettingsActivity : ComponentActivity() {

    private lateinit var vm: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository(applicationContext)
        vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(repo) as T
        })[SettingsViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val s by vm.settings.collectAsState()
                    val canOverlay = Settings.canDrawOverlays(this)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Notch Settings", style = MaterialTheme.typography.headlineSmall)

                        if (!canOverlay) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Overlay permission dibutuhkan (AFR-4.4)")
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:$packageName")
                                            )
                                        )
                                    }) { Text("Grant overlay") }
                                }
                            }
                        }

                        if (s.hidden) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Notch is hidden")
                                    Button(onClick = {
                                        vm.setHidden(false)
                                        startForegroundService(Intent(this@SettingsActivity, NotchService::class.java))
                                    }) { Text("Restore") }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Grants (P3)", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                val mediaOk = MediaObserver.isListenerEnabled(this@SettingsActivity)
                                RowGrant(
                                    label = if (mediaOk) "Media: granted" else "Media: butuh Notification access",
                                    btn = if (mediaOk) null else "Grant"
                                ) { startActivity(MediaObserver.grantIntent()) }
                                val dndOk = DndController.hasAccess(this@SettingsActivity)
                                RowGrant(
                                    label = if (dndOk) "DND toggle: ready" else "DND: butuh Policy access",
                                    btn = if (dndOk) null else "Grant"
                                ) { startActivity(DndController.grantIntent()) }
                                val exactOk = TimerAlarm.canExact(this@SettingsActivity)
                                RowGrant(
                                    label = if (exactOk) "Timer: exact alarm ok"
                                    else "Timer: inexact (AFR-11.2)",
                                    btn = if (exactOk) null else "Grant"
                                ) { startActivity(TimerAlarm.exactGrantIntent()) }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                RowToggle("Start at boot", s.startAtBoot) { vm.setBoot(it) }
                                RowToggle("Hide pill", s.hidden) {
                                    vm.setHidden(it)
                                    startService(
                                        Intent(this@SettingsActivity, NotchService::class.java)
                                            .setAction(if (it) NotchService.ACTION_HIDE else NotchService.ACTION_SHOW)
                                    )
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Pill size: ${listOf("Compact", "Regular", "Large")[s.pillSize]}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Compact", "Regular", "Large").forEachIndexed { i, name ->
                                        OutlinedButton(onClick = { vm.setPill(i) }) { Text(name) }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Y offset: ${s.yOffsetDp}dp (0–24)")
                                Slider(
                                    value = s.yOffsetDp.toFloat(),
                                    onValueChange = { vm.setY(it.toInt()) },
                                    valueRange = 0f..24f, steps = 23
                                )
                                Text("Auto-collapse: ${if (s.autoCollapseSecs == 0) "off" else "${s.autoCollapseSecs}s"}")
                                Slider(
                                    value = s.autoCollapseSecs.toFloat(),
                                    onValueChange = { vm.setCollapse(it.toInt()) },
                                    valueRange = 0f..30f, steps = 29
                                )
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Inbox & Todo (P4)", style = MaterialTheme.typography.titleSmall)
                                RowToggle("Inbox enabled", s.inboxEnabled) { vm.setInbox(it) }
                                Text("Retention: ${s.inboxRetentionDays} hari")
                                Slider(
                                    value = s.inboxRetentionDays.toFloat(),
                                    onValueChange = { vm.setRetention(it.toInt()) },
                                    valueRange = 1f..90f, steps = 88
                                )
                                RowToggle("Todo enabled", s.todoEnabled) { vm.setTodo(it) }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Reminders (P4)", style = MaterialTheme.typography.titleSmall)
                                RowToggle("Hydrate", s.hydrateEnabled) { vm.setHydrate(it) }
                                Text("Cadence: ${s.hydrateCadenceMins} mnt (15–240)")
                                Slider(
                                    value = s.hydrateCadenceMins.toFloat(),
                                    onValueChange = { vm.setHydrateCad(it.toInt()) },
                                    valueRange = 15f..240f, steps = 14
                                )
                                RowToggle("Eye break 20-20-20", s.eyeEnabled) { vm.setEye(it) }
                                Text("Cadence: ${s.eyeCadenceMins} mnt (5–120)")
                                Slider(
                                    value = s.eyeCadenceMins.toFloat(),
                                    onValueChange = { vm.setEyeCad(it.toInt()) },
                                    valueRange = 5f..120f, steps = 22
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                startForegroundService(Intent(this@SettingsActivity, NotchService::class.java))
                            }) { Text("Start notch") }
                            OutlinedButton(onClick = {
                                startService(
                                    Intent(this@SettingsActivity, NotchService::class.java)
                                        .setAction(NotchService.ACTION_COLLAPSE)
                                )
                            }) { Text("Collapse") }
                        }
                    }
                }
            }
        }
    }

    // canDrawOverlays dibaca tiap composition; refresh via onRestart bawaan
    // (tanpa recreate agar tidak loop).
}

@androidx.compose.runtime.Composable
private fun RowGrant(label: String, btn: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (btn != null) OutlinedButton(onClick = onClick) { Text(btn) }
    }
}

@androidx.compose.runtime.Composable
private fun RowToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
