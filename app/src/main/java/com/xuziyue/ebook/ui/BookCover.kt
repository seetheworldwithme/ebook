package com.xuziyue.ebook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

/**
 * 封面（Coil 加载 `filesDir/covers/{bookId}.png`，LIB-01）。
 *
 * coverPath 为空（封面缺失）时用占位：surfaceVariant 底 + 书名首字。
 * 尺寸由调用方经 [modifier] 控制（书库列表固定 48×66，网格 fillMaxWidth + aspectRatio 0.66，详情页大封面）。
 */
@Composable
fun BookCover(coverPath: String?, title: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = "《$title》封面",
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
