package com.o2dialer.app

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.o2dialer.app.ui.components.O2Icons

@Composable
fun ReferenceStageSheet(listId: String, entryId: String) {
    val ctx = LocalContext.current
    val entry = Store.lists.find { it.id == listId }?.entries?.find { it.id == entryId }
    if (entry == null) { LaunchedEffect(Unit) { Store.stageTarget = null }; return }
    var selected by rememberSaveable(listId, entryId) { mutableIntStateOf(entry.stage.takeIf { it != Stg.NONE } ?: Stg.POTENTIAL) }
    var notes by rememberSaveable(listId, entryId) { mutableStateOf(entry.feedback) }
    LaunchedEffect(listId, entryId) { if (Store.campActive()) DialerService.send(ctx, DialerService.ACT_STAGE_HOLD) }
    val close: () -> Unit = {
        if (Store.campActive()) DialerService.send(ctx, DialerService.ACT_STAGE_DONE)
        Store.stageTarget = null
    }
    val cs = MaterialTheme.colorScheme
    val descriptions = mapOf(
        Stg.POTENTIAL to T("Interested prospect", "عميل مهتم بالعرض"),
        Stg.CALL_BACK to T("Requested follow-up call", "طلب الاتصال لاحقاً"),
        Stg.WHATSAPP to T("Send details via WhatsApp", "متابعة عبر واتساب"),
        Stg.VOICEMAIL to T("Left voice message", "تم ترك رسالة صوتية"),
        Stg.NOT_INTERESTED to T("Declined offer", "غير مهتم حالياً"))
    O2Modal(onDismiss = close) {
                    Row(Modifier.padding(20.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(T("Classify Call Outcome", "تصنيف نتيجة المكالمة"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(entry.name.ifBlank { entry.number } + " · " + entry.number + " · " + fmtDur(entry.duration), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(close, Modifier.size(32.dp)) { Icon(O2Icons.Close, L(R.string.ref_close), Modifier.size(16.dp)) }
                    }
                    HorizontalDivider(color = cs.outlineVariant)
                    Column(Modifier.fillMaxWidth()
                        .padding(20.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Use the same Material input that passed the paste-number IME tests.
                        // Keep it near the top so even a compact keyboard viewport can reach it.
                        BTextField(
                            value = notes,
                            onChange = { notes = it },
                            label = T("Feedback & Follow-up Notes", "الملاحظات والتعليقات"),
                            placeholder = T("Follow-up notes", "ملاحظات المتابعة"),
                            singleLine = false,
                            minLines = 3
                        )
                        HorizontalDivider(color = cs.outlineVariant)
                        Text(T("Select Call Stage", "اختر المرحلة (5 فئات أساسية)"), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        listOf(Stg.POTENTIAL, Stg.CALL_BACK, Stg.WHATSAPP, Stg.VOICEMAIL, Stg.NOT_INTERESTED).chunked(2).forEach { row ->
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { stage ->
                                    val active = selected == stage
                                    val foreground = if (active) Color.White else cs.onSurface
                                    Row(Modifier.weight(1f).fillMaxHeight().background(if (active) Color(0xFF17191D) else cs.surfaceVariant, RoundedCornerShape(12.dp))
                                        .border(1.dp, if (active) Color(0xFF17191D) else cs.outlineVariant, RoundedCornerShape(12.dp))
                                        .selectable(active, role = Role.RadioButton) { selected = stage }.padding(12.dp)) {
                                        Icon(when (stage) { Stg.NOT_INTERESTED -> O2Icons.PhoneOff; Stg.CALL_BACK -> O2Icons.PhoneForwarded; else -> stageIcon(stage) }, null, Modifier.padding(top = 2.dp).size(16.dp), tint = foreground)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(Stg.label(stage), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(descriptions.getValue(stage), Modifier.padding(top = 2.dp), fontSize = 12.sp, color = if (active) Color(0xFFD4D4D4) else cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (selected == Stg.POTENTIAL || selected == Stg.WHATSAPP) Row(Modifier.fillMaxWidth().background(cs.surfaceVariant, RoundedCornerShape(12.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(T("WhatsApp Follow-up Shortcut", "متابعة فورية عبر واتساب"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(T("Opens external WhatsApp chat with this contact", "يفتح محادثة واتساب خارجية مع الرقم مباشرة"), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton({ openWhatsApp(ctx, entry.number) }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(12.dp, 6.dp)) {
                                Icon(O2Icons.ExternalLink, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                                Text("WhatsApp", fontSize = 12.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = cs.outlineVariant)
                    Box(Modifier.padding(20.dp, 12.dp)) {
                        ReferenceModalActions(T("Skip", "تخطي"), T("Save Result", "حفظ التصنيف"), close, {
                            Store.setStage(listId, entryId, selected, notes.trim())
                            close(); Store.notify(Tk.SUCCESS, L(R.string.ui_saved_65225f27))
                        })
                    }
    }
}
