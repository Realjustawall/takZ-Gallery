package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.database.VaultItemEntity
import com.example.core.localization.AppLanguage
import com.example.core.localization.BilingualStrings
import com.example.core.localization.CalendarSystem
import com.example.core.localization.PersianNormalizer
import com.example.ui.TakzGalleryViewModel
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SecureVaultScreen(
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val isPinConfigured by viewModel.isPinConfigured.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val isFa = settings.isPersianActive

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun submitPin(candidatePin: String) {
        if (candidatePin.length < 4) {
            pinError = strings.tr("Enter at least 4 digits", "حداقل ۴ رقم وارد کنید")
            return
        }
        if (!isPinConfigured) {
            val ok = viewModel.setupOrChangeVaultPin(candidatePin)
            if (ok) {
                pinInput = ""
                pinError = null
            }
        } else {
            val unlocked = viewModel.unlockVault(candidatePin)
            if (unlocked) {
                pinInput = ""
                pinError = null
            } else {
                pinError = strings.tr("Incorrect PIN", "رمز نادرست است")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("secure_vault_screen")
    ) {
        // Samsung One UI Secure Folder / Vault Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.tr("Private Vault", "گاوصندوق خصوصی"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (isUnlocked) {
                FilledTonalButton(
                    onClick = {
                        pinInput = ""
                        viewModel.lockVault()
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("vault_lock_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.tr("Lock", "قفل کردن"))
                }
            }
        }

        if (!isUnlocked) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.EnhancedEncryption,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (isPinConfigured) {
                                    strings.tr("Enter Vault PIN", "رمز گاوصندوق را وارد کنید")
                                } else {
                                    strings.tr("Set 4-Digit Vault PIN", "انتخاب رمز ۴ رقمی گاوصندوق")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Visual PIN Dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until 4) {
                                    val filled = i < pinInput.length
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (filled) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    if (it.length <= 8) {
                                        pinInput = it
                                        pinError = null
                                    }
                                },
                                label = { Text(strings.tr("PIN", "رمز عبور")) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vault_pin_input")
                            )

                            if (pinError != null) {
                                Text(
                                    text = pinError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            // One UI Soft Numeric Keypad
                            val keypadRows = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9")
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (row in keypadRows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (digit in row) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(50.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        if (pinInput.length < 8) {
                                                            val next = pinInput + digit
                                                            pinInput = next
                                                            pinError = null
                                                            if (next.length == 4) {
                                                                submitPin(next)
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = PersianNormalizer.formatDigits(digit, isFa),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                pinInput = ""
                                                pinError = null
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = strings.tr("Clear", "پاک کردن"),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (pinInput.length < 8) {
                                                    val next = pinInput + "0"
                                                    pinInput = next
                                                    pinError = null
                                                    if (next.length == 4) {
                                                        submitPin(next)
                                                    }
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = PersianNormalizer.formatDigits("0", isFa),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (pinInput.isNotEmpty()) {
                                                    pinInput = pinInput.dropLast(1)
                                                    pinError = null
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { submitPin(pinInput) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("vault_submit_pin_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isPinConfigured) strings.tr("Unlock Vault", "بازگشایی")
                                        else strings.tr("Set PIN & Open", "تأیید و ورود")
                                    )
                                }

                            }
                        }
                    }
                }
            }
        } else {
            val availableGalleryMedia = remember(allMedia) {
                allMedia.filter { !it.isTrashed }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = strings.tr(
                            "Files in Vault (${vaultItems.size})",
                            "تصاویر داخل گاوصندوق (${PersianNormalizer.formatNumber(vaultItems.size, isFa)})"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (vaultItems.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.tr(
                                    "Vault is empty. Tap 'Move to Vault' on any photo below.",
                                    "گاوصندوق خالی است. از لیست زیر هر عکسی را با یک لمس به گاوصندوق منتقل کنید."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(vaultItems, key = { it.vaultId }) { vItem ->
                        DecryptedVaultItemCard(
                            vItem = vItem,
                            viewModel = viewModel,
                            strings = strings,
                            isFa = isFa
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.tr("Add Photos to Vault", "افزودن عکس از گالری به گاوصندوق"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(availableGalleryMedia, key = { "quick_enc_${it.id}" }) { media ->
                    val imgModel = remember(media.contentUri) {
                        val u = Uri.parse(media.contentUri)
                        if (u.scheme == "file") File(u.path ?: "") else media.contentUri
                    }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AsyncImage(
                                model = imgModel,
                                contentDescription = media.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${media.folderName} • ${PersianNormalizer.formatNumber((media.sizeBytes / 1024).toInt(), isFa)} KB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.encryptMediaToVault(media) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("quick_encrypt_${media.id}")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.tr("Move to Vault", "انتقال"),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecryptedVaultItemCard(
    vItem: VaultItemEntity,
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings,
    isFa: Boolean
) {
    var decryptedBitmap by remember(vItem.vaultId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(vItem.vaultId, vItem.encryptedFilePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                val encFile = File(vItem.encryptedFilePath)
                if (encFile.exists()) {
                    val plainBytes = viewModel.vaultCryptoEngine.decryptVaultFileToBytes(vItem.encryptedFilePath, vItem.ivHex)
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    decryptedBitmap = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size, opts)
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_item_${vItem.vaultId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (decryptedBitmap != null) {
                    Image(
                        bitmap = decryptedBitmap!!.asImageBitmap(),
                        contentDescription = vItem.originalFileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.EnhancedEncryption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = vItem.originalFileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${PersianNormalizer.formatNumber((vItem.originalSizeBytes / 1024).toInt(), isFa)} KB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = { viewModel.decryptVaultItemToGallery(vItem) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("decrypt_restore_${vItem.vaultId}")
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = strings.tr("Restore", "بازگردانی"),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    androidx.activity.compose.BackHandler {
        viewModel.closeSettings()
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isFa = settings.isPersianActive
    var exportedBackupJson by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf("NEON_THEME") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen")
    ) {
        // Samsung One UI Settings Header
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { viewModel.closeSettings() },
                        modifier = Modifier.testTag("close_settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.tr("Back", "بازگشت"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.tr("Gallery Settings", "تنظیمات گالری"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                FilledTonalButton(
                    onClick = { viewModel.resetSettingsToDefault() },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.tr("Reset", "پیش‌فرض"))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val categories = listOf(
                "NEON_THEME" to strings.tr("Theme & Color", "پوسته و رنگ"),
                "LANGUAGE" to strings.tr("Language & Date", "زبان و تقویم"),
                "LAYOUT" to strings.tr("Grid Layout", "چیدمان گالری"),
                "AI" to strings.tr("Background AI", "هوش مصنوعی پس‌زمینه"),
                "BACKUP" to strings.tr("Backup", "پشتیبان‌گیری")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((catKey, catLabel) in categories) {
                    val selected = selectedSection == catKey
                    FilterChip(
                        selected = selected,
                        onClick = { selectedSection = catKey },
                        label = { Text(catLabel) },
                        shape = RoundedCornerShape(20.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("settings_cat_${catKey.lowercase()}")
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedSection == "NEON_THEME") {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = strings.tr("Theme Mode", "حالت نمایش (تیره / روشن)"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (tm in ThemeMode.entries) {
                                    FilterChip(
                                        selected = settings.themeMode == tm,
                                        onClick = { viewModel.updateSettings { it.copy(themeMode = tm) } },
                                        label = { Text(if (isFa) tm.labelFa else tm.labelEn) },
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.testTag("theme_option_${tm.id}")
                                    )
                                }
                            }

                            Text(
                                text = strings.tr("Accent Color", "رنگ اصلی برنامه"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (preset in AccentPreset.entries) {
                                    val dotColor = if (preset == AccentPreset.CUSTOM_HUE) {
                                        com.example.ui.theme.neonColorFromHue(settings.customNeonHue)
                                    } else {
                                        preset.primaryDark
                                    }
                                    FilterChip(
                                        selected = settings.accentPreset == preset,
                                        onClick = { viewModel.updateSettings { it.copy(accentPreset = preset) } },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                            )
                                        },
                                        label = { Text(if (isFa) preset.labelFa else preset.labelEn) },
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = strings.tr(
                                    "Custom Color Spectrum (${settings.customNeonHue.toInt()}°)",
                                    "انتخاب طیف رنگ دلخواه (${PersianNormalizer.formatNumber(settings.customNeonHue.toInt(), isFa)}°)"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Slider(
                                value = settings.customNeonHue,
                                onValueChange = { hue ->
                                    viewModel.updateSettings {
                                        it.copy(
                                            accentPreset = AccentPreset.CUSTOM_HUE,
                                            customNeonHue = hue
                                        )
                                    }
                                },
                                valueRange = 0f..360f,
                                modifier = Modifier.testTag("custom_neon_hue_slider")
                            )
                        }
                    }
                }
            }

            if (selectedSection == "LANGUAGE") {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = strings.tr("Language", "زبان برنامه"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (lang in AppLanguage.entries) {
                                    FilterChip(
                                        selected = settings.language == lang,
                                        onClick = { viewModel.updateSettings { it.copy(language = lang) } },
                                        label = { Text(if (isFa) lang.displayNameFa else lang.displayNameEn) },
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.testTag("lang_option_${lang.code}")
                                    )
                                }
                            }

                            Text(
                                text = strings.tr("Calendar", "نوع تقویم"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (cal in CalendarSystem.entries) {
                                    FilterChip(
                                        selected = settings.calendarSystem == cal,
                                        onClick = { viewModel.updateSettings { it.copy(calendarSystem = cal) } },
                                        label = { Text(if (isFa) cal.labelFa else cal.labelEn) },
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.testTag("calendar_option_${cal.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedSection == "LAYOUT") {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = strings.tr(
                                    "Columns: ${settings.gridColumns}",
                                    "تعداد ستون‌ها: ${PersianNormalizer.formatNumber(settings.gridColumns, isFa)}"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Slider(
                                value = settings.gridColumns.toFloat(),
                                onValueChange = { c -> viewModel.updateSettings { it.copy(gridColumns = c.toInt()) } },
                                valueRange = 2f..5f,
                                steps = 2
                            )

                            Text(
                                text = strings.tr(
                                    "Corner Radius: ${settings.cornerRadiusDp} dp",
                                    "گردی گوشه عکس‌ها: ${PersianNormalizer.formatNumber(settings.cornerRadiusDp, isFa)}"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Slider(
                                value = settings.cornerRadiusDp.toFloat(),
                                onValueChange = { r -> viewModel.updateSettings { it.copy(cornerRadiusDp = r.toInt()) } },
                                valueRange = 0f..24f
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.tr("Show Stories", "نمایش بخش داستان‌ها"))
                                Switch(
                                    checked = settings.memoriesEnabled,
                                    onCheckedChange = { m -> viewModel.updateSettings { it.copy(memoriesEnabled = m) } }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedSection == "AI") {
                item { AiIndexProgressPanel(viewModel, strings) }
                item {
                    val enabled by viewModel.autoAiEnabled.collectAsStateWithLifecycle()
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        strings.tr("Automatic AI tagging", "تگ‌گذاری خودکار هوش مصنوعی"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        strings.tr(
                                            "Runs only while the device is idle, with safe battery and storage; processes a small batch each time.",
                                            "فقط در زمان بیکار بودن گوشی و مناسب بودن باتری و حافظه، عکس‌ها را دسته‌دسته بررسی می‌کند."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = viewModel::setAutomaticAiEnabled,
                                    modifier = Modifier.testTag("background_ai_toggle")
                                )
                            }
                            Text(
                                strings.tr(
                                    "Images indexed in background: ${viewModel.getBackgroundAiProcessedTotal()}",
                                    "تعداد تصاویر پردازش‌شده در پس‌زمینه: ${viewModel.getBackgroundAiProcessedTotal()}"
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(
                                onClick = viewModel::triggerReindexAll,
                                enabled = enabled
                            ) {
                                Text(strings.tr("Rebuild AI tags when idle", "ساخت دوباره تگ‌ها هنگام بیکاری گوشی"))
                            }
                        }
                    }
                }
            }

            if (selectedSection == "BACKUP") {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        exportedBackupJson = viewModel.libraryManager.exportBackupJson()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_backup_json_button")
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.tr("Export Backup", "تهیه نسخه پشتیبان"))
                            }

                            if (exportedBackupJson != null) {
                                Text(
                                    text = exportedBackupJson!!.take(400) + "\n…",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
