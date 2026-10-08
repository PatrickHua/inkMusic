package com.calmapps.calmmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.slider.SliderMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    completeAlbumsWithYouTube: Boolean,
    onCompleteAlbumsWithYouTubeChange: (Boolean) -> Unit,
    musicFolderPath: String,
    hasAllFilesAccess: Boolean,
    onGrantAllFilesAccessClick: () -> Unit,
    onRescanLocalMusicClick: () -> Unit,
    isRescanningLocal: Boolean,
    localScanProgress: Float,
    isIngestingLocal: Boolean,
    localIngestProgress: Float,
    localScanTotalDiscovered: Int?,
    localScanSkippedUnchanged: Int?,
    localScanIndexedNewOrUpdated: Int?,
    localScanDeletedMissing: Int?,
) {
    // 0 = Streaming, 1 = Local
    val tabOptions = listOf("Streaming", "Local")

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PrimaryTabRowMMD(selectedTabIndex = selectedTab) {
            tabOptions.forEachIndexed { index, title ->
                TabMMD(
                    selected = selectedTab == index,
                    onClick = { onSelectedTabChange(index) },
                    text = {
                        TextMMD(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        if (selectedTab == 0) {
            InkLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                item {

                    TextMMD(
                        text = "Library features",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    HorizontalDividerMMD(
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCompleteAlbumsWithYouTubeChange(!completeAlbumsWithYouTube) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                            ) {
                                TextMMD(
                                    text = "Complete albums with YouTube",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                TextMMD(
                                    text = "When viewing a local album, search YouTube for missing songs and display them in the list.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SwitchMMD(
                                checked = completeAlbumsWithYouTube,
                                onCheckedChange = onCompleteAlbumsWithYouTubeChange,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        } else if (selectedTab == 1) {
            InkLazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        TextMMD(
                            text = "Music folder",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextMMD(
                            text = musicFolderPath,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextMMD(
                            text = "Songs go in songs/ (any folders), playlists in playlists/ as .m3u8, " +
                                "lyrics as .lrc next to each song. Everything stays here if the app is removed.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDividerMMD(
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                if (!hasAllFilesAccess) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        ) {
                            TextMMD(
                                text = "inkMusic needs \"All files access\" to read songs and playlists " +
                                    "copied from a computer.",
                                fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButtonMMD(
                                onClick = onGrantAllFilesAccessClick,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                TextMMD(text = "Allow access", fontSize = 16.sp)
                            }
                        }
                    }
                }

                item {
                    OutlinedButtonMMD(
                        onClick = onRescanLocalMusicClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        enabled = !isRescanningLocal,
                    ) {
                        TextMMD(
                            text = "Rescan",
                            fontSize = 16.sp,
                        )
                    }
                }

                if (isRescanningLocal && !isIngestingLocal) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                            ) {
                                SliderMMD(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = localScanProgress.coerceIn(0f, 1f),
                                    onValueChange = { },
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                val percent =
                                    (localScanProgress * 100f).toInt().coerceIn(0, 100)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    TextMMD(
                                        text = "Step 1 of 2 – Scanning folders for audio files… $percent%",
                                        fontSize = 14.sp,
                                    )
                                    if (localScanTotalDiscovered != null && localScanSkippedUnchanged != null && localScanIndexedNewOrUpdated != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextMMD(
                                            text = "Found $localScanTotalDiscovered files · Skipped $localScanSkippedUnchanged unchanged · Indexed $localScanIndexedNewOrUpdated new/updated",
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isIngestingLocal) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                            ) {
                                SliderMMD(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = localIngestProgress.coerceIn(0f, 1f),
                                    onValueChange = { },
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                val ingestPercent =
                                    (localIngestProgress * 100f).toInt().coerceIn(0, 100)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    TextMMD(
                                        text = "Step 2 of 2 – Adding music to library… $ingestPercent%",
                                        fontSize = 14.sp,
                                    )
                                    if (localScanDeletedMissing != null && localScanDeletedMissing > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextMMD(
                                            text = "Removed ${localScanDeletedMissing} files that are no longer present",
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Show the last scan summary after work is complete.
                if (!isRescanningLocal && !isIngestingLocal &&
                    localScanTotalDiscovered != null &&
                    localScanSkippedUnchanged != null &&
                    localScanIndexedNewOrUpdated != null
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            TextMMD(
                                text = "Last scan",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            TextMMD(
                                text = "Found $localScanTotalDiscovered files · Skipped $localScanSkippedUnchanged unchanged · Indexed $localScanIndexedNewOrUpdated new/updated",
                                fontSize = 13.sp,
                            )

                            if (localScanDeletedMissing != null && localScanDeletedMissing > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                TextMMD(
                                    text = "Removed ${localScanDeletedMissing} files that are no longer present",
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}
