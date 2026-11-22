package com.dhakarun.game

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlin.reflect.KProperty

class Skin(val name: String, val cost: Int, val color: Long)

val SKINS = listOf(
    Skin("সবুজ", 0, 0xFF2E8B57),
    Skin("নীল", 200, 0xFF1E88E5),
    Skin("লাল", 400, 0xFFE53935),
    Skin("বেগুনি", 800, 0xFF8E24AA),
    Skin("সোনালি", 1500, 0xFFFFC107)
)

/** সব সেভ ডেটা (SharedPreferences) */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("dhakarun_v2", Context.MODE_PRIVATE)

    private inner class IntPref(val key: String, val def: Int = 0) {
        operator fun getValue(thisRef: Any?, prop: KProperty<*>): Int = p.getInt(key, def)
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, v: Int) {
            p.edit().putInt(key, v).apply()
        }
    }

    var coins by IntPref("coins")
    var best by IntPref("best")
    var totalCoins by IntPref("total_coins")
    var totalPowerups by IntPref("total_powerups")
    var games by IntPref("games")
    var magnetLvl by IntPref("magnet_lvl")
    var shieldLvl by IntPref("shield_lvl")
    var coinLvl by IntPref("coin_lvl")
    var skin by IntPref("skin")
    var skinMask by IntPref("skin_mask", 1)
    private var soundInt by IntPref("sound", 1)

    var sound: Boolean
        get() = soundInt == 1
        set(v) {
            soundInt = if (v) 1 else 0
        }

    fun missionLvl(i: Int): Int = p.getInt("mission_$i", 0)
    fun setMissionLvl(i: Int, v: Int) {
        p.edit().putInt("mission_$i", v).apply()
    }

    fun ownsSkin(i: Int): Boolean = (skinMask shr i) and 1 == 1
    fun unlockSkin(i: Int) {
        skinMask = skinMask or (1 shl i)
    }

    // ---------- মিশন ----------
    private val missionBase = intArrayOf(100, 2000, 10)

    fun missionTarget(i: Int): Int = missionBase[i] * (missionLvl(i) + 1)
    fun missionValue(i: Int): Int = when (i) {
        0 -> totalCoins
        1 -> best
        else -> totalPowerups
    }

    fun missionName(i: Int): String {
        val t = missionTarget(i)
        return when (i) {
            0 -> "মোট ${t}টি কয়েন কুড়ান"
            1 -> "এক রানে $t স্কোর করুন"
            else -> "${t}টি পাওয়ার-আপ নিন"
        }
    }

    fun missionReward(i: Int): Int = 100 * (missionLvl(i) + 1)
}

/** সাউন্ড: কোনো ফাইল ছাড়া ToneGenerator দিয়ে */
class Sfx {
    var enabled = true
    private var tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    } catch (e: Throwable) {
        null
    }

    fun play(ev: Int) {
        if (!enabled) return
        try {
            when (ev) {
                Ev.COIN -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                Ev.POWER -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 160)
                Ev.HIT -> tone?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
                Ev.DIE -> tone?.startTone(ToneGenerator.TONE_PROP_NACK, 450)
                Ev.JUMP -> tone?.startTone(ToneGenerator.TONE_DTMF_1, 40)
                Ev.ZONE -> tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
            }
        } catch (e: Throwable) {
        }
    }

    fun release() {
        try {
            tone?.release()
        } catch (e: Throwable) {
        }
        tone = null
    }
}
