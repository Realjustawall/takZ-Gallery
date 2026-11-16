package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.localization.BilingualStrings
import com.example.ui.TakzGalleryViewModel

/**
 * Reports the actual persisted index state, never an animated/fake percentage.
 * Counts originate from Room's reactive SQL queries, not a full UI-side media scan.
 */
@Composable
fun AiIndexProgressPanel(
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings,
    compact: Boolean = false
) {
    val progress by viewModel.aiIndexProgress.collectAsStateWithLifecycle()
    val enabled by viewModel.autoAiEnabled.collectAsStateWithLifecycle()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    strings.tr("On-device AI indexing", "ایندکس هوش مصنوعی آفلاین"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${progress.percentage}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { (progress.indexed.toFloat() / progress.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                strings.tr(
                    "${progress.indexed} of ${progress.total} photos indexed · ${progress.remaining} remaining",
                    "${progress.indexed} تصویر از ${progress.total} ایندکس شده · ${progress.remaining} باقی‌مانده"
                ),
                style = MaterialTheme.typography.bodySmall
            )
            val status = when {
                !enabled -> strings.tr("Paused in settings", "در تنظیمات متوقف شده")
                progress.isRunning -> strings.tr("Analyzing pictures in background", "در حال بررسی تصاویر در پس‌زمینه")
                progress.remaining == 0 -> strings.tr("Indexing complete", "ایندکس کامل است")
                progress.isWaitingForIdle -> strings.tr("Waiting until the phone is idle", "در انتظار بیکار شدن گوشی")
                else -> strings.tr("Scheduled for the next idle session", "در صف نوبت بعدی بیکاری گوشی")
            }
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!compact && progress.isRunning && progress.currentFile.isNotBlank()) {
                Text(
                    strings.tr("Last indexed: ${progress.currentFile}", "آخرین فایل: ${progress.currentFile}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
