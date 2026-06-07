package com.example.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

sealed class CadEntity {
    abstract val color: Color
    abstract val strokeWidth: Float
    abstract val layerId: Int // Group by CAD layers

    data class Line(
        val start: Offset,
        val end: Offset,
        override val color: Color,
        override val strokeWidth: Float,
        override val layerId: Int
    ) : CadEntity()

    data class Rect(
        val topLeft: Offset,
        val size: Offset, // width, height as offset
        val isFilled: Boolean,
        override val color: Color,
        override val strokeWidth: Float,
        override val layerId: Int
    ) : CadEntity()

    data class Circle(
        val center: Offset,
        val radius: Float,
        val isFilled: Boolean,
        override val color: Color,
        override val strokeWidth: Float,
        override val layerId: Int
    ) : CadEntity()

    data class Text(
        val text: String,
        val position: Offset,
        val fontSizeSp: Float,
        override val color: Color,
        override val strokeWidth: Float = 1f,
        override val layerId: Int
    ) : CadEntity()
}

/**
 * Standard AutoCAD layers that draftspersons use to toggle visibility of system schematics.
 */
data class CadLayer(
    val id: Int,
    val name: String,
    val isVisible: Boolean,
    val color: Color
)

typealias Blueprint = List<CadEntity>

object BlueprintData {
    // Layer IDs
    const val LAYER_BASE_GEOMETRY = 0
    const val LAYER_DIMENSIONS = 1
    const val LAYER_FURNITURE_DETAILS = 2
    const val LAYER_ANNOTATIONS = 3

    val DefaultLayers = listOf(
        CadLayer(LAYER_BASE_GEOMETRY, "0 - Base Geometry", true, Color(0xFFE0E0E0)),
        CadLayer(LAYER_DIMENSIONS, "Dimensions (Linear)", true, Color(0xFFFF9100)),
        CadLayer(LAYER_FURNITURE_DETAILS, "Interior & Fixtures", true, Color(0xFF00E676)),
        CadLayer(LAYER_ANNOTATIONS, "User Notes & Redlines", true, Color(0xFFFF1744))
    )

