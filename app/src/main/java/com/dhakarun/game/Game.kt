package com.dhakarun.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** গেমের ইভেন্ট (সাউন্ডের জন্য) */
object Ev {
    const val COIN = 0
    const val POWER = 1
    const val HIT = 2
    const val DIE = 3
    const val JUMP = 4
    const val ZONE = 5
}

/**
 * kind:
 * 0 = গর্ত (লাফ দিন), 1 = সিএনজি (লাফ দিন), 2 = ঝুলন্ত তার (স্লাইড করুন),
 * 3 = রিকশা (লেন বদলান), 4 = বাস (লেন বদলান)
 */
class Obstacle(val lane: Int, var y: Float, val kind: Int, val seed: Int) {
    val len: Float
        get() = when (kind) {
            0 -> 0.05f
            1 -> 0.09f
            2 -> 0.04f
            3 -> 0.11f
            else -> 0.22f
        }
}

class Coin(val lane: Int, var y: Float, var taken: Boolean = false)

/** kind: 0 = চুম্বক, 1 = ঢাল */
class Pickup(val lane: Int, var y: Float, val kind: Int, var taken: Boolean = false)

class Game(
    private val magnetSeconds: Float,
    startShield: Boolean,
    val coinValue: Int
) {
    companion object {
        const val PLAYER_Y = 0.8f
        const val JUMP_TIME = 0.6f
        const val SLIDE_TIME = 0.6f
        const val ZONE_LEN = 40f
        val ZONE_NAMES = arrayOf("গুলশান", "পুরান ঢাকা", "সদরঘাট", "গ্রামবাংলা")
    }

    var onEvent: (Int) -> Unit = {}

    var lane = 1
    var laneX = 1f
    var jump = 0f
    var slide = 0f
    var speed = 0.55f
    var dist = 0f
    var coins = 0
    var powerups = 0
    var dead = false
    var paused = false

    var magnet = 0f
    var shield = startShield
    var chase = false
    var chaseTimer = 0f
    var invuln = 0f

    var zone = 0
    var bannerTimer = 3f
    var anim = 0f

    private var spawnGap = 0.6f
    val obstacles = ArrayList<Obstacle>()
    val coinList = ArrayList<Coin>()
    val pickups = ArrayList<Pickup>()

    val score: Int get() = (dist * 100f).toInt() + coins * 10

    // ---------- ইনপুট ----------
    fun moveLeft() {
        if (!dead && !paused) lane = max(0, lane - 1)
    }

    fun moveRight() {
        if (!dead && !paused) lane = min(2, lane + 1)
    }

    fun doJump() {
        if (dead || paused) return
        if (jump <= 0f) {
            jump = JUMP_TIME
            slide = 0f
            onEvent(Ev.JUMP)
        }
    }

    fun doSlide() {
        if (dead || paused) return
        jump = 0f // শূন্য থেকে দ্রুত নামা
        slide = SLIDE_TIME
    }

    // ---------- আপডেট ----------
    fun update(dt: Float) {
        if (dead || paused) return

        anim += dt * (6f + speed * 10f)
        speed = min(1.3f, speed + dt * 0.012f)
        dist += speed * dt

        laneX += (lane - laneX) * min(1f, dt * 14f)
        if (jump > 0f) jump = max(0f, jump - dt)
        if (slide > 0f) slide = max(0f, slide - dt)
        if (magnet > 0f) magnet = max(0f, magnet - dt)
        if (invuln > 0f) invuln = max(0f, invuln - dt)
        if (bannerTimer > 0f) bannerTimer = max(0f, bannerTimer - dt)
        if (chase) {
            chaseTimer -= dt
            if (chaseTimer <= 0f) chase = false
        }

        val z = (dist / ZONE_LEN).toInt() % ZONE_NAMES.size
        if (z != zone) {
            zone = z
            bannerTimer = 3f
            onEvent(Ev.ZONE)
        }

        spawnGap -= speed * dt
        if (spawnGap <= 0f) spawnRow()

        val d = speed * dt
        for (o in obstacles) o.y += d
        for (c in coinList) c.y += d
        for (p in pickups) p.y += d
        obstacles.removeAll { it.y > 1.35f }
        coinList.removeAll { it.y > 1.35f || it.taken }
        pickups.removeAll { it.y > 1.35f || it.taken }

        // কয়েন
        val reach = if (magnet > 0f) 0.22f else 0.05f
        for (c in coinList) {
            if (c.taken) continue
            val near = abs(c.y - PLAYER_Y) < reach
            val sameLane = abs(c.lane - laneX) < 0.6f
            if (near && (magnet > 0f || sameLane)) {
                c.taken = true
                coins++
                onEvent(Ev.COIN)
            }
        }
        // পাওয়ার-আপ
        for (p in pickups) {
            if (p.taken) continue
            if (abs(p.lane - laneX) < 0.6f && abs(p.y - PLAYER_Y) < 0.06f) {
                p.taken = true
                powerups++
                if (p.kind == 0) magnet = magnetSeconds else shield = true
                onEvent(Ev.POWER)
            }
        }
        // সংঘর্ষ
        if (invuln <= 0f) {
            for (o in obstacles) {
                if (abs(o.lane - laneX) < 0.55f && abs(o.y - PLAYER_Y) < o.len / 2f + 0.025f) {
                    val safe = when (o.kind) {
                        0, 1 -> jump > 0f
                        2 -> slide > 0f
                        else -> false
                    }
                    if (!safe) {
                        onHit()
                        break
                    }
                }
            }
        }
    }

    private fun onHit() {
        invuln = 1.2f
        when {
            shield -> {
                shield = false
                onEvent(Ev.HIT)
            }
            !chase -> {
                chase = true
                chaseTimer = 10f
                onEvent(Ev.HIT)
            }
            else -> {
                dead = true
                onEvent(Ev.DIE)
            }
        }
    }

    private fun pickKind(): Int {
        val r = Random.nextInt(100)
        return when {
            r < 25 -> 0
            r < 50 -> 1
            r < 65 -> 2
            r < 85 -> 3
            else -> 4
        }
    }

    private fun spawnRow() {
        val lanes = mutableListOf(0, 1, 2)
        lanes.shuffle()
        val twoChance = 0.3f + min(0.25f, dist * 0.004f)
        val count = if (Random.nextFloat() < twoChance) 2 else 1
        var maxLen = 0f
        for (l in lanes.take(count)) {
            val o = Obstacle(l, -0.15f, pickKind(), Random.nextInt(1000))
            obstacles.add(o)
            maxLen = max(maxLen, o.len)
        }
        // অন্তত একটা লেন সবসময় ফাঁকা থাকে
        val free = lanes.drop(count)
        val fl = free[Random.nextInt(free.size)]
        val r = Random.nextFloat()
        if (r < 0.5f) {
            for (i in 0..4) coinList.add(Coin(fl, -0.15f - i * 0.07f))
        } else if (r < 0.62f) {
            pickups.add(Pickup(fl, -0.15f, Random.nextInt(2)))
        }
        spawnGap = 0.55f + Random.nextFloat() * 0.35f + maxLen
    }
}
