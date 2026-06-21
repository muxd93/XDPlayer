package org.mz.mzdkplayer.ui.screen.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.tool.Tools

/**
 * Web 配置对话框：显示开关、访问地址和二维码
 */
@Composable
fun WebConfigDialog(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var qrCodeBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var serverUrl by remember { mutableStateOf("") }

    LaunchedEffect(enabled) {
        if (enabled) {
            val ip = Tools.getLocalIpAddress()
            serverUrl = if (ip != null) "http://$ip:18080" else ""
            qrCodeBitmap = if (ip != null) Tools.generateQRCode(serverUrl) else null
        } else {
            qrCodeBitmap = null
        }
    }

    Dialog(onDismissRequest = { onDismiss() }) {
        var allowClick by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .width(400.dp)
                .background(
                    color = Color.DarkGray,
                    shape = RoundedCornerShape(8.dp)
                )
                .onPreviewKeyEvent { keyEvent ->
                    if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) && !allowClick) {
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> { allowClick = false; true }
                            KeyEventType.KeyUp -> { allowClick = true; true }
                            else -> true
                        }
                    } else {
                        false
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Web 配置",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "启用 Web 配置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (enabled && qrCodeBitmap != null) {
                    Text(
                        text = "扫码访问配置页面",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrCodeBitmap!!,
                            contentDescription = "Web Config QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                } else if (enabled) {
                    Text(
                        text = "无法获取 IP 地址，请检查网络连接",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Web 配置已禁用，开启后可通过手机扫码配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                MyIconButton(
                    text = "关闭(按两下生效)",
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.close24dp,
                    onClick = { if (allowClick) onDismiss() }
                )
            }
        }
    }
}
