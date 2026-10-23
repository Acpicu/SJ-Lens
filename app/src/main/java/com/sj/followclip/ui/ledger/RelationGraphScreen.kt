package com.sj.followclip.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.LedgerRecord
import com.sj.followclip.data.local.LedgerStore
import com.sj.followclip.data.local.RecordKind
import com.sj.followclip.ui.state.AppUiState
import kotlin.math.cos
import kotlin.math.sin

/**
 * 关系网图谱（手机端）：
 * 跟电脑端一个路子——圆形头像 + 名字写在圆里，超长名字省略，绝不溢出圆外。
 * 关系数据来自数据本里的 RELATION 记录（由 GuangXIW 导出的关系摘要导入而来）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationGraphScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val persons = AppUiState.persons
    var picked by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<LedgerRecord?>(null) }

    val rels = remember(persons.size, tick) {
        val all = LedgerStore.all(context)
        persons.map { p ->
            p.remarkName.ifBlank { "未命名" } to all[p.id].orEmpty().filter { it.kind == RecordKind.RELATION }
        }
    }
    val nameOf: (LedgerRecord) -> String = { it.value.substringBefore("（").trim() }
    val typeOf: (LedgerRecord) -> String = { it.fieldName.removePrefix("关系·") }
    val current = rels.getOrNull(picked)
    val centerName = current?.first ?: ""
    val items = current?.second.orEmpty()

    Scaffold(topBar = {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            },
            title = { Text("关系网", maxLines = 1) }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (rels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有目标。先加人、记数据，再导入关系摘要。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            // 目标切换
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                persons.forEachIndexed { i, p ->
                    FilterChip(
                        selected = i == picked,
                        onClick = { picked = i; selected = null },
                        label = { Text(p.remarkName.ifBlank { "未命名" }, maxLines = 1) }
                    )
                }
            }

            // 图：圆心 + 外圈，圆形节点，名字在圆里
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val w = maxWidth
                val h = maxHeight
                val cx = w / 2
                val cy = h / 2
                val radius = (if (w < h) w else h) * 0.32f
                val positions = items.mapIndexed { i, rec ->
                    val a = -Math.PI / 2 + i * 2 * Math.PI / (if (items.isEmpty()) 1 else items.size)
                    Triple(rec, cx + (cos(a) * radius.value).dp, cy + (sin(a) * radius.value).dp)
                }

                Canvas(Modifier.fillMaxSize()) {
                    positions.forEach { (rec, x, y) ->
                        val on = selected == nameOf(rec)
                        drawLine(
                            color = if (on) Color(0xFFC8A96A) else Color(0xFF4A5561),
                            start = Offset(size.width / 2f, size.height / 2f),
                            end = Offset(x.toPx(), y.toPx()),
                            strokeWidth = if (on) 3.5f else 2f
                        )
                    }
                }

                NodeBall(name = centerName, primary = true, highlight = true, modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = cx - 44.dp, y = cy - 44.dp))

                positions.forEach { (rec, x, y) ->
                    NodeBall(
                        name = nameOf(rec),
                        primary = false,
                        highlight = selected == nameOf(rec),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = x - 34.dp, y = y - 34.dp)
                            .clickable { editing = rec }
                    )
                }
            }

            // 关系清单
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (items.isEmpty()) {
                    Text("这个人还没有关系记录。电脑端导出关系摘要后导入即可。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    items.forEach { rec ->
                        val nm = nameOf(rec); val tp = typeOf(rec)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .clickable { editing = rec },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).background(
                                if (selected == nm) Color(0xFFC8A96A) else Color(0xFF8AA2BD), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(nm, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, maxLines = 1)
                            Spacer(Modifier.weight(1f))
                            Text(tp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("改", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    val target = editing
    if (target != null) {
        var vName by remember(target.id) { mutableStateOf(nameOf(target)) }
        var vType by remember(target.id) { mutableStateOf(typeOf(target)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("修改关系") },
            text = {
                Column {
                    OutlinedTextField(value = vName, onValueChange = { vName = it },
                        label = { Text("关系人") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = vType, onValueChange = { vType = it },
                        label = { Text("关系类型") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Text("手机端只改这两项，强度和证据在电脑端维护。",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val me = persons.getOrNull(picked)
                    if (me != null && vName.isNotBlank()) {
                        val strength = Regex("""\\(强度\\s*(\\d+)\\)""").find(target.value)
                            ?.groupValues?.get(1) ?: "3"
                        LedgerStore.update(context, me.id, target.copy(
                            fieldName = "关系·" + vType.trim().ifBlank { "其他" },
                            value = vName.trim() + "（强度 " + strength + "）"
                        ))
                    }
                    editing = null; tick++
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val me = persons.getOrNull(picked)
                        if (me != null) LedgerStore.remove(context, me.id, target.id)
                        editing = null; tick++
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text("取消") }
                }
            }
        )
    }
}

/** 圆形节点：名字写在圆里，超长省略，字号随字数缩小一档。 */
@Composable
private fun NodeBall(name: String, primary: Boolean, highlight: Boolean, modifier: Modifier = Modifier) {
    val size = if (primary) 88.dp else 68.dp
    val bg = when {
        highlight -> Color(0xFFC8A96A).copy(alpha = 0.22f)
        primary -> Color(0xFF1B2027)
        else -> Color(0xFF181D23)
    }
    val border = if (highlight || primary) Color(0xFFC8A96A) else Color(0xFF3A444F)
    Box(
        modifier = modifier.size(size).background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(size).border(1.5.dp, border, CircleShape))
        Text(
            text = name,
            color = if (primary || highlight) Color(0xFFE8E2D4) else Color(0xFFCFD4DC),
            fontSize = if (primary) 13.sp else 11.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = if (primary) 2 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

