package org.mz.mzdkplayer.ui.elder

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.repository.HomeSlotRepository
import org.mz.mzdkplayer.tool.AppInfo
import org.mz.mzdkplayer.tool.AppLauncherHelper
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@Composable
fun ElderAppListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val firstFocusRequester = remember { FocusRequester() }

    // Issue 12 修复: 后台包变更刷新时, 不重置 loading, 避免列表闪烁导致焦点丢失
    suspend fun loadApps(showLoading: Boolean = true) {
        if (showLoading) loading = true
        val list = withContext(Dispatchers.IO) {
            AppLauncherHelper.getLaunchableApps()
        }
        apps = list
        if (showLoading) loading = false
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    // 监听包变更：安装/卸载/替换时刷新列表，并清理失效栏位
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_REPLACED -> {
                        scope.launch {
                            // 卸载时清理失效的 app 栏位
                            if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                                HomeSlotRepository.cleanupUninstalledAppSlots()
                            }
                            // Issue 12: 后台刷新不显示 loading, 保持当前列表与焦点
                            loadApps(showLoading = false)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 数据首次加载完成后请求焦点 (不因后台包变更刷新而重复请求)
    var appsInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(apps) {
        if (!appsInitialized && apps.isNotEmpty()) {
            delay(200)
            firstFocusRequester.requestFocus()
            appsInitialized = true
        }
    }

    // 返回键返回首页
    BackHandler {
        navController.popBackStack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                // 空格/回车由 Card 自身处理，这里只兜底
                false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // 顶部栏
            AppListTopBar(
                appCount = apps.size,
                loading = loading,
                onBack = { navController.popBackStack() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.elder_loading_apps),
                        color = ElderColors.textSecondary,
                        fontSize = ElderDimens.bodyFontSize
                    )
                }
            } else if (apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.elder_no_apps_found),
                        color = ElderColors.textSecondary,
                        fontSize = ElderDimens.bodyFontSize
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        AppGridCard(
                            app = app,
                            isFirst = index == 0,
                            firstFocusRequester = firstFocusRequester,
                            onClick = {
                                // Issue 2: 应用启动失败时给出明确反馈
                                val success = AppLauncherHelper.launchApp(app.packageName)
                                if (!success) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.elder_app_launch_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListTopBar(
    appCount: Int,
    loading: Boolean,
    onBack: () -> Unit
) {
    val backText = stringResource(R.string.elder_back)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ElderTopBarButton(
            text = backText,
            onClick = onBack,
            icon = painterResource(id = R.drawable.baseline_arrow_back_24)
        )
        Text(
            text = if (loading) stringResource(R.string.elder_all_apps)
                   else stringResource(R.string.elder_all_apps_count, appCount),
            color = ElderColors.textPrimary,
            fontSize = ElderDimens.titleFontSize,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AppGridCard(
    app: AppInfo,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "appCardScale"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .then(if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier),
        shape = CardDefaults.shape(shape = RoundedCornerShape(ElderDimens.cardCornerLarge)),
        colors = CardDefaults.colors(
            containerColor = ElderColors.cardBackground,
            focusedContainerColor = ElderColors.focusBackground
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(ElderDimens.focusBorderWidth, ElderColors.focusBorder),
                shape = RoundedCornerShape(ElderDimens.cardCornerLarge)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val imageBitmap = remember(app.packageName) {
                val drawable = AppLauncherHelper.getAppIcon(app.packageName)
                AppLauncherHelper.drawableToImageBitmap(drawable)
            }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = app.label,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_play_arrow_24),
                    contentDescription = app.label,
                    modifier = Modifier.size(48.dp),
                    tint = ElderColors.accent
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.label,
                color = ElderColors.textPrimary,
                fontSize = ElderDimens.captionFontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
