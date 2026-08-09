package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.NotificationTarget
import com.example.ui.theme.CardBackground
import com.example.ui.theme.DividerColor
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessageFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "تنظیمات",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )

        // 1. Sending Methods
        Card(
            modifier = Modifier.fillMaxWidth().testTag("settings_sending_methods_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "روش‌های ارسال",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateEnableSms(!settings.enableSms) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = settings.enableSms,
                        onCheckedChange = { viewModel.updateEnableSms(it) },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
                        modifier = Modifier.testTag("enable_sms_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("پیامک (SMS)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateEnableWhatsapp(!settings.enableWhatsapp) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = settings.enableWhatsapp,
                        onCheckedChange = { viewModel.updateEnableWhatsapp(it) },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
                        modifier = Modifier.testTag("enable_whatsapp_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("واتساپ (WhatsApp)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }

        // 2. Notification Target
        Card(
            modifier = Modifier.fillMaxWidth().testTag("settings_target_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ارسال وضعیت حضور",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val targets = listOf(
                    NotificationTarget.ABSENT_ONLY to "فقط غایبین",
                    NotificationTarget.PRESENT_ONLY to "فقط حاضرین",
                    NotificationTarget.BOTH to "حاضرین و غایبین"
                )

                targets.forEach { (target, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateNotificationTarget(target) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.notificationTarget == target,
                            onClick = { viewModel.updateNotificationTarget(target) },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue),
                            modifier = Modifier.testTag("target_radio_${target.name}")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }
        }

        // 3. Departure Toggle
        Card(
            modifier = Modifier.fillMaxWidth().testTag("settings_departure_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ارسال پیام خروج از مکتب",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "نمایش دکمه ثبت خروج پس از پایان کلاس",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = settings.enableDeparture,
                    onCheckedChange = { viewModel.updateEnableDeparture(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue),
                    modifier = Modifier.testTag("enable_departure_switch")
                )
            }
        }

        // 4. Variables Guide Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "متغیرهای قابل استفاده در متن پیام‌ها:",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "{نام_شاگرد}   •   {نام_پدر}   •   {ساعت}   •   {تاریخ}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }

        // 5. Message Templates
        Card(
            modifier = Modifier.fillMaxWidth().testTag("settings_templates_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "متن پیام‌های اطلاع‌رسانی",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = settings.absenceTemplate,
                    onValueChange = { viewModel.updateAbsenceTemplate(it) },
                    label = { Text("متن پیام غیبت") },
                    modifier = Modifier.fillMaxWidth().testTag("template_absence_input"),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = DividerColor
                    )
                )

                OutlinedTextField(
                    value = settings.arrivalTemplate,
                    onValueChange = { viewModel.updateArrivalTemplate(it) },
                    label = { Text("متن پیام ورود / حضور") },
                    modifier = Modifier.fillMaxWidth().testTag("template_arrival_input"),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = DividerColor
                    )
                )

                if (settings.enableDeparture) {
                    OutlinedTextField(
                        value = settings.departureTemplate,
                        onValueChange = { viewModel.updateDepartureTemplate(it) },
                        label = { Text("متن پیام خروج") },
                        modifier = Modifier.fillMaxWidth().testTag("template_departure_input"),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = DividerColor
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
