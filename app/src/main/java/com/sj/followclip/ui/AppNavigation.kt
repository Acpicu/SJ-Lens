package com.sj.followclip.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sj.followclip.data.check.CheckController
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.work.CheckScheduler
import com.sj.followclip.data.work.CheckWorker
import com.sj.followclip.util.Notifications
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sj.followclip.domain.model.Platform
import com.sj.followclip.ui.add.AddAccountScreen
import com.sj.followclip.ui.add.CreateFolderScreen
import com.sj.followclip.ui.detail.PersonDetailScreen
import com.sj.followclip.ui.detail.BiliLoginScreen
import com.sj.followclip.ui.detail.PlatformVideosScreen
import com.sj.followclip.ui.detail.PlatformWebView
import com.sj.followclip.ui.guide.OnboardingScreen
import com.sj.followclip.ui.guide.markOnboardingShown
import com.sj.followclip.ui.guide.shouldShowOnboarding
import com.sj.followclip.ui.home.HomeScreen
import com.sj.followclip.ui.ledger.LedgerOverviewScreen
import com.sj.followclip.ui.ledger.LedgerReportScreen
import com.sj.followclip.ui.ledger.LedgerScreen
import com.sj.followclip.ui.ledger.RelationGraphScreen
import com.sj.followclip.ui.osint.ExposureAuditScreen
import com.sj.followclip.ui.preview.FakeData
import com.sj.followclip.ui.settings.SettingsScreen
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.ui.unconfirmed.LatestCheckScreen
import com.sj.followclip.util.AvatarStore

/**
 * 路由表。路由字符串只允许出现在这里。
 *
 * 结构按「文件夹」来：
 * 首页（文件夹列表）→ 文件夹内（这个人的各平台账号）→ 平台作品页。
 * 新建文件夹在首页的加号；往文件夹里加平台账号，在文件夹内的加号。
 * 数据本（情报记录）按目标走：目标页 → 数据本 → 报告预览。
 */
object Routes {
    const val HOME = "home"
    const val UNCONFIRMED = "unconfirmed"
    const val SETTINGS = "settings"
    const val CREATE_FOLDER = "createFolder"
    const val BILI_LOGIN = "biliLogin"
    const val EXPOSURE_AUDIT = "exposureAudit"
    const val GUIDE = "guide"

    /** 数据本 · 全部目标（从设置页 OSINT 区进） */
    const val LEDGER_ALL = "ledgerAll"
    const val RELATION_GRAPH = "relationGraph"

    const val ARG_PERSON_ID = "personId"
    const val ARG_PLATFORM = "platform"
    const val ARG_ACCOUNT_ID = "accountId"

    const val PERSON = "person/{$ARG_PERSON_ID}"
    const val ADD_ACCOUNT = "person/{$ARG_PERSON_ID}/addAccount"
    const val PLATFORM_VIDEOS = "person/{$ARG_PERSON_ID}/account/{$ARG_ACCOUNT_ID}"
    const val PERSON_LEDGER = "person/{$ARG_PERSON_ID}/ledger"
    const val PERSON_REPORT = "person/{$ARG_PERSON_ID}/ledgerReport"

    fun person(personId: Long): String = "person/$personId"

    fun ledger(personId: Long): String = "person/$personId/ledger"

    fun report(personId: Long): String = "person/$personId/ledgerReport"

    fun addAccount(personId: Long): String = "person/$personId/addAccount"

    fun platformVideos(personId: Long, accountId: Long): String =
        "person/$personId/account/$accountId"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "首页", Icons.Filled.Home),
    BottomNavItem(Routes.UNCONFIRMED, "最近检查", Icons.Filled.Notifications),
    BottomNavItem(Routes.SETTINGS, "设置", Icons.Filled.Settings)
)

