package com.dhakarun.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** পারসপেকটিভ ক্যামেরা: p = 0 দিগন্ত, p = 1 স্ক্রিনের নিচ */
private class Cam(val w: Float, val h: Float) {
    val horizon = h * 0.28f
    fun sy(p: Float): Float = horizon + (h - horizon) * max(p, 0f)
    fun r(p: Float): Float = 0.18f + 0.82f * max(p, 0f)
    fun u(p: Float): Float = w * 0.28f * r(p)
    fun cx(lane: Float, p: Float): Float = w / 2f + (lane - 1f) * u(p)
}

private val skyTop = longArrayOf(0xFF5AA9E6, 0xFFF2A65A, 0xFF6FB1D8, 0xFF7EC8E3)
private val skyBottom = longArrayOf(0xFFBFE3F7, 0xFFFAD7A0, 0xFFD6EEF7, 0xFFE3F5D0)
private val sideColor = longArrayOf(0xFF7A8B7A, 0xFF8D6E63, 0xFF2F80B5, 0xFF4CAF50)
private val roadColor = longArrayOf(0xFF3A3A3F, 0xFF4A4036, 0xFF45454A, 0xFF5B5148)

private val glass = longArrayOf(0xFF607D8B, 0xFF78909C, 0xFF546E7A, 0xFF90A4AE)
private val pastel = longArrayOf(0xFFE57373, 0xFFFFB74D, 0xFF81C784, 0xFF64B5F6, 0xFFBA68C8)
private val rickshawColors = longArrayOf(0xFFE53935, 0xFF1E88E5, 0xFFFB8C00, 0xFF8E24AA)
private val busColors = longArrayOf(0xFFFBC02D, 0xFFE64A19, 0xFF1976D2, 0xFF388E3C)

private val paint = Paint().apply {
    isAntiAlias = true
    typeface = Typeface.DEFAULT_BOLD
}

private fun hash(n: Int): Int {
    var x = n * 73856093 xor (n shr 3) * 19349663
    x = x xor (x ushr 13)
    return x and 0x7fffffff
}

fun DrawScope.drawWorld(g: Game, skinColor: Long, best: Int) {
    val cam = Cam(size.width, size.height)
    val w = cam.w
    val h = cam.h
    val z = g.zone

    // আকাশ ও পাশের মাটি
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(skyTop[z]), Color(skyBottom[z])),
            startY = 0f,
            endY = cam.horizon
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, cam.horizon)
    )
    drawRect(Color(sideColor[z]), Offset(0f, cam.horizon), Size(w, h - cam.horizon))

    // রাস্তা
    val pEnd = 1.3f
    val road = Path().apply {
        moveTo(w / 2f - w * 0.42f * cam.r(0f), cam.horizon)
        lineTo(w / 2f + w * 0.42f * cam.r(0f), cam.horizon)
        lineTo(w / 2f + w * 0.42f * cam.r(pEnd), cam.sy(pEnd))
        lineTo(w / 2f - w * 0.42f * cam.r(pEnd), cam.sy(pEnd))
        close()
    }
    drawPath(road, Color(roadColor[z]))
    // রাস্তার দুই কিনারা
    for (side in intArrayOf(-1, 1)) {
        drawLine(
            Color(0xFFEEEEEE),
            Offset(cam.cx(1f + side * 1.5f, 0f), cam.sy(0f)),
            Offset(cam.cx(1f + side * 1.5f, pEnd), cam.sy(pEnd)),
            strokeWidth = w * 0.008f
        )
    }

    val scroll = g.dist

    // চলন্ত লেনের দাগ
    val dashPeriod = 0.2f
    val dashBase = floor(scroll / dashPeriod).toInt()
    val dashOff = scroll - dashBase * dashPeriod
    for (b in floatArrayOf(0.5f, 1.5f)) {
        for (j in 0..7) {
            val p0 = j * dashPeriod + dashOff
            val p1 = p0 + dashPeriod * 0.5f
            if (p1 <= 0.01f) continue
            drawLine(
                Color(0xCCFFFFFF),
                Offset(cam.cx(b, p0), cam.sy(p0)),
                Offset(cam.cx(b, p1), cam.sy(p1)),
                strokeWidth = w * 0.012f * cam.r(p0)
            )
        }
    }

    drawSideDecor(cam, z, scroll)

    // কয়েন ও পাওয়ার-আপ
    for (i in g.coinList.indices.reversed()) drawCoin(cam, g.coinList[i])
    for (i in g.pickups.indices.reversed()) drawPickup(cam, g.pickups[i])

    // বাধা (দূরেরগুলো আগে), খেলোয়াড়ের আগে-পরে
    for (i in g.obstacles.indices.reversed()) {
        val o = g.obstacles[i]
        if (o.y <= Game.PLAYER_Y) drawObstacle(cam, o)
    }
    drawPlayer(cam, g, skinColor)
    for (i in g.obstacles.indices.reversed()) {
        val o = g.obstacles[i]
        if (o.y > Game.PLAYER_Y) drawObstacle(cam, o)
    }

    if (g.chase) drawChaser(cam, g)

    drawHud(g, w, h, best)
}

