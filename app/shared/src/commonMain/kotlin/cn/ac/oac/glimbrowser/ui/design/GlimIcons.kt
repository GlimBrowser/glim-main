package cn.ac.oac.glimbrowser.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 全站线性图标集（纯 Canvas 绘制，1.8dp 描边，圆角端点；无 emoji/字符图标）。 */
enum class GlimIconName {
    HOME, TABS, MENU, SEARCH, BACK, FORWARD, RELOAD,
    SHIELD, SPARKLE, STAR, STAR_FILLED, CLOSE, PLUS,
    BOOKMARK, HISTORY, DOWNLOAD, PLUGIN, SETTINGS, INFO, FOLDER,
    CHEVRON_RIGHT, ARROW_RIGHT, ARROW_UP, LOCK, TRASH, PIN, GLOBE, CLOCK,
    INCOGNITO, DESKTOP, SHIELD_CHECK,
}

@Composable
fun GlimIcon(
    name: GlimIconName,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 1.8.dp,
    filled: Boolean = false,
) {
    val tint = if (color == Color.Unspecified) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else color
    Canvas(modifier = modifier.size(size)) {
        drawGlimIcon(name, tint, strokeWidth.toPx(), filled)
    }
}

private fun DrawScope.drawGlimIcon(name: GlimIconName, color: Color, sw: Float, filled: Boolean) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin = Stroke(width = sw * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun path(block: Path.() -> Unit): Path = Path().apply(block)

    when (name) {
        GlimIconName.HOME -> drawPath(
            path {
                moveTo(w * 0.14f, h * 0.46f); lineTo(w * 0.5f, h * 0.14f); lineTo(w * 0.86f, h * 0.46f)
                lineTo(w * 0.86f, h * 0.86f); lineTo(w * 0.60f, h * 0.86f); lineTo(w * 0.60f, h * 0.62f)
                lineTo(w * 0.40f, h * 0.62f); lineTo(w * 0.40f, h * 0.86f); lineTo(w * 0.14f, h * 0.86f); close()
            },
            color, style = stroke,
        )

        GlimIconName.TABS -> {
            drawRoundRect(
                color, Offset(w * 0.08f, h * 0.30f), Size(w * 0.84f, h * 0.58f),
                CornerRadius(w * 0.08f), style = stroke,
            )
            drawLine(color, Offset(w * 0.26f, h * 0.30f), Offset(w * 0.26f, h * 0.18f), sw)
            drawLine(color, Offset(w * 0.26f, h * 0.18f), Offset(w * 0.74f, h * 0.18f), sw)
            drawLine(color, Offset(w * 0.74f, h * 0.18f), Offset(w * 0.74f, h * 0.30f), sw)
        }

        GlimIconName.MENU -> listOf(0.22f, 0.5f, 0.78f).forEach {
            drawCircle(color, radius = sw * 0.9f, center = Offset(w * it, h * 0.5f))
        }

        GlimIconName.SEARCH -> {
            drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.44f, h * 0.44f), style = stroke)
            drawLine(color, Offset(w * 0.64f, h * 0.64f), Offset(w * 0.88f, h * 0.88f), sw, StrokeCap.Round)
        }

        GlimIconName.BACK -> {
            drawLine(color, Offset(w * 0.70f, h * 0.20f), Offset(w * 0.32f, h * 0.50f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.32f, h * 0.50f), Offset(w * 0.70f, h * 0.80f), sw, StrokeCap.Round)
        }

        GlimIconName.FORWARD -> {
            drawLine(color, Offset(w * 0.30f, h * 0.20f), Offset(w * 0.68f, h * 0.50f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.68f, h * 0.50f), Offset(w * 0.30f, h * 0.80f), sw, StrokeCap.Round)
        }

        GlimIconName.RELOAD -> {
            drawArc(color, startAngle = 40f, sweepAngle = 280f, useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.18f), size = Size(w * 0.64f, h * 0.64f), style = stroke)
            drawPath(path {
                moveTo(w * 0.62f, h * 0.10f); lineTo(w * 0.80f, h * 0.22f); lineTo(w * 0.62f, h * 0.30f)
            }, color, style = stroke)
        }

        GlimIconName.SHIELD, GlimIconName.SHIELD_CHECK -> {
            val shield = path {
                moveTo(w * 0.5f, h * 0.10f); lineTo(w * 0.84f, h * 0.24f)
                lineTo(w * 0.84f, h * 0.52f)
                cubicTo(w * 0.84f, h * 0.70f, w * 0.68f, h * 0.82f, w * 0.5f, h * 0.90f)
                cubicTo(w * 0.32f, h * 0.82f, w * 0.16f, h * 0.70f, w * 0.16f, h * 0.52f)
                lineTo(w * 0.16f, h * 0.24f); close()
            }
            drawPath(shield, color, style = stroke)
            if (name == GlimIconName.SHIELD_CHECK) {
                drawPath(path {
                    moveTo(w * 0.34f, h * 0.50f); lineTo(w * 0.46f, h * 0.62f); lineTo(w * 0.68f, h * 0.38f)
                }, color, style = stroke)
            }
        }

        GlimIconName.SPARKLE -> {
            drawPath(path {
                moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.60f, h * 0.40f); lineTo(w * 0.92f, h * 0.50f)
                lineTo(w * 0.60f, h * 0.60f); lineTo(w * 0.5f, h * 0.92f); lineTo(w * 0.40f, h * 0.60f)
                lineTo(w * 0.08f, h * 0.50f); lineTo(w * 0.40f, h * 0.40f); close()
            }, if (filled) color else color, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
        }

        GlimIconName.STAR, GlimIconName.STAR_FILLED -> {
            val p = path {
                moveTo(w * 0.5f, h * 0.12f); lineTo(w * 0.61f, h * 0.38f); lineTo(w * 0.89f, h * 0.41f)
                lineTo(w * 0.68f, h * 0.59f); lineTo(w * 0.74f, h * 0.86f); lineTo(w * 0.5f, h * 0.72f)
                lineTo(w * 0.26f, h * 0.86f); lineTo(w * 0.32f, h * 0.59f); lineTo(w * 0.11f, h * 0.41f)
                lineTo(w * 0.39f, h * 0.38f); close()
            }
            drawPath(p, color, style = if (name == GlimIconName.STAR_FILLED) androidx.compose.ui.graphics.drawscope.Fill else stroke)
        }

        GlimIconName.CLOSE -> {
            drawLine(color, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), sw, StrokeCap.Round)
        }

        GlimIconName.PLUS -> {
            drawLine(color, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), sw, StrokeCap.Round)
        }

        GlimIconName.BOOKMARK -> drawPath(path {
            moveTo(w * 0.28f, h * 0.10f); lineTo(w * 0.72f, h * 0.10f); lineTo(w * 0.72f, h * 0.90f)
            lineTo(w * 0.5f, h * 0.70f); lineTo(w * 0.28f, h * 0.90f); close()
        }, color, style = stroke)

        GlimIconName.HISTORY, GlimIconName.CLOCK -> {
            drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            drawLine(color, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.52f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.64f, h * 0.60f), sw, StrokeCap.Round)
        }

        GlimIconName.DOWNLOAD -> {
            drawLine(color, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.60f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.30f, h * 0.42f), Offset(w * 0.5f, h * 0.62f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.70f, h * 0.42f), Offset(w * 0.5f, h * 0.62f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.18f, h * 0.84f), Offset(w * 0.82f, h * 0.84f), sw, StrokeCap.Round)
        }

        GlimIconName.PLUGIN -> {
            drawRoundRect(color, Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f),
                CornerRadius(w * 0.16f), style = stroke)
            drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
        }

        GlimIconName.SETTINGS -> {
            drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            listOf(0f, 90f, 180f, 270f).forEach { angle ->
                val rad = angle * kotlin.math.PI.toFloat() / 180f
                drawLine(
                    color,
                    Offset(w * 0.5f + kotlin.math.cos(rad) * w * 0.28f, h * 0.5f + kotlin.math.sin(rad) * h * 0.28f),
                    Offset(w * 0.5f + kotlin.math.cos(rad) * w * 0.40f, h * 0.5f + kotlin.math.sin(rad) * h * 0.40f),
                    sw, StrokeCap.Round,
                )
            }
        }

        GlimIconName.INFO -> {
            drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            drawCircle(color, radius = sw * 0.8f, center = Offset(w * 0.5f, h * 0.32f))
            drawLine(color, Offset(w * 0.5f, h * 0.44f), Offset(w * 0.5f, h * 0.70f), sw, StrokeCap.Round)
        }

        GlimIconName.FOLDER -> drawPath(path {
            moveTo(w * 0.12f, h * 0.30f); lineTo(w * 0.40f, h * 0.30f); lineTo(w * 0.48f, h * 0.40f)
            lineTo(w * 0.88f, h * 0.40f); lineTo(w * 0.88f, h * 0.82f); lineTo(w * 0.12f, h * 0.82f); close()
        }, color, style = stroke)

        GlimIconName.CHEVRON_RIGHT -> {
            drawLine(color, Offset(w * 0.40f, h * 0.24f), Offset(w * 0.64f, h * 0.50f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.64f, h * 0.50f), Offset(w * 0.40f, h * 0.76f), sw, StrokeCap.Round)
        }

        GlimIconName.ARROW_RIGHT -> {
            drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.60f, h * 0.28f), Offset(w * 0.84f, h * 0.5f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.60f, h * 0.72f), Offset(w * 0.84f, h * 0.5f), sw, StrokeCap.Round)
        }

        GlimIconName.ARROW_UP -> {
            drawLine(color, Offset(w * 0.5f, h * 0.84f), Offset(w * 0.5f, h * 0.18f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.28f, h * 0.40f), Offset(w * 0.5f, h * 0.16f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.72f, h * 0.40f), Offset(w * 0.5f, h * 0.16f), sw, StrokeCap.Round)
        }

        GlimIconName.LOCK -> {
            drawRoundRect(color, Offset(w * 0.20f, h * 0.46f), Size(w * 0.60f, h * 0.42f),
                CornerRadius(w * 0.12f), style = stroke)
            drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(w * 0.32f, h * 0.20f), size = Size(w * 0.36f, h * 0.40f), style = thin)
        }

        GlimIconName.TRASH -> {
            drawLine(color, Offset(w * 0.16f, h * 0.30f), Offset(w * 0.84f, h * 0.30f), sw, StrokeCap.Round)
            drawRoundRect(color, Offset(w * 0.24f, h * 0.30f), Size(w * 0.52f, h * 0.56f),
                CornerRadius(w * 0.10f), style = stroke)
            drawLine(color, Offset(w * 0.40f, h * 0.18f), Offset(w * 0.60f, h * 0.18f), sw, StrokeCap.Round)
        }

        GlimIconName.PIN -> {
            drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.36f), style = stroke)
            drawLine(color, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.5f, h * 0.88f), sw, StrokeCap.Round)
        }

        GlimIconName.GLOBE -> {
            drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            drawLine(color, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), thin.width, StrokeCap.Round)
            drawArc(color, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(w * 0.30f, h * 0.12f), size = Size(w * 0.40f, h * 0.76f), style = thin)
        }

        GlimIconName.INCOGNITO -> {
            drawRoundRect(color, Offset(w * 0.12f, h * 0.40f), Size(w * 0.76f, h * 0.26f),
                CornerRadius(w * 0.12f), style = stroke)
            drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.32f, h * 0.72f), style = thin)
            drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.68f, h * 0.72f), style = thin)
            drawLine(color, Offset(w * 0.20f, h * 0.30f), Offset(w * 0.80f, h * 0.30f), thin.width, StrokeCap.Round)
        }

        GlimIconName.DESKTOP -> {
            drawRoundRect(color, Offset(w * 0.12f, h * 0.18f), Size(w * 0.76f, h * 0.50f),
                CornerRadius(w * 0.08f), style = stroke)
            drawLine(color, Offset(w * 0.36f, h * 0.86f), Offset(w * 0.64f, h * 0.86f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.50f, h * 0.68f), Offset(w * 0.50f, h * 0.86f), sw, StrokeCap.Round)
        }
    }
}
