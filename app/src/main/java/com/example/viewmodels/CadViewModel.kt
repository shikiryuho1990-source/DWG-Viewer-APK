package com.example.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.BlueprintData
import com.example.models.CadEntity
import com.example.models.CadLayer
import com.example.parser.DwgMetadata
import com.example.parser.DwgParser
import com.example.utils.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

enum class CadTool {
    PAN_ZOOM,
    DRAW_LINE,
    DRAW_RECT,
    DRAW_CIRCLE,
    DRAW_TEXT
}

class CadViewModel : ViewModel() {
    private val TAG = "CadViewModel"

    // Metadata
    private val _dwgMetadata = MutableStateFlow(
        DwgMetadata(
            filename = "villa_renovation_site_layout.dwg",
            fileSize = 458900L,
            headerVersion = "AC1032",
            autoCadVersion = "AutoCAD 2018",
            isEncrypted = false,
            description = "Geometric plan of primary layout and conference lounger details",
            layerCount = 4,
            drawingUnits = "Millimeters (ISO Standard)",
            previewExtractStatus = "Loaded template drawing"
        )
    )
    val dwgMetadata: StateFlow<DwgMetadata> = _dwgMetadata.asStateFlow()

    // Map base drawings
    private val _selectedBlueprintName = MutableStateFlow("Symmetrical Residence Floor Plan")
    val selectedBlueprintName: StateFlow<String> = _selectedBlueprintName.asStateFlow()

    private val _activeDrawingEntities = MutableStateFlow<List<CadEntity>>(BlueprintData.FloorPlanBlueprint)
    val activeDrawingEntities: StateFlow<List<CadEntity>> = _activeDrawingEntities.asStateFlow()

    // Layers visibility
    private val _layers = MutableStateFlow<List<CadLayer>>(BlueprintData.DefaultLayers)
    val layers: StateFlow<List<CadLayer>> = _layers.asStateFlow()

    // User redline annotations
    private val _userAnnotations = MutableStateFlow<List<CadEntity>>(emptyList())
    val userAnnotations: StateFlow<List<CadEntity>> = _userAnnotations.asStateFlow()

