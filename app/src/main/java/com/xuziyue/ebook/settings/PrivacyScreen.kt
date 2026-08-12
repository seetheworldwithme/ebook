package com.xuziyue.ebook.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuziyue.ebook.R

/**
 * 隐私说明页（design.md §4.6 SET-05，红线 #8）。
 *
 * 向用户透明地说明数据存储 / 网络权限 / 崩溃日志 / 第三方依赖四方面。
 * 纯文本展示，无网络请求。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_privacy)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle(stringResource(R.string.privacy_section_data))
            Spacer(Modifier.height(8.dp))
            BodyText(stringResource(R.string.privacy_data_body))

            SectionDivider()

            SectionTitle(stringResource(R.string.privacy_section_network))
            Spacer(Modifier.height(8.dp))
            BodyText(stringResource(R.string.privacy_network_body))

            SectionDivider()

            SectionTitle(stringResource(R.string.privacy_section_crash))
            Spacer(Modifier.height(8.dp))
            BodyText(stringResource(R.string.privacy_crash_body))

            SectionDivider()

            SectionTitle(stringResource(R.string.privacy_section_deps))
            Spacer(Modifier.height(8.dp))
            BodyText(stringResource(R.string.privacy_deps_body))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
}
