package org.mz.mzdkplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.TabColors
import androidx.tv.material3.TabDefaults

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

@Composable
fun MyIconButtonColor(): ButtonColors{
    return ButtonDefaults.colors(
        containerColor = Color(0xFF2D2D2D), // 保持默认或根据需要调整
        contentColor = Color(255, 248, 240), // 暖白色替代默认
        focusedContainerColor = Color(255, 250, 245), // 米白色替代纯白
        focusedContentColor = Color(80, 70, 60), // 暖深灰替代纯黑
        pressedContainerColor = Color(255, 250, 245), // 与聚焦状态一致
        pressedContentColor = Color(80, 70, 60), // 与聚焦状态一致
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), // 保持默认
        disabledContentColor = Color(150, 140, 130) // 暖中灰替代默认
    )
}
/**
 * Home等ListItem颜色 OperationListItem
 */
@Composable
fun myListItemCoverColor(): ListItemColors {
    return ListItemDefaults.colors(
        containerColor = Color(32, 32, 32), // 保持深色背景
        contentColor = Color(255, 248, 240), // 暖白色替代纯白
        selectedContainerColor = Color(32, 32, 32), // 保持深色背景
        selectedContentColor = Color(255, 248, 240), // 暖白色
        focusedSelectedContentColor = Color(255, 248, 240), // 暖白色
        focusedSelectedContainerColor = Color(32, 32, 32), // 保持深色背景
        focusedContainerColor = Color(255, 250, 245), // 米白色替代纯白
        focusedContentColor = Color(0, 0, 0) // 纯黑
    )
}
/**
 * FileListScreen ListItem 颜色
 */
@Composable
fun MyFileListItemColor(): ListItemColors {
    return ListItemDefaults.colors(
        containerColor = Color(0, 0, 0), // 保持深色背景
        contentColor = Color(255, 248, 240), // 暖白色替代纯白
        selectedContainerColor = Color(32, 32, 32), // 保持深色背景
        selectedContentColor = Color(255, 248, 240), // 暖白色
        focusedSelectedContentColor = Color(255, 248, 240), // 暖白色
        focusedSelectedContainerColor = Color(32, 32, 32), // 保持深色背景
        focusedContainerColor = Color(255, 250, 245), // 米白色替代纯白
        focusedContentColor = Color(0, 0, 0) // 纯黑
    )
}

/**
 * 主页侧边栏 ListItem 颜色
 */
@Composable
fun mySideListItemColor(): ListItemColors {
    return ListItemDefaults.colors(
        // --- 默认状态 (Default) ---
        containerColor = Color(38, 38, 42, 255), // 默认背景深灰
        contentColor = Color(255, 248, 240),      // 默认文字暖白

        // --- 选中状态 (Selected) ---
        // 选中但未获得焦点。使用浅暖灰背景，区分于默认深灰。
        selectedContainerColor = Color(220, 220, 220, 255), // 浅暖灰
        selectedContentColor = Color(80, 70, 60),             // 暖深灰文字

        // --- 获得焦点状态 (Focused) ---
        // 获得焦点但未选中。使用最亮的米白色背景，突出焦点。
        focusedContainerColor = Color(255, 250, 245, 255), // 最亮米白
        focusedContentColor = Color(80, 70, 60),             // 暖深灰文字

        // --- 获得焦点且选中状态 (FocusedSelected) ---
        // 获得焦点且选中。使用略微偏黄的颜色，强调是“被操作的选中项”。
        focusedSelectedContainerColor = Color(255, 240, 200, 255), // 米黄色/淡金色，最高优先级
        focusedSelectedContentColor = Color(80, 70, 60),             // 暖深灰文字
    )
}
@Composable
fun MyTabColors(): TabColors {
    return TabDefaults.pillIndicatorTabColors(
        contentColor = Color(255, 248, 240),
        inactiveContentColor = Color(255, 248, 240),
        selectedContentColor = Color(255, 248, 240),
        focusedContentColor = Color(255, 248, 240),
        focusedSelectedContentColor = Color(255, 250, 245),

    )
}