package com.hypai.fion.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object Icons {
    private fun stroke(vararg paths: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach {
                    addPath(
                        pathData = addPathNodes(it),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    private const val BUBBLE = "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
    private const val HANDSET = "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67" +
        "A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6" +
        "l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92z"
    private const val SHIELD = "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
    private const val SHIELD_TICK = "m9 12 2 2 4-4"
    private const val EYE = "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"
    private const val PUPIL = "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"

    val Back = stroke("M19 12H5M12 19l-7-7 7-7")
    val Chevron = stroke("m9 18 6-6-6-6", width = 2.2f)
    val External = stroke("M7 17 17 7", "M8 7h9v9")
    val Scroll = stroke(
        "M15 12h-5M15 8h-5",
        "M19 17V5a2 2 0 0 0-2-2H4",
        "M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3",
    )
    val GitHub = stroke(
        "M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5" +
            "-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.4 5.4 0 0 0 4 9c0 3.5 3 5.5 6 5.5" +
            "-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4",
        "M9 18c-4.51 2-5-2-7-2",
    )
    val Book = stroke("M2 4.5h6a4 4 0 0 1 4 4V21a3 3 0 0 0-3-3H2z", "M22 4.5h-6a4 4 0 0 0-4 4V21a3 3 0 0 1 3-3h7z")
    val Check = stroke("M20 6 9 17l-5-5", width = 2.6f)
    val Alert = stroke("M12 7v6M12 17h.01", width = 2.6f)
    val Warning = stroke(
        "M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z",
        "M12 9v4M12 17h.01",
        width = 2.1f,
    )
    val Power = stroke("M12 3v9", "M18.4 6.6a9 9 0 1 1-12.8 0", width = 2.4f)
    val Bolt = stroke("M13 2 3 14h9l-1 8 10-12h-9z", width = 2.4f)
    val Bubble = stroke(BUBBLE)
    val Sms = stroke(BUBBLE, "M8 9h8M8 13h5")
    val Phone = stroke(HANDSET)
    val MissedCall = stroke(HANDSET, "m16 2 6 6M22 2l-6 6")
    val Shield = stroke(SHIELD, SHIELD_TICK)
    val Reset = stroke("M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5")
    val Info = stroke("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 16v-4M12 8h.01")
    val Bell = stroke("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0", width = 2.2f)
    val Eye = stroke(EYE, PUPIL)
    val EyeOff = stroke(EYE, PUPIL, "M3 3l18 18")
    val Battery = stroke("M5 7h11a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M22 11v2", "m10 8.5-2 3.5h3l-2 3.5")
    val Gear = stroke(
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38" +
            "a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38" +
            "a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
            "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08" +
            "a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08" +
            "a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        PUPIL,
    )
    val Link = stroke(
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
        width = 2.2f,
    )
    val Template = stroke("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6M8 13h8M8 17h5", width = 2.2f)
    val LockKey = stroke(
        "M6.5 11h11a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 18.5v-5A2.5 2.5 0 0 1 6.5 11z",
        "M8 11V7.5a4 4 0 0 1 8 0V11",
        "M12 15.2v2",
        width = 1.8f,
    )
    val Pulse = stroke("M3 12h4l2.5-6 5 12 2.5-6h4", width = 1.8f)
    val Lock = stroke("M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4", width = 2.2f)
    val Globe = stroke("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M2 12h20M12 2a15.3 15.3 0 0 1 0 20M12 2a15.3 15.3 0 0 0 0 20")
    val Forward = stroke("M15 17l5-5-5-5", "M4 18v-2a4 4 0 0 1 4-4h12", width = 2.2f)
    val Sim = stroke("M15 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7z", "M9.5 11h5a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z", width = 1.8f)
    val Close = stroke("M18 6 6 18", "M6 6l12 12", width = 2.4f)
    val Ban = stroke("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M4.93 4.93l14.14 14.14")
    val Smartphone = stroke("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 18h.01")
    val ArrowDown = stroke("M12 5v14", "m19 12-7 7-7-7", width = 2.2f)
    val Cloud = stroke("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9z")
}