// ---------------- পাশের দৃশ্য ----------------
private fun DrawScope.drawSideDecor(cam: Cam, zone: Int, scroll: Float) {
    val w = cam.w
    val period = 0.25f
    val base = floor(scroll / period).toInt()
    val off = scroll - base * period
    for (j in 0..6) {
        val p = j * period + off
        if (p <= 0.02f) continue
        val n = j - base
        val hv = hash(n)
        val u = cam.u(p)
        val y = cam.sy(p)
        val edge = w * 0.42f * cam.r(p)
        for (side in intArrayOf(-1, 1)) {
            val hh = hash(n * 2 + (if (side == 1) 1 else 0))
            val x = w / 2f + side * (edge + 0.75f * u)
            when (zone) {
                0 -> { // গুলশান: কাচের বহুতল
                    val bh = (2.5f + (hh % 30) / 10f) * u
                    val bw = 1.2f * u
                    drawRect(Color(glass[hh % glass.size]), Offset(x - bw / 2, y - bh), Size(bw, bh))
                    for (k in 1..3) {
                        drawRect(
                            Color(0x55FFFFFF),
                            Offset(x - bw * 0.4f, y - bh + bh * k * 0.22f),
                            Size(bw * 0.8f, bh * 0.07f)
                        )
                    }
                }
                1 -> { // পুরান ঢাকা: রঙিন বাড়ি
                    val bh = (1.3f + (hh % 12) / 10f) * u
                    val bw = 1.05f * u
                    drawRect(Color(pastel[hh % pastel.size]), Offset(x - bw / 2, y - bh), Size(bw, bh))
                    drawRect(Color(0x55000000), Offset(x - bw / 2, y - bh), Size(bw, bh * 0.08f))
                    for (k in 0..1) {
                        drawRect(
                            Color(0xAA3E2723),
                            Offset(x - bw * 0.35f + k * bw * 0.45f, y - bh * 0.65f),
                            Size(bw * 0.25f, bh * 0.25f)
                        )
                    }
                }
                2 -> { // সদরঘাট: নদী, নৌকা, বাতি
                    if (hh % 2 == 0) {
                        val bx = w / 2f + side * (edge + 1.4f * u)
                        drawRect(Color(0xFF6D4C41), Offset(bx - 0.7f * u, y - 0.25f * u), Size(1.4f * u, 0.25f * u))
                        drawRect(Color(0xFFFAFAFA), Offset(bx - 0.3f * u, y - 0.55f * u), Size(0.6f * u, 0.3f * u))
                        drawRect(Color(0xFF1565C0), Offset(bx - 0.3f * u, y - 0.4f * u), Size(0.6f * u, 0.07f * u))
                    } else {
                        val lx = w / 2f + side * (edge + 0.3f * u)
                        drawRect(Color(0xFF424242), Offset(lx - 0.04f * u, y - 1.8f * u), Size(0.08f * u, 1.8f * u))
                        drawCircle(Color(0xFFFFF176), 0.14f * u, Offset(lx, y - 1.85f * u))
                    }
                }
                else -> { // গ্রামবাংলা: গাছ ও কুঁড়েঘর
                    if (hv % 5 == 0 && hh % 2 == 0) {
                        val bw = 1.1f * u
                        val bh = 0.7f * u
                        drawRect(Color(0xFFD7B98E), Offset(x - bw / 2, y - bh), Size(bw, bh))
                        val roof = Path().apply {
                            moveTo(x - bw * 0.65f, y - bh)
                            lineTo(x + bw * 0.65f, y - bh)
                            lineTo(x, y - bh - 0.6f * u)
                            close()
                        }
                        drawPath(roof, Color(0xFF8D6E63))
                    } else {
                        drawRect(Color(0xFF5D4037), Offset(x - 0.06f * u, y - 0.7f * u), Size(0.12f * u, 0.7f * u))
                        drawCircle(Color(0xFF2E7D32), 0.55f * u, Offset(x, y - 1.1f * u))
                        drawCircle(Color(0xFF388E3C), 0.35f * u, Offset(x - 0.2f * u, y - 1.25f * u))
                    }
                }
            }
        }
    }
}

