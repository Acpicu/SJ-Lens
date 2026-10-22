package com.sj.followclip.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.LedgerStore
import com.sj.followclip.data.local.RecordKind
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * 关系网图谱（手机端只读）：
 * 把每个人数据本里的 RELATION 记录抽成边，中心是当前目标，外圈是关系人。
 * 关系来源是 GuangXIW 导出的关系摘要，导入后即出现在这里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationGraphScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val persons = AppUiState.persons
    var picked by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }

    // 读取数据本里的关系
    val rels = remember(persons.size) {
        val all = LedgerStore.all(context)
        persons.map { p ->
            val list = all[p.id].orEmpty()
                .filter { it.kind == RecordKind.RELATION }
                .map { it.value.substringBefore("（").trim() to it.fieldName.removePrefix("关系·") }
            p.remarkName.ifBlank { "未命名" } to list
        }
    }
    val current = rels.getOrNull(picked)

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                title = { Text("关系网", maxLines = 1) }
            )
        }
    ) { pad ->
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
                        label = { Text(p.remarkName.ifBlank { "未命名" }) }
                    )
                }
            }

            val center = current?.first ?: ""
            val items = current?.second.orEmpty()

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(Modifier.fillMaxSize().pointerInput(items) {
                    detectTapGestures { tap ->
                        // 命中最外圈节点即选中
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = minOf(size.width, size.height) * 0.34f
                        items.forEachIndexed { i, _ ->
                            val a = -Math.PI / 2 + i * 2 * Math.PI / maxOf(1, items.size)
                            val p = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                            if ((tap - p).getDistance() < 46f) selected = items[i].first
                        }
                    }
                }) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = minOf(size.width, size.height) * 0.34f
                    val brass = Color(0xFFC8A96A)
                    val steel = Color(0xFF8AA2BD)
                    // 中心
                    drawCircle(Color(0xFF1B2027), 44f, c)
                    drawCircle(brass, 44f, c, style = Stroke(2.5f))
                    items.forEachIndexed { i, item ->
                        val a = -Math.PI / 2 + i * 2 * Math.PI / maxOf(1, items.size)
                        val p = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
                        drawLine(if (selected == item.first) brass else steel, c, p, strokeWidth = 3f)
                        drawCircle(if (selected == item.first) brass else Color(0xFF8BA0B5), 20f, p)
                    }
                }
                // 中心名字
                Text(
                    text = center,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 关系清单
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (items.isEmpty()) {
                    Text("这个人还没有关系记录。电脑端 GuangXIW 导出的摘要导入后会出现。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    items.forEach { (name, type) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).background(
                                if (selected == name) Color(0xFFC8A96A) else Color(0xFF8AA2BD), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Text(type, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
