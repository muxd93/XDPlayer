package org.mz.mzdkplayer.ui.screen.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.mz.mzdkplayer.R

@Composable
fun LibraryEmpty(isMovie: Boolean =true, navController: NavController){
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp) // 添加整体内边距
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()) // 允许内容滚动以防文本过长
                .padding(24.dp), // 给内容区域添加内边距
            horizontalAlignment = Alignment.CenterHorizontally, // 水平居中 Column 内的元素
            verticalArrangement = Arrangement.spacedBy(16.dp) // 设置元素间的垂直间距
        ) {
            // 图标
            Icon(
                painter =  if (isMovie )painterResource(id = R.drawable.movieoff24dp) else painterResource(id = R.drawable.tvoff24dp), // 请确保资源存在
                contentDescription = "Error Icon",
                tint = Color.Gray,
                modifier = Modifier.size(100.dp) // 可选：调整图标大小
            )

            // 标题
            Text(
                text = if (isMovie )"暂无电影信息" else "暂无TV剧信息",
                style = MaterialTheme.typography.headlineSmall, // 使用 Material3 标题样式
                fontWeight = FontWeight.Bold, // 加粗
                color = Color.White, // 使用主题文字颜色
                textAlign = TextAlign.Center
            )

            //
            MyIconButton(
                text = "去文件区添加",
                icon = R.drawable.videoadd24dp,
                onClick = {navController.navigate("FileHomePage")}
            )
        }
    }}