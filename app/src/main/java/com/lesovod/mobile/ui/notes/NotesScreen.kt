package com.lesovod.mobile.ui.notes

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.location.CurrentPlace
import com.lesovod.mobile.data.local.NoteReminderEntry
import com.lesovod.mobile.data.network.dto.NoteDto
import com.lesovod.mobile.data.network.dto.SentNoteDto
import com.lesovod.mobile.ui.components.LvEmptyState
import com.lesovod.mobile.ui.components.LvStatusBadge
import com.lesovod.mobile.ui.components.LvTopBar
import com.lesovod.mobile.ui.components.SendKind
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Быстрые фразы над полем ввода — одно нажатие, и текст готов (прототип «Переписка»). */
private val QUICK_PHRASES = listOf(
    "Нужна солярка",
    "Сломался",
    "Закончил делянку",
    "Нужна помощь",
    "Всё по плану",
    "Задерживаюсь",
)

private const val NOTES_HELP = "Это переписка с конторой. Внизу пишете сообщение или жмёте готовую фразу, " +
    "кнопка с булавкой добавит, где вы сейчас (квартал и выдел). Сверху выбираете, кому: всем или конкретному человеку. " +
    "Ответ конторы появится слева. Без связи сообщение подождёт и уйдёт само."

/** Одна строка ленты: моё сообщение, ответ конторы или входящее от рабочего. */
private sealed class ChatItem(val key: String, val sortAt: String) {
    class Mine(val note: SentNoteDto) : ChatItem("m${note.id}", note.createdAt)
    class Reply(val note: SentNoteDto) : ChatItem("r${note.id}", note.otvetAt ?: note.createdAt)
    class Pending(val note: PendingNoteView) : ChatItem("p${note.id}", "9999")
    class Incoming(val note: NoteDto) : ChatItem("i${note.id}", note.createdAt)
}