/**
 * 应用根界面：一个 Scaffold 承载底部导航与悬浮按钮，内部一个 NavHost。
 *
 * 数据来源统一走 [AppUiState]（内存状态），所以新建、添加、删除都能当场生效。
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onHome = currentRoute == Routes.HOME
    val onPerson = currentRoute == Routes.PERSON
    val currentPersonId = backStackEntry?.arguments?.getLong(Routes.ARG_PERSON_ID) ?: 0L
    val context = LocalContext.current

    AppUiState.init(context)
    // 自动检查排期：进应用排一次即可，KEEP 策略不会重置已有计时
    LaunchedEffect(Unit) {
        Notifications.ensureChannel(context)
        CheckScheduler.schedule(context)
    }

    // 后台那一轮跑不了快手（WebView 要窗口）：留了标记就趁现在补一轮
    LaunchedEffect(Unit) {
        if (AppUiState.persons.isNotEmpty() &&
            CheckWorker.consumeKuaishouPending(context)
        ) {
            CheckController.start(context, AppUiState.persons.toList(), includeWeb = true)
        }
    }

    // Android 13+ 通知权限是运行时权限，不授权则后台发现了新内容也发不出来
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            if (bottomNavItems.any { it.route == currentRoute }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            icon = {
                                // 「最近检查」带未看角标：新结果来了不点进去也知道
                                val unseen = if (item.route == Routes.UNCONFIRMED) {
                                    val ctx = LocalContext.current
                                    PendingStore.items.size
                                    PendingStore.unseenCount(ctx)
                                } else {
                                    0
                                }
                                if (unseen > 0) {
                                    BadgedBox(
                                        badge = { Badge { Text(unseen.toString()) } }
                                    ) { Icon(item.icon, contentDescription = item.label) }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when {
                onHome -> FloatingActionButton(
                    onClick = { navController.navigate(Routes.CREATE_FOLDER) }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "新建文件夹")
                }

                onPerson -> FloatingActionButton(
                    onClick = { navController.navigate(Routes.addAccount(currentPersonId)) }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加平台主页")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            // 首次启动直接进入引导页，而不是盖一层弹窗
            startDestination = if (remember { shouldShowOnboarding(context) }) Routes.GUIDE else Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    persons = AppUiState.persons,
                    onPersonClick = { personId ->
                        navController.navigate(Routes.person(personId))
                    },
                    onDeletePerson = { personId -> AppUiState.removePerson(personId) },
                    onMovePerson = { from, to -> AppUiState.movePerson(from, to) }
                )
            }

            composable(Routes.UNCONFIRMED) {
                LatestCheckScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onLoginBili = { navController.navigate(Routes.BILI_LOGIN) },
                    onOpenAudit = { navController.navigate(Routes.EXPOSURE_AUDIT) },
                    onOpenLedger = { navController.navigate(Routes.LEDGER_ALL) }
                )
            }

            composable(Routes.LEDGER_ALL) {
                LedgerOverviewScreen(
                    personNames = AppUiState.persons.associate {
                        it.id to it.remarkName
                    },
                    onBack = { navController.popBackStackSafely() },
                    onOpenTarget = { personId ->
                        navController.navigate(Routes.ledger(personId))
                    }
                )
            }

            composable(Routes.GUIDE) {
                OnboardingScreen(
                    onBack = {
                        markOnboardingShown(context)
                        if (navController.previousBackStackEntry == null) {
                            navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                        } else {
                            navController.popBackStackSafely()
                        }
                    }
                )
            }

            composable(Routes.EXPOSURE_AUDIT) {
                ExposureAuditScreen(onBack = { navController.popBackStackSafely() })
            }

            composable(Routes.CREATE_FOLDER) {
                CreateFolderScreen(
                    onBack = { navController.popBackStackSafely() },
                    onCreate = { name, avatar ->
                        val person = AppUiState.addPerson(name)
                        // 头像按人物 id 落盘，首页卡片下次读得到
                        if (avatar != null) {
                            AvatarStore.save(context, person.id, avatar)
                        }
                        navController.popBackStackSafely()
                    }
                )
            }

            composable(
                route = Routes.PERSON,
                arguments = listOf(
                    navArgument(Routes.ARG_PERSON_ID) { type = NavType.LongType }
                )
            ) { entry ->
                val personId = entry.arguments?.getLong(Routes.ARG_PERSON_ID) ?: 0L
                PersonDetailScreen(
                    person = AppUiState.findPerson(personId),
                    onBack = { navController.popBackStack() },
                    onAccountClick = { account ->
                        if (account.platform == Platform.QQZONE) {
                            // QQ空间在 WebView 里打不开（强依赖自家客户端），直接交给浏览器
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(account.homepageUrl)
                                    )
                                )
                            }
                        } else {
                            navController.navigate(Routes.platformVideos(personId, account.id))
                        }
                    },
                    onDeleteAccount = { accountId ->
                        AppUiState.removeAccount(personId, accountId)
                    },
                    onOpenLedger = { id -> navController.navigate(Routes.ledger(id)) }
                )
            }

            composable(
                route = Routes.PERSON_LEDGER,
                arguments = listOf(
                    navArgument(Routes.ARG_PERSON_ID) { type = NavType.LongType }
                )
            ) { entry ->
                val personId = entry.arguments?.getLong(Routes.ARG_PERSON_ID) ?: 0L
                LedgerScreen(
                    personId = personId,
                    personName = AppUiState.findPerson(personId)?.remarkName.orEmpty(),
                    onBack = { navController.popBackStackSafely() },
                    onOpenReport = { navController.navigate(Routes.report(personId)) },
                    onOpenGraph = { navController.navigate(Routes.RELATION_GRAPH) }
                )
            }

            composable(
                route = Routes.PERSON_REPORT,
                arguments = listOf(
                    navArgument(Routes.ARG_PERSON_ID) { type = NavType.LongType }
                )
            ) { entry ->
                val personId = entry.arguments?.getLong(Routes.ARG_PERSON_ID) ?: 0L
                LedgerReportScreen(
                    personId = personId,
                    personName = AppUiState.findPerson(personId)?.remarkName.orEmpty(),
                    onBack = { navController.popBackStackSafely() }
                )
            }

            composable(
                route = Routes.ADD_ACCOUNT,
                arguments = listOf(
                    navArgument(Routes.ARG_PERSON_ID) { type = NavType.LongType }
                )
            ) { entry ->
                val personId = entry.arguments?.getLong(Routes.ARG_PERSON_ID) ?: 0L
                AddAccountScreen(
                    personName = AppUiState.findPerson(personId)?.remarkName.orEmpty(),
                    onBack = { navController.popBackStack() },
                    onAdd = { platform, label, link ->
                        if (AppUiState.addAccount(personId, platform, label, link)) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(
                route = Routes.PLATFORM_VIDEOS,
                arguments = listOf(
                    navArgument(Routes.ARG_PERSON_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_ACCOUNT_ID) { type = NavType.LongType }
                )
            ) { entry ->
                val personId = entry.arguments?.getLong(Routes.ARG_PERSON_ID) ?: 0L
                val accountId = entry.arguments?.getLong(Routes.ARG_ACCOUNT_ID) ?: 0L
                val person = AppUiState.findPerson(personId)

                // 必须按账号 ID 精确查找：同一个人可能有两个同平台账号，
                // 按平台取第一个会张冠李戴（之前就是这个 bug）
                val account = person?.accounts?.firstOrNull { it.id == accountId }
                val platform = account?.platform
                if (platform == Platform.BILI) {
                    // B 站能拿到结构化数据，用原生列表 + 自动检查更新
                    PlatformVideosScreen(
                        personId = personId,
                        personName = person?.remarkName.orEmpty(),
                        account = account,
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.BILI_LOGIN) }
                    )
                } else {
                    // 抖音与快手用内嵌浏览器直接把主页投进来
                    PlatformWebView(
                        personId = personId,
                        personName = person?.remarkName.orEmpty(),
                        account = account,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

        composable(Routes.RELATION_GRAPH) {
            RelationGraphScreen(onBack = { navController.popBackStack() })
        }

            composable(Routes.BILI_LOGIN) {
                BiliLoginScreen(
                    onBack = { navController.popBackStack() },
                    onLoginDone = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * 安全回退：只有在还有上一层时才弹栈。
 *
 * 直接调 popBackStack() 在连点的情况下可能把根页面也弹掉，
 * 栈空了就是白屏 —— 这是实际发生过的 bug。
 */
private fun NavHostController.popBackStackSafely() {
    if (previousBackStackEntry != null) popBackStack()
}
