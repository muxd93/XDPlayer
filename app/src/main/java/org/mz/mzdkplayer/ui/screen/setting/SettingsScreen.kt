package org.mz.mzdkplayer.ui.screen.setting

import android.R.attr.versionCode
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.viewModelWithFactory
import org.mz.mzdkplayer.ui.screen.common.FilePermissionScreen
import org.mz.mzdkplayer.ui.screen.common.MyIconButton
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.screen.vm.MovieViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsUiState
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import org.mz.mzdkplayer.ui.theme.myListItemCoverColor
import org.mz.mzdkplayer.ui.theme.mySideFilterChipColor

// 定义左侧菜单分类
enum class SettingCategory(val title: String, val iconRes: Int? = null) {
    General("常规设置"),
    Playback("播放与视频"),
    Audio("音频设置"),
    Subtitle("字幕外观"),
    Source("数据源刮削"),
    Tools("工具与权限"),
    About("关于软件")
}

@Composable
fun SettingsScreen(
    mainNavController: NavHostController,
    settingsVM: SettingsViewModel = viewModel(),
    audioViewModel: AudioViewModel
) {
    // 获取 ViewModel

    val movieVM: MovieViewModel = viewModelWithFactory { RepositoryProvider.createMovieViewModel() }
    val state by settingsVM.uiState.collectAsState()
    val context = LocalContext.current

    // 当前选中的分类，默认为第一个
    var selectedCategory by remember { mutableStateOf(SettingCategory.General) }

    // 焦点计数器 (用于隐藏的计数)
    var focusClickCount by remember { mutableIntStateOf(0) }
    // 跟踪上次点击时间，用于防止长按或焦点保持
    var lastClickTime by remember { mutableLongStateOf(0L) }

    // 重置焦点的协程
    LaunchedEffect(focusClickCount) {
        if (focusClickCount > 0) {
            val currentTime = System.currentTimeMillis()
            // 如果两次点击间隔超过 1000ms，则重置计数器
            if (currentTime - lastClickTime > 1000L) {
                focusClickCount = 0
            }
            lastClickTime = currentTime
        }
    }
    // 使用 Row 实现左右两栏布局
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        // --- 左侧：导航栏 (占宽 30%) ---
        LazyColumn(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .selectableGroup() // 优化无障碍和焦点
                .padding(end = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        )
        {
            item {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp, start = 12.dp)
                )
            }
            items(SettingCategory.entries.toTypedArray()) { category ->
                CategoryItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    onClick = { selectedCategory = category }
                )
            }
        }

        // --- 中间分割线 (可选) ---
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.border.copy(alpha = 0.5f))
        )

        // --- 右侧：内容区域 (占宽 70%) ---
        // 使用 Box 容纳内容，根据 selectedCategory 切换显示不同的 Composable
        Box(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
                .padding(horizontal = 32.dp)
        ) {
            // 这里使用 LazyColumn 保证右侧内容过多时也可以在内部滚动
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 50.dp)
            ) {
                item {
                    // 右侧顶部的标题提示
                    Text(
                        text = selectedCategory.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                // 根据分类加载具体内容
                when (selectedCategory) {
                    SettingCategory.General -> item {
                        GeneralSection(state, settingsVM)
                    }

                    SettingCategory.Playback -> item {
                        PlaybackSection(state, settingsVM)
                    }

                    SettingCategory.Audio -> item {
                        AudioSection(state, settingsVM)
                    }

                    SettingCategory.Subtitle -> item {
                        SubtitleSection(state, settingsVM)
                    }

                    SettingCategory.Source -> item {
                        SourceSection(state, settingsVM)
                    }

                    SettingCategory.Tools -> item {
                        ToolsSection(movieVM, audioViewModel)
                    }

                    SettingCategory.About -> item {
                        AboutSection(context, mainNavController)
                    }
                }
            }
        }
    }
}

