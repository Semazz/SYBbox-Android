package com.sybbox.ui.scanner

import android.Manifest
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sybbox.R
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@androidx.camera.core.ExperimentalGetImage
@Composable
fun ScannerScreen(onScanned: (String) -> Unit, onDismiss: () -> Unit) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val galleryScanner = remember { BarcodeScanning.getClient() }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            val image = InputImage.fromBitmap(bitmap, 0)
            galleryScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val value = if (barcode.valueType == Barcode.TYPE_URL) barcode.url?.url else barcode.rawValue
                        if (!value.isNullOrBlank()) { onScanned(value); break }
                    }
                }
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermission.status.isGranted) {
            CameraPreview(onBarcodeScanned = onScanned, modifier = Modifier.fillMaxSize())
            ScannerOverlay()
            TopBar(onDismiss)
            ScannerBottomBar(
                onPaste = {
                    val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val text = clip?.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrBlank()) onScanned(text)
                },
                onGallery = { galleryLauncher.launch("image/*") },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.scanner_permission), color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { cameraPermission.launchPermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text(stringResource(R.string.grant_permission), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TopBar(onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(stringResource(R.string.scan_qr), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ScannerBottomBar(onPaste: () -> Unit, onGallery: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.scanner_title),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.server_link_hint),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(onClick = onGallery) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Галерея", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                androidx.compose.material3.TextButton(onClick = onPaste) {
                    Icon(
                        Icons.Filled.ContentPaste,
                        null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.import_from_clipboard),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanY by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    val limeColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val scanSize = w * 0.65f
            val left = (w - scanSize) / 2
            val top = (h - scanSize) / 2.5f
            val cornerLen = scanSize * 0.12f
            val cornerWidth = 4.dp.toPx()

            drawRect(Color.Black.copy(alpha = 0.55f))

            drawRoundRect(Color.Transparent, topLeft = Offset(left, top), size = Size(scanSize, scanSize), cornerRadius = CornerRadius(16.dp.toPx()))

            drawRoundRect(Color.White.copy(alpha = 0.15f), topLeft = Offset(left, top), size = Size(scanSize, scanSize), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))

            val corners = listOf(
                Offset(left, top) to Offset(left + cornerLen, top),
                Offset(left, top) to Offset(left, top + cornerLen),
                Offset(left + scanSize, top) to Offset(left + scanSize - cornerLen, top),
                Offset(left + scanSize, top) to Offset(left + scanSize, top + cornerLen),
                Offset(left, top + scanSize) to Offset(left + cornerLen, top + scanSize),
                Offset(left, top + scanSize) to Offset(left, top + scanSize - cornerLen),
                Offset(left + scanSize, top + scanSize) to Offset(left + scanSize - cornerLen, top + scanSize),
                Offset(left + scanSize, top + scanSize) to Offset(left + scanSize, top + scanSize - cornerLen),
            )
            corners.forEach { (start, end) -> drawLine(limeColor, start, end, cornerWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round) }

            val lineY = top + scanY * scanSize
            drawLine(limeColor, Offset(left + 12.dp.toPx(), lineY), Offset(left + scanSize - 12.dp.toPx(), lineY), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(limeColor.copy(alpha = 0.3f), 30.dp.toPx(), Offset(left + scanSize / 2, lineY))
        }
    }
}

@androidx.camera.core.ExperimentalGetImage
@Composable
fun CameraPreview(onBarcodeScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember { BarcodeScanning.getClient() }
    var hasScanned by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            if (!hasScanned) {
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val value = if (barcode.valueType == Barcode.TYPE_URL) barcode.url?.url else barcode.rawValue
                                                if (value != null) { hasScanned = true; onBarcodeScanned(value) }
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else { imageProxy.close() }
                            } else { imageProxy.close() }
                        }
                    }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                } catch (e: Exception) { Log.e("Scanner", "Camera bind failed", e) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
    )
}