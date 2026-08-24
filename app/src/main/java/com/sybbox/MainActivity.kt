package com.sybbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sybbox.ui.home.HomeScreen
import com.sybbox.ui.home.HomeViewModel
import com.sybbox.ui.logs.LogsScreen
import com.sybbox.ui.navigation.Screen
import com.sybbox.ui.navigation.bottomNavItems
import com.sybbox.ui.routing.PerAppScreen
import com.sybbox.ui.scanner.ScannerScreen
import com.sybbox.ui.servers.ServersScreen
import com.sybbox.ui.servers.ServersViewModel
import com.sybbox.ui.settings.SettingsScreen
import com.sybbox.ui.settings.SettingsViewModel
import com.sybbox.ui.theme.LocaleHelper
import com.sybbox.ui.theme.SYBboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun attachBaseContext(newBase: android.content.Context) {
        val tag = LocaleHelper.storedTag(newBase)
        if (tag.isEmpty()) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = java.util.Locale.forLanguageTag(tag)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        requestNotificationPermission()
        setContent { AppContent() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppContent(settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()

    SYBboxTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val snackbarHost = remember { SnackbarHostState() }
        val context = LocalContext.current

        val fullScreenRoutes = setOf(Screen.Logs.route, Screen.PerApp.route, Screen.Scanner.route)
        val showBottomBar = currentRoute !in fullScreenRoutes

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                if (showBottomBar) {
                    Surface(
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            modifier = Modifier.navigationBarsPadding(),
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.screen.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            item.icon,
                                            contentDescription = null,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            stringResource(item.title),
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = if (selected) 0.2.sp else 0.sp,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp)) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it / 8 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + fadeIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 8 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + fadeOut(
                            animationSpec = tween(120),
                        ) + scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(120),
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -it / 8 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + fadeIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it / 8 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + fadeOut(tween(120)) + scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(120),
                        )
                    },
                ) {
                    composable(Screen.Home.route) {
                        val viewModel: HomeViewModel = hiltViewModel()
                        LaunchedEffect(viewModel) {
                            viewModel.messages.collect { snackbarHost.showSnackbar(it.resolve(context)) }
                        }
                        HomeScreen(
                            onBrowseServers = {
                                navController.navigate(Screen.Servers.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            viewModel = viewModel,
                        )
                    }
                    composable(Screen.Servers.route) {
                        val viewModel: ServersViewModel = hiltViewModel()
                        LaunchedEffect(viewModel) {
                            viewModel.messages.collect { snackbarHost.showSnackbar(it.resolve(context)) }
                        }
                        ServersScreen(
                            onScanQr = { navController.navigate(Screen.Scanner.route) },
                            viewModel = viewModel,
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onOpenLogs = { navController.navigate(Screen.Logs.route) },
                            onOpenPerApp = { navController.navigate(Screen.PerApp.route) },
                            viewModel = settingsViewModel,
                        )
                    }
                    composable(Screen.Logs.route) {
                        LogsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.PerApp.route) {
                        PerAppScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Scanner.route) {
                        ScannerDestination(onFinished = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun ScannerDestination(onFinished: () -> Unit) {
    val viewModel: ServersViewModel = hiltViewModel()
    ScannerScreen(
        onScanned = { result ->
            viewModel.importText(result)
            onFinished()
        },
        onDismiss = onFinished,
    )
}
