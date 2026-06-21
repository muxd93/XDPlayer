package org.mz.mzdkplayer.ui.elder

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.tool.Tools
import java.io.File

/**
 * 老人模式本地文件选择器
 *
 * - folder 类型：浏览目录，点击"选择此文件夹"按钮确认当前目录
 * - video 类型：浏览目录，点击视频文件直接选择
 *
 * @param slotId 栏位 ID
 * @param slotType "folder" 或 "video"
 * @param navController 导航控制器
 */
@Composable
fun ElderLocalFilePickerScreen(
    slotId: Int,
    slotType: String,
    navController: NavHostController
) {
    // 起始路径：外部存储根目录
    var currentPath by remember {
        mutableStateOf(
            Environment.getExternalStorageDirectory()?.absolutePath ?: "/sdcard"
        )
    }
    var files by remember(currentPath) {
        mutableStateOf(listFiles(currentPath))
    }
    val firstItemFocusRequester = remember { FocusRequester() }
    val selectButtonFocusRequester = remember { FocusRequester() }

    BackHandler {
        val parent = File(currentPath).parentFile
        if (parent != null && parent.canRead()) {
            currentPath = parent.absolutePath
        } else {
            navController.popBackStack()
        }
    }

    LaunchedEffect(files) {
        if (files.isNotEmpty()) {
            withFrameNanos { }
            firstItemFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ElderColors.background)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (slotType == "folder") stringResource(R.string.elder_select_folder)
                       else stringResource(R.string.elder_select_video),
                color = ElderColors.textPrimary,
                fontSize = ElderDimens.titleFontSize,
                fontWeight = FontWeight.Bold
            )
            ElderButton(
                text = stringResource(R.string.elder_back),
                onClick = { navController.popBackStack() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 当前路径显示
        Text(
            text = currentPath,
            color = ElderColors.textSecondary,
            fontSize = ElderDimens.captionFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 文件列表
        if (files.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.elder_no_files),
                    color = ElderColors.textSecondary,
                    fontSize = ElderDimens.bodyFontSize
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(files, key = { _, f -> f.absolutePath }) { index, file ->
                    FilePickerItem(
                        file = file,
                        isFirst = index == 0,
                        firstFocusRequester = firstItemFocusRequester,
                        onClick = {
                            if (file.isDirectory) {
                                currentPath = file.absolutePath
                            } else {
                                // 选中视频文件（仅 video 类型）
                                if (slotType == "video" &&
                                    Tools.containsVideoFormat(Tools.extractFileExtension(file.name))
                                ) {
                                    SlotPickerResultHolder.setResult(
                                        SlotPickerResultHolder.Result(
                                            slotId = slotId,
                                            slotType = "video",
                                            dataSourceType = "FILE",
                                            uri = file.absolutePath,
                                            fileName = file.name,
                                            connectionName = ""
                                        )
                                    )
                                    navController.popBackStack()
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // folder 类型：底部"选择此文件夹"按钮
        if (slotType == "folder") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ElderButton(
                    text = stringResource(R.string.elder_select_this_folder),
                    onClick = {
                        val dir = File(currentPath)
                        SlotPickerResultHolder.setResult(
                            SlotPickerResultHolder.Result(
                                slotId = slotId,
                                slotType = "folder",
                                dataSourceType = "FILE",
                                uri = dir.absolutePath,
                                fileName = dir.name,
                                connectionName = ""
                            )
                        )
                        navController.popBackStack()
                    },
                    focusRequester = selectButtonFocusRequester,
                    icon = painterResource(id = R.drawable.baseline_folder_24)
                )
            }
        }
    }
}

/**
 * 列出目录下的子目录和视频文件（目录在前，按名称排序）
 */
private fun listFiles(path: String): List<File> {
    val dir = File(path)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()?.sortedWith(
        compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
    )?.filter { file ->
        file.isDirectory ||
        (file.isFile && Tools.containsVideoFormat(Tools.extractFileExtension(file.name)))
    } ?: emptyList()
}

@Composable
private fun FilePickerItem(
    file: File,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val modifier = if (isFirst) {
        Modifier.focusRequester(firstFocusRequester)
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.cardCornerSmall)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.focusBackground else ElderColors.cardBackground,
            contentColor = ElderColors.textPrimary,
            focusedContainerColor = ElderColors.focusBackground,
            focusedContentColor = ElderColors.textPrimary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth(),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    id = if (file.isDirectory) R.drawable.baseline_folder_24
                         else R.drawable.baseline_movie_24
                ),
                contentDescription = null,
                tint = if (isFocused) ElderColors.accent else ElderColors.textSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = file.name,
                color = ElderColors.textPrimary,
                fontSize = ElderDimens.bodyFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (file.isDirectory) {
                Icon(
                    painter = painterResource(id = R.drawable.arrowright24dp),
                    contentDescription = null,
                    tint = ElderColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
