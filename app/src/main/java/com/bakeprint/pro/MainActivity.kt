package com.bakeprint.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bakeprint.pro.ui.*

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        com.bakeprint.pro.print.ActivityHolder.current = java.lang.ref.WeakReference(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.bakeprint.pro.print.ActivityHolder.current = java.lang.ref.WeakReference(this)
        setContent {
            BakePrintTheme {
                App(viewModel())
            }
        }
    }
}

@Composable
fun App(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ur = s.lang == "ur"
    CompositionLocalProvider(
        LocalUr provides ur,
        LocalLayoutDirection provides if (ur) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        var tab by rememberSaveable { mutableStateOf("home") }
        var printJobs by remember { mutableStateOf<List<LabelJob>?>(null) }
        var showBulk by remember { mutableStateOf(false) }
        var showName by remember { mutableStateOf(false) }
        val snack = remember { SnackbarHostState() }

        LaunchedEffect(vm.toast) {
            vm.toast?.let { snack.showSnackbar(it); vm.toast = null }
        }
        LaunchedEffect(vm.ready, s.bakeryName) {
            if (vm.ready && s.bakeryName.isBlank()) showName = true
        }

        val nav = Nav(
            go = { tab = it },
            print = { jobs -> if (s.bakeryName.isBlank()) showName = true else printJobs = jobs },
            bulk = { showBulk = true },
            editName = { showName = true },
        )

        Scaffold(
            containerColor = BP.Ivory,
            snackbarHost = { SnackbarHost(snack) { Snackbar(it, containerColor = BP.Choc, contentColor = BP.White) } },
            bottomBar = { BottomBar(tab) { tab = it } },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (!vm.ready) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BP.Gold)
                        Spacer(Modifier.height(12.dp))
                        Text("BAKEPRINT PRO", fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    }
                } else when (tab) {
                    "products" -> ProductsScreen(vm, nav)
                    "label" -> LabelScreen(vm, nav)
                    "history" -> HistoryScreen(vm, nav)
                    "settings" -> SettingsScreen(vm, nav)
                    else -> HomeScreen(vm, nav)
                }
            }
        }

        if (showName) NameDialog(s.bakeryName, onDismiss = { if (s.bakeryName.isNotBlank()) showName = false }) {
            vm.set("bakeryName", it); showName = false
        }
        printJobs?.let { jobs -> PrintSheet(vm, jobs, onDismiss = { printJobs = null }) }
        if (showBulk) BulkSheet(vm, onDismiss = { showBulk = false }) { jobs -> showBulk = false; nav.print(jobs) }
        vm.printState?.let { PrintProgressDialog(it) { vm.printState = null; if (it.finished) printJobs = null } }
    }
}

@Composable
private fun BottomBar(tab: String, onTab: (String) -> Unit) {
    val items = listOf(
        Triple("home", Icons.Rounded.Home, tr("Home", "ہوم")),
        Triple("products", Icons.Rounded.GridView, tr("Products", "پروڈکٹس")),
        Triple("label", Icons.Rounded.LocalOffer, tr("Create Label", "لیبل")),
        Triple("history", Icons.Rounded.History, tr("History", "ہسٹری")),
        Triple("settings", Icons.Rounded.Settings, tr("Settings", "سیٹنگز")),
    )
    Surface(color = BP.White, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(74.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (key, icon, label) ->
                NavItem(Modifier.weight(1f), icon, label, tab == key, center = key == "label") { onTab(key) }
            }
        }
    }
}

@Composable
private fun NavItem(modifier: Modifier, icon: ImageVector, label: String, selected: Boolean, center: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxHeight(), color = BP.White) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (center) {
                Surface(shape = RoundedCornerShape(16.dp), color = BP.Choc, modifier = Modifier.size(48.dp).shadow(6.dp, RoundedCornerShape(16.dp))) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = BP.Gold, modifier = Modifier.size(24.dp)) }
                }
            } else {
                Icon(icon, label, tint = if (selected) BP.Gold else BP.Grey, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected || center) BP.Choc else BP.Grey,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
