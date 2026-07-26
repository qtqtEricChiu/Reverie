package cn.mocabolka.run.ui.game.steamart.domain

/**
 * 最近一次美术提取的计时报告（仅在 debug 构建中输出，release 构建不依赖此类运行）。
 *
 * 探针路径主链：start → onPageFinished → firstLink → extractFired → select → download → done
 * 兜底路径：start → store → download → done
 * 缺失的 phase 在 report 中自动跳过。
 *
 * 时间单位：纳秒（System.nanoTime）。毫秒分辨率在「缓存命中 / OkHttp 磁盘缓存秒回」场景下会把所有
 * mark 压进同一毫秒导致阶段耗时全 0；纳秒可区分，调试报告才能看清真正卡点。
 */
object ExtractionTiming {

    private var t0 = 0L
    private val events = linkedMapOf<String, Long>() // 按插入序 + 去重（相同 label 仅记首次）

    /** 总耗时（ns）；null 表示尚未 markDone / markAborted。 */
    var totalElapsedMs: Long? = null
        private set

    /** 是否异常中止（错误分支调用 markAborted）。用于区分「进行中」与「已错误结束」。 */
    var aborted: Boolean = false
        private set

    // ── 探针轮询 ──
    /** 已执行轮询轮数。 */
    var pollRounds: Int = 0; private set
    /** 首链接到达（elapsed ns）。 */
    var firstLinkElapsedMs: Long? = null; private set
    /** 轮询被停止（elapsed ns，= 首链接上报时间）。 */
    var pollStoppedElapsedMs: Long? = null; private set
    /** 是否到达 MAX_POLLS 上限（30 轮未拿到链接）。 */
    var reachedMaxPolls: Boolean = false; private set
    /** 检测到 Cloudflare 挑战（elapsed ns）。 */
    var cfDetectedElapsedMs: Long? = null; private set

    // ── 防抖 ──
    /** 防抖被重置次数（链接陆续到达导致每次新批次重置）。 */
    var debounceResets: Int = 0; private set
    /** 提取触发（elapsed ns）。 */
    var extractFiredElapsedMs: Long? = null; private set
    /** 是否由硬上限触发（vs debounce 正常到期）。 */
    var settledByCap: Boolean? = null; private set

    // ── 商店兜底 ──
    /** 跳过商店 API（探针路径已有全部资源）。 */
    var storeSkipped: Boolean? = null; private set
    /** 商店 API 耗时（ns，仅 needsStore=true 时有值）。 */
    var storeCalledMs: Long? = null; private set

    /**
     * 单阶段分段数据。
     */
    data class Phase(
        val label: String,          // 阶段中文名
        val durMs: Long,            // 耗时（ns）
        val note: String = ""       // 额外说明
    )

    /**
     * 一次提取的完整计时报告。
     */
    data class Report(
        val totalMs: Long,          // 总耗时（ns）
        val phases: List<Phase>,
        val pollRounds: Int,
        val firstLinkElapsedMs: Long?,
        val pollStoppedElapsedMs: Long?,
        val debounceResets: Int,
        val settledByCap: Boolean?,
        val extractFiredElapsedMs: Long?,
        val reachedMaxPolls: Boolean,
        val cfDetectedElapsedMs: Long?,
        val storeSkipped: Boolean?,
        val storeCalledMs: Long?,
        /** 自动识别的瓶颈/循环卡点描述。 */
        val bottleneck: String,
        /** 事件原始时间戳列表（ns，用于 debug 展示详细）。 */
        val rawEvents: List<Pair<String, Long>>,
        /** 是否已完成（markDone 已调用）；false 表示提取进行中或已异常中止。 */
        val completed: Boolean = false,
        /** 是否异常中止（markAborted 已调用）；用于区分「进行中」与「错误结束」。 */
        val aborted: Boolean = false
    )

    fun reset() {
        t0 = System.nanoTime()
        events.clear()
        totalElapsedMs = null
        aborted = false
        pollRounds = 0
        firstLinkElapsedMs = null
        pollStoppedElapsedMs = null
        reachedMaxPolls = false
        cfDetectedElapsedMs = null
        debounceResets = 0
        extractFiredElapsedMs = null
        settledByCap = null
        storeSkipped = null
        storeCalledMs = null
    }

