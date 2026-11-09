package com.cloud.launcher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloud.launcher.ui.LauncherViewModel
import com.cloud.launcher.ui.components.SetupDialog
import com.cloud.launcher.ui.screens.AppDrawerScreen
import com.cloud.launcher.ui.screens.CustomRecentsScreen
import com.cloud.launcher.ui.screens.HomeScreen
import com.cloud.launcher.ui.screens.ProtectedAppsScreen
import com.cloud.launcher.ui.theme.CloudBlack
import com.cloud.launcher.ui.theme.CloudLauncherTheme
import com.cloud.launcher.utils.PermissionUtils

object Routes {
    const val HOME = "home"
    const val DRAWER = "drawer"
    const val RECENTS = "recents"
    const val PROTECTED = "protected"
}

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CloudLauncherTheme {
                LauncherRoot(vm = vm, homeSignal = homeSignal.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // زر Home أثناء عمل اللانشر: العودة إلى الشاشة الرئيسية
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignal.intValue = homeSignal.intValue + 1
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
        vm.syncRecents()
    }
}

@Composable
fun LauncherRoot(vm: LauncherViewModel, homeSignal: Int) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val showSetup by vm.showSetup.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refreshPermissions() }

    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) nav.popBackStack(Routes.HOME, false)
    }

    Box(modifier = Modifier.fillMaxSize().background(CloudBlack)) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onOpenDrawer = { nav.navigate(Routes.DRAWER) { launchSingleTop = true } },
                    onOpenRecents = { nav.navigate(Routes.RECENTS) { launchSingleTop = true } },
                    onOpenProtected = { nav.navigate(Routes.PROTECTED) { launchSingleTop = true } }
                )
            }
            composable(
                route = Routes.DRAWER,
                enterTransition = {
                    fadeIn(tween(250)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                },
                popExitTransition = {
                    fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180))
                }
            ) {
                AppDrawerScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.RECENTS) {
                CustomRecentsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.PROTECTED) {
                ProtectedAppsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }

    if (showSetup) {
        SetupDialog(
            vm = vm,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionUtils.openNotificationSettings(context)
                }
            },
            onDismiss = { vm.finishSetup() }
        )
    }
}
