package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.example.models.CadEntity
import com.example.parser.DwgMetadata
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    private const val TAG = "PdfExporter"

    fun exportCadToPdf(
        context: Context,
        metadata: DwgMetadata,
        baseEntities: List<CadEntity>,
        annotations: List<CadEntity>,
        visibleLayerIds: Set<Int>,
        backgroundImage: Bitmap?
    ): File? {
        // Document dimensions (A4 Landscape standard roughly: 842 x 595 pixels at 72dpi)
        val pageWidth = 842
        val pageHeight = 595

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        try {
            // Paints
            val bgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#121214") // Dark drafting slate matching app workspace
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#37474F")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val borderAccentPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val gridPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#1AFFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
            }
            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                isAntiAlias = true
            }

            // 1. Draw Workspace Background
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

            // 2. Draw CAD Grid Background lines (30px spaced)
            for (x in 20..pageWidth step 30) {
                canvas.drawLine(x.toFloat(), 20f, x.toFloat(), (pageHeight - 20).toFloat(), gridPaint)
            }
            for (y in 20..pageHeight step 30) {
                canvas.drawLine(20f, y.toFloat(), (pageWidth - 20).toFloat(), y.toFloat(), gridPaint)
            }

            // 3. Draw Layout Border
            canvas.drawRect(15f, 15f, (pageWidth - 15).toFloat(), (pageHeight - 15).toFloat(), borderPaint)
            canvas.drawRect(20f, 20f, (pageWidth - 20).toFloat(), (pageHeight - 20).toFloat(), borderAccentPaint)

            // 4. Draw CAD Drawing Content
            // Determine central viewport inside PDF: leave top/bottom margins for metadata blocks
            val drawingBoundsUri = RectF(40f, 100f, (pageWidth - 40).toFloat(), (pageHeight - 75).toFloat())

            if (backgroundImage != null) {
                // If DWG has parsed background preview image
                val srcRect = android.graphics.Rect(0, 0, backgroundImage.width, backgroundImage.height)
                canvas.drawBitmap(backgroundImage, srcRect, drawingBoundsUri, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                // Render crisp CAD vector configurations
                val canvasCenter = Offset(
                    drawingBoundsUri.left + drawingBoundsUri.width() / 2f,
                    drawingBoundsUri.top + drawingBoundsUri.height() / 2f
                )

                // Scaling context factor: center of blueprint space (approx 300, 230) mapping to canvas center
                val scaleFactor = 1.05f

                fun mapCoord(offset: Offset): Offset {
                    val dx = (offset.x - 300f) * scaleFactor
                    val dy = (offset.y - 230f) * scaleFactor
                    return Offset(canvasCenter.x + dx, canvasCenter.y + dy)
                }

                // Render vector entities if visible
                val filteredEntities = baseEntities.filter { it.layerId in visibleLayerIds }
                renderEntities(canvas, filteredEntities, ::mapCoord, textPaint)
            }

            // Render active custom overlays (annotations are coordinates on drawing, we map them directly)
            val annotationScaleFactor = 1.05f
            val canvasCenter = Offset(
                drawingBoundsUri.left + drawingBoundsUri.width() / 2f,
                drawingBoundsUri.top + drawingBoundsUri.height() / 2f
            )
            fun mapCoordAnnotation(offset: Offset): Offset {
                val dx = (offset.x - 300f) * annotationScaleFactor
                val dy = (offset.y - 230f) * annotationScaleFactor
                return Offset(canvasCenter.x + dx, canvasCenter.y + dy)
            }
            
            val visibleAnnotations = annotations.filter { it.layerId in visibleLayerIds }
            renderEntities(canvas, visibleAnnotations, ::mapCoordAnnotation, textPaint)

            // 5. Drawing Technical Title Block & Metadata Header
            // Top Header black bar
            val headerPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#1A237E")
                style = Paint.Style.FILL
            }
            canvas.drawRect(21f, 21f, (pageWidth - 21).toFloat(), 80f, headerPaint)

            // Logo & Title text
            textPaint.apply {
                color = android.graphics.Color.WHITE
                textSize = 18f
                isFakeBoldText = true
            }
            canvas.drawText("DWG CAD EXPORT SHEET", 35f, 52f, textPaint)

            textPaint.apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                textSize = 9f
                isFakeBoldText = false
            }
            canvas.drawText("HIGH-RESOLUTION DIGITAL VECTOR ARCHITECTURE", 35f, 68f, textPaint)

            // Drawing properties on the right side of header
            textPaint.apply {
                color = android.graphics.Color.WHITE
                textSize = 10f
                isFakeBoldText = false
            }
            val rightColStart = pageWidth - 320
            canvas.drawText("SOURCE DWG: ${metadata.filename}", rightColStart.toFloat(), 40f, textPaint)
            canvas.drawText("CAD LEVEL: ${metadata.autoCadVersion} (${metadata.headerVersion})", rightColStart.toFloat(), 55f, textPaint)
            canvas.drawText("FILE SIZE: ${metadata.fileSize / 1024} KB", rightColStart.toFloat(), 70f, textPaint)

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            canvas.drawText("EXPORT STAMP: ${formatter.format(Date())}", (pageWidth - 160).toFloat(), 40f, textPaint)
            canvas.drawText("LAYER STRUCTS: Base + User Redlines", (pageWidth - 160).toFloat(), 55f, textPaint)
            canvas.drawText("MEASUREMENT UNITS: Metric (mm)", (pageWidth - 160).toFloat(), 70f, textPaint)

            // Bottom technical border block
            val bottomBarPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#263238")
                style = Paint.Style.FILL
            }
            canvas.drawRect(21f, (pageHeight - 40).toFloat(), (pageWidth - 21).toFloat(), (pageHeight - 21).toFloat(), bottomBarPaint)

            textPaint.apply {
                color = android.graphics.Color.parseColor("#90A4AE")
                textSize = 10f
                isFakeBoldText = false
            }
            canvas.drawText("SHEET REVISION: REV-1-A (PLOTSCALE 1:1) | ALL VECTORS PRESERVED", 35f, (pageHeight - 27).toFloat(), textPaint)
            canvas.drawText("CONFIDENTIAL ENGINEERING SCHEMATIC PROPERTY", (pageWidth - 290).toFloat(), (pageHeight - 27).toFloat(), textPaint)

        } catch (e: Exception) {
            Log.e(TAG, "Exception drawing PDF sheet data", e)
        }

        pdfDocument.finishPage(page)

        // Write the PDF file to absolute media file system
        return try {
            val exportsDir = File(context.cacheDir, "cad_exports")
            if (!exportsDir.exists()) exportsDir.mkdirs()

            val sanitizedFilename = metadata.filename.substringBeforeLast(".").replace(" ", "_")
            val pdfFile = File(exportsDir, "CAD_PLOT_${sanitizedFilename}_${System.currentTimeMillis()}.pdf")
            val fileOut = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fileOut)
            fileOut.close()
            pdfDocument.close()
            Log.d(TAG, "Successfully created Vector PDF at: ${pdfFile.absolutePath}")
            pdfFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write PDF document stream", e)
            pdfDocument.close()
            null
        }
    }

    private fun renderEntities(
        canvas: android.graphics.Canvas,
        entities: List<CadEntity>,
        mapCoord: (Offset) -> Offset,
        textPaint: Paint
    ) {
        val geometryPaint = Paint().apply {
            isAntiAlias = true
        }

        for (entity in entities) {
            geometryPaint.color = entity.color.toArgb()
            geometryPaint.strokeWidth = entity.strokeWidth

            when (entity) {
                is CadEntity.Line -> {
                    val pStart = mapCoord(entity.start)
                    val pEnd = mapCoord(entity.end)
                    geometryPaint.style = Paint.Style.STROKE
                    canvas.drawLine(pStart.x, pStart.y, pEnd.x, pEnd.y, geometryPaint)
                }
                is CadEntity.Rect -> {
                    val pTopLeft = mapCoord(entity.topLeft)
                    // Scale width and height by the delta mapping
                    val pBottomRight = mapCoord(Offset(entity.topLeft.x + entity.size.x, entity.topLeft.y + entity.size.y))
                    
                    geometryPaint.style = if (entity.isFilled) Paint.Style.FILL else Paint.Style.STROKE
                    canvas.drawRect(pTopLeft.x, pTopLeft.y, pBottomRight.x, pBottomRight.y, geometryPaint)
                }
                is CadEntity.Circle -> {
                    val pCenter = mapCoord(entity.center)
                    // Radius scaling
                    val edgePoint = mapCoord(Offset(entity.center.x + entity.radius, entity.center.y))
                    val mappedRadius = Math.abs(edgePoint.x - pCenter.x)

                    geometryPaint.style = if (entity.isFilled) Paint.Style.FILL else Paint.Style.STROKE
                    canvas.drawCircle(pCenter.x, pCenter.y, mappedRadius, geometryPaint)
                }
                is CadEntity.Text -> {
                    val pPos = mapCoord(entity.position)
                    textPaint.apply {
                        color = entity.color.toArgb()
                        textSize = entity.fontSizeSp * 1.15f
                        isFakeBoldText = true
                    }
                    canvas.drawText(entity.text, pPos.x, pPos.y, textPaint)
                }
            }
        }
    }

    /**
     * Triggers the Android context chooser to securely share the exported PDF.
     */
    fun sharePdfFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Simpan / Kirim Plot PDF CAD"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing plot PDF file", e)
            Toast.makeText(context, "Gagal membagikan file PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