// ---------------- বাধা ----------------
private fun DrawScope.drawObstacle(cam: Cam, o: Obstacle) {
    val p = o.y
    if (p <= 0.02f) return
    val u = cam.u(p)
    val x = cam.cx(o.lane.toFloat(), p)
    val y = cam.sy(p)
    when (o.kind) {
        0 -> { // গর্ত
            drawOval(Color(0xFF1B1B1B), Offset(x - 0.45f * u, y - 0.12f * u), Size(0.9f * u, 0.24f * u))
            drawOval(Color(0xFF0D0D0D), Offset(x - 0.3f * u, y - 0.08f * u), Size(0.6f * u, 0.16f * u))
        }
        1 -> { // সিএনজি
            drawRect(Color(0xFF2E7D32), Offset(x - 0.4f * u, y - 0.75f * u), Size(0.8f * u, 0.55f * u))
            drawRect(Color(0xFF1B5E20), Offset(x - 0.35f * u, y - 0.95f * u), Size(0.7f * u, 0.2f * u))
            drawRect(Color(0xFFB3E5FC), Offset(x - 0.28f * u, y - 0.7f * u), Size(0.56f * u, 0.22f * u))
            drawRect(Color(0xFFFFEB3B), Offset(x - 0.4f * u, y - 0.4f * u), Size(0.8f * u, 0.06f * u))
            drawCircle(Color(0xFF111111), 0.12f * u, Offset(x - 0.28f * u, y - 0.1f * u))
            drawCircle(Color(0xFF111111), 0.12f * u, Offset(x + 0.28f * u, y - 0.1f * u))
        }
        2 -> { // ঝুলন্ত তার
            drawRect(Color(0xFF616161), Offset(x - 0.52f * u, y - 1.6f * u), Size(0.07f * u, 1.6f * u))
            drawRect(Color(0xFF616161), Offset(x + 0.45f * u, y - 1.6f * u), Size(0.07f * u, 1.6f * u))
            drawRect(Color(0xFFB71C1C), Offset(x - 0.5f * u, y - 1.0f * u), Size(1.0f * u, 0.18f * u))
            for (k in 0..4) {
                drawRect(
                    Color(0xFF212121),
                    Offset(x - 0.5f * u + k * 0.2f * u, y - 1.0f * u),
                    Size(0.08f * u, 0.18f * u)
                )
            }
        }
        3 -> { // রিকশা
            val body = Color(rickshawColors[o.seed % rickshawColors.size])
            drawRect(body, Offset(x - 0.38f * u, y - 0.7f * u), Size(0.76f * u, 0.4f * u))
            drawRect(Color(0xFF212121), Offset(x - 0.38f * u, y - 1.1f * u), Size(0.76f * u, 0.4f * u))
            drawRect(Color(0xFFFFD54F), Offset(x - 0.38f * u, y - 0.74f * u), Size(0.76f * u, 0.05f * u))
            drawCircle(Color(0xFF111111), 0.2f * u, Offset(x - 0.3f * u, y - 0.2f * u))
            drawCircle(Color(0xFF111111), 0.2f * u, Offset(x + 0.3f * u, y - 0.2f * u))
            drawCircle(Color(0xFFBDBDBD), 0.07f * u, Offset(x - 0.3f * u, y - 0.2f * u))
            drawCircle(Color(0xFFBDBDBD), 0.07f * u, Offset(x + 0.3f * u, y - 0.2f * u))
        }
        else -> { // বাস
            val body = Color(busColors[o.seed % busColors.size])
            drawRect(body, Offset(x - 0.45f * u, y - 1.8f * u), Size(0.9f * u, 1.6f * u))
            drawRect(Color(0xFFB3E5FC), Offset(x - 0.38f * u, y - 1.65f * u), Size(0.76f * u, 0.5f * u))
            drawRect(Color(0xCCFFFFFF), Offset(x - 0.45f * u, y - 0.95f * u), Size(0.9f * u, 0.16f * u))
            drawRect(Color(0xFF212121), Offset(x - 0.45f * u, y - 0.32f * u), Size(0.9f * u, 0.12f * u))
            drawCircle(Color(0xFFFFF59D), 0.08f * u, Offset(x - 0.32f * u, y - 0.5f * u))
            drawCircle(Color(0xFFFFF59D), 0.08f * u, Offset(x + 0.32f * u, y - 0.5f * u))
            drawCircle(Color(0xFF111111), 0.15f * u, Offset(x - 0.35f * u, y - 0.12f * u))
            drawCircle(Color(0xFF111111), 0.15f * u, Offset(x + 0.35f * u, y - 0.12f * u))
        }
    }
}

