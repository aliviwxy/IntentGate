package com.intentgate.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("MainActivity", MODE_PRIVATE) }
    data class AppChoice(val packageName: String, val label: String)

    @Suppress("DEPRECATION")
    private fun installedApps(): List<AppChoice> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) null else AppChoice(pkg, info.loadLabel(packageManager).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = installedApps()
        setContent {
            val colors = lightColorScheme(
                primary = Color(0xFF315C9B),
                onPrimary = Color.White,
                secondary = Color(0xFF527A9D),
                background = Color(0xFFF4F6FA),
                surface = Color.White
            )
            MaterialTheme(colorScheme = colors) {
                var tab by remember { mutableIntStateOf(0) }
                var selected by remember {
                    mutableStateOf(prefs.getStringSet("protected_packages", setOf("org.telegram.messenger"))?.toSet() ?: emptySet())
                }
                var customText by remember { mutableStateOf(prefs.getString("custom_prompt_text", "").orEmpty()) }
                var history by remember { mutableStateOf(UsageHistory.read(this)) }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("⌂") }, label = { Text("خانه") })
                            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("▦") }, label = { Text("برنامه‌ها") })
                            NavigationBarItem(selected = tab == 2, onClick = { tab = 2; history = UsageHistory.read(this@MainActivity) }, icon = { Text("▤") }, label = { Text("آمار") })
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> HomeTab(
                            modifier = Modifier.padding(padding),
                            customText = customText,
                            onCustomTextChange = {
                                customText = it
                                prefs.edit().putString("custom_prompt_text", it).apply()
                            },
                            selectedCount = selected.size,
                            onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        )
                        1 -> AppsTab(
                            modifier = Modifier.padding(padding),
                            apps = apps,
                            selected = selected,
                            onSelectedChange = { next ->
                                selected = next
                                prefs.edit().putStringSet("protected_packages", next.toSet()).apply()
                            }
                        )
                        else -> StatsTab(Modifier.padding(padding), history)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTab(
    modifier: Modifier,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    selectedCount: Int,
    onAccessibility: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("IntentGate", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("برای استفاده آگاهانه‌تر از برنامه‌ها", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت تنظیمات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$selectedCount برنامه تحت کنترل انتخاب شده است.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("تنظیم دسترسی‌پذیری") }
                Text("برای فعال شدن پرسش هنگام باز کردن برنامه‌ها، سرویس IntentGate را در تنظیمات دسترس‌پذیری روشن کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("پیام دلخواه صفحه پرسش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = customText,
                    onValueChange = onCustomTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("مثلاً: اگر حوصله‌ات سر رفته، می‌توانی کمی قدم بزنی یا کتاب بخوانی…") },
                    supportingText = { Text("این پیام بالای کادر وارد کردن دلیل استفاده نمایش داده می‌شود.") }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text("اطلاعات استفاده فقط روی همین دستگاه ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppsTab(
    modifier: Modifier,
    apps: List<MainActivity.AppChoice>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val visibleApps = remember(apps, query) { apps.filter { it.label.contains(query, ignoreCase = true) } }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text("برنامه‌های تحت کنترل", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("هر برنامه‌ای را انتخاب کنی، پیش از ورود دلیل و مدت استفاده پرسیده می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("جست‌وجوی برنامه") })
        Spacer(Modifier.height(8.dp))
        Text("${selected.size} برنامه انتخاب شده", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(visibleApps, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked = app.packageName in selected, onCheckedChange = { checked ->
                        onSelectedChange(if (checked) selected + app.packageName else selected - app.packageName)
                    })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsTab(modifier: Modifier, history: List<UsageHistory.Visit>) {
    var filter by remember { mutableStateOf("همه") }
    val appNames = listOf("همه") + history.map { it.appName }.distinct().sorted()
    val filtered = remember(history, filter) { if (filter == "همه") history else history.filter { it.appName == filter } }
    val totalDuration = filtered.sumOf { it.duration }
    val totalEntries = filtered.size
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text("آمار استفاده", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("تعداد ورود", totalEntries.toString(), Modifier.weight(1f))
            SummaryCard("زمان کل", formatDuration(totalDuration), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = filter, onValueChange = {}, readOnly = true, label = { Text("فیلتر برنامه") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                appNames.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { filter = name; expanded = false }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("هنوز سابقه‌ای ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                val grouped = filtered.groupBy { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(it.start)) }
                grouped.forEach { (day, visits) ->
                    item(key = "day-$day") {
                        val dayTotal = visits.sumOf { it.duration }
                        Text("$day  •  ${visits.size} ورود  •  ${formatDuration(dayTotal)}", modifier = Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    items(visits, key = { "${it.packageName}-${it.start}" }) { visit ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(visit.start))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(visit.appName, fontWeight = FontWeight.SemiBold)
                                    Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("دلیل: ${visit.purpose.ifBlank { "ثبت نشده" }}", style = MaterialTheme.typography.bodyMedium)
                                Text("مدت: ${formatDuration(visit.duration)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    val minutes = seconds / 60L
    return if (minutes > 0) "${minutes} دقیقه و ${seconds % 60} ثانیه" else "${seconds} ثانیه"
}
