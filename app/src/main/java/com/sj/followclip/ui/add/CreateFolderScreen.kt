package com.sj.followclip.ui.add

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.WindowInsets
import com.sj.followclip.ui.common.AvatarCropDialog
import com.sj.followclip.ui.theme.SJFollowClipTheme
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.AvatarStore

/**
 * 新建文件夹（也就是「一个人」）。
 *
 * 流程按用户的理解来：文件夹 = 一个人。
 * 先在首页右下角新建一个文件夹，给它设头像和名字；
 * 点进文件夹之后，才在里头往各平台加主页。
 *
 * 目前只有界面，按钮不落库；真实创建在后续接数据库时补上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderScreen(
    onBack: () -> Unit,
    onCreate: (String, Bitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var pickedAvatar by remember { mutableStateOf<Bitmap?>(null) }
    // 防止连点：第一次点击后就锁住，否则会创建两个文件夹并把返回栈弹空（白屏）
    var submitted by rememberSaveable { mutableStateOf(false) }
    // 相册选出来的原图，先进裁切界面，确认后才成为 pickedAvatar
    var rawPicked by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) rawPicked = AvatarStore.decode(context, uri)
    }

    val cropSource = rawPicked
    if (cropSource != null) {
        AvatarCropDialog(
            source = cropSource,
            onDismiss = { rawPicked = null },
            onConfirm = { cropped ->
                pickedAvatar = cropped
                rawPicked = null
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("新建文件夹") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            Spacer(Modifier.height(Spacing.sectionGap))

            // 文件夹头像：点了去相册换图
            Box(
                modifier = Modifier
                    .size(Spacing.avatarFolder)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val avatar = pickedAvatar
                if (avatar != null) {
                    Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = "文件夹头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = "点头像\n或选图",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = "文件夹头像（可跳过，之后在首页长按也能换）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )

            Spacer(Modifier.height(Spacing.itemGap))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("文件夹名字") },
                singleLine = true,
                supportingText = { Text("比如博主名，或者你自己起的外号") }
            )

            Spacer(Modifier.height(Spacing.itemGap))

            Button(
                onClick = {
                    if (submitted) return@Button
                    submitted = true
                    onCreate(name, pickedAvatar)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !submitted
            ) {
                Text("创建")
            }

            Text(
                text = "创建后进入这个文件夹，再往里添加他在 B站 / 抖音 / 快手 的主页",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }
    }
}

@Preview(showBackground = true, name = "新建文件夹")
@Composable
private fun CreateFolderScreenPreview() {
    SJFollowClipTheme {
        CreateFolderScreen(onBack = {}, onCreate = { _, _ -> })
    }
}