/** «Заметки» в виде переписки (редизайн 12.11.2026, прототип «Переписка»). */
@Composable
fun NotesScreen(viewModel: NotesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val pendingNotes by viewModel.pendingNotes.collectAsState()
    val lv = LocalLv.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* напоминание всё равно планируется — просто может не показаться без разрешения */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // «Отправлено» / «Ждёт сети» висит три секунды и уходит
    LaunchedEffect(state.sent, state.queuedOffline) {
        if (state.sent || state.queuedOffline) {
            delay(3000)
            viewModel.resetSent()
        }
    }

    var tab by rememberSaveable { mutableStateOf(0) } // 0 — мои, 1 — входящие (у мастера)
    val inboxTab = state.canViewInbox && tab == 1

    val items: List<ChatItem> = remember(state.sentNotes, pendingNotes, state.inbox, inboxTab) {
        if (inboxTab) {
            state.inbox.map { ChatItem.Incoming(it) }.sortedByDescending { it.sortAt }
        } else {
            val list = ArrayList<ChatItem>()
            state.sentNotes.forEach { n ->
                list += ChatItem.Mine(n)
                if (!n.otvet.isNullOrBlank()) list += ChatItem.Reply(n)
            }
            list.sortByDescending { it.sortAt }
            pendingNotes.map { ChatItem.Pending(it) } + list
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(lv.bg).imePadding()) {
        LvTopBar(title = "Заметки", help = NOTES_HELP) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).clickable {
                    viewModel.loadSentNotes()
                    if (state.canViewInbox) viewModel.loadInbox()
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = lv.tx) }
        }

        if (state.canViewInbox) {
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabPill("Мои", selected = tab == 0, onClick = { tab = 0 })
                TabPill("Входящие" + if (state.inbox.isNotEmpty()) " · ${state.inbox.size}" else "", selected = tab == 1, onClick = { tab = 1 })
            }
        }

        if (inboxTab && state.reminders.isNotEmpty()) {
            Row(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.reminders.forEach { r -> ReminderPill(r, onCancel = { viewModel.cancelReminder(r.noteId) }) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val loading = if (inboxTab) state.isLoadingInbox else state.isLoadingSent
            val error = if (inboxTab) state.inboxError else state.sentError
            when {
                items.isEmpty() && loading -> CircularProgressIndicator(color = lv.gT, modifier = Modifier.align(Alignment.Center))
                items.isEmpty() && error != null -> LvEmptyState(
                    icon = Icons.Rounded.Chat,
                    title = "Не загрузилось",
                    text = error,
                    action = "Повторить",
                    onAction = { if (inboxTab) viewModel.loadInbox() else viewModel.loadSentNotes() },
                    modifier = Modifier.align(Alignment.Center),
                )
                items.isEmpty() -> LvEmptyState(
                    icon = Icons.Rounded.Chat,
                    title = if (inboxTab) "Входящих нет" else "Пока пусто",
                    text = if (inboxTab) "Когда рабочие напишут, сообщения появятся тут."
                    else "Напишите конторе: что сломалось, что нужно, где вы. Можно нажать готовую фразу внизу.",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is ChatItem.Mine -> Bubble(
                                text = item.note.text,
                                meta = (item.note.recipientFio ?: "Всем") + " · " + shortTime(item.note.createdAt),
                                mine = true,
                                status = SendKind.SENT,
                            )
                            is ChatItem.Pending -> Bubble(text = item.note.text, meta = item.note.createdAt, mine = true, status = SendKind.QUEUED)
                            is ChatItem.Reply -> Bubble(
                                text = item.note.otvet.orEmpty(),
                                meta = listOfNotNull(item.note.otvetBy?.takeIf { it.isNotBlank() } ?: "Контора", item.note.otvetAt?.let { shortTime(it) }).joinToString(" · "),
                                mine = false,
                                header = "Ответ на «${item.note.text.take(30)}${if (item.note.text.length > 30) "…" else ""}»",
                            )
                            is ChatItem.Incoming -> Column {
                                Bubble(
                                    text = item.note.text,
                                    meta = shortTime(item.note.createdAt),
                                    mine = false,
                                    header = item.note.authorFio,
                                    onRemind = { viewModel.openReminderPicker(item.note.id) },
                                )
                                if (state.reminderNoteId == item.note.id) {
                                    ReminderPicker(
                                        onPick15 = { viewModel.scheduleReminderInMinutes(item.note.id, item.note.text, 15) },
                                        onPick60 = { viewModel.scheduleReminderInMinutes(item.note.id, item.note.text, 60) },
                                        onPickTomorrow = { viewModel.scheduleReminderTomorrowMorning(item.note.id, item.note.text) },
                                        onPickCustom = { h, m -> viewModel.scheduleReminderAtTime(item.note.id, item.note.text, h, m) },
                                        onDismiss = viewModel::dismissReminderPicker,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            when {
                state.sent -> LvStatusBadge(SendKind.SENT, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                state.queuedOffline -> LvStatusBadge(
                    SendKind.QUEUED,
                    Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                    text = "Нет сети: уйдёт само",
                )
            }
        }

        Composer(state = state, viewModel = viewModel)
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val lv = LocalLv.current
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) lv.g else lv.sf2)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp))
    }
}

/** Пузырь сообщения: моё справа зелёное, чужое слева на подложке. */
@Composable
private fun Bubble(
    text: String,
    meta: String,
    mine: Boolean,
    status: SendKind? = null,
    header: String? = null,
    onRemind: (() -> Unit)? = null,
) {
    val lv = LocalLv.current
    val bg = if (mine) lv.g else lv.sf
    val fg = if (mine) Color.White else lv.tx
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (mine) 20.dp else 6.dp,
                        bottomEnd = if (mine) 6.dp else 20.dp,
                    ),
                )
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            header?.let {
                Text(it, color = if (mine) fg else lv.gT, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp))
            }
            Text(text, color = fg, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End).padding(top = 4.dp)) {
                Text(meta, color = fg.copy(alpha = 0.75f), style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                if (status == SendKind.QUEUED) {
                    Spacer(Modifier.width(6.dp))
                    Text("⏳ ждёт сети", color = fg, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp))
                } else if (status == SendKind.SENT) {
                    Spacer(Modifier.width(4.dp))
                    Text("✓", color = fg, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp))
                }
                if (onRemind != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onRemind), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = "Напомнить", tint = lv.aT, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Низ экрана: кому, быстрые фразы, «где я», поле и кнопка отправки. */
