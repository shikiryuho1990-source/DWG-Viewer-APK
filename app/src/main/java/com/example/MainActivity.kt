package com.example

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.CadViewport
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodels.CadTool
import com.example.viewmodels.CadViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.toArgb
import com.example.parser.DwgMetadata

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = Color(0xFF0F0F11) // Dark Slate CAD background
                ) { innerPadding ->
                    CadWorkbenchScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadWorkbenchScreen(
    modifier: Modifier = Modifier,
    viewModel: CadViewModel = viewModel()
) {
    val context = LocalContext.current
    val metadata by viewModel.dwgMetadata.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val selectedBlueprint by viewModel.selectedBlueprintName.collectAsState()
    val userAnnotations by viewModel.userAnnotations.collectAsState()
    val layers by viewModel.layers.collectAsState()
    val isExporting by remember { viewModel.isExporting }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showBlueprintSelector by remember { mutableStateOf(false) }

    // SAF Document Picker launcher to load external .dwg drafts
    val dwgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            var fileName = "imported_drawing.dwg"
            var fileSize = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameCol != -1) fileName = cursor.getString(nameCol)
                        if (sizeCol != -1) fileSize = cursor.getLong(sizeCol)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.importDwgFile(context, uri, fileName, fileSize)
        }
    }

    // Capture transient feedback messages
    val feedback by remember { viewModel.feedbackMessage }
    LaunchedEffect(feedback) {
        feedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.feedbackMessage.value = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11))
    ) {
        // 1. TOP HEADER & WORKSPACE BANNER
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp),
            color = Color(0xFF16161A),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Architecture,
                            contentDescription = "Logo CAD",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "LITE DWG WORKBENCH",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Drafting & Annotation Utility",
                                color = Color(0xFF90A4AE),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Top Action Row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Info sheet button
                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier.testTag("info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Detail File",
                                tint = Color(0xFF90A4AE)
                            )
                        }

                        // Open Local DWG Document Trigger
                        Button(
                            onClick = {
                                dwgPickerLauncher.launch(
                                    arrayOf(
                                        "application/x-dwg",
                                        "image/vnd.dwg",
                                        "image/x-dwg",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00AFB9),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("import_dwg_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buka DWG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Active Dwg Metadata HUD Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF222228))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = metadata.filename,
                                color = Color(0xFFECEFF1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "Standard: ${metadata.autoCadVersion} (${metadata.headerVersion}) • Size: ${metadata.fileSize / 1024} KB",
                            color = Color(0xFF90A4AE),
                            fontSize = 9.sp
                        )
                    }

                    // Blueprint Changer Button
                    Box {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showBlueprintSelector = true }
                                .background(Color(0xFF2E2E38))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Pilih Gambar",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedBlueprint.length > 18) selectedBlueprint.take(16) + ".." else selectedBlueprint,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showBlueprintSelector,
                            onDismissRequest = { showBlueprintSelector = false },
                            modifier = Modifier.background(Color(0xFF1E1E24))
                        ) {
                            listOf(
                                "Symmetrical Residence Floor Plan",
                                "Heavy Structural Steel Framing",
                                "Mechanical Flange Assembly Detail",
                                "Primary Electrical Logic Schematic"
                            ).forEach { bName ->
                                DropdownMenuItem(
                                    text = { Text(bName, color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.selectPreloadedArtwork(bName)
                                        showBlueprintSelector = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. MIDDLE VIEWPORT & FLOATING TOOLS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Main CAD drawing viewport
            CadViewport(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().testTag("cad_viewport")
            )

            // Floating Vertical Toolbar (Drafting tools overlay on Left edge)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xDC1A1A20) // Translucent dark slate
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x3300E5FF)),
                    modifier = Modifier.width(52.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        ToolbarToolBtn(
                            icon = Icons.Default.ZoomOutMap,
                            label = "Navigasi",
                            active = activeTool == CadTool.PAN_ZOOM,
                            onClick = { viewModel.setTool(CadTool.PAN_ZOOM) },
                            testTag = "btn_nav_tool"
                        )
                        Divider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(horizontal = 6.dp))
                        ToolbarToolBtn(
                            icon = Icons.Default.Gesture,
                            label = "Garis",
                            active = activeTool == CadTool.DRAW_LINE,
                            onClick = { viewModel.setTool(CadTool.DRAW_LINE) },
                            testTag = "btn_line_tool"
                        )
                        ToolbarToolBtn(
                            icon = Icons.Default.CropSquare,
                            label = "Kotak",
                            active = activeTool == CadTool.DRAW_RECT,
                            onClick = { viewModel.setTool(CadTool.DRAW_RECT) },
                            testTag = "btn_rect_tool"
                        )
                        ToolbarToolBtn(
                            icon = Icons.Default.RadioButtonUnchecked,
                            label = "Lingkaran",
                            active = activeTool == CadTool.DRAW_CIRCLE,
                            onClick = { viewModel.setTool(CadTool.DRAW_CIRCLE) },
                            testTag = "btn_circle_tool"
                        )
                        ToolbarToolBtn(
                            icon = Icons.Default.TextFields,
                            label = "Teks",
                            active = activeTool == CadTool.DRAW_TEXT,
                            onClick = { viewModel.setTool(CadTool.DRAW_TEXT) },
                            testTag = "btn_text_tool"
                        )
                        
                        Divider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(horizontal = 6.dp))

                        // Undo last redline
                        IconButton(
                            onClick = { viewModel.undoLastAnnotation() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_undo")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = if (userAnnotations.isNotEmpty()) Color.White else Color(0xFF55555F),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Wipe all redlines
                        IconButton(
                            onClick = { viewModel.clearAllAnnotations() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Bersihkan Semua",
                                tint = if (userAnnotations.isNotEmpty()) Color(0xFFFF1744) else Color(0xFF55555F),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Real-time HUD stats (Bottom-Right overlays)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Compass-like Quick reset viewport button
                FloatingActionButton(
                    onClick = { viewModel.resetViewport() },
                    containerColor = Color(0xFF1E1E24),
                    contentColor = Color(0xFF00E5FF),
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Atur Ulang Kamera", modifier = Modifier.size(16.dp))
                }

                // Grid Coordinates HUD widget
                Surface(
                    color = Color(0xDC1A1A20),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, Color(0x3390A4AE))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GRID ON • SNAP OFF | REDLINES: ${userAnnotations.size}",
                            color = Color(0xFF90A4AE),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 3. BOTTOM CONTROL DRAWER (Layer Manager & Redline customizer)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF16161A),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Line 1: Layer Visibility Filter Chips (Scrollable Row)
                Text(
                    text = "MANAJEMEN LAYER DRAFTING",
                    color = Color(0xFF90A4AE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    layers.forEach { layer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (layer.isVisible) Color(0xFF202A25) else Color(0xFF2E2E38))
                                .border(
                                    width = 1.dp,
                                    color = if (layer.isVisible) Color(0xFF00E676) else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { viewModel.toggleLayerVisibility(layer.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("layer_toggle_${layer.id}")
                        ) {
                            Icon(
                                imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (layer.isVisible) Color(0xFF00E676) else Color(0xFF90A4AE),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = layer.name,
                                color = if (layer.isVisible) Color(0xFF00E676) else Color(0xFFECEFF1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Conditional Layout rendering based on chosen Tool
                AnimatedVisibility(
                    visible = activeTool != CadTool.PAN_ZOOM,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF202026))
                            .padding(10.dp)
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "KONFIGURASI ANOTASI REDLINE",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Layer yang sedang diedit: Redlines (Layer 3)",
                                color = Color(0xFF90A4AE),
                                fontSize = 9.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // If drawing Text, show the input text field
                            if (activeTool == CadTool.DRAW_TEXT) {
                                OutlinedTextField(
                                    value = viewModel.textAnnotationValue.collectAsState().value,
                                    onValueChange = { viewModel.setTextAnnotationValue(it) },
                                    label = { Text("Teks Redline", color = Color(0xFF90A4AE), fontSize = 11.sp) },
                                    maxLines = 1,
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        unfocusedBorderColor = Color(0x66FFFFFF),
                                        focusedContainerColor = Color(0xFF16161A),
                                        unfocusedContainerColor = Color(0xFF16161A)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("text_annotation_input")
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            // Color picker palette
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text("Pilih Warna", color = Color(0xFF90A4AE), fontSize = 9.sp, modifier = Modifier.padding(bottom = 4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val colors = listOf(
                                        Color(0xFFFF1744), // Coral Red
                                        Color(0xFF00E5FF), // Cyan
                                        Color(0xFF00E676), // Lime Green
                                        Color(0xFFFF9100), // Orange
                                        Color(0xFFFFFFFF)  // White
                                    )
                                    val currentColor by viewModel.selectedColor.collectAsState()
                                    colors.forEach { brushColor ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(brushColor)
                                                .border(
                                                    width = if (currentColor == brushColor) 2.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.setColor(brushColor) }
                                                .testTag("color_picker_${brushColor.toArgb()}")
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Stroke weight quick steps
                            Column(modifier = Modifier.weight(0.8f)) {
                                Text("Ketebalan", color = Color(0xFF90A4AE), fontSize = 9.sp, modifier = Modifier.padding(bottom = 4.dp))
                                val strokeBy by viewModel.selectedStrokeWidth.collectAsState()
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(2f, 4f, 8f).forEach { weight ->
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (strokeBy == weight) Color(0xFF00E5FF) else Color(0xFF2E2E38))
                                                .clickable { viewModel.setStrokeWidth(weight) }
                                                .testTag("stroke_weight_${weight.toInt()}")
                                        ) {
                                            Text(
                                                text = "${weight.toInt()}px",
                                                color = if (strokeBy == weight) Color.Black else Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Line 3: Vector PDF Plotter triggers
                Button(
                    onClick = { viewModel.triggerPdfExport(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91E63), // High-intensity color for export focus
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("btn_export_pdf"),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SEDANG MEM-PLOT PDF...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("KONVERSI KE PDF & BAGIKAN", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // A. DETAILED CAD PROPERTIES DIALOG SHEET
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PROPERTI FILE CAD (DWG)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    DwgMetaRow(label = "Format Standard", value = metadata.headerVersion)
                    DwgMetaRow(label = "Aplikasi Kompatibel", value = metadata.autoCadVersion)
                    DwgMetaRow(label = "Nama Berkas", value = metadata.filename)
                    DwgMetaRow(label = "Ukuran Data", value = "${metadata.fileSize} bytes (~${metadata.fileSize / 1024} KB)")
                    DwgMetaRow(label = "Total Layer CAD", value = "${metadata.layerCount} Terkonfigurasi")
                    DwgMetaRow(label = "Satuan Ukur", value = metadata.drawingUnits)
                    DwgMetaRow(label = "Laporan Dekoder", value = metadata.previewExtractStatus)
                    DwgMetaRow(label = "Deskripsi Model", value = metadata.description)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoDialog = false },
                    modifier = Modifier.testTag("close_info_dialog")
                ) {
                    Text("TUTUP", color = Color(0xFF00E5FF))
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }
}

@Composable
fun DwgMetaRow(label: String, value: String) {
    Column {
        Text(text = label.uppercase(), color = Color(0xFF90A4AE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 12.sp)
        Divider(color = Color(0x1AFFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ToolbarToolBtn(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF00AFB9) else Color.Transparent)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color.White else Color(0xFF90A4AE),
            modifier = Modifier.size(20.dp)
        )
    }
}
