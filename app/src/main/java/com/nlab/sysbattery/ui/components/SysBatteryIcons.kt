package com.nlab.sysbattery.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Crisp, optically-centered 24x24 Material 3 vector icons tailored for SysBattery telemetry UI.
 * Keeps APK size minimal while providing distinct iconography for every hardware metric.
 */
object SysBatteryIcons {

    private fun vector(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    val BrandLogo: ImageVector by lazy {
        vector("BrandLogo") {
            path(fill = SolidColor(Color.Black)) {
                // Terminal cap
                moveTo(10f, 1.5f)
                horizontalLineToRelative(4f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineTo(3.5f)
                horizontalLineTo(9f)
                verticalLineTo(2.5f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                close()
                // Outer battery frame with lightning cutout
                moveTo(7.5f, 4f)
                horizontalLineToRelative(9f)
                curveToRelative(1.38f, 0f, 2.5f, 1.12f, 2.5f, 2.5f)
                verticalLineToRelative(13f)
                curveToRelative(0f, 1.38f, -1.12f, 2.5f, -2.5f, 2.5f)
                horizontalLineToRelative(-9f)
                curveTo(6.12f, 22f, 5f, 20.88f, 5f, 19.5f)
                verticalLineToRelative(-13f)
                curveTo(5f, 5.12f, 6.12f, 4f, 7.5f, 4f)
                close()
                moveTo(11.2f, 19.5f)
                lineToRelative(4.5f, -7.2f)
                horizontalLineToRelative(-3f)
                lineToRelative(1.1f, -5.8f)
                lineToRelative(-4.5f, 7.2f)
                horizontalLineToRelative(3f)
                lineToRelative(-1.1f, 5.8f)
                close()
            }
        }
    }

    val Bolt: ImageVector by lazy {
        vector("Bolt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 21f)
                horizontalLineToRelative(-1f)
                lineToRelative(1f, -7f)
                horizontalLineTo(7.5f)
                curveToRelative(-0.58f, 0f, -0.57f, -0.32f, -0.38f, -0.66f)
                lineToRelative(0.19f, -0.34f)
                lineTo(13f, 3f)
                horizontalLineToRelative(1f)
                lineToRelative(-1f, 7f)
                horizontalLineToRelative(3.5f)
                curveToRelative(0.49f, 0f, 0.56f, 0.33f, 0.47f, 0.51f)
                lineToRelative(-0.07f, 0.15f)
                lineTo(11f, 21f)
                close()
            }
        }
    }

    val ElectricBolt: ImageVector by lazy {
        vector("ElectricBolt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.69f, 2.21f)
                lineTo(4.33f, 11.49f)
                curveToRelative(-0.64f, 0.58f, -0.28f, 1.65f, 0.58f, 1.73f)
                lineTo(13f, 14f)
                lineToRelative(-4.85f, 6.76f)
                curveToRelative(-0.22f, 0.31f, -0.19f, 0.74f, 0.08f, 1.01f)
                curveToRelative(0.3f, 0.3f, 0.77f, 0.31f, 1.08f, 0.02f)
                lineToRelative(10.36f, -9.28f)
                curveToRelative(0.64f, -0.58f, 0.28f, -1.65f, -0.58f, -1.73f)
                lineTo(11f, 10f)
                lineToRelative(4.85f, -6.76f)
                curveToRelative(0.22f, -0.31f, 0.19f, -0.74f, -0.08f, -1.01f)
                curveToRelative(-0.3f, -0.3f, -0.77f, -0.31f, -1.08f, -0.02f)
                close()
            }
        }
    }

    val FlashOn: ImageVector by lazy {
        vector("FlashOn") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 2f)
                verticalLineToRelative(11f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(9f)
                lineToRelative(7f, -12f)
                horizontalLineToRelative(-4f)
                lineToRelative(4f, -8f)
                horizontalLineTo(7f)
                close()
            }
        }
    }

    val VoltageWave: ImageVector by lazy {
        vector("VoltageWave") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 12f)
                horizontalLineToRelative(3.5f)
                lineToRelative(2.5f, -6f)
                lineToRelative(4f, 12f)
                lineToRelative(3.5f, -9f)
                lineToRelative(2f, 3f)
                horizontalLineTo(22f)
            }
        }
    }

    val Timeline: ImageVector by lazy {
        vector("Timeline") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(23f, 8f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                curveToRelative(-0.18f, 0f, -0.35f, -0.02f, -0.51f, -0.07f)
                lineToRelative(-3.56f, 3.55f)
                curveToRelative(0.05f, 0.16f, 0.07f, 0.34f, 0.07f, 0.52f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                reflectiveCurveToRelative(-2f, -0.9f, -2f, -2f)
                curveToRelative(0f, -0.18f, 0.02f, -0.36f, 0.07f, -0.52f)
                lineToRelative(-2.55f, -2.55f)
                curveToRelative(-0.16f, 0.05f, -0.34f, 0.07f, -0.52f, 0.07f)
                reflectiveCurveToRelative(-0.36f, -0.02f, -0.52f, -0.07f)
                lineToRelative(-4.55f, 4.56f)
                curveToRelative(0.05f, 0.16f, 0.07f, 0.33f, 0.07f, 0.51f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                reflectiveCurveToRelative(-2f, -0.9f, -2f, -2f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                curveToRelative(0.18f, 0f, 0.35f, 0.02f, 0.51f, 0.07f)
                lineToRelative(4.56f, -4.55f)
                curveTo(8.02f, 9.36f, 8f, 9.18f, 8f, 9f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                curveToRelative(0f, 0.18f, -0.02f, 0.36f, -0.07f, 0.52f)
                lineToRelative(2.55f, 2.55f)
                curveToRelative(0.16f, -0.05f, 0.34f, -0.07f, 0.52f, -0.07f)
                reflectiveCurveToRelative(0.36f, 0.02f, 0.52f, 0.07f)
                lineToRelative(3.55f, -3.56f)
                curveTo(19.02f, 8.35f, 19f, 8.18f, 19f, 8f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                close()
            }
        }
    }

    val ShowChart: ImageVector by lazy {
        vector("ShowChart") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.5f, 18.49f)
                lineToRelative(6f, -6.01f)
                lineToRelative(4f, 4f)
                lineTo(22f, 6.92f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-7.09f, 7.97f)
                lineToRelative(-4f, -4f)
                lineTo(2f, 16.99f)
                lineToRelative(1.5f, 1.5f)
                close()
            }
        }
    }

    val BatteryFull: ImageVector by lazy {
        vector("BatteryFull") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.67f, 4f)
                horizontalLineTo(14f)
                verticalLineTo(2f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(2f)
                horizontalLineTo(8.33f)
                curveTo(7.6f, 4f, 7f, 4.6f, 7f, 5.33f)
                verticalLineToRelative(15.33f)
                curveTo(7f, 21.4f, 7.6f, 22f, 8.33f, 22f)
                horizontalLineToRelative(7.33f)
                curveToRelative(0.74f, 0f, 1.34f, -0.6f, 1.34f, -1.33f)
                verticalLineTo(5.33f)
                curveTo(17f, 4.6f, 16.4f, 4f, 15.67f, 4f)
                close()
                // 3 capacity segments
                moveTo(9f, 7f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(3f)
                horizontalLineTo(9f)
                close()
                moveTo(9f, 11.5f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(3f)
                horizontalLineTo(9f)
                close()
                moveTo(9f, 16f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(3.5f)
                horizontalLineTo(9f)
                close()
            }
        }
    }

    val BatteryChargingFull: ImageVector by lazy {
        vector("BatteryChargingFull") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.67f, 4f)
                horizontalLineTo(14f)
                verticalLineTo(2f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(2f)
                horizontalLineTo(8.33f)
                curveTo(7.6f, 4f, 7f, 4.6f, 7f, 5.33f)
                verticalLineToRelative(15.33f)
                curveTo(7f, 21.4f, 7.6f, 22f, 8.33f, 22f)
                horizontalLineToRelative(7.33f)
                curveToRelative(0.74f, 0f, 1.34f, -0.6f, 1.34f, -1.33f)
                verticalLineTo(5.33f)
                curveTo(17f, 4.6f, 16.4f, 4f, 15.67f, 4f)
                close()
                moveTo(11f, 20f)
                verticalLineToRelative(-5.5f)
                horizontalLineTo(9f)
                lineTo(13f, 7f)
                verticalLineToRelative(5.5f)
                horizontalLineToRelative(2f)
                lineTo(11f, 20f)
                close()
            }
        }
    }

    val CpuAwake: ImageVector by lazy {
        vector("CpuAwake") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 9f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(6f)
                verticalLineTo(9f)
                close()
                moveTo(13f, 13f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(21f, 11f)
                verticalLineTo(9f)
                horizontalLineToRelative(-2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-2f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(2f)
                horizontalLineTo(7f)
                curveTo(5.9f, 5f, 5f, 5.9f, 5f, 7f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(7f)
                verticalLineTo(7f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(10f)
                close()
            }
        }
    }

    val Apps: ImageVector by lazy {
        vector("Apps") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 8f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(10f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(10f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                close()
                moveTo(10f, 8f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    val Settings: ImageVector by lazy {
        vector("Settings") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.14f, 12.94f)
                curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                curveToRelative(0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                lineToRelative(2.03f, -1.58f)
                curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                lineToRelative(-1.92f, -3.32f)
                curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                lineToRelative(-2.39f, 0.96f)
                curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                lineTo(14.4f, 2.81f)
                curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                horizontalLineToRelative(-3.84f)
                curveToRelative(-0.24f, 0f, -0.43f, 0.17f, -0.47f, 0.41f)
                lineTo(9.25f, 5.35f)
                curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                lineTo(5.24f, 5.33f)
                curveToRelative(-0.22f, -0.08f, -0.47f, 0f, -0.59f, 0.22f)
                lineTo(2.74f, 8.87f)
                curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                lineToRelative(2.03f, 1.58f)
                curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
                curveToRelative(0f, 0.31f, 0.02f, 0.65f, 0.07f, 0.94f)
                lineToRelative(-2.03f, 1.58f)
                curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                lineToRelative(1.92f, 3.32f)
                curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                lineToRelative(2.39f, -0.96f)
                curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                lineToRelative(0.36f, 2.54f)
                curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                horizontalLineToRelative(3.84f)
                curveToRelative(0.24f, 0f, 0.44f, -0.17f, 0.47f, -0.41f)
                lineToRelative(0.36f, -2.54f)
                curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                lineToRelative(2.39f, 0.96f)
                curveToRelative(0.22f, 0.08f, 0.47f, 0f, 0.59f, -0.22f)
                lineToRelative(1.92f, -3.32f)
                curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                lineTo(19.14f, 12.94f)
                close()
                moveTo(12f, 15.6f)
                curveToRelative(-1.98f, 0f, -3.6f, -1.62f, -3.6f, -3.6f)
                reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                reflectiveCurveTo(13.98f, 15.6f, 12f, 15.6f)
                close()
            }
        }
    }

    val Thermostat: ImageVector by lazy {
        vector("Thermostat") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 13f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                reflectiveCurveTo(9f, 3.34f, 9f, 5f)
                verticalLineToRelative(8f)
                curveToRelative(-1.21f, 0.91f, -2f, 2.37f, -2f, 4f)
                curveToRelative(0f, 2.76f, 2.24f, 5f, 5f, 5f)
                reflectiveCurveToRelative(5f, -2.24f, 5f, -5f)
                curveTo(17f, 15.37f, 16.21f, 13.91f, 15f, 13f)
                close()
                moveTo(11f, 11f)
                verticalLineTo(5f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    val Speed: ImageVector by lazy {
        vector("Speed") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.38f, 8.57f)
                lineToRelative(-1.23f, 1.85f)
                arcToRelative(8f, 8f, 0f, false, true, -0.22f, 7.58f)
                horizontalLineTo(5.07f)
                arcTo(8f, 8f, 0f, false, true, 15.58f, 6.85f)
                lineToRelative(1.85f, -1.23f)
                arcTo(10f, 10f, 0f, false, false, 3.35f, 19f)
                arcToRelative(2f, 2f, 0f, false, false, 1.72f, 1f)
                horizontalLineToRelative(13.85f)
                arcToRelative(2f, 2f, 0f, false, false, 1.74f, -1f)
                arcToRelative(10f, 10f, 0f, false, false, -0.27f, -10.44f)
                close()
                moveTo(10.59f, 15.41f)
                arcToRelative(2f, 2f, 0f, false, false, 2.83f, 0f)
                lineToRelative(5.66f, -8.49f)
                lineToRelative(-8.49f, 5.66f)
                arcToRelative(2f, 2f, 0f, false, false, 0f, 2.83f)
                close()
            }
        }
    }

    val Favorite: ImageVector by lazy {
        vector("Favorite") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.5f, 3f)
                curveToRelative(-1.74f, 0f, -3.41f, 0.81f, -4.5f, 2.09f)
                curveTo(10.91f, 3.81f, 9.24f, 3f, 7.5f, 3f)
                curveTo(4.42f, 3f, 2f, 5.42f, 2f, 8.5f)
                curveToRelative(0f, 3.78f, 3.4f, 6.86f, 8.55f, 11.54f)
                lineTo(12f, 21.35f)
                lineToRelative(1.45f, -1.32f)
                curveTo(18.6f, 15.36f, 22f, 12.28f, 22f, 8.5f)
                curveTo(22f, 5.42f, 19.58f, 3f, 16.5f, 3f)
                close()
                // Pulse line cutout across heart
                moveTo(6f, 11.5f)
                horizontalLineToRelative(2.6f)
                lineToRelative(1.4f, -3.2f)
                lineToRelative(2.2f, 6f)
                lineToRelative(1.6f, -3.8f)
                horizontalLineTo(18f)
                verticalLineTo(13f)
                horizontalLineToRelative(-3.2f)
                lineToRelative(-2.5f, 4.5f)
                lineToRelative(-2.2f, -6f)
                lineToRelative(-0.6f, 1.5f)
                horizontalLineTo(6f)
                verticalLineToRelative(-1.5f)
                close()
            }
        }
    }

    val TrendingDown: ImageVector by lazy {
        vector("TrendingDown") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 18f)
                lineToRelative(2.29f, -2.29f)
                lineToRelative(-4.88f, -4.88f)
                lineToRelative(-4f, 4f)
                lineTo(2f, 7.41f)
                lineTo(3.41f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(4f, -4f)
                lineToRelative(6.3f, 6.29f)
                lineTo(22f, 12f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-6f)
                close()
            }
        }
    }

    val Smartphone: ImageVector by lazy {
        vector("Smartphone") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 1.01f)
                lineTo(7f, 1f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(10f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(3f)
                curveToRelative(0f, -1.1f, -0.9f, -1.99f, -2f, -1.99f)
                close()
                moveTo(17f, 19f)
                horizontalLineTo(7f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(14f)
                close()
            }
        }
    }

    val NightlightRound: ImageVector by lazy {
        vector("NightlightRound") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.5f, 22f)
                curveToRelative(1.05f, 0f, 2.05f, -0.16f, 3f, -0.46f)
                curveToRelative(-4.06f, -1.27f, -7f, -5.06f, -7f, -9.54f)
                reflectiveCurveToRelative(2.94f, -8.27f, 7f, -9.54f)
                curveTo(17.55f, 2.16f, 16.55f, 2f, 15.5f, 2f)
                curveToRelative(-5.52f, 0f, -10f, 4.48f, -10f, 10f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                close()
            }
        }
    }

    val Autorenew: ImageVector by lazy {
        vector("Autorenew") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 6f)
                verticalLineToRelative(3f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
                verticalLineToRelative(3f)
                curveToRelative(-4.42f, 0f, -8f, 3.58f, -8f, 8f)
                curveToRelative(0f, 1.57f, 0.46f, 3.03f, 1.24f, 4.26f)
                lineTo(6.7f, 14.8f)
                curveToRelative(-0.45f, -0.83f, -0.7f, -1.79f, -0.7f, -2.8f)
                curveToRelative(0f, -3.31f, 2.69f, -6f, 6f, -6f)
                close()
                moveTo(18.76f, 7.74f)
                lineTo(17.3f, 9.2f)
                curveToRelative(0.44f, 0.84f, 0.7f, 1.79f, 0.7f, 2.8f)
                curveToRelative(0f, 3.31f, -2.69f, 6f, -6f, 6f)
                verticalLineToRelative(-3f)
                lineToRelative(-4f, 4f)
                lineToRelative(4f, 4f)
                verticalLineToRelative(-3f)
                curveToRelative(4.42f, 0f, 8f, -3.58f, 8f, -8f)
                curveToRelative(0f, -1.57f, -0.46f, -3.03f, -1.24f, -4.26f)
                close()
            }
        }
    }

    val Refresh: ImageVector
        get() = Autorenew

    val HealthAndSafety: ImageVector by lazy {
        vector("HealthAndSafety") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.5f, 13f)
                horizontalLineTo(8f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(2.5f)
                verticalLineTo(7.5f)
                horizontalLineToRelative(3f)
                verticalLineTo(10f)
                horizontalLineTo(16f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(-2.5f)
                verticalLineToRelative(2.5f)
                horizontalLineToRelative(-3f)
                verticalLineTo(13f)
                close()
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                verticalLineToRelative(6.09f)
                curveToRelative(0f, 5.05f, 3.41f, 9.76f, 8f, 10.91f)
                curveToRelative(4.59f, -1.15f, 8f, -5.86f, 8f, -10.91f)
                verticalLineTo(5f)
                lineTo(12f, 2f)
                close()
            }
        }
    }

    val Security: ImageVector by lazy {
        vector("Security") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                verticalLineTo(5f)
                lineTo(12f, 1f)
                close()
                moveTo(12f, 11.99f)
                horizontalLineToRelative(7f)
                curveToRelative(-0.53f, 4.12f, -3.28f, 7.79f, -7f, 8.94f)
                verticalLineTo(12f)
                horizontalLineTo(5f)
                verticalLineTo(6.3f)
                lineToRelative(7f, -3.11f)
                verticalLineToRelative(8.8f)
                close()
            }
        }
    }

    val AdminPanelSettings: ImageVector by lazy {
        vector("AdminPanelSettings") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                verticalLineTo(5f)
                lineTo(12f, 1f)
                close()
                moveTo(12f, 7f)
                curveToRelative(1.66f, 0f, 3f, 1.34f, 3f, 3f)
                reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
                reflectiveCurveToRelative(-3f, -1.34f, -3f, -3f)
                reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
                close()
                moveTo(12f, 17.9f)
                curveToRelative(-2.5f, 0f, -4.71f, -1.28f, -6f, -3.22f)
                curveToRelative(0.03f, -1.99f, 4f, -3.08f, 6f, -3.08f)
                curveToRelative(1.99f, 0f, 5.97f, 1.09f, 6f, 3.08f)
                curveToRelative(-1.29f, 1.94f, -3.5f, 3.22f, -6f, 3.22f)
                close()
            }
        }
    }

    val NotificationsActive: ImageVector by lazy {
        vector("NotificationsActive") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 22f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                horizontalLineToRelative(-4f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                close()
                moveTo(18f, 16f)
                verticalLineToRelative(-5f)
                curveToRelative(0f, -3.07f, -1.64f, -5.64f, -4.5f, -6.32f)
                verticalLineTo(4f)
                curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveTo(7.63f, 5.36f, 6f, 7.92f, 6f, 11f)
                verticalLineToRelative(5f)
                lineToRelative(-2f, 2f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-1f)
                lineToRelative(-2f, -2f)
                close()
            }
        }
    }

    val LockOpen: ImageVector by lazy {
        vector("LockOpen") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 17f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
                moveTo(18f, 8f)
                horizontalLineToRelative(-1f)
                verticalLineTo(6f)
                curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
                reflectiveCurveTo(7f, 3.24f, 7f, 6f)
                horizontalLineToRelative(1.9f)
                curveToRelative(0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
                curveToRelative(1.71f, 0f, 3.1f, 1.39f, 3.1f, 3.1f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(10f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

    val Info: ImageVector by lazy {
        vector("Info") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(13f, 17f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                close()
                moveTo(13f, 9f)
                horizontalLineToRelative(-2f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val Terminal: ImageVector by lazy {
        vector("Terminal") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 4f)
                horizontalLineTo(4f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(20f, 18f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(10f)
                close()
                moveTo(18f, 17f)
                horizontalLineToRelative(-6f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                close()
                moveTo(7.5f, 17f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(8.67f, 13f)
                lineToRelative(-2.59f, -2.59f)
                lineTo(7.5f, 9f)
                lineToRelative(4f, 4f)
                lineToRelative(-4f, 4f)
                close()
            }
        }
    }

    val Description: ImageVector by lazy {
        vector("Description") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(4f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(16f, 18f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(16f, 14f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    val DeleteSweep: ImageVector by lazy {
        vector("DeleteSweep") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 16f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-4f)
                close()
                moveTo(15f, 8f)
                horizontalLineToRelative(7f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-7f)
                close()
                moveTo(15f, 12f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-6f)
                close()
                moveTo(3f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(6f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                horizontalLineTo(3f)
                verticalLineToRelative(10f)
                close()
                moveTo(14f, 5f)
                horizontalLineToRelative(-3f)
                lineToRelative(-1f, -1f)
                horizontalLineTo(6f)
                lineTo(5f, 5f)
                horizontalLineTo(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(12f)
                close()
            }
        }
    }

    val ExpandMore: ImageVector by lazy {
        vector("ExpandMore") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }
    }

    val ExpandLess: ImageVector by lazy {
        vector("ExpandLess") {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 15f)
                lineTo(12f, 9f)
                lineTo(6f, 15f)
            }
        }
    }

    val ContentCopy: ImageVector by lazy {
        vector("ContentCopy") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(12f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(11f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(14f)
                close()
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        vector("CheckCircle") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(10f, 17f)
                lineToRelative(-5f, -5f)
                lineToRelative(1.41f, -1.41f)
                lineTo(10f, 14.17f)
                lineToRelative(7.59f, -7.59f)
                lineTo(19f, 8f)
                lineToRelative(-9f, 9f)
                close()
            }
        }
    }
}