@Composable
private fun Composer(state: NotesUiState, viewModel: NotesViewModel) {
    val lv = LocalLv.current
    val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = lv.tx)
    Column(Modifier.fillMaxWidth().background(lv.nav).padding(top = 8.dp, bottom = 10.dp)) {
        Row(
            Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipientPill(state, viewModel)
            QUICK_PHRASES.forEach { phrase ->
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, lv.ln, RoundedCornerShape(14.dp)).background(lv.sf)
                        .clickable {
                            val cur = state.noteText.trim()
                            viewModel.onNoteTextChange(if (cur.isEmpty()) phrase else "$cur. $phrase")
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(phrase, color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)) }
            }
        }
        state.sendError?.let {
            Text(it, color = lv.rT, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val place = CurrentPlace.fresh()
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(lv.sf2).clickable {
                    val p = CurrentPlace.place.value
                    if (p != null) {
                        val cur = state.noteText.trim()
                        viewModel.onNoteTextChange((if (cur.isEmpty()) "" else "$cur ") + "📍 ${p.label}")
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Place, contentDescription = "Добавить, где я", tint = if (place != null) lv.gT else lv.tx2, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(lv.sf2).padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.noteText.isEmpty()) Text("Сообщение", style = style.copy(color = lv.tx2))
                BasicTextField(
                    value = state.noteText,
                    onValueChange = viewModel::onNoteTextChange,
                    enabled = !state.isSending,
                    textStyle = style,
                    maxLines = 5,
                    cursorBrush = SolidColor(lv.gT),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = state.noteText.isNotBlank() && !state.isSending
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(if (canSend) lv.g else lv.sf2).clickable(enabled = canSend, onClick = viewModel::sendNote),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isSending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Отправить", tint = if (canSend) Color.White else lv.tx2)
            }
        }
    }
}

@Composable
private fun RecipientPill(state: NotesUiState, viewModel: NotesViewModel) {
    val lv = LocalLv.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(lv.gS).clickable {
                if (state.recipients.isEmpty()) viewModel.loadRecipients()
                open = true
            }.padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Кому: " + (state.selectedRecipient?.fio ?: "всем"),
                color = lv.gT,
                maxLines = 1,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = lv.gT)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Всем") }, onClick = { viewModel.selectRecipient(null); open = false })
            state.recipients.forEach { r ->
                DropdownMenuItem(text = { Text(r.fio) }, onClick = { viewModel.selectRecipient(r); open = false })
            }
            if (state.isLoadingRecipients) DropdownMenuItem(text = { Text("Загружаю…") }, onClick = {}, enabled = false)
            state.recipientsError?.let { DropdownMenuItem(text = { Text("Не загрузилось, нажмите, чтобы повторить") }, onClick = viewModel::loadRecipients) }
        }
    }
}

@Composable
private fun ReminderPill(reminder: NoteReminderEntry, onCancel: () -> Unit) {
    val lv = LocalLv.current
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(lv.aS).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = lv.aT, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            formatReminderTime(reminder.triggerAtMillis) + " · " + reminder.noteText.take(20),
            color = lv.tx,
            maxLines = 1,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp),
        )
        Box(Modifier.size(40.dp).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
            Text("✕", color = lv.tx2, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp))
        }
    }
}

/** «2026-11-12T09:13:00» → «12.11 09:13»; сегодняшнее — просто «09:13». */
private fun shortTime(raw: String): String {
    val s = raw.replace('T', ' ')
    if (s.length < 16) return s
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val time = s.substring(11, 16)
    return if (s.startsWith(today)) time else "${s.substring(8, 10)}.${s.substring(5, 7)} $time"
}

private fun formatReminderTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))

@Composable
private fun ReminderPicker(
    onPick15: () -> Unit,
    onPick60: () -> Unit,
    onPickTomorrow: () -> Unit,
    onPickCustom: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lv = LocalLv.current
    Column(
        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(lv.aS).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Напомнить", color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerChip("Через 15 мин", onPick15)
            PickerChip("Через час", onPick60)
            PickerChip("Завтра утром", onPickTomorrow)
            PickerChip("Своё время") {
                val now = Calendar.getInstance()
                TimePickerDialog(context, { _, h, m -> onPickCustom(h, m) }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
            }
            PickerChip("Отмена", onDismiss)
        }
    }
}

@Composable
private fun PickerChip(text: String, onClick: () -> Unit) {
    val lv = LocalLv.current
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(lv.sf).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp)) }
}
