package com.dhakarun.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs

class RunSummary(
    val score: Int,
    val newBest: Boolean,
    val earned: Int,
    val missions: List<String>
)

class AppState(val store: Store, val sfx: Sfx) {
    var screen by mutableStateOf("menu")
    var rev by mutableIntStateOf(0)
    var runId by mutableIntStateOf(0)

    fun bump() {
        rev++
    }

    fun startRun() {
        runId++
        screen = "game"
    }

    /** রান শেষে কয়েন, রেকর্ড আর মিশন হিসাব */
    fun commitRun(g: Game): RunSummary {
        val s = store
        val score = g.score
        val earned = g.coins * g.coinValue
        val newBest = score > s.best
        if (newBest) s.best = score
        s.coins = s.coins + earned
        s.totalCoins = s.totalCoins + g.coins
        s.totalPowerups = s.totalPowerups + g.powerups
        s.games = s.games + 1

        val msgs = ArrayList<String>()
        for (i in 0..2) {
            while (s.missionValue(i) >= s.missionTarget(i)) {
                val reward = s.missionReward(i)
                msgs.add("মিশন সম্পন্ন: ${s.missionName(i)} (+$reward কয়েন)")
                s.coins = s.coins + reward
                s.setMissionLvl(i, s.missionLvl(i) + 1)
            }
        }
        bump()
        return RunSummary(score, newBest, earned, msgs)
    }
}

private val bgBrush = Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF1B5E20)))

@Composable
fun Root(app: AppState) {
    when (app.screen) {
        "shop" -> ShopScreen(app)
        "game" -> GameScreen(app)
        else -> MenuScreen(app)
    }
    BackHandler(enabled = app.screen == "shop") { app.screen = "menu" }
}

