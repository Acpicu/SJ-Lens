package com.sj.followclip.ui.home

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.sj.followclip.domain.model.Account
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.lastFetchAt
import com.sj.followclip.domain.model.totalUnread
import com.sj.followclip.ui.common.AvatarCropDialog
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.badgeUnread
import com.sj.followclip.ui.theme.dotColor
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.AvatarStore
import com.sj.followclip.util.relativeTime

/**
 * 首页的人物卡片：一个人一张，跨平台合并。
 *
 * 卡片上会用一行小色点标出这个人覆盖了哪些平台。
 *
 * 交互：点头像换自定义头像（选完图先进裁切界面）；点卡片其余位置进入文件夹。
 * [manageMode] 为真时右上角变成删除按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonCard(
    person: Person,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    manageMode: Boolean = false,
    onDelete: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val unread = person.totalUnread

    var avatarVersion by remember { mutableIntStateOf(0) }
    val customAvatar: Bitmap? = remember(person.id, avatarVersion) {
        AvatarStore.load(context, person.id)
    }
    // 选完图先放这里，进裁切界面；确认后再存
    var pendingCrop by remember { mutableStateOf<Bitmap?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingCrop = AvatarStore.decode(context, uri)
    }

    val cropSource = pendingCrop
    if (cropSource != null) {
        AvatarCropDialog(
            source = cropSource,
            onDismiss = { pendingCrop = null },
            onConfirm = { cropped ->
                if (AvatarStore.save(context, person.id, cropped)) {
                    avatarVersion++
                }
                pendingCrop = null
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                Avatar(
                    name = person.remarkName,
                    bitmap = customAvatar,
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                Spacer(Modifier.height(Spacing.gridGap))

                Text(
                    text = person.remarkName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(Spacing.tinyGap))

                PlatformDots(accounts = person.accounts)

                Spacer(Modifier.height(Spacing.itemGap))

                Text(
                    text = relativeTime(person.lastFetchAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            if (manageMode) {
                // 管理模式：右上角角标换成删除按钮，点了由外部弹确认
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.cardPadding)
                        .size(Spacing.avatar / 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除文件夹",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Spacing.badgeMinHeight)
                    )
                }
            } else if (unread > 0) {
                UnreadBadge(
                    count = unread,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.cardPadding)
                )
            }
        }
    }
}

/**
 * 头像。有自定义图片就显示图片，没有则用「名字首字 + 主色底」占位。
 *
 * AvatarStore 存的时候已经裁成正方形了，这里只要剪个圆就一定是正圆，
 * 不会出现压扁或者圆里套长方形的情况。
 */
@Composable
private fun Avatar(
    name: String,
    bitmap: Bitmap?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Spacing.avatar)
            .clip(CircleShape)
            .then(
                if (bitmap == null) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "自定义头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 这行小字标出这个人覆盖了哪些平台。
 *
 * 按平台去重：同一个人在同一平台可能有多个号（主号 + 生活号），
 * 直接遍历账号会显示成「B站 · B站 · 抖音」，很乱。
 * 这里只保留平台，每个平台出现一次。
 */
@Composable
private fun PlatformDots(accounts: List<Account>) {
    val platforms = accounts.map { it.platform }.distinct()

    Row(verticalAlignment = Alignment.CenterVertically) {
        platforms.forEachIndexed { index, platform ->
            if (index > 0) {
                Spacer(Modifier.width(Spacing.tinyGap))
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
                Spacer(Modifier.width(Spacing.tinyGap))
            }
            Box(
                modifier = Modifier
                    .size(Spacing.unreadDot)
                    .clip(CircleShape)
                    .background(platform.dotColor)
            )
            Spacer(Modifier.width(Spacing.tinyGap))
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 新内容角标：右上角实心数字，颜色跟随主题强调色。 */
@Composable
private fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Spacing.badgeMinHeight, minHeight = Spacing.badgeMinHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.badgeUnread)
            .padding(horizontal = Spacing.tinyGap),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