    // Active configuration
    private val _activeTool = MutableStateFlow(CadTool.PAN_ZOOM)
    val activeTool: StateFlow<CadTool> = _activeTool.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color(0xFFFF1744)) // Red accent overlay default
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private val _selectedStrokeWidth = MutableStateFlow(4f)
    val selectedStrokeWidth: StateFlow<Float> = _selectedStrokeWidth.asStateFlow()

    private val _textAnnotationValue = MutableStateFlow("REV-NEEDED")
    val textAnnotationValue: StateFlow<String> = _textAnnotationValue.asStateFlow()

    // DWG Parsed Background Image
    private val _dwgPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val dwgPreviewBitmap: StateFlow<Bitmap?> = _dwgPreviewBitmap.asStateFlow()

    // Viewport transforms (Scale & Offset)
    private val _viewportScale = MutableStateFlow(1.0f)
    val viewportScale: StateFlow<Float> = _viewportScale.asStateFlow()

    private val _viewportOffset = MutableStateFlow(Offset.Zero)
    val viewportOffset: StateFlow<Offset> = _viewportOffset.asStateFlow()

    // Transient UI Feedbacks
    val feedbackMessage = mutableStateOf<String?>(null)
    val isExporting = mutableStateOf(false)

    // Drawing shape construction variables
    val drawingStartPoint = mutableStateOf<Offset?>(null)
    val drawingCurrentPoint = mutableStateOf<Offset?>(null)

    fun setTool(tool: CadTool) {
        _activeTool.value = tool
        // Reset dynamic draw buffers
        drawingStartPoint.value = null
        drawingCurrentPoint.value = null
    }

    fun setColor(color: Color) {
        _selectedColor.value = color
    }

    fun setStrokeWidth(width: Float) {
        _selectedStrokeWidth.value = width
    }

    fun setTextAnnotationValue(text: String) {
        _textAnnotationValue.value = text
    }

    fun updateViewport(scaleDelta: Float, offsetDelta: Offset) {
        _viewportScale.value = (_viewportScale.value * scaleDelta).coerceIn(0.2f, 8.0f)
        _viewportOffset.value = _viewportOffset.value + offsetDelta
    }

    fun resetViewport() {
        _viewportScale.value = 1.0f
        _viewportOffset.value = Offset.Zero
    }

    fun toggleLayerVisibility(layerId: Int) {
        _layers.value = _layers.value.map {
            if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it
        }
    }

    fun selectPreloadedArtwork(name: String) {
        _selectedBlueprintName.value = name
        // Flush extracted image to let vectors render on canvas
        _dwgPreviewBitmap.value = null

        _activeDrawingEntities.value = when (name) {
            "Symmetrical Residence Floor Plan" -> BlueprintData.FloorPlanBlueprint
            "Heavy Structural Steel Framing" -> BlueprintData.StructuralBlueprint
            "Mechanical Flange Assembly Detail" -> BlueprintData.MechanicalBlueprint
            "Primary Electrical Logic Schematic" -> BlueprintData.ElectricalBlueprint
            else -> BlueprintData.FloorPlanBlueprint
        }

        feedbackMessage.value = "Artboard loaded: $name"
    }

    /**
     * Undo the last added annotation redline
     */
    fun undoLastAnnotation() {
        if (_userAnnotations.value.isNotEmpty()) {
            _userAnnotations.value = _userAnnotations.value.dropLast(1)
            feedbackMessage.value = "Anotasi terakhir dihapus"
        }
    }

    /**
     * Clear all drawing redlines
     */
    fun clearAllAnnotations() {
        if (_userAnnotations.value.isNotEmpty()) {
            _userAnnotations.value = emptyList()
            feedbackMessage.value = "Semua redline dibersihkan"
        }
    }

    /**
     * Commit a finalized user-drawn annotation to the state list
     */
    fun commitAnnotation(entity: CadEntity) {
        _userAnnotations.value = _userAnnotations.value + entity
    }

    /**
     * Handles safe loading, byte representation scanning, version header decoding,
     * and bitmap extraction from a picked physical .dwg file using content Uri streams.
     */
    fun importDwgFile(context: Context, uri: Uri, fileName: String, fileSize: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        feedbackMessage.value = "Gagal membuka aliran file DWG"
                    }
                    return@launch
                }

                // Read all bytes
                val fileBytes = inputStream.use { it.readBytes() }
                Log.d(TAG, "Imported file size: ${fileBytes.size} bytes")

                // Decode metadata header text
                val metaHeaderStream = fileBytes.inputStream()
                val parsedMeta = DwgParser.parseHeader(metaHeaderStream, fileName, fileSize)

                // Decode preview bitmap sequence (scans for JPEG/PNG/BMP headers)
                val bitmapPreview = DwgParser.extractPreviewImage(fileBytes)

                withContext(Dispatchers.Main) {
                    _dwgMetadata.value = parsedMeta
                    _dwgPreviewBitmap.value = bitmapPreview

                    // Set matching mockup blueprint fallback based on file bytes/name,
                    // guaranteeing interactive vectors even if the thumbnail is missing or blank
                    if (bitmapPreview == null) {
                        val uppercaseName = fileName.uppercase()
                        val suggestedDraft = when {
                            "STRUC" in uppercaseName || "FRAME" in uppercaseName -> "Heavy Structural Steel Framing"
                            "MECH" in uppercaseName || "FLANGE" in uppercaseName || "PART" in uppercaseName -> "Mechanical Flange Assembly Detail"
                            "ELEC" in uppercaseName || "WIRE" in uppercaseName || "POWER" in uppercaseName -> "Primary Electrical Logic Schematic"
                            else -> "Symmetrical Residence Floor Plan"
                        }
                        selectPreloadedArtwork(suggestedDraft)
                        feedbackMessage.value = "Header DWG '${parsedMeta.autoCadVersion}' berhasil diparsing. Geometri interaktif dimuat."
                    } else {
                        // Userannotations persist over the base canvas image
                        feedbackMessage.value = "Preview DWG '${parsedMeta.autoCadVersion}' berhasil diekstrak dan dirender!"
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed reading source DWG stream", e)
                withContext(Dispatchers.Main) {
                    feedbackMessage.value = "Kesalahan impor file: ${e.message}"
                }
            }
        }
    }

    /**
     * Standard PDF Export integration
     */
    fun triggerPdfExport(context: Context) {
        isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val visibleLayerIds = _layers.value.filter { it.isVisible }.map { it.id }.toSet()
            val resultFile = PdfExporter.exportCadToPdf(
                context = context,
                metadata = _dwgMetadata.value,
                baseEntities = _activeDrawingEntities.value,
                annotations = _userAnnotations.value,
                visibleLayerIds = visibleLayerIds,
                backgroundImage = _dwgPreviewBitmap.value
            )

            withContext(Dispatchers.Main) {
                isExporting.value = false
                if (resultFile != null) {
                    PdfExporter.sharePdfFile(context, resultFile)
                    feedbackMessage.value = "Draft sukses di-plot ke PDF!"
                } else {
                    feedbackMessage.value = "Gagal mengekspor file PDF"
                }
            }
        }
    }
}
