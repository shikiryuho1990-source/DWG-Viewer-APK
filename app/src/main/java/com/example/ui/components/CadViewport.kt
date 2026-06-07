package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.models.CadEntity
import com.example.models.CadLayer
import com.example.viewmodels.CadTool
import com.example.viewmodels.CadViewModel

@Composable
fun CadViewport(
    viewModel: CadViewModel,
    modifier: Modifier = Modifier
) {
    val scale = viewModel.viewportScale.collectAsState().value
    val offset = viewModel.viewportOffset.collectAsState().value
    val activeTool = viewModel.activeTool.collectAsState().value
    val activeColor = viewModel.selectedColor.collectAsState().value
    val activeStroke = viewModel.selectedStrokeWidth.collectAsState().value
    val activeText = viewModel.textAnnotationValue.collectAsState().value

    val baseEntities = viewModel.activeDrawingEntities.collectAsState().value
    val annotations = viewModel.userAnnotations.collectAsState().value
    val layers = viewModel.layers.collectAsState().value
    val bgBitmap = viewModel.dwgPreviewBitmap.collectAsState().value

    val visibleLayerIds = layers.filter { it.isVisible }.map { it.id }.toSet()

    // Interactive helper mappings
    fun screenToDrawing(screenOffset: Offset): Offset {
        return Offset(
            (screenOffset.x - offset.x) / scale,
            (screenOffset.y - offset.y) / scale
        )
    }

    fun drawingToScreen(drawingOffset: Offset): Offset {
        return Offset(
            (drawingOffset.x * scale) + offset.x,
            (drawingOffset.y * scale) + offset.y
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11)) // Immersive dark workbench color
            // Gesture detector for PAN_ZOOM navigation or drawing redlines
            .pointerInput(activeTool, scale, offset) {
                if (activeTool == CadTool.PAN_ZOOM) {
                    // Navigate standard pan and pinch zooms
                    detectTransformGestures { _, pan, zoom, _ ->
                        viewModel.updateViewport(zoom, pan)
                    }
                } else {
                    // Handle manual vector drawing gestures
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val changes = event.changes
                            
                            // Multi-touch fallback -> zoom and pan always takes precedence
                            if (changes.size > 1) {
                                // Delegate to viewport navigation dynamically
                                var zoomAccumulator = 1f
                                var panAccumulator = Offset.Zero
                                // Calculate simple multi-touch delta
                                // Since direct calculations are locked in event sequence, use basic centroid changes
                                // For robustness, we reset drawing buffers to prevent messy circles
                                viewModel.drawingStartPoint.value = null
                                viewModel.drawingCurrentPoint.value = null
                                break
                            }

                            val change = changes.first()
                            val position = change.position

                            if (change.pressed) {
                                if (viewModel.drawingStartPoint.value == null) {
                                    // Start fresh drawing redline
                                    viewModel.drawingStartPoint.value = screenToDrawing(position)
                                }
                                viewModel.drawingCurrentPoint.value = screenToDrawing(position)
                                change.consume()
                            } else if (change.previousPressed && !change.pressed) {
                                // Pointer lifted! Commit completed geometry shape to annotations layer
                                val start = viewModel.drawingStartPoint.value
                                val end = viewModel.drawingCurrentPoint.value
                                if (start != null && end != null && start != end) {
                                    val newEntity = when (activeTool) {
                                        CadTool.DRAW_LINE -> CadEntity.Line(
                                            start = start,
                                            end = end,
                                            color = activeColor,
                                            strokeWidth = activeStroke,
                                            layerId = 3 // USER ANNOTATIONS LAYER
                                        )
                                        CadTool.DRAW_RECT -> {
                                            val width = end.x - start.x
                                            val height = end.y - start.y
                                            CadEntity.Rect(
                                                topLeft = Offset(
                                                    minOf(start.x, end.x),
                                                    minOf(start.y, end.y)
                                                ),
                                                size = Offset(Math.abs(width), Math.abs(height)),
                                                isFilled = false,
                                                color = activeColor,
                                                strokeWidth = activeStroke,
                                                layerId = 3
                                            )
                                        }
                                        CadTool.DRAW_CIRCLE -> {
                                            val dx = end.x - start.x
                                            val dy = end.y - start.y
                                            val radius = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                            CadEntity.Circle(
                                                center = start,
                                                radius = radius,
                                                isFilled = false,
                                                color = activeColor,
                                                strokeWidth = activeStroke,
                                                layerId = 3
                                            )
                                        }
                                        CadTool.DRAW_TEXT -> CadEntity.Text(
                                            text = activeText,
                                            position = start,
                                            fontSizeSp = 14f,
                                            color = activeColor,
                                            layerId = 3
                                        )
                                        else -> null
                                    }
                                    if (newEntity != null) {
                                        viewModel.commitAnnotation(newEntity)
                                    }
                                }
                                // Reset touch trackers
                                viewModel.drawingStartPoint.value = null
                                viewModel.drawingCurrentPoint.value = null
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            val drawScope = this

            // 1. Render Fine Blueprint Grid lines
            val spacing = 30f * scale
            val startX = offset.x % spacing
            val startY = offset.y % spacing

            for (x in generateSequence(startX) { it + spacing }.takeWhile { it < widthPx }) {
                drawScope.drawLine(
                    color = Color(0x13FFFFFF),
                    start = Offset(x, 0f),
                    end = Offset(x, heightPx.toFloat()),
                    strokeWidth = 1f
                )
            }
            for (y in generateSequence(startY) { it + spacing }.takeWhile { it < heightPx }) {
                drawScope.drawLine(
                    color = Color(0x13FFFFFF),
                    start = Offset(0f, y),
                    end = Offset(widthPx.toFloat(), y),
                    strokeWidth = 1f
                )
            }

            // 2. Render Base Backdrop Image (Parsed Bitmap from real DWG)
            if (bgBitmap != null) {
                val imgBitmap = bgBitmap.asImageBitmap()
                val centerOffset = Offset(
                    (widthPx / 2f) + offset.x,
                    (heightPx / 2f) + offset.y
                )
                
                val targetSizeWidth = imgBitmap.width * scale
                val targetSizeHeight = imgBitmap.height * scale

                drawScope.drawImage(
                    image = imgBitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (centerOffset.x - targetSizeWidth / 2f).toInt(),
                        (centerOffset.y - targetSizeHeight / 2f).toInt()
                    ),
                    dstSize = androidx.compose.ui.unit.IntSize(
                        targetSizeWidth.toInt(),
                        targetSizeHeight.toInt()
                    )
                )
            } else {
                // Render crisp Vector blueprint shapes in real time
                val canvasCenter = Offset(widthPx / 2f, heightPx / 2f)

                fun projectDrawing(pt: Offset): Offset {
                    // Default mockup centers are preconfigured around (300, 230)
                    val dx = (pt.x - 300f) * scale
                    val dy = (pt.y - 230f) * scale
                    return Offset(canvasCenter.x + dx + offset.x, canvasCenter.y + dy + offset.y)
                }

                // Render Base Entities grouped by active/visible layers
                for (entity in baseEntities) {
                    if (entity.layerId !in visibleLayerIds) continue

                    when (entity) {
                        is CadEntity.Line -> {
                            drawScope.drawLine(
                                color = entity.color,
                                start = projectDrawing(entity.start),
                                end = projectDrawing(entity.end),
                                strokeWidth = entity.strokeWidth * scale
                            )
                        }
                        is CadEntity.Rect -> {
                            val projTopLeft = projectDrawing(entity.topLeft)
                            val projBottomRight = projectDrawing(Offset(entity.topLeft.x + entity.size.x, entity.topLeft.y + entity.size.y))
                            
                            val width = Math.abs(projBottomRight.x - projTopLeft.x)
                            val height = Math.abs(projBottomRight.y - projTopLeft.y)

                            drawScope.drawRect(
                                color = entity.color,
                                topLeft = projTopLeft,
                                size = Size(width, height),
                                style = if (entity.isFilled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(entity.strokeWidth * scale)
                            )
                        }
                        is CadEntity.Circle -> {
                            val projCenter = projectDrawing(entity.center)
                            val projEdge = projectDrawing(Offset(entity.center.x + entity.radius, entity.center.y))
                            val projRadius = Math.abs(projEdge.x - projCenter.x)

                            drawScope.drawCircle(
                                color = entity.color,
                                center = projCenter,
                                radius = projRadius,
                                style = if (entity.isFilled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(entity.strokeWidth * scale)
                            )
                        }
                        is CadEntity.Text -> {
                            val projPos = projectDrawing(entity.position)
                            drawScope.drawIntoCanvas { canvas ->
                                val paint = Paint().apply {
                                    color = entity.color.toArgb()
                                    textSize = entity.fontSizeSp * scale * 1.1f
                                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                                }
                                canvas.nativeCanvas.drawText(entity.text, projPos.x, projPos.y, paint)
                            }
                        }
                    }
                }
            }

            // 3. Render Committed User Redlines / Annotations
            for (anno in annotations) {
                if (anno.layerId !in visibleLayerIds) continue

                val canvasCenter = Offset(widthPx / 2f, heightPx / 2f)
                fun projectDrawing(pt: Offset): Offset {
                    val dx = (pt.x - 300f) * scale
                    val dy = (pt.y - 230f) * scale
                    return Offset(canvasCenter.x + dx + offset.x, canvasCenter.y + dy + offset.y)
                }

                when (anno) {
                    is CadEntity.Line -> {
                        drawScope.drawLine(
                            color = anno.color,
                            start = projectDrawing(anno.start),
                            end = projectDrawing(anno.end),
                            strokeWidth = anno.strokeWidth * scale
                        )
                    }
                    is CadEntity.Rect -> {
                        val projTopLeft = projectDrawing(anno.topLeft)
                        val projBottomRight = projectDrawing(Offset(anno.topLeft.x + anno.size.x, anno.topLeft.y + anno.size.y))
                        drawScope.drawRect(
                            color = anno.color,
                            topLeft = projTopLeft,
                            size = Size(Math.abs(projBottomRight.x - projTopLeft.x), Math.abs(projBottomRight.y - projTopLeft.y)),
                            style = Stroke(anno.strokeWidth * scale)
                        )
                    }
                    is CadEntity.Circle -> {
                        val projCenter = projectDrawing(anno.center)
                        val projEdge = projectDrawing(Offset(anno.center.x + anno.radius, anno.center.y))
                        drawScope.drawCircle(
                            color = anno.color,
                            center = projCenter,
                            radius = Math.abs(projEdge.x - projCenter.x),
                            style = Stroke(anno.strokeWidth * scale)
                        )
                    }
                    is CadEntity.Text -> {
                        val projPos = projectDrawing(anno.position)
                        drawScope.drawIntoCanvas { canvas ->
                            val paint = Paint().apply {
                                color = anno.color.toArgb()
                                textSize = anno.fontSizeSp * scale * 1.2f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            }
                            canvas.nativeCanvas.drawText(anno.text, projPos.x, projPos.y, paint)
                        }
                    }
                }
            }

            // 4. Render Active Floating/Ghost Drawing Path (Realtime User Drag)
            val ghostStart = viewModel.drawingStartPoint.value
            val ghostCurrent = viewModel.drawingCurrentPoint.value
            if (ghostStart != null && ghostCurrent != null) {
                val canvasCenter = Offset(widthPx / 2f, heightPx / 2f)
                fun projectDrawing(pt: Offset): Offset {
                    val dx = (pt.x - 300f) * scale
                    val dy = (pt.y - 230f) * scale
                    return Offset(canvasCenter.x + dx + offset.x, canvasCenter.y + dy + offset.y)
                }

                val pStart = projectDrawing(ghostStart)
                val pCurrent = projectDrawing(ghostCurrent)

                when (activeTool) {
                    CadTool.DRAW_LINE -> {
                        drawScope.drawLine(
                            color = activeColor.copy(alpha = 0.65f),
                            start = pStart,
                            end = pCurrent,
                            strokeWidth = activeStroke * scale
                        )
                    }
                    CadTool.DRAW_RECT -> {
                        val widthVal = pCurrent.x - pStart.x
                        val heightVal = pCurrent.y - pStart.y
                        drawScope.drawRect(
                            color = activeColor.copy(alpha = 0.6f),
                            topLeft = Offset(minOf(pStart.x, pCurrent.x), minOf(pStart.y, pCurrent.y)),
                            size = Size(Math.abs(widthVal), Math.abs(heightVal)),
                            style = Stroke(activeStroke * scale)
                        )
                    }
                    CadTool.DRAW_CIRCLE -> {
                        val dx = pCurrent.x - pStart.x
                        val dy = pCurrent.y - pStart.y
                        val rad = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        drawScope.drawCircle(
                            color = activeColor.copy(alpha = 0.6f),
                            center = pStart,
                            radius = rad,
                            style = Stroke(activeStroke * scale)
                        )
                    }
                    CadTool.DRAW_TEXT -> {
                        drawScope.drawIntoCanvas { canvas ->
                            val paint = Paint().apply {
                                color = activeColor.copy(alpha = 0.7f).toArgb()
                                textSize = 14f * scale * 1.2f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            }
                            canvas.nativeCanvas.drawText("$activeText <TAP>", pStart.x, pStart.y, paint)
                        }
                    }
                    else -> {}
                }

                // Render glowing Crosshair circle guides on touch anchor
                drawScope.drawCircle(
                    color = activeColor,
                    center = pCurrent,
                    radius = 8f,
                    style = Stroke(1.5f)
                )
                drawScope.drawLine(
                    color = activeColor.copy(alpha = 0.5f),
                    start = Offset(pCurrent.x - 25f, pCurrent.y),
                    end = Offset(pCurrent.x + 25f, pCurrent.y),
                    strokeWidth = 1f
                )
                drawScope.drawLine(
                    color = activeColor.copy(alpha = 0.5f),
                    start = Offset(pCurrent.x, pCurrent.y - 25f),
                    end = Offset(pCurrent.x, pCurrent.y + 25f),
                    strokeWidth = 1f
                )
            }
        }
    }
}