    /** 当前已流逝纳秒数。 */
    fun elapsed() = if (t0 == 0L) 0L else System.nanoTime() - t0

    /** 记录一个里程碑事件（首次同名 label 生效，后续忽略）。 */
    fun mark(label: String) {
        if (!events.containsKey(label)) {
            events[label] = elapsed()
        }
    }

    /** 记录防抖重置次数 +1。 */
    fun incDebounceResets() { debounceResets++ }

    /** 记录一次探针轮询（在 CfWebViewClient 轮询 runnable 中调用）。 */
    fun recordPollRound() { pollRounds++ }

    /** 到达 MAX_POLLS 上限。 */
    fun markMaxPolls() { reachedMaxPolls = true; mark("maxPolls") }

    /** 首链接到达。 */
    fun markFirstLink() {
        if (firstLinkElapsedMs == null) {
            val e = elapsed()
            firstLinkElapsedMs = e
            pollStoppedElapsedMs = e // 首链接到达即触发 stopPolling
            mark("firstLink")
        }
    }

    /** CF 检测到。 */
    fun markCfDetected() {
        if (cfDetectedElapsedMs == null) {
            cfDetectedElapsedMs = elapsed()
            mark("cfDetected")
        }
    }

    /** 提取定稿触发。 */
    fun markExtractFired(byCap: Boolean) {
        if (extractFiredElapsedMs == null) {
            extractFiredElapsedMs = elapsed()
            settledByCap = byCap
            mark("extractFired")
        }
    }

    /** 商店 API 调用开始/结束。 */
    fun markStoreStart() { mark("storeStart") }
    fun markStoreEnd() {
        mark("storeEnd")
        storeCalledMs = (events["storeEnd"] ?: 0L) - (events["storeStart"] ?: 0L)
    }
    fun markStoreSkipped() { storeSkipped = true; mark("storeSkipped") }

    /** 提取完成。 */
    fun markDone() {
        totalElapsedMs = elapsed()
        mark("done")
    }

    /** 提取异常中止（错误分支）：冻结总耗时但标记未完成，使 debug 报告完整闭合
     *  （区分于「进行中」——Report.completed = totalElapsedMs != null && !aborted）。 */
    fun markAborted() {
        if (totalElapsedMs == null) totalElapsedMs = elapsed()
        aborted = true
        mark("error")
    }