private fun DrawScope.drawCoin(cam: Cam, c: Coin) {
    val p = c.y
    if (p <= 0.02f || c.taken) return
    val u = cam.u(p)
    val x = cam.cx(c.lane.toFloat(), p)
    val y = cam.sy(p) - 0.45f * u
    drawCircle(Color(0xFFFFB300), 0.17f * u, Offset(x, y))
    drawCircle(Color(0xFFFFE082), 0.11f * u, Offset(x, y))
}

private fun DrawScope.drawPickup(cam: Cam, pk: Pickup) {
    val p = pk.y
    if (p <= 0.02f || pk.taken) return
    val u = cam.u(p)
    val x = cam.cx(pk.lane.toFloat(), p)
    val y = cam.sy(p) - 0.6f * u
    val col = if (pk.kind == 0) Color(0xFF1E88E5) else Color(0xFF43A047)
    drawCircle(col.copy(alpha = 0.3f), 0.38f * u, Offset(x, y))
    drawCircle(col, 0.26f * u, Offset(x, y))
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 0.3f * u
    paint.textAlign = Paint.Align.CENTER
    drawContext.canvas.nativeCanvas.drawText(if (pk.kind == 0) "M" else "S", x, y + 0.1f * u, paint)
}

// ---------------- খেলোয়াড় ----------------
private fun DrawScope.drawPlayer(cam: Cam, g: Game, skinColor: Long) {
    if (g.invuln > 0f && ((g.invuln * 12f).toInt() % 2 == 0)) return
    val p = Game.PLAYER_Y
    val u = cam.u(p)
    val x = cam.cx(g.laneX, p)
    val foot = cam.sy(p)

    val lift = if (g.jump > 0f) {
        sin(PI * (1.0 - g.jump / Game.JUMP_TIME)).toFloat() * 1.4f * u
    } else 0f

    // ছায়া
    val sh = 1f - min(0.5f, lift / (2.8f * u))
    drawOval(Color(0x44000000), Offset(x - 0.35f * u * sh, foot - 0.1f * u), Size(0.7f * u * sh, 0.2f * u))

    val skin = Color(0xFFC68642)
    val hair = Color(0xFF2B1B10)
    val body = Color(skinColor)
    val base = foot - lift

    if (g.slide > 0f) {
        // স্লাইড ভঙ্গি
        drawRect(body, Offset(x - 0.4f * u, base - 0.5f * u), Size(0.8f * u, 0.4f * u))
        drawRect(Color(0xFF1A237E), Offset(x - 0.4f * u, base - 0.18f * u), Size(0.8f * u, 0.14f * u))
        drawCircle(skin, 0.18f * u, Offset(x, base - 0.55f * u))
        drawArc(hair, 180f, 180f, true, Offset(x - 0.18f * u, base - 0.73f * u), Size(0.36f * u, 0.36f * u))
    } else {
        val swing = if (g.jump > 0f) 0f else sin(g.anim) * 0.25f * u
        // পা
        drawRect(Color(0xFF1A237E), Offset(x - 0.2f * u + swing, base - 0.45f * u), Size(0.16f * u, 0.45f * u))
        drawRect(Color(0xFF1A237E), Offset(x + 0.04f * u - swing, base - 0.45f * u), Size(0.16f * u, 0.45f * u))
        // শরীর
        drawRect(body, Offset(x - 0.23f * u, base - 0.97f * u), Size(0.46f * u, 0.55f * u))
        // হাত
        val arm = if (g.jump > 0f) 0.3f * u else swing
        drawRect(skin, Offset(x - 0.33f * u, base - 0.92f * u - arm * 0.5f), Size(0.1f * u, 0.4f * u))
        drawRect(skin, Offset(x + 0.23f * u, base - 0.92f * u - arm * 0.5f), Size(0.1f * u, 0.4f * u))
        // মাথা
        drawCircle(skin, 0.2f * u, Offset(x, base - 1.08f * u))
        drawArc(hair, 180f, 180f, true, Offset(x - 0.2f * u, base - 1.28f * u), Size(0.4f * u, 0.4f * u))
    }

    if (g.shield) {
        drawCircle(Color(0x4400E676), 0.75f * u, Offset(x, base - 0.6f * u))
        drawCircle(Color(0x8800E676), 0.75f * u, Offset(x, base - 0.6f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.05f * u))
    }
}