// --- 左侧菜单项组件 ---
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryItem(
    category: SettingCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        selected = isSelected,
        onClick = onClick,
        headlineContent = { Text(category.title) },
        colors = myListItemCoverColor(),
        trailingContent = {
            if (isSelected) {
                Icon(painterResource(R.drawable.chevronright24dp), contentDescription = null)
            }
        },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

// --- 以下为拆分后的右侧具体设置内容块 ---

@Composable
fun GeneralSection(state: SettingsUiState, settingsVM: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchSettingItem(
            title = "隐藏详情页",
            subtitle = "直接播放，不显示电影/电视剧详情",
            checked = state.hideDetails,
            onCheckedChange = { settingsVM.toggleHideDetails(it) }
        )

        SwitchSettingItem(
            title = "隐藏播放页网速显示",
            checked = state.hideNetworkSpeed,
            onCheckedChange = { settingsVM.toggleHideNetWorkSpeed(it) }
        )
    }
}

@Composable
fun PlaybackSection(state: SettingsUiState, settingsVM: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionSettingItem(
            title = "音频首选语言",
            value = formatLang(state.audioLang),
            onClick = {
                val next = when (state.audioLang) {
                    "" -> "zh"; "zh" -> "en"; else -> ""
                }
                settingsVM.setAudioLanguage(next)
            }
        )
        // ==================== 新增：播放器内核选择 ====================
        ActionSettingItem(
            title = "默认播放器内核",
            value = if (state.defaultPlayer == "vlc") "VLC" else "ExoPlayer",
            onClick = {
                val next = if (state.defaultPlayer == "exo") "vlc" else "exo"
                settingsVM.setDefaultPlayer(next)
            }
        )
        ActionSettingItem(
            title = "字幕首选语言",
            value = formatLang(state.subLang),
            onClick = {
                val next = when (state.subLang) {
                    "" -> "zh"; "zh" -> "en"; else -> ""
                }
                settingsVM.setSubLanguage(next)
            }
        )
        SwitchSettingItem(
            title = "视频隧道模式 (Tunneling)",
            subtitle = "可能改善 4K/HDR 播放性能，但可能存在兼容性问题",
            checked = state.enableTunneling,
            onCheckedChange = { settingsVM.toggleTunneling(it) }
        )
    }
}

@Composable
fun AudioSection(state: SettingsUiState, settingsVM: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchSettingItem(
            title = "音频透传 (Passthrough)",
            subtitle = "开启后，音频将源码直传至功放或解码设备。注意：此设置仅对 VLC 播放器生效（ExoPlayer 会自动智能匹配）。若播放时遇到静音，请尝试关闭此开关。",
            checked = state.enablePassthrough,
            onCheckedChange = { settingsVM.togglePassthrough(it) }
        )
    }
}

