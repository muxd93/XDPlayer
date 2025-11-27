package org.mz.mzdkplayer.ui.screen.setting



import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.viewModelWithFactory
import org.mz.mzdkplayer.ui.screen.common.FilePermissionScreen
import org.mz.mzdkplayer.ui.screen.common.MyIconButton
import org.mz.mzdkplayer.ui.screen.vm.MovieViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import org.mz.mzdkplayer.ui.theme.myListItemCoverColor

@Composable
fun SettingsScreen(mainNavController: NavHostController) {
    // 获取 ViewModel
    val settingsVM: SettingsViewModel = viewModelWithFactory{SettingsViewModel()}
    val movieVM: MovieViewModel = viewModelWithFactory { RepositoryProvider.createMovieViewModel() }
    val state by settingsVM.uiState.collectAsState()
    val context = LocalContext.current

    // 整体布局使用 LazyColumn，方便 TV 遥控器滚动
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        contentPadding = PaddingValues(bottom = 50.dp) // 底部留白
    ) {

        // --- 标题 ---
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.displaySmall.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // --- 常规设置 ---
        item { SectionHeader("常规设置") }
        item {
            SwitchSettingItem(
                title = "隐藏详情页",
                subtitle = "直接播放，不显示电影/电视剧详情",
                checked = state.hideDetails,
                onCheckedChange = { settingsVM.toggleHideDetails(it) }
            )
        }

        // --- 播放 & 视频设置 ---
        item { SectionHeader("播放与视频") }
        item {
            // 语言选择模拟，实际可做成弹窗或子页面，这里简化为循环切换演示
            ActionSettingItem(
                title = "音频首选语言",
                value = formatLang(state.audioLang),
                onClick = {
                    // 简单逻辑：无 -> 中 -> 英 -> 无
                    val next = when(state.audioLang) { "" -> "zh"; "zh" -> "en"; else -> "" }
                    settingsVM.setAudioLanguage(next)
                }
            )
        }
        item {
            ActionSettingItem(
                title = "字幕首选语言",
                value = formatLang(state.subLang),
                onClick = {
                    val next = when(state.subLang) { "" -> "zh"; "zh" -> "en"; else -> "" }
                    settingsVM.setSubLanguage(next)
                }
            )
        }
        item {
            // 补充：ExoPlayer 隧道模式 (对 Android TV 很重要)
            SwitchSettingItem(
                title = "视频隧道模式 (Tunneling)",
                subtitle = "可能改善 4K/HDR 播放性能，但可能存在兼容性问题",
                checked = state.enableTunneling,
                onCheckedChange = { settingsVM.toggleTunneling(it) }
            )
        }

        // --- 音频设置 ---
        item { SectionHeader("音频设置") }
        item {
            SwitchSettingItem(
                title = "音频透传 (Passthrough)",
                subtitle = "源码输出到功放 (HDMI/Optical)，需设备支持",
                checked = state.enablePassthrough,
                onCheckedChange = { settingsVM.togglePassthrough(it) }
            )
        }

        // --- 字幕外观设置 ---
        item { SectionHeader("字幕设置 (文本字幕)") }
        item {
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
        }
        item {
            // 字体颜色
            ActionSettingItem(
                title = "字体颜色",
                value = if (state.subColor == 0xFFFFFFFF) "白色" else "黄色",
                onClick = {
                    // 简单切换 白/黄
                    val next = if (state.subColor == 0xFFFFFFFF) 0xFFFFFF00 else 0xFFFFFFFF
                    settingsVM.setSubColor(next)
                }
            )
        }
        item {
            // 背景颜色
            ActionSettingItem(
                title = "背景颜色",
                value = parseBgColorName(state.subBgColor),
                onClick = {
                    // 轮换：黑(50%) -> 白(50%) -> 黄(50%) -> 全透
                    val next = when(state.subBgColor) {
                        0x80000000 -> 0x80FFFFFF // 白半透
                        0x80FFFFFF -> 0x80FFFF00 // 黄半透
                        0x80FFFF00 -> 0x00000000 // 无背景
                        else -> 0x80000000       // 回到黑半透
                    }
                    settingsVM.setSubBgColor(next)
                }
            )
        }
        item {
            ActionSettingItem(
                title = "距离底部位置",
                value = "${state.subBottomPadding.toInt()} dp",
                onClick = {
                    val next = if (state.subBottomPadding >= 100f) 10f else state.subBottomPadding + 10f
                    settingsVM.setSubBottomPadding(next)
                }
            )
        }
        item {
            SwitchSettingItem(
                title = "强制 PGS 字幕居中",
                subtitle = "仅对图形字幕生效",
                checked = state.forcePgsCenter,
                onCheckedChange = { settingsVM.togglePgsCenter(it) }
            )
        }

        // --- 刮削源设置 ---
        item { SectionHeader("数据源刮削配置") }
        item {
            Column(Modifier.fillMaxWidth()) {
                // 两列布局或者流式布局，这里简单用 Column 里的 Row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val modifier = Modifier.weight(1f)
                    DataSourceSwitch("SMB", state.smb, modifier) { settingsVM.toggleSource("SMB", it) }
                    DataSourceSwitch("WebDav", state.webdav, modifier) { settingsVM.toggleSource("WebDav", it) }
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
                    DataSourceSwitch("Local", state.local, modifier) { settingsVM.toggleSource("Local", it) }
                    DataSourceSwitch("HTTP", state.http, modifier) { settingsVM.toggleSource("HTTP", it) }
                }
            }
        }

        // --- 权限与其他 ---
        item { SectionHeader("文件权限与其他") }
        item {
            // 直接嵌入原来的 FilePermissionScreen 逻辑有点怪，建议做成一个操作项
            // 但如果必须嵌入，放在这里：
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
                //FilePermissionScreen()
            }
        }
        item {
            MyIconButton(
                text = "清理媒体资料库",
                icon = R.drawable.close24dp,
                onClick = { movieVM.clearMediaLibrary() }
            )
        }

        // --- 性能测试工具 (折叠或嵌入) ---
        item {
            Spacer(Modifier.height(16.dp))
            // 这里因为 PerformanceTestScreen 比较大，建议只在点击后展开或者显示
            // 简单起见，我们把它作为一个 Card 包裹起来
//            Surface(
//                modifier = Modifier.fillMaxWidth(),
//                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
//                shape = RoundedCornerShape(12.dp)
//            ) {
                PerformanceTestScreen()
            //}
        }

        // --- 关于 ---
        item { SectionHeader("关于") }
        item {
            AboutSection(context)
        }
    }
}

// --- 辅助组件 ---

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
    )
}

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
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null) // 这里的 null 是因为点击 ListItem 触发
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
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
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
    // 使用较小的 Surface 或 Button 样式
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        leadingIcon = {
            if (checked) Icon(painterResource(R.drawable.check24dp), contentDescription = null)
        }
    ) {
        Text(name)
    }
}

@Composable
fun AboutSection(context: Context) {
    val pkgInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        // App Icon
        Icon(
            painter = painterResource(R.mipmap.ic_launcher), // 确保你有这个资源
            contentDescription = "Logo",
            modifier = Modifier.size(64.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = context.getString(R.string.app_name), // 确保你有 app_name
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Version: ${pkgInfo?.versionName} (${pkgInfo?.versionCode})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Helper Functions ---
fun formatLang(code: String): String = when(code) {
    "zh" -> "中文"
    "en" -> "English"
    else -> "自动 (ExoPlayer默认)"
}

fun parseBgColorName(color: Long): String = when(color) {
    0x80000000 -> "黑色 (50%)"
    0x80FFFFFF -> "白色 (50%)"
    0x80FFFF00 -> "黄色 (50%)"
    0x00000000L -> "透明"
    else -> "自定义"
}