// ---------------- তাড়াকারী দোকানদার ----------------
private fun DrawScope.drawChaser(cam: Cam, g: Game) {
    val p = 1.05f
    val u = cam.u(p)
    val x = cam.cx(g.laneX, p) + sin(g.anim * 0.5f) * 0.04f * u
    val foot = cam.sy(p)
    val bob = sin(g.anim) * 0.05f * u
    // শরীর
    drawRect(Color(0xFFC62828), Offset(x - 0.5f * u, foot - 1.4f * u + bob), Size(1.0f * u, 0.95f * u))
    // হাত (উঁচু করা)
    drawRect(Color(0xFFC68642), Offset(x - 0.72f * u, foot - 1.7f * u + bob), Size(0.2f * u, 0.7f * u))
    drawRect(Color(0xFFC68642), Offset(x + 0.52f * u, foot - 1.7f * u + bob), Size(0.2f * u, 0.7f * u))
    // মাথা
    drawCircle(Color(0xFFC68642), 0.34f * u, Offset(x, foot - 1.75f * u + bob))
    drawArc(Color(0xFF212121), 180f, 180f, true, Offset(x - 0.34f * u, foot - 2.09f * u + bob), Size(0.68f * u, 0.68f * u))
    // রাগী চোখ ও মুখ
    drawCircle(Color.White, 0.07f * u, Offset(x - 0.13f * u, foot - 1.78f * u + bob))
    drawCircle(Color.White, 0.07f * u, Offset(x + 0.13f * u, foot - 1.78f * u + bob))
    drawCircle(Color.Black, 0.035f * u, Offset(x - 0.13f * u, foot - 1.78f * u + bob))
    drawCircle(Color.Black, 0.035f * u, Offset(x + 0.13f * u, foot - 1.78f * u + bob))
    drawRect(Color(0xFF212121), Offset(x - 0.2f * u, foot - 1.88f * u + bob), Size(0.14f * u, 0.04f * u))
    drawRect(Color(0xFF212121), Offset(x + 0.06f * u, foot - 1.88f * u + bob), Size(0.14f * u, 0.04f * u))
    drawRect(Color(0xFF5D0000), Offset(x - 0.14f * u, foot - 1.6f * u + bob), Size(0.28f * u, 0.1f * u))
}

// ---------------- HUD ----------------
private fun DrawScope.drawHud(g: Game, w: Float, h: Float, best: Int) {
    val nc = drawContext.canvas.nativeCanvas
    paint.textAlign = Paint.Align.LEFT
    paint.setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)

    paint.color = android.graphics.Color.WHITE
    paint.textSize = w * 0.07f
    nc.drawText("${g.score}", w * 0.04f, w * 0.1f, paint)

    paint.textSize = w * 0.035f
    paint.color = android.graphics.Color.rgb(255, 224, 130)
    val bestShown = max(best, g.score)
    nc.drawText("BEST $bestShown", w * 0.04f, w * 0.155f, paint)

    // কয়েন
    drawCircle(Color(0xFFFFB300), w * 0.022f, Offset(w * 0.055f, w * 0.215f))
    paint.color = android.graphics.Color.WHITE
    paint.textSize = w * 0.045f
    nc.drawText("${g.coins * g.coinValue}", w * 0.1f, w * 0.23f, paint)

    // স্ট্যাটাস
    var yy = w * 0.3f
    paint.textSize = w * 0.04f
    if (g.magnet > 0f) {
        paint.color = android.graphics.Color.rgb(100, 181, 246)
        nc.drawText("চুম্বক ${g.magnet.toInt() + 1}s", w * 0.04f, yy, paint)
        yy += w * 0.06f
    }
    if (g.shield) {
        paint.color = android.graphics.Color.rgb(129, 199, 132)
        nc.drawText("ঢাল চালু", w * 0.04f, yy, paint)
        yy += w * 0.06f
    }
    if (g.chase) {
        paint.color = android.graphics.Color.rgb(239, 83, 80)
        nc.drawText("তাড়া করছে! ${g.chaseTimer.toInt() + 1}s", w * 0.04f, yy, paint)
    }

    // বিরতির বোতাম
    val bx = w * 0.82f
    val by = w * 0.03f
    val bs = w * 0.14f
    drawRect(Color(0x66000000), Offset(bx, by), Size(bs, bs))
    drawRect(Color.White, Offset(bx + bs * 0.3f, by + bs * 0.25f), Size(bs * 0.14f, bs * 0.5f))
    drawRect(Color.White, Offset(bx + bs * 0.56f, by + bs * 0.25f), Size(bs * 0.14f, bs * 0.5f))

    // অঞ্চলের নাম
    if (g.bannerTimer > 0f) {
        val a = min(1f, g.bannerTimer)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = w * 0.11f
        paint.color = android.graphics.Color.WHITE
        paint.alpha = (a * 255).toInt()
        nc.drawText(Game.ZONE_NAMES[g.zone], w / 2f, h * 0.2f, paint)
        paint.alpha = 255
    }
    paint.clearShadowLayer()
}
