package com.xuziyue.ebook.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xuziyue.ebook.R
import com.xuziyue.ebook.settings.LicenseData.Entry

/**
 * 开源许可证页（design.md §4.6 SET-05，红线 #7）。
 *
 * 手写清单渲染（[LicenseData]）；点击条目展开内联全文（从 assets 读取，同一许可证缓存复用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.licenses_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
            }
            items(LicenseData.entries, key = { it.name }) { entry ->
                LicenseRow(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LicenseRow(entry: Entry) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // 许可证全文缓存（按 assetPath 缓存，同一许可证只读一次）。
    val licenseTextCache = remember { mutableMapOf<String, String>() }
    val transitiveSuffix = stringResource(R.string.licenses_transitive)
    val expandDesc = stringResource(R.string.licenses_expand)
    val collapseDesc = stringResource(R.string.licenses_collapse)
    val loadFailed = stringResource(R.string.licenses_load_failed)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name + " " + entry.version + if (entry.transitive) transitiveSuffix else "",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.license.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) collapseDesc else expandDesc,
            )
        }
        AnimatedVisibility(visible = expanded) {
            val text = licenseTextCache.getOrPut(entry.license.assetPath) {
                runCatching {
                    context.assets.open(entry.license.assetPath).bufferedReader().use { it.readText() }
                }.getOrElse { loadFailed }
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    // heightIn 限定高度后再 verticalScroll——LazyColumn item 的 maxHeight=Infinity，
                    // 直接 verticalScroll 会抛 IllegalStateException（嵌套滚动约束冲突）。
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
