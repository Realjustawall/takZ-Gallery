package com.example

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.localization.BilingualStrings
import com.example.ui.TakzGalleryViewModel
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.GalleryHomeScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.SearchAndAiScreen
import com.example.ui.screens.SecureVaultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartCleanupScreen
import com.example.ui.theme.TakzGalleryTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val galleryViewModel: TakzGalleryViewModel = viewModel()
            TakzGalleryAppRoot(viewModel = galleryViewModel)
        }
    }
}

@Composable
fun TakzGalleryAppRoot(viewModel: TakzGalleryViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeBottomTab.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.triggerReindexAll() }
    LaunchedEffect(Unit) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = requiredPermissions.filter {
            activity?.let { ctx -> ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED } == true
        }
        if (missing.isNotEmpty()) mediaPermissionLauncher.launch(missing.toTypedArray())
    }
    val vaultActive = settings.blockVaultScreenshots && activeTab == "VAULT"
    DisposableEffect(activity, vaultActive) {
        if (vaultActive) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val openedMediaId by viewModel.openedMediaId.collectAsStateWithLifecycle()
    val editingMediaId by viewModel.editingMediaId.collectAsStateWithLifecycle()
    val initialEditorTab by viewModel.initialEditorTab.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val isFa = settings.isPersianActive
    val strings = remember(isFa) { BilingualStrings(isFa) }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(2500)
            viewModel.showStatusMessage(null)
        }
    }

    TakzGalleryTheme(
        themeMode = settings.themeMode,
        accentPreset = settings.accentPreset,
        customNeonHue = settings.customNeonHue,
        dynamicColor = settings.dynamicColor,
        isRtl = isFa
    ) {
        val editingItem = remember(allMedia, editingMediaId) {
            allMedia.find { it.id == editingMediaId }
        }
        val openedItem = remember(allMedia, openedMediaId) {
            allMedia.find { it.id == openedMediaId }
        }

        when {
            editingItem != null -> {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        PhotoEditorScreen(
                            item = editingItem,
                            initialTab = initialEditorTab,
                            viewModel = viewModel,
                            strings = strings
                        )
                    }
                }
            }
            openedItem != null -> {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MediaViewerScreen(
                            item = openedItem,
                            viewModel = viewModel,
                            strings = strings
                        )
                    }
                }
            }
            isSettingsOpen -> {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SettingsScreen(viewModel = viewModel, strings = strings)
                    }
                }
            }
            else -> {
                if (activeTab != "PHOTOS") {
                    BackHandler { viewModel.selectBottomTab("PHOTOS") }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.background,
                            tonalElevation = 0.dp,
                            modifier = Modifier.testTag("main_bottom_nav")
                        ) {
                            val navItems = listOf(
                                Triple("PHOTOS", strings.tr("Pictures", "تصاویر"), Icons.Default.PhotoLibrary),
                                Triple("ALBUMS", strings.tr("Albums", "آلبوم‌ها"), Icons.Default.PhotoAlbum),
                                Triple("SEARCH", strings.tr("Search", "جستجو"), Icons.Default.Search),
                                Triple("CLEANUP", strings.tr("Cleanup", "پاک‌سازی"), Icons.Default.CleaningServices),
                                Triple("VAULT", strings.tr("Vault", "گاوصندوق"), Icons.Default.Lock)
                            )
                            for ((key, label, icon) in navItems) {
                                val isSelected = activeTab == key
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { viewModel.selectBottomTab(key) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${key.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (activeTab) {
                            "PHOTOS" -> GalleryHomeScreen(viewModel = viewModel, strings = strings)
                            "ALBUMS" -> AlbumsScreen(viewModel = viewModel, strings = strings)
                            "SEARCH" -> SearchAndAiScreen(viewModel = viewModel, strings = strings)
                            "CLEANUP" -> SmartCleanupScreen(viewModel = viewModel, strings = strings)
                            "VAULT" -> SecureVaultScreen(viewModel = viewModel, strings = strings)
                        }

                        AnimatedVisibility(
                            visible = statusBanner != null,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(24.dp),
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = statusBanner ?: "",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
