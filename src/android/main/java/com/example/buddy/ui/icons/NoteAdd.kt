package com.example.buddy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _noteAdd: ImageVector? = null

val Icons.Filled.NoteAdd: ImageVector
    get() {
        if (_noteAdd != null) return _noteAdd!!
        _noteAdd = ImageVector.Builder(
            name = "Filled.NoteAdd",
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
                moveTo(11f, 18f)
                horizontalLineToRelative(2f)
                verticalLineTo(15f)
                horizontalLineToRelative(3f)
                verticalLineTo(13f)
                horizontalLineTo(13f)
                verticalLineTo(10f)
                horizontalLineTo(11f)
                verticalLineToRelative(3f)
                horizontalLineTo(8f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(3f)
                close()
                moveTo(6f, 22f)
                quadTo(5.18f, 22f, 4.59f, 21.41f)
                reflectiveQuadTo(4f, 20f)
                verticalLineTo(4f)
                quadTo(4f, 3.17f, 4.59f, 2.59f)
                reflectiveQuadTo(6f, 2f)
                horizontalLineToRelative(8f)
                lineToRelative(6f, 6f)
                verticalLineTo(20f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(18f, 22f)
                horizontalLineTo(6f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(4f)
                horizontalLineTo(6f)
                verticalLineTo(20f)
                horizontalLineTo(18f)
                verticalLineTo(9f)
                horizontalLineTo(13f)
                close()
                moveTo(6f, 4f)
                verticalLineTo(9f)
                verticalLineTo(4f)
                verticalLineTo(9f)
                verticalLineTo(20f)
                verticalLineTo(4f)
                close()
            }
        }.build()
        return _noteAdd!!
    }