@Composable
fun SubtitleSection(state: SettingsUiState, settingsVM: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 字体大小
        val sizeOpts = listOf(18f, 22f, 26f, 30f, 40f)
        ActionSettingItem(
            title = "字体大小",
            value = "${state.subFontSize.toInt()} sp",
            onClick = {
                val idx = sizeOpts.indexOf(state.subFontSize)
                val next = sizeOpts[(idx + 1) % sizeOpts.size]
                settingsVM.setSubFontSize(next)
            }
        )
        // 字体颜色
        ActionSettingItem(
            title = "字体颜色",
            value = if (state.subColor == 0xFFFFFFFF) "白色" else "黄色",
            onClick = {
                val next = if (state.subColor == 0xFFFFFFFF) 0xFFFFFF00 else 0xFFFFFFFF
                settingsVM.setSubColor(next)
            }
        )
        // 背景颜色
        ActionSettingItem(
            title = "背景颜色",
            value = parseBgColorName(state.subBgColor),
            onClick = {
                val next = when (state.subBgColor) {
                    0x80000000 -> 0x80FFFFFF
                    0x80FFFFFF -> 0x80FFFF00
                    0x80FFFF00 -> 0x00000000
                    else -> 0x80000000
                }
                settingsVM.setSubBgColor(next)
            }
        )
        // 距离底部
        ActionSettingItem(
            title = "距离底部位置",
            value = "${state.subBottomPadding.toInt()} dp",
            onClick = {
                val next = if (state.subBottomPadding >= 100f) 10f else state.subBottomPadding + 10f
                settingsVM.setSubBottomPadding(next)
            }
        )
        SwitchSettingItem(
            title = "强制 PGS 字幕居中",
            subtitle = "仅对图形字幕生效",
            checked = state.forcePgsCenter,
            onCheckedChange = { settingsVM.togglePgsCenter(it) }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourceSection(state: SettingsUiState, settingsVM: SettingsViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val modifier = Modifier.weight(1f)
            DataSourceSwitch("SMB", state.smb, modifier) { settingsVM.toggleSource("SMB", it) }
            DataSourceSwitch("WebDav", state.webdav, modifier) {
                settingsVM.toggleSource(
                    "WebDav",
                    it
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val modifier = Modifier.weight(1f)
            DataSourceSwitch("FTP", state.ftp, modifier) { settingsVM.toggleSource("FTP", it) }
            DataSourceSwitch("NFS", state.nfs, modifier) { settingsVM.toggleSource("NFS", it) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val modifier = Modifier.weight(1f)
            DataSourceSwitch("Local", state.local, modifier) {
                settingsVM.toggleSource(
                    "Local",
                    it
                )
            }
            DataSourceSwitch("HTTP", state.http, modifier) { settingsVM.toggleSource("HTTP", it) }
        }
    }
}

@Composable
fun ToolsSection(movieVM: MovieViewModel, audioViewModel: AudioViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()) {
            FilePermissionScreen() // 如果需要可以取消注释
        }
        MyIconButton(
            text = "清理电影与TV媒体资料库",
            icon = R.drawable.close24dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = { movieVM.clearMediaLibrary() }
        )
        Spacer(Modifier.height(16.dp))
        MyIconButton(
            text = "清理音乐资料库",
            icon = R.drawable.close24dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = { audioViewModel.clearLibrary() }
        )
        Spacer(Modifier.height(16.dp))

        PerformanceTestScreen()
    }
}


// --- 基础组件 (保持大致不变，略微调整以适应新布局) ---

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        selected = false,
        onClick = { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        colors = myListItemCoverColor(),
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else null,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActionSettingItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        colors = myListItemCoverColor(),
        trailingContent = {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DataSourceSwitch(
    name: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        colors = mySideFilterChipColor(),
        scale = FilterChipDefaults.scale(1f, 1.05f),
        modifier = modifier,
        leadingIcon = {
            if (checked) Icon(painterResource(R.drawable.check24dp), contentDescription = null)
        }
    ) {
        Text(name)
    }
}
val PackageInfo.versionCodeCompat: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode // API 28+ 使用这个
    } else {
        @Suppress("DEPRECATION")
        android.R.attr.versionCode.toLong() // 旧版本使用这个，并压制警告
    }
@Composable
fun AboutSection(context: Context, navController: NavHostController) {
    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    // 用于记录点击次数
    var clickCount by remember { mutableIntStateOf(0) }
    // 记录最后一次点击时间，超过 2 秒没点就重置计数
    var lastClickTime by remember { mutableLongStateOf(0L) }


    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = "Logo",
            modifier = Modifier.size(80.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width(24.dp))
        Column {
            Text(
                text = context.getString(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
            Surface(
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    pressedContainerColor = Color.White.copy(alpha = 0.2f)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)))
                ),
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 1000) {
                        clickCount++
                    } else {
                        clickCount = 1
                    }
                    lastClickTime = currentTime

                    // 达到 5 次点击触发
                    if (clickCount >= 5) {
                        clickCount = 0 // 重置
                        // 逻辑：奇数次去太阳系，偶数次去黑洞（或者根据喜好修改）
                        if (System.currentTimeMillis() % 2 == 0L) {
                            navController.navigate("SolarSystemScreen")
                        } else {
                            navController.navigate("BlackHoleSimulationScreen")
                        }
                    }
                }
            ) {
                Text(
                    modifier = Modifier.padding(3.dp),
                    text = "Version: ${pkgInfo?.versionName} (${pkgInfo?.versionCodeCompat})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        }
    }
}

// --- Helper Functions ---
fun formatLang(code: String): String = when (code) {
    "zh" -> "中文"
    "en" -> "English"
    else -> "自动 (ExoPlayer默认)"
}

fun parseBgColorName(color: Long): String = when (color) {
    0x80000000 -> "黑色 (50%)"
    0x80FFFFFF -> "白色 (50%)"
    0x80FFFF00 -> "黄色 (50%)"
    0x00000000L -> "透明"
    else -> "自定义"
}


