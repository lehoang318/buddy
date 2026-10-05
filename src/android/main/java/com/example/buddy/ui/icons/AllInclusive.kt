package com.example.buddy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _allInclusive: ImageVector? = null

val Icons.Filled.AllInclusive: ImageVector
    get() {
        if (_allInclusive != null) return _allInclusive!!
        _allInclusive = ImageVector.Builder(
            name = "Filled.AllInclusive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5.5f, 17.5f)
                quadToRelative(-2.3f, 0f, -3.9f, -1.6f)
                reflectiveQuadTo(0f, 12f)
                reflectiveQuadTo(1.6f, 8.1f)
                reflectiveQuadTo(5.5f, 6.5f)
                quadToRelative(0.93f, 0f, 1.78f, 0.32f)
                reflectiveQuadTo(8.8f, 7.75f)
                lineTo(10.5f, 9.3f)
                lineTo(9f, 10.65f)
                lineTo(7.45f, 9.25f)
                quadTo(7.05f, 8.9f, 6.55f, 8.7f)
                reflectiveQuadTo(5.5f, 8.5f)
                quadTo(4.05f, 8.5f, 3.03f, 9.52f)
                reflectiveQuadTo(2f, 12f)
                reflectiveQuadToRelative(1.03f, 2.47f)
                reflectiveQuadTo(5.5f, 15.5f)
                quadToRelative(0.55f, 0f, 1.05f, -0.2f)
                reflectiveQuadToRelative(0.9f, -0.55f)
                lineToRelative(7.75f, -7f)
                quadToRelative(0.67f, -0.6f, 1.53f, -0.93f)
                reflectiveQuadTo(18.5f, 6.5f)
                quadToRelative(2.3f, 0f, 3.9f, 1.6f)
                reflectiveQuadTo(24f, 12f)
                reflectiveQuadToRelative(-1.6f, 3.9f)
                reflectiveQuadToRelative(-3.9f, 1.6f)
                quadToRelative(-0.93f, 0f, -1.77f, -0.32f)
                reflectiveQuadTo(15.2f, 16.25f)
                lineTo(13.5f, 14.7f)
                lineTo(15f, 13.35f)
                lineToRelative(1.55f, 1.4f)
                quadToRelative(0.4f, 0.35f, 0.9f, 0.55f)
                reflectiveQuadToRelative(1.05f, 0.2f)
                quadToRelative(1.45f, 0f, 2.48f, -1.03f)
                reflectiveQuadTo(22f, 12f)
                reflectiveQuadTo(20.98f, 9.52f)
                reflectiveQuadTo(18.5f, 8.5f)
                quadToRelative(-0.55f, 0f, -1.05f, 0.2f)
                reflectiveQuadToRelative(-0.9f, 0.55f)
                lineToRelative(-7.75f, 7f)
                quadToRelative(-0.68f, 0.6f, -1.53f, 0.93f)
                reflectiveQuadTo(5.5f, 17.5f)
                close()
            }
        }.build()
        return _allInclusive!!
    }
