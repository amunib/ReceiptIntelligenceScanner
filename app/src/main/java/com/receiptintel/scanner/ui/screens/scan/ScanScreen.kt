package com.receiptintel.scanner.ui.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.foundation.Image
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageCapture
import com.receiptintel.scanner.R
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBatchReadyForProcessing: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var reviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    stringResource(R.string.scan_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        )

        if (!hasCameraPermission) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.scan_camera_permission_required),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.scan_grant_permission))
                }
            }
            return@Column
        }

        if (reviewBitmap != null) {
            // Review/crop-adjust screen for the most recently captured photo.
            var cropRect by remember(reviewBitmap) {
                mutableStateOf(androidx.compose.ui.geometry.Rect(60f, 200f, 700f, 1400f))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Image(
                    bitmap = reviewBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                CropOverlay(
                    initialRect = cropRect,
                    onRectChanged = { cropRect = it }
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    stringResource(R.string.scan_adjust_crop),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = { reviewBitmap = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_retake))
                }
                Button(
                    onClick = {
                        val bmp = reviewBitmap!!
                        val cropped = safeCrop(bmp, cropRect)
                        viewModel.addCapture(cropped)
                        reviewBitmap = null
                        if (!viewModel.batchMode) {
                            viewModel.commitToProcessingQueue()
                            onBatchReadyForProcessing()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_confirm))
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraPreviewContent(onImageCaptureReady = { imageCapture = it })

                // Receipt Guide Frame Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    ) {}
                }

                // Top Hint Chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        stringResource(R.string.scan_position_receipt),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }

            if (viewModel.captures.isNotEmpty()) {
                LazyRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.captures.size) { index ->
                        Image(
                            bitmap = viewModel.captures[index].asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.scan_batch_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.scan_batch_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = viewModel.batchMode, onCheckedChange = { viewModel.toggleBatchMode() })
            }

            // Redesigned Shutter Button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {
                        imageCapture?.captureToBitmap(
                            executor = executor,
                            onCaptured = { bitmap -> reviewBitmap = bitmap },
                            onError = { }
                        )
                    },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(36.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.scan_capture),
                        modifier = Modifier.size(32.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }

            if (viewModel.batchMode && viewModel.captures.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.commitToProcessingQueue()
                        onBatchReadyForProcessing()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("${stringResource(R.string.processing_title)} (${viewModel.captures.size})")
                }
            }
        }
    }
}

private fun safeCrop(bitmap: Bitmap, rect: androidx.compose.ui.geometry.Rect): Bitmap {
    val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
    val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
    val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
    val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)
    return try {
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    } catch (e: Exception) {
        bitmap
    }
}
