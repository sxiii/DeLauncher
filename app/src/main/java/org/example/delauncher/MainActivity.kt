package org.example.delauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.view.View
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.heightIn
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LaunchableApp(val label: String, val packageName: String)
private const val PREFS = "delauncher_preferences"
private const val KEY_FAVORITES = "favorite_packages"
private const val KEY_DARK = "dark_theme"
private const val KEY_ACCENT = "accent"
private const val KEY_WIDGET_ID = "top_widget_id"
private const val APPWIDGET_HOST_ID = 0xD31A

class MainActivity : ComponentActivity() {
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private val widgetIdState = mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID)
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        widgetIdState.intValue = prefs.getInt(KEY_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        appWidgetHost.startListening()
        setContent {
            LauncherScreen(
                apps = loadApps(),
                widgetId = widgetIdState.intValue,
                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,
                onAddWidget = { beginAddWidget() },
                onRemoveWidget = { removeWidget() }
            )
        }
    }

    private var pendingPickerId = AppWidgetManager.INVALID_APPWIDGET_ID

    private fun beginAddWidget() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingPickerId = appWidgetId
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(pickIntent, REQUEST_PICK_WIDGET)
    }

    private fun removeWidget() {
        val id = widgetIdState.intValue
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetHost.deleteAppWidgetId(id)
            prefs.edit().remove(KEY_WIDGET_ID).apply()
            widgetIdState.intValue = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    @Deprecated("Uses Android's app-widget picker activity for broad platform compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val id = pendingWidgetId
            if (resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) saveWidget(id)
            else if (id != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(id)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            return
        }
        if (requestCode != REQUEST_PICK_WIDGET) return
        val newId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (resultCode != RESULT_OK || newId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val idToDelete = if (newId != AppWidgetManager.INVALID_APPWIDGET_ID) newId else pendingPickerId
            if (idToDelete != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(idToDelete)
            pendingPickerId = AppWidgetManager.INVALID_APPWIDGET_ID
            return
        }
        pendingPickerId = AppWidgetManager.INVALID_APPWIDGET_ID
        val info = appWidgetManager.getAppWidgetInfo(newId)
        if (info == null) {
            appWidgetHost.deleteAppWidgetId(newId)
            return
        }
        if (info.configure != null) {
            val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(configureIntent, REQUEST_CONFIGURE_WIDGET)
            pendingWidgetId = newId
        } else {
            saveWidget(newId)
        }
    }

    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private fun saveWidget(id: Int) {
        widgetIdState.intValue = id
        prefs.edit().putInt(KEY_WIDGET_ID, id).apply()
    }

    override fun onDestroy() {
        try { appWidgetHost.stopListening() } catch (_: Exception) { }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun loadApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                val pkg = info.activityInfo?.packageName.orEmpty()
                if (label.isBlank() || pkg == packageName) null else LaunchableApp(label, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    companion object {
        private const val REQUEST_PICK_WIDGET = 7101
        private const val REQUEST_CONFIGURE_WIDGET = 7102
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherScreen(
    apps: List<LaunchableApp>,
    widgetId: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onAddWidget: () -> Unit,
    onRemoveWidget: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var manageFavorites by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, true)) }
    var accentChoice by remember { mutableIntStateOf(prefs.getInt(KEY_ACCENT, 0)) }
    val accents = listOf(Color(0xFFB8D7A3), Color(0xFFB7C7FF), Color(0xFFFFC1A8), Color(0xFFE5B8FF))
    val accent = accents[accentChoice.coerceIn(accents.indices)]
    val bg = if (dark) Color(0xFF101114) else Color(0xFFF5F4F0)
    val fg = if (dark) Color(0xFFF4F3F0) else Color(0xFF202126)
    val muted = if (dark) Color(0xFF9D9DA4) else Color(0xFF686870)
    val panel = if (dark) Color(0xFF202126) else Color(0xFFE8E7E2)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }

    var favoritePackages by remember {
        mutableStateOf(prefs.getStringSet(KEY_FAVORITES, null)?.toList() ?: defaultFavorites(apps))
    }
    fun saveFavorites(next: List<String>) {
        favoritePackages = next.distinct()
        prefs.edit().putStringSet(KEY_FAVORITES, favoritePackages.toSet()).apply()
    }

    val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
    val grouped = filtered.groupBy { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
    val letters = grouped.keys.toList()
    val rowIndexByLetter = buildMap {
        var index = 0
        grouped.forEach { (letter, letterApps) ->
            put(letter, index)
            index += 1 + letterApps.size
        }
    }
    val favoriteApps = favoritePackages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

    Surface(modifier = Modifier.fillMaxSize(), color = bg, contentColor = fg) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(start = 25.dp, end = 28.dp, top = 34.dp, bottom = 14.dp)) {
                // The built-in top-area widget: live clock and date, refreshed while the launcher is open.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), fontSize = 48.sp,
                            fontWeight = FontWeight.Light, letterSpacing = (-2).sp, color = fg)
                        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now).replaceFirstChar { it.uppercase() },
                            fontSize = 15.sp, color = muted, modifier = Modifier.padding(top = 1.dp))
                    }
                    Text("⚙", fontSize = 23.sp, color = muted, modifier = Modifier.clip(CircleShape)
                        .clickable { showSettings = !showSettings }.padding(8.dp))
                }

                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Spacer(Modifier.height(10.dp))
                    key(widgetId) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 260.dp),
                            factory = { viewContext ->
                                val info = appWidgetManager.getAppWidgetInfo(widgetId)
                                if (info != null) appWidgetHost.createView(viewContext, widgetId, info).apply {
                                    setAppWidget(widgetId, info)
                                } else View(viewContext)
                            },
                            update = { view ->
                                if (view is AppWidgetHostView) view.setAppWidget(widgetId, appWidgetManager.getAppWidgetInfo(widgetId))
                            }
                        )
                    }
                }

                if (showSettings) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(14.dp)) {
                        Text("Appearance", color = fg, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (dark) "Dark theme" else "Light theme", color = muted, modifier = Modifier.weight(1f))
                            Text("Toggle", color = accent, modifier = Modifier.clickable {
                                dark = !dark; prefs.edit().putBoolean(KEY_DARK, dark).apply()
                            }.padding(6.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Accent", color = muted)
                            accents.forEachIndexed { i, color ->
                                Box(Modifier.size(25.dp).clip(CircleShape).background(color)
                                    .clickable { accentChoice = i; prefs.edit().putInt(KEY_ACCENT, i).apply() })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Top-area widget", color = fg, fontWeight = FontWeight.SemiBold)
                        Text("Add any widget provided by an installed Android app.", color = muted, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp, bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("＋ Add widget", color = accent, modifier = Modifier.clickable(onClick = onAddWidget).padding(vertical = 5.dp))
                            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                Text("Remove widget", color = muted, modifier = Modifier.clickable(onClick = onRemoveWidget).padding(vertical = 5.dp))
                            }
                        }
                        Text("Favorites are saved on this device.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Favourites", fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (manageFavorites) "Done" else "Edit", fontSize = 13.sp, color = accent,
                        modifier = Modifier.clickable { manageFavorites = !manageFavorites }.padding(6.dp))
                }
                if (favoriteApps.isEmpty()) {
                    Text("Tap Edit, then add apps below.", color = muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    favoriteApps.forEachIndexed { index, app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppRow(app, fg, accent, Modifier.weight(1f)) { launch(context, app) }
                            if (manageFavorites) {
                                Text("−", fontSize = 22.sp, color = accent, modifier = Modifier.clickable {
                                    saveFavorites(favoritePackages.filterNot { it == app.packageName })
                                }.padding(horizontal = 8.dp))
                                Text("↑", fontSize = 16.sp, color = muted, modifier = Modifier.clickable {
                                    if (index > 0) {
                                        val next = favoritePackages.toMutableList()
                                        val current = next.indexOf(app.packageName)
                                        if (current > 0) { val item = next.removeAt(current); next.add(current - 1, item); saveFavorites(next) }
                                    }
                                }.padding(horizontal = 5.dp))
                                Text("↓", fontSize = 16.sp, color = muted, modifier = Modifier.clickable {
                                    val next = favoritePackages.toMutableList(); val current = next.indexOf(app.packageName)
                                    if (current >= 0 && current < next.lastIndex) { val item = next.removeAt(current); next.add(current + 1, item); saveFavorites(next) }
                                }.padding(horizontal = 5.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = fg, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(panel).padding(horizontal = 14.dp, vertical = 12.dp)) {
                            if (query.isEmpty()) Text(if (manageFavorites) "Search to add favourites" else "Search apps", color = muted, fontSize = 16.sp)
                            inner()
                        }
                    }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(end = 8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    grouped.forEach { (letter, letterApps) ->
                        stickyHeader(key = "header-$letter") {
                            Text(letter, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().background(bg).padding(top = 8.dp, bottom = 3.dp))
                        }
                        items(letterApps, key = { it.packageName }) { app ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppRow(app, fg, accent, Modifier.weight(1f)) { launch(context, app) }
                                if (manageFavorites) {
                                    val isFavorite = app.packageName in favoritePackages
                                    Text(if (isFavorite) "✓" else "+", color = accent, fontSize = 21.sp,
                                        modifier = Modifier.clickable {
                                            saveFavorites(if (isFavorite) favoritePackages.filterNot { it == app.packageName }
                                            else favoritePackages + app.packageName)
                                        }.padding(horizontal = 9.dp, vertical = 7.dp))
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) item { Text("No apps found", color = muted, modifier = Modifier.padding(16.dp)) }
                }
            }

            // Drag vertically along the right edge to jump to a letter; tapping a letter also works.
            if (letters.isNotEmpty() && query.isEmpty()) {
                Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight(0.72f).width(24.dp)
                    .padding(vertical = 8.dp).pointerInput(letters, rowIndexByLetter) {
                        detectDragGestures(onDragStart = { offset ->
                            val i = ((offset.y / size.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                            scope.launch { listState.scrollToItem(rowIndexByLetter[letters[i]] ?: 0) }
                        }, onDrag = { change, _ ->
                            val i = ((change.position.y / size.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                            scope.launch { listState.scrollToItem(rowIndexByLetter[letters[i]] ?: 0) }
                        })
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                    letters.forEach { letter ->
                        Text(letter, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { scope.launch { listState.scrollToItem(rowIndexByLetter[letter] ?: 0) } })
                    }
                }
            }
        }
    }
}

private fun defaultFavorites(apps: List<LaunchableApp>): List<String> {
    val preferred = listOf("phone", "messages", "camera", "chrome")
    return preferred.mapNotNull { key -> apps.firstOrNull { it.label.contains(key, ignoreCase = true) }?.packageName }.distinct().take(4)
}

@Composable
private fun AppRow(app: LaunchableApp, foreground: Color, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        try { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }
        catch (_: Exception) { null }
    }
    Row(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .padding(vertical = 7.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (iconBitmap != null) Image(bitmap = iconBitmap, contentDescription = "${app.label} icon", modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
        else Box(Modifier.size(34.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = foreground)
        }
        Text(app.label, fontSize = 16.sp, modifier = Modifier.padding(start = 13.dp), fontWeight = FontWeight.Normal, color = foreground)
    }
}

private fun launch(context: Context, app: LaunchableApp) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
