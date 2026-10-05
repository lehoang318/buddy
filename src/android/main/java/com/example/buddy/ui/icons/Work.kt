package com.example.buddy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _work: ImageVector? = null

val Icons.Filled.Work: ImageVector
    get() {
        if (_work != null) return _work!!
        _work = ImageVector.Builder(
            name = "Filled.Work",
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
                moveTo(4f, 21f)
                quadTo(3.18f, 21f, 2.59f, 20.41f)
                reflectiveQuadTo(2f, 19f)
                verticalLineTo(8f)
                quadTo(2f, 7.18f, 2.59f, 6.59f)
                reflectiveQuadTo(4f, 6f)
                horizontalLineTo(8f)
                verticalLineTo(4f)
                quadTo(8f, 3.17f, 8.59f, 2.59f)
                reflectiveQuadTo(10f, 2f)
                horizontalLineToRelative(4f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                reflectiveQuadTo(16f, 4f)
                verticalLineTo(6f)
                horizontalLineToRelative(4f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 7.18f, 22f, 8f)
                verticalLineTo(19f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(20f, 21f)
                horizontalLineTo(4f)
                close()
                moveTo(4f, 19f)
                horizontalLineTo(20f)
                verticalLineTo(8f)
                horizontalLineTo(4f)
                verticalLineTo(19f)
                close()
                moveTo(10f, 6f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineTo(10f)
                verticalLineTo(6f)
                close()
                moveTo(4f, 19f)
                verticalLineTo(8f)
                verticalLineTo(19f)
                close()
            }
        }.build()
        return _work!!
    }
