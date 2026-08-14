package com.xuziyue.ebook.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuziyue.ebook.R
import com.xuziyue.ebook.model.Collection
import com.xuziyue.ebook.model.CollectionKind

/**
 * 书架选择浮层（LIB-05 / LIB-06 共享）。
 *
 * 列出全部书架（系统「收藏」置顶），多选勾选；底部可内联新建书架。
 * 单本加入（详情页）与批量加入（书库批量模式）共用同一组件，调用方传入
 * [initiallySelected] 决定初始勾选态、[onConfirm] 回传最终勾选的书架 id 集合。
 *
 * @param collections 全部书架（含书籍数，按 sortOrder 排，收藏天然最前）。
 * @param initiallySelected 初始已勾选的书架 id（当前书已加入的）。
 * @param onConfirm 返回确认时勾选的书架 id 集合（调用方据此 diff 出新增/移除）。
 * @param onQuickCreate 用户填名新建书架（VM 异步创建；新书架经 collections 响应式回流后用户可勾选）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionPickerSheet(
    collections: List<Collection>,
    initiallySelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onQuickCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 用 mapOf 记勾选态，避免每行 recomposition 抖动。
    val checked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            initiallySelected.forEach { put(it, true) }
        }
    }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.shelf_pick_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )

            if (collections.isEmpty()) {
                Text(
                    stringResource(R.string.shelf_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(collections, key = { it.id }) { c ->
                        ShelfCheckRow(
                            collection = c,
                            checked = checked[c.id] == true,
                            onToggle = { checked[c.id] = it },
                        )
                        HorizontalDivider()
                    }
                }
            }

            // 内联新建书架
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.shelf_new_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { creating = false; newName = "" }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val name = newName.trim()
                            newName = ""
                            creating = false
                            if (name.isNotEmpty()) onQuickCreate(name)
                        },
                    ) { Text(stringResource(R.string.shelf_new)) }
                }
            } else {
                TextButton(
                    onClick = { creating = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shelf_new))
                }
            }

            Button(
                onClick = { onConfirm(checked.filter { it.value }.keys) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.shelf_add_to)) }
        }
    }
}

@Composable
private fun ShelfCheckRow(
    collection: Collection,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val name = if (collection.kind == CollectionKind.SYSTEM_FAVORITE) {
        stringResource(R.string.shelf_system_favorite)
    } else {
        collection.name
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = name }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collection.kind == CollectionKind.SYSTEM_FAVORITE) {
            Icon(Icons.Default.Star, contentDescription = null)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (collection.bookCount == 0) {
                    stringResource(R.string.shelf_book_count_zero)
                } else {
                    stringResource(R.string.shelf_book_count, collection.bookCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}
