package com.example.buddy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _cameraAlt: ImageVector? = null

val Icons.Filled.CameraAlt: ImageVector
    get() {
        if (_cameraAlt != null) return _cameraAlt!!
        _cameraAlt = ImageVector.Builder(
            name = "Filled.CameraAlt",
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
                moveTo(12f, 17.5f)
                quadToRelative(1.88f, 0f, 3.19f, -1.31f)
                reflectiveQuadTo(16.5f, 13f)
                reflectiveQuadTo(15.19f, 9.81f)
                reflectiveQuadTo(12f, 8.5f)
                reflectiveQuadTo(8.81f, 9.81f)
                reflectiveQuadTo(7.5f, 13f)
                reflectiveQuadToRelative(1.31f, 3.19f)
                reflectiveQuadTo(12f, 17.5f)
                close()
                moveToRelative(0f, -2f)
                quadToRelative(-1.05f, 0f, -1.77f, -0.72f)
                reflectiveQuadTo(9.5f, 13f)
                reflectiveQuadToRelative(0.73f, -1.78f)
                reflectiveQuadTo(12f, 10.5f)
                reflectiveQuadToRelative(1.78f, 0.72f)
                reflectiveQuadTo(14.5f, 13f)
                reflectiveQuadToRelative(-0.72f, 1.78f)
                reflectiveQuadTo(12f, 15.5f)
                close()
                moveTo(4f, 21f)
                quadTo(3.18f, 21f, 2.59f, 20.41f)
                reflectiveQuadTo(2f, 19f)
                verticalLineTo(7f)
                quadTo(2f, 6.18f, 2.59f, 5.59f)
                reflectiveQuadTo(4f, 5f)
                horizontalLineTo(7.15f)
                lineTo(9f, 3f)
                horizontalLineToRelative(6f)
                lineToRelative(1.85f, 2f)
                horizontalLineTo(20f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 6.18f, 22f, 7f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 21f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 19f)
                horizontalLineTo(20f)
                verticalLineTo(7f)
                horizontalLineTo(15.95f)
                lineTo(14.13f, 5f)
                horizontalLineTo(9.88f)
                lineTo(8.05f, 7f)
                horizontalLineTo(4f)
                verticalLineTo(19f)
                close()
                moveToRelative(8f, -6f)
                close()
            }
        }.build()
        return _cameraAlt!!
    }