    /**
     * Symmetrical Luxury Residence Blueprint
     */
    val FloorPlanBlueprint: Blueprint = listOf(
        // Outer Boundaries (m units scaled to dp coordinates, say, center is around (300, 300))
        CadEntity.Rect(Offset(50f, 50f), Offset(500f, 400f), false, Color(0xFFECEFF1), 4f, LAYER_BASE_GEOMETRY),
        
        // Inner Walls / Dividers
        CadEntity.Line(Offset(50f, 250f), Offset(250f, 250f), Color(0xFFECEFF1), 3f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(250f, 50f), Offset(250f, 450f), Color(0xFFECEFF1), 3f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(250f, 200f), Offset(550f, 200f), Color(0xFFECEFF1), 3f, LAYER_BASE_GEOMETRY),

        // Windows (cyan double-lines)
        CadEntity.Line(Offset(100f, 48f), Offset(200f, 48f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(100f, 52f), Offset(200f, 52f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(350f, 48f), Offset(450f, 48f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(350f, 52f), Offset(450f, 52f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),

        // Doors (swings)
        CadEntity.Line(Offset(250f, 250f), Offset(200f, 290f), Color(0xFF8D6E63), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Circle(Offset(250f, 250f), 50f, false, Color(0xFF8D6E63), 1f, LAYER_BASE_GEOMETRY), // Arc simulation

        CadEntity.Line(Offset(250f, 100f), Offset(210f, 140f), Color(0xFF8D6E63), 2f, LAYER_BASE_GEOMETRY),
        // Dining Table and Chairs (Furniture Detail Layer)
        CadEntity.Rect(Offset(330f, 280f), Offset(140f, 80f), false, Color(0xFF29B6F6), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(400f, 320f), 15f, false, Color(0xFF00E676), 1.5f, LAYER_FURNITURE_DETAILS), // Platter
        CadEntity.Circle(Offset(310f, 320f), 12f, false, Color(0xFF29B6F6), 2f, LAYER_FURNITURE_DETAILS), // Chair Left
        CadEntity.Circle(Offset(490f, 320f), 12f, false, Color(0xFF29B6F6), 2f, LAYER_FURNITURE_DETAILS), // Chair Right
        CadEntity.Circle(Offset(360f, 255f), 12f, false, Color(0xFF29B6F6), 2f, LAYER_FURNITURE_DETAILS), // Chair Top 1
        CadEntity.Circle(Offset(440f, 255f), 12f, false, Color(0xFF29B6F6), 2f, LAYER_FURNITURE_DETAILS), // Chair Top 2
        CadEntity.Circle(Offset(360f, 385f), 12f, false, Color(0xFF29B6F6), 2f, LAYER_FURNITURE_DETAILS), // Chair Bottom 1
        CadEntity.Circle(Offset(440f, 385f), 12f, false, Color(0xFF29B6F6), 2f, LAYER_FURNITURE_DETAILS), // Chair Bottom 2

        // Room Labels
        CadEntity.Text("PRIMARY SUITE", Offset(80f, 150f), 14f, Color(0xFF90A4AE), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("GUEST BATH", Offset(90f, 350f), 13f, Color(0xFF90A4AE), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("CONFERENCE HALL", Offset(310f, 120f), 15f, Color(0xFF90A4AE), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("DINING LOUNGE", Offset(350f, 420f), 14f, Color(0xFF90A4AE), 1f, LAYER_BASE_GEOMETRY),

        // Dimensions (Linear markings)
        CadEntity.Line(Offset(50f, 20f), Offset(550f, 20f), Color(0xFFFFB300), 1.5f, LAYER_DIMENSIONS),
        CadEntity.Line(Offset(50f, 12f), Offset(50f, 28f), Color(0xFFFFB300), 2f, LAYER_DIMENSIONS), // tick left
        CadEntity.Line(Offset(550f, 12f), Offset(550f, 28f), Color(0xFFFFB300), 2f, LAYER_DIMENSIONS), // tick right
        CadEntity.Text("X-LENGTH: 500.00 mm", Offset(220f, 15f), 12f, Color(0xFFFFB300), 1f, LAYER_DIMENSIONS),

        CadEntity.Line(Offset(20f, 50f), Offset(20f, 450f), Color(0xFFFFB300), 1.5f, LAYER_DIMENSIONS),
        CadEntity.Line(Offset(12f, 50f), Offset(28f, 50f), Color(0xFFFFB300), 2f, LAYER_DIMENSIONS), // tick top
        CadEntity.Line(Offset(12f, 450f), Offset(28f, 450f), Color(0xFFFFB300), 2f, LAYER_DIMENSIONS), // tick bottom
        CadEntity.Text("Y-SPAN: 400.00 mm", Offset(8f, 235f), 11f, Color(0xFFFFB300), 1f, LAYER_DIMENSIONS)
    )

    /**
     * Structural Framing Blueprint
     */
    val StructuralBlueprint: Blueprint = listOf(
        // Columns / Gridlines
        CadEntity.Line(Offset(100f, 50f), Offset(100f, 450f), Color(0x77E0E0E0), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(250f, 50f), Offset(250f, 450f), Color(0x77E0E0E0), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(400f, 50f), Offset(400f, 450f), Color(0x77E0E0E0), 1f, LAYER_BASE_GEOMETRY),
        
        // Grid Labels
        CadEntity.Circle(Offset(100f, 40f), 15f, false, Color(0xFF4FC3F7), 1.5f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("A1", Offset(93f, 45f), 12f, Color(0xFF4FC3F7), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Circle(Offset(250f, 40f), 15f, false, Color(0xFF4FC3F7), 1.5f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("A2", Offset(243f, 45f), 12f, Color(0xFF4FC3F7), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Circle(Offset(400f, 40f), 15f, false, Color(0xFF4FC3F7), 1.5f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("A3", Offset(393f, 45f), 12f, Color(0xFF4FC3F7), 1f, LAYER_BASE_GEOMETRY),

        // Heavy steel beams (thick red/orange lines)
        CadEntity.Line(Offset(100f, 100f), Offset(400f, 100f), Color(0xFFFF1744), 6f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(100f, 250f), Offset(400f, 250f), Color(0xFFFF1744), 6f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(100f, 400f), Offset(400f, 400f), Color(0xFFFF1744), 6f, LAYER_BASE_GEOMETRY),
        
        // Vertical girders
        CadEntity.Line(Offset(100f, 100f), Offset(100f, 400f), Color(0xFF00E5FF), 5f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(250f, 100f), Offset(250f, 400f), Color(0xFF00E5FF), 5f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(400f, 100f), Offset(400f, 400f), Color(0xFF00E5FF), 5f, LAYER_BASE_GEOMETRY),

        // Concrete footings (cross-hatched squares)
        CadEntity.Rect(Offset(80f, 80f), Offset(40f, 40f), false, Color(0xFF78909C), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Rect(Offset(230f, 80f), Offset(40f, 40f), false, Color(0xFF78909C), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Rect(Offset(380f, 80f), Offset(40f, 40f), false, Color(0xFF78909C), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Rect(Offset(80f, 230f), Offset(40f, 40f), false, Color(0xFF78909C), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Rect(Offset(230f, 230f), Offset(40f, 40f), false, Color(0xFF78909C), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Rect(Offset(380f, 230f), Offset(40f, 40f), false, Color(0xFF78909C), 2f, LAYER_FURNITURE_DETAILS),

        // Bolt specs and dimensions
        CadEntity.Text("STEEL BEAM W12x50", Offset(130f, 85f), 12f, Color(0xFFECEFF1), 1f, LAYER_DIMENSIONS),
        CadEntity.Text("FOOTING BORE: 400mm", Offset(110f, 280f), 11f, Color(0xFF81C784), 1f, LAYER_FURNITURE_DETAILS),
        
        // Dimension vectors
        CadEntity.Line(Offset(100f, 440f), Offset(250f, 440f), Color(0xFFFF9100), 1.5f, LAYER_DIMENSIONS),
        CadEntity.Text("6000.00 mm", Offset(150f, 455f), 12f, Color(0xFFFF9100), 1f, LAYER_DIMENSIONS)
    )

    /**
     * Mechanical Flange Assembly
     */
    val MechanicalBlueprint: Blueprint = listOf(
        // Concentric Circles
        CadEntity.Circle(Offset(300f, 230f), 180f, false, Color(0xFF00E5FF), 3f, LAYER_BASE_GEOMETRY), // Outer flange
        CadEntity.Circle(Offset(300f, 230f), 140f, false, Color(0xFFB0BEC5), 1.5f, LAYER_BASE_GEOMETRY), // Pitch circle for bolts (dashed look)
        CadEntity.Circle(Offset(300f, 230f), 80f, false, Color(0xFF00E5FF), 3f, LAYER_BASE_GEOMETRY), // Bore entry
        CadEntity.Circle(Offset(300f, 230f), 50f, false, Color(0xFFFFFFFF), 2f, LAYER_BASE_GEOMETRY), // Axis shaft

        // Center lines
        CadEntity.Line(Offset(100f, 230f), Offset(500f, 230f), Color(0xFFD32F2F), 1f, LAYER_DIMENSIONS),
        CadEntity.Line(Offset(300f, 30f), Offset(300f, 430f), Color(0xFFD32F2F), 1f, LAYER_DIMENSIONS),

        // Bolts (evenly distributed circles on pitch circle r=140)
        // Cos 0, 45, 90 etc.
        CadEntity.Circle(Offset(440f, 230f), 15f, false, Color(0xFF00E676), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(160f, 230f), 15f, false, Color(0xFF00E676), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(300f, 370f), 15f, false, Color(0xFF00E676), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(300f, 90f), 15f, false, Color(0xFF00E676), 2f, LAYER_FURNITURE_DETAILS),

        // Inner detailed hatching/screws
        CadEntity.Circle(Offset(440f, 230f), 6f, true, Color(0xFFECEFF1), 1f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(160f, 230f), 6f, true, Color(0xFFECEFF1), 1f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(300f, 370f), 6f, true, Color(0xFFECEFF1), 1f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(300f, 90f), 6f, true, Color(0xFFECEFF1), 1f, LAYER_FURNITURE_DETAILS),

        // Dimensional annotation and text labels
        CadEntity.Line(Offset(300f, 230f), Offset(410f, 120f), Color(0xFFFF9100), 1.5f, LAYER_DIMENSIONS),
        CadEntity.Text("4x BOLT HOLES M12", Offset(350f, 105f), 13f, Color(0xFFFF9100), 1f, LAYER_DIMENSIONS),
        CadEntity.Text("FLANGE DIA: 360mm", Offset(140f, 60f), 14f, Color(0xFF00E5FF), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("AXIS BORE DIA: 100mm", Offset(140f, 410f), 13f, Color(0xFFECEFF1), 1f, LAYER_BASE_GEOMETRY)
    )

    /**
     * Electrical Grid Node Diagram
     */
    val ElectricalBlueprint: Blueprint = listOf(
        // Core Battery/Source symbol
        CadEntity.Line(Offset(80f, 230f), Offset(180f, 230f), Color(0xFFFFFFFF), 2f, LAYER_BASE_GEOMETRY),
        // Alternating power wires
        CadEntity.Line(Offset(180f, 230f), Offset(180f, 150f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(180f, 150f), Offset(280f, 150f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),

        // Transformer coils (repeating circles)
        CadEntity.Circle(Offset(290f, 150f), 12f, false, Color(0xFFFFE082), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(310f, 150f), 12f, false, Color(0xFFFFE082), 2f, LAYER_FURNITURE_DETAILS),
        CadEntity.Circle(Offset(330f, 150f), 12f, false, Color(0xFFFFE082), 2f, LAYER_FURNITURE_DETAILS),
        
        // Ground Nodes (parallel decreased lines)
        CadEntity.Line(Offset(310f, 250f), Offset(310f, 290f), Color(0xFFECEFF1), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(290f, 290f), Offset(330f, 290f), Color(0xFFECEFF1), 3f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(297f, 297f), Offset(323f, 297f), Color(0xFFECEFF1), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(304f, 304f), Offset(316f, 304f), Color(0xFFECEFF1), 1.5f, LAYER_BASE_GEOMETRY),

        // Relays/Capacitor block
        CadEntity.Rect(Offset(400f, 130f), Offset(40f, 40f), false, Color(0xFF81C784), 2.5f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(342f, 150f), Offset(400f, 150f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),
        CadEntity.Line(Offset(440f, 150f), Offset(500f, 150f), Color(0xFF00E5FF), 2f, LAYER_BASE_GEOMETRY),

        // Schematic Texts
        CadEntity.Text("240V MAIN SOURCE", Offset(70f, 255f), 12f, Color(0xFFECEFF1), 1f, LAYER_BASE_GEOMETRY),
        CadEntity.Text("XFMR COIL 10:1", Offset(270f, 115f), 12f, Color(0xFFFFE082), 1f, LAYER_FURNITURE_DETAILS),
        CadEntity.Text("K1 OVERCURRENT RELAY", Offset(365f, 205f), 11f, Color(0xFF81C784), 1f, LAYER_DIMENSIONS),
        CadEntity.Text("CHASSIS GND", Offset(270f, 325f), 12f, Color(0xFFECEFF1), 1f, LAYER_BASE_GEOMETRY)
    )
}
