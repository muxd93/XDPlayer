package org.mz.mzdkplayer.ui.elder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState

/**
 * 老人模式统一 UI 主题
 *
 * 集中管理所有老人模式界面的配色、尺寸、字号等样式常量，
 * 确保各页面/弹框/按钮风格一致。
 */

// ==================== 统一配色 ====================
object ElderColors {
    val background = Color(0xFF0D0D1A)
    val cardBackground = Color(0xFF1A1A2E)
    val focusBackground = Color(0xFF2A2A4E)
    val accent = Color(0xFFFFC200)
    val accentGradientEnd = Color(0xFFFF9800)
    val textPrimary = Color.White
    val textSecondary = Color.Gray
    val textHint = Color.White.copy(alpha = 0.6f)
    val error = Color.Red
    val focusBorder = Color.White
    val overlayDim = Color.Black.copy(alpha = 0.85f)
    val helpBoxBackground = Color(0xFF2A2A4E)
    val helpTitleColor = Color(0xFFFFD700)
}

// ==================== 统一尺寸 ====================
object ElderDimens {
    // 圆角
    val dialogCorner = 16.dp
    val buttonCorner = 12.dp
    val cardCornerLarge = 16.dp
    val cardCornerSmall = 12.dp
    val inputBoxCorner = 8.dp

    // 间距
    val dialogPadding = 32.dp
    val dialogSpacing = 24.dp
    val buttonPaddingH = 24.dp
    val buttonPaddingV = 14.dp
    val topBarButtonPaddingH = 20.dp
    val topBarButtonPaddingV = 10.dp

    // 边框
    val focusBorderWidth = 3.dp

    // 字号
    val titleFontSize = 22.sp
    val dialogTitleFontSize = 24.sp
    val dialogSubtitleFontSize = 14.sp
    val buttonFontSize = 18.sp
    val bodyFontSize = 16.sp
    val captionFontSize = 14.sp
    val smallCaptionFontSize = 13.sp
}

// ==================== 统一按钮组件 ====================

/**
 * 老人模式标准按钮
 *
 * 统一的圆角、配色、字号、内边距，确保所有按钮风格一致。
 * 支持可选图标，图标与文字水平居中对齐。
 */
@Composable
fun ElderButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    icon: Painter? = null,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val focusModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.buttonCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.textPrimary else ElderColors.focusBackground,
            contentColor = if (isFocused) Color.Black else ElderColors.textPrimary,
            focusedContainerColor = ElderColors.textPrimary,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.then(focusModifier),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ElderDimens.buttonPaddingH,
                vertical = ElderDimens.buttonPaddingV
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = ElderDimens.buttonFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 老人模式顶部栏按钮（带图标）
 *
 * 与 ElderButton 风格一致，但内边距更紧凑，适合顶部栏空间。
 */
@Composable
fun ElderTopBarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    icon: Painter? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val focusModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.buttonCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.textPrimary else ElderColors.focusBackground,
            contentColor = if (isFocused) Color.Black else ElderColors.textPrimary,
            focusedContainerColor = ElderColors.textPrimary,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.then(focusModifier),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ElderDimens.topBarButtonPaddingH,
                vertical = ElderDimens.topBarButtonPaddingV
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = ElderDimens.bodyFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
