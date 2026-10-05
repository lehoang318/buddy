package com.example.buddy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _smartToy: ImageVector? = null

val Icons.Filled.SmartToy: ImageVector
    get() {
        if (_smartToy != null) return _smartToy!!
        _smartToy = ImageVector.Builder(
            name = "Filled.SmartToy",
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
                moveTo(4f, 15f)
                quadTo(2.75f, 15f, 1.88f, 14.13f)
                reflectiveQuadTo(1f, 12f)
                reflectiveQuadTo(1.88f, 9.88f)
                reflectiveQuadTo(4f, 9f)
                verticalLineTo(7f)
                quadTo(4f, 6.18f, 4.59f, 5.59f)
                reflectiveQuadTo(6f, 5f)
                horizontalLineTo(9f)
                quadTo(9f, 3.75f, 9.88f, 2.88f)
                reflectiveQuadTo(12f, 2f)
                reflectiveQuadToRelative(2.13f, 0.88f)
                reflectiveQuadTo(15f, 5f)
                horizontalLineToRelative(3f)
                quadToRelative(0.82f, 0f, 1.41f, 0.59f)
                quadTo(20f, 6.18f, 20f, 7f)
                verticalLineTo(9f)
                quadToRelative(1.25f, 0f, 2.13f, 0.88f)
                reflectiveQuadTo(23f, 12f)
                reflectiveQuadToRelative(-0.88f, 2.13f)
                reflectiveQuadTo(20f, 15f)
                verticalLineToRelative(4f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(18f, 21f)
                horizontalLineTo(6f)
                quadTo(5.18f, 21f, 4.59f, 20.41f)
                reflectiveQuadTo(4f, 19f)
                verticalLineTo(15f)
                close()
                moveToRelative(6.06f, -2.44f)
                quadTo(10.5f, 12.13f, 10.5f, 11.5f)
                reflectiveQuadTo(10.06f, 10.44f)
                reflectiveQuadTo(9f, 10f)
                reflectiveQuadTo(7.94f, 10.44f)
                reflectiveQuadTo(7.5f, 11.5f)
                reflectiveQuadToRelative(0.44f, 1.06f)
                reflectiveQuadTo(9f, 13f)
                reflectiveQuadToRelative(1.06f, -0.44f)
                close()
                moveToRelative(6f, 0f)
                quadTo(16.5f, 12.13f, 16.5f, 11.5f)
                reflectiveQuadTo(16.06f, 10.44f)
                reflectiveQuadTo(15f, 10f)
                reflectiveQuadToRelative(-1.06f, 0.44f)
                reflectiveQuadTo(13.5f, 11.5f)
                reflectiveQuadToRelative(0.44f, 1.06f)
                reflectiveQuadTo(15f, 13f)
                reflectiveQuadToRelative(1.06f, -0.44f)
                close()
                moveTo(8f, 17f)
                horizontalLineToRelative(8f)
                verticalLineTo(15f)
                horizontalLineTo(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(6f, 19f)
                horizontalLineTo(18f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveToRelative(6f, -6f)
                close()
            }
        }.build()
        return _smartToy!!
    }
