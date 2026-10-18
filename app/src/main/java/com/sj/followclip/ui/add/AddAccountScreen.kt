package com.sj.followclip.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sj.followclip.domain.model.Platform
import com.sj.followclip.ui.theme.SJFollowClipTheme
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted

/**
 * 往文件夹里添加一个平台主页。
 *
 * 用户在文件夹（= 一个人）内部点右下角的加号进来，
 * 选平台 + 粘主页链接，就多一个这个平台上的账号。
 *
 * 目前只有界面，真正解析链接与保存要等后续接数据层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    personName: String,
    onBack: () -> Unit,
    onAdd: (Platform, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var platform by rememberSaveable { mutableStateOf(Platform.BILI) }
    var link by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("添加平台主页") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.pageHorizontal,
                    end = Spacing.pageHorizontal,
                    bottom = Spacing.sectionGap
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            Text(
                text = if (personName.isEmpty()) "添加一个平台主页" else "给「$personName」添加一个平台主页",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "平台",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                Platform.entries.forEach { item ->
                    FilterChip(
                        selected = item == platform,
                        onClick = { platform = item },
                        label = { Text(item.displayName) }
                    )
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账号备注（可留空）") },
                singleLine = true,
                placeholder = { Text("比如：主号 / 生活号 / 工作室号") },
                supportingText = { Text("同一个人在同一个平台有多个号时，用这个区分") }
            )

            OutlinedTextField(
                value = link,
                onValueChange = {
                    link = it
                    hint = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("主页链接") },
                placeholder = { Text("把这个人${platform.displayName}的主页链接粘进来") },
                minLines = 2,
                supportingText = { Text("也可以在对应 App 里点分享、复制链接，再粘到这里") }
            )

            if (hint != null) {
                Text(
                    text = hint.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(Spacing.itemGap))

            Button(
                onClick = {
                    val normalized = normalizeUrl(link, platform)
                    when {
                        link.isBlank() -> hint = "还没粘链接。先把主页链接粘到上面"
                        normalized == null -> hint = if (platform == Platform.BILI) {
                            "请填 B站 UID（纯数字），或粘贴主页链接"
                        } else {
                            "这不像网页链接。抖音/快手 请用「分享 → 复制链接」后粘贴 https:// 开头的地址"
                        }
                        else -> {
                            hint = null
                            onAdd(platform, label, normalized)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("添加")
            }
        }
    }
}

/**
 * 归一化用户输入的主页地址。
 *
 * 处理三件事：
 * 1. 从「分享文本」里抽出第一条链接（用户经常整段粘进来）；
 * 2. http 统一升到 https —— Android 默认禁止明文 HTTP，否则内嵌页面会报
 *    ERR_CLEARTEXT_NOT_PERMITTED，直接打不开；
 * 3. 没有协议头就补 https://。
 *
 * 判不出来返回 null，由界面提示用户，而不是存一条打不开的地址。
 */
private fun normalizeUrl(raw: String, platform: Platform): String? {
    var text = raw.trim()
    if (text.isEmpty()) return null

    // 纯数字：B站 用户习惯直接填 UID，这里自动拼成空间地址。
    // 别的平台没这个约定，直接拒绝并让用户粘链接。
    if (text.all { it.isDigit() }) {
        return if (platform == Platform.BILI && text.length in 1..12) {
            "https://space.bilibili.com/$text"
        } else {
            null
        }
    }

    // 抖音/快手「分享」里复制出来的往往是 App 深链（snssdk1128://…），
    // 直接存下来网页打不开。先尝试还原成网页地址。
    if (!com.sj.followclip.util.PlatformUrls.isWebUrl(text)) {
        com.sj.followclip.util.PlatformUrls.toWebUrl(text)?.let { return it }
    }

    // 分享文本里抽出第一条链接
    Regex("""https?://[^\s"'，。、；]+""").find(text)?.let { text = it.value }

    if (text.startsWith("http://")) text = "https://" + text.removePrefix("http://")
    if (!text.startsWith("https://")) text = "https://$text"

    val host = text.removePrefix("https://").substringBefore('/')
    if (!host.contains('.') || host.length < 4) return null
    if (host.any { it.isWhitespace() || it.code > 127 }) return null

    return text
}

@Preview(showBackground = true, name = "添加平台主页")
@Composable
private fun AddAccountScreenPreview() {
    SJFollowClipTheme {
        AddAccountScreen(personName = "老饭骨", onBack = {}, onAdd = { _, _, _ -> })
    }
}