// ======================= মেনু =======================
@Composable
fun MenuScreen(app: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = app.rev
    val s = app.store
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("ঢাকা রান", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text("Dhaka Run", color = Color(0xFFFFE082), fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("কয়েন: ${s.coins}", color = Color(0xFFFFD54F), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("সেরা: ${s.best}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))

        Button(onClick = { app.startRun() }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("খেলুন", fontSize = 22.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { app.screen = "shop" }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("দোকান ও আপগ্রেড", fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                s.sound = !s.sound
                app.sfx.enabled = s.sound
                app.bump()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (s.sound) "সাউন্ড: চালু" else "সাউন্ড: বন্ধ", fontSize = 18.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text("মিশন", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        for (i in 0..2) {
            val cur = s.missionValue(i)
            val tgt = s.missionTarget(i)
            Text(
                "${s.missionName(i)}  ($cur/$tgt)  +${s.missionReward(i)}",
                color = Color(0xFFE0E0E0),
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "বাঁ-ডানে সোয়াইপ: লেন বদল\nওপরে সোয়াইপ: লাফ (গর্ত ও সিএনজি)\nনিচে সোয়াইপ: স্লাইড (ঝুলন্ত তার)\nরিকশা ও বাস এড়াতে লেন বদলান\nনীল M = চুম্বক, সবুজ S = ঢাল",
            color = Color(0xFFB3E5FC),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ======================= দোকান =======================
@Composable
fun ShopScreen(app: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = app.rev
    val s = app.store
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("দোকান", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("কয়েন: ${s.coins}", color = Color(0xFFFFD54F), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("আপগ্রেড", color = Color(0xFFFFE082), fontSize = 20.sp, fontWeight = FontWeight.Bold)

        val ml = s.magnetLvl
        UpgradeRow(
            "চুম্বকের সময়",
            "এখন ${6 + 2 * ml} সেকেন্ড (লেভেল $ml/5)",
            if (ml < 5) 100 * (ml + 1) else null,
            s.coins
        ) {
            s.coins = s.coins - 100 * (ml + 1)
            s.magnetLvl = ml + 1
            app.bump()
        }

        val sl = s.shieldLvl
        UpgradeRow(
            "ঢাল দিয়ে শুরু",
            if (sl >= 1) "চালু: প্রতি রান ঢাল নিয়ে শুরু" else "বন্ধ",
            if (sl < 1) 300 else null,
            s.coins
        ) {
            s.coins = s.coins - 300
            s.shieldLvl = 1
            app.bump()
        }

        val cl = s.coinLvl
        UpgradeRow(
            "কয়েনের মান",
            "প্রতি কয়েন = ${1 + cl} (লেভেল $cl/4)",
            if (cl < 4) 200 * (cl + 1) else null,
            s.coins
        ) {
            s.coins = s.coins - 200 * (cl + 1)
            s.coinLvl = cl + 1
            app.bump()
        }

        Spacer(Modifier.height(20.dp))
        Text("পোশাকের রং", color = Color(0xFFFFE082), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        for (i in SKINS.indices) {
            val sk = SKINS[i]
            val owned = s.ownsSkin(i)
            val selected = s.skin == i
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(28.dp).background(Color(sk.color), CircleShape))
                Spacer(Modifier.size(12.dp))
                Text(
                    sk.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (owned) {
                            s.skin = i
                        } else if (s.coins >= sk.cost) {
                            s.coins = s.coins - sk.cost
                            s.unlockSkin(i)
                            s.skin = i
                        }
                        app.bump()
                    },
                    enabled = !selected && (owned || s.coins >= sk.cost)
                ) {
                    Text(
                        when {
                            selected -> "নির্বাচিত"
                            owned -> "ব্যবহার করুন"
                            else -> "কিনুন ${sk.cost}"
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { app.screen = "menu" }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("ফিরে যান", fontSize = 18.sp)
        }
    }
}

@Composable
private fun UpgradeRow(
    title: String,
    desc: String,
    cost: Int?,
    coins: Int,
    onBuy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = Color(0xFFE0E0E0), fontSize = 14.sp)
        }
        Button(onClick = onBuy, enabled = cost != null && coins >= cost) {
            Text(if (cost == null) "সর্বোচ্চ" else "$cost")
        }
    }
}

// ======================= গেম =======================
@Composable
fun GameScreen(app: AppState) {
    val g = remember(app.runId) {
        val s = app.store
        Game(
            magnetSeconds = 6f + 2f * s.magnetLvl,
            startShield = s.shieldLvl >= 1,
            coinValue = 1 + s.coinLvl
        ).also { game -> game.onEvent = { ev -> app.sfx.play(ev) } }
    }
    val skinColor = SKINS[app.store.skin.coerceIn(0, SKINS.size - 1)].color
    val best = remember(app.runId) { app.store.best }

    var tick by remember(g) { mutableIntStateOf(0) }
    var showPause by remember(g) { mutableStateOf(false) }
    var summary by remember(g) { mutableStateOf<RunSummary?>(null) }

    fun setPause(v: Boolean) {
        g.paused = v
        showPause = v
    }

    // গেম লুপ
    LaunchedEffect(g) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    g.update(dt)
                }
                last = now
                if (g.dead && summary == null) {
                    summary = app.commitRun(g)
                }
                tick++
            }
        }
    }

    // অ্যাপ ব্যাকগ্রাউন্ডে গেলে বিরতি
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, g) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE && !g.dead) setPause(true)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = true) {
        if (summary != null) app.screen = "menu" else setPause(!showPause)
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(g) {
                    var tx = 0f
                    var ty = 0f
                    var fired = false
                    detectDragGestures(
                        onDragStart = {
                            tx = 0f
                            ty = 0f
                            fired = false
                        },
                        onDrag = { change, d ->
                            change.consume()
                            if (!fired) {
                                tx += d.x
                                ty += d.y
                                val th = size.width * 0.04f
                                if (abs(tx) > th || abs(ty) > th) {
                                    fired = true
                                    if (abs(tx) > abs(ty)) {
                                        if (tx > 0f) g.moveRight() else g.moveLeft()
                                    } else {
                                        if (ty < 0f) g.doJump() else g.doSlide()
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(g) {
                    detectTapGestures { off ->
                        if (!g.dead && off.x > size.width * 0.78f && off.y < size.width * 0.22f) {
                            setPause(!g.paused)
                        }
                    }
                }
        ) {
            tick // প্রতি ফ্রেমে আবার আঁকার জন্য
            drawWorld(g, skinColor, best)
        }

        if (showPause && summary == null) {
            Overlay {
                Text("বিরতি", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { setPause(false) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("চালিয়ে যান", fontSize = 18.sp)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { app.startRun() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("নতুন করে শুরু", fontSize = 18.sp)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { app.screen = "menu" }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("মেনু", fontSize = 18.sp)
                }
            }
        }

        summary?.let { sm ->
            Overlay {
                Text("খেলা শেষ", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("স্কোর: ${sm.score}", color = Color.White, fontSize = 26.sp)
                if (sm.newBest) {
                    Text("নতুন রেকর্ড!", color = Color(0xFFFFD54F), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text("কয়েন পেলেন: +${sm.earned}", color = Color(0xFFFFD54F), fontSize = 20.sp)
                for (m in sm.missions) {
                    Spacer(Modifier.height(6.dp))
                    Text(m, color = Color(0xFFA5D6A7), fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { app.startRun() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("আবার খেলুন", fontSize = 18.sp)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { app.screen = "shop" }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("দোকান", fontSize = 18.sp)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { app.screen = "menu" }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("মেনু", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}