    /** 构建最终报告。 */
    fun report(): Report {
        val e = events.toMap() // 快照

        val phases = mutableListOf<Phase>()

        fun segment(startLbl: String, endLbl: String, label: String, note: String = "") {
            val s = e[startLbl] ?: return
            val ed = e[endLbl] ?: return
            phases.add(Phase(label, (ed - s).coerceAtLeast(0), note))
        }

        segment("start", "onPageFinished", "页面加载")
        // 探针轮询段：onPageFinished 到 firstLink（存在 firstLink 说明探针拿到了链接）
        if (e.containsKey("firstLink") && e.containsKey("onPageFinished")) {
            val dur = (e["firstLink"]!! - e["onPageFinished"]!!).coerceAtLeast(0)
            val note = "共 ${pollRounds} 轮轮询 → 第 ${firstLinkRound()} 轮拿到"
            phases.add(Phase("探针轮询等待首链接", dur, note))
        }
        // 防抖等待段：firstLink 到 extractFired
        if (e.containsKey("extractFired") && e.containsKey("firstLink")) {
            val dur = (e["extractFired"]!! - e["firstLink"]!!).coerceAtLeast(0)
            val trigger = if (settledByCap == true) "硬上限" else "稳定到期"
            val note = "防抖重置 ${debounceResets} 次 · 触发方式：$trigger"
            phases.add(Phase("防抖等待定稿", dur, note))
        }
        segment("selectStart", "selectEnd", "AssetSelector 选择")
        segment("downloadStart", "downloadEnd", "下载")

        // 商店兜底段
        if (storeSkipped == true) {
            phases.add(Phase("商店 API 兜底", 0, "跳过（探针已有全部资源）"))
        } else if (e.containsKey("storeEnd") && e.containsKey("storeStart")) {
            val dur = (e["storeEnd"]!! - e["storeStart"]!!).coerceAtLeast(0)
            phases.add(Phase("商店 API 兜底", dur, "超时上限 15s"))
        }

        segment("storeEnd", "done", "落盘装配") // storeEnd 若不存在则跳过

        // ── 提取完成之后的 Activity 关闭段（用户体感"下载后等几秒才回游戏页"的根因诊断） ──
        segment("done", "uiReceivedDone", "提取完成→UI 收到")
        segment("uiReceivedDone", "onDoneFired", "UI→Activity 关闭（含 300ms 缓冲 / Error 2000ms）")

        // 总耗时：已完成/已中止用冻结时刻；进行中用实时 elapsed（避免全部 mark 同毫秒时总耗时=0）
        val total = totalElapsedMs ?: e["done"] ?: (if (t0 != 0L) elapsed() else 0L)
        val completed = totalElapsedMs != null && !aborted

        // ── 瓶颈自动识别 ──
        val bottleneck = buildString {
            if (firstLinkElapsedMs == null && reachedMaxPolls) {
                append("⚠ 探针轮询循环卡死：${pollRounds} 轮未拿到任何链接，已到 MAX_POLLS 上限；")
            }
            if (firstLinkElapsedMs == null && cfDetectedElapsedMs != null) {
                append("⚠ Cloudflare 拦截：探针持续轮询但 CF 阻挡，已检测到 CF 信号；")
            }
            val debouncePhase = phases.firstOrNull { it.label == "防抖等待定稿" }
            if (debouncePhase != null && debouncePhase.durMs > 2_000_000_000L) {
                append("⚠ 防抖等待过长（${debouncePhase.durMs.nsFmt()}）：链接持续陆续到达推后计时器 ${debounceResets} 次；")
            }
            val maxPhase = phases.filter { it.label != "落盘装配" && it.label != "AssetSelector 选择" }
                .maxByOrNull { it.durMs }
            if (maxPhase != null && appendPhase(maxPhase)) {
                append("⏱ 最慢阶段「${maxPhase.label}」= ${maxPhase.durMs.nsFmt()}")
            }
            if (isEmpty()) append("正常")
        }

        return Report(
            totalMs = total,
            phases = phases,
            pollRounds = pollRounds,
            firstLinkElapsedMs = firstLinkElapsedMs,
            pollStoppedElapsedMs = pollStoppedElapsedMs,
            debounceResets = debounceResets,
            settledByCap = settledByCap,
            extractFiredElapsedMs = extractFiredElapsedMs,
            reachedMaxPolls = reachedMaxPolls,
            cfDetectedElapsedMs = cfDetectedElapsedMs,
            storeSkipped = storeSkipped,
            storeCalledMs = storeCalledMs,
            bottleneck = bottleneck,
            rawEvents = events.toList(),
            completed = completed,
            aborted = aborted
        )
    }

    private fun firstLinkRound(): Int {
        // 探针轮询每秒 1 轮，首链接 = 轮询开始后的第 (firstLink_ns/1e9) 轮左右
        val ns = firstLinkElapsedMs ?: return pollRounds
        val pageFinishNs = events["onPageFinished"] ?: 0L
        val waitNs = (ns - pageFinishNs).coerceAtLeast(0)
        return (waitNs / 1_000_000_000L + 1).toInt().coerceAtMost(pollRounds)
    }

    private fun appendPhase(maxPhase: Phase) =
        !maxPhase.note.contains("跳过")
}

/** 纳秒自适应格式化（ns / µs / ms / s）。 */
internal fun Long.nsFmt(): String = when {
    this < 1000L -> "${this}ns"
    this < 1_000_000L -> "${this / 1000L}µs"
    this < 1_000_000_000L -> "${"%.1f".format(this / 1_000_000f)}ms"
    else -> "${"%.2f".format(this / 1_000_000_000f)}s"
}
