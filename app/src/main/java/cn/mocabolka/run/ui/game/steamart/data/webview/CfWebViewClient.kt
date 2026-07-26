package cn.mocabolka.run.ui.game.steamart.data.webview

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import cn.mocabolka.run.ui.game.steamart.domain.ExtractionTiming

/**
 * SteamDB info 页 WebView 客户端。
 *
 * 采用与 `Run-SteamdbDemo` 验证可用的原始方案（2026-07-25 回滚恢复）：
 *  - `getJs()` 探针**对整页 `document.documentElement.outerHTML` 做正则扫描**抓取所有 steamstatic 资产链接，
 *    并额外扫描 `<img>` 的 src/data-src/srcset、内联 `style` 与 `<style>/<link>` 样式表中的
 *    `background-image: url(...)`、`<link rel=image_src>`，以及 `<script type="application/json">`/`__NEXT_DATA__` 中的
 *    logo_position —— 覆盖首页横幅、库背景、Metadata 标签页的 `<a href>` 链接列表等所有真实资产，
 *   不漏抓、不挑页面结构。同时解析 logo_position、游戏名，并处理成人内容/锁区门控。
 *  - `onPageFinished` 后每秒重跑一次探针（最多 [MAX_POLLS] 次），直到命中链接/logo/游戏名并通过
 *    `AndroidBridge` 回调上报；链接一旦上报，由 Activity 侧的 `bridge.onLinks` 触发停止轮询 + 中止多余图片加载。
 *
 * 这是经过实测「能力高、问题少」的方案，不追求所谓「轻量化」（轻量探针会漏抓整页链接）。
 */
class CfWebViewClient : WebViewClient() {

    /**
     * 顶层导航安全策略：仅允许 steamdb.info 域内的导航（对齐 SearchWebViewClient）。
     * 防止 CF 挑战页 / 注入链接 / 重定向把提取 WebView 带离 SteamDB 到任意外部站点
     * （此前未重写 → 默认行为放行任意顶层跳转，存在安全缺口）。域内 info 页跳转照常放行。
     */
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        return !SteamWebViewPolicy.isAllowedTopLevelNavigation(url)
    }

    /** 最大轮询次数（每秒一次，约 30s），避免无限占用 WebView。 */
    private val MAX_POLLS = 30

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        runCatching { view?.evaluateJavascript(getJs(), null) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        runCatching { view?.evaluateJavascript(getJs(), null) }
        // 持续轮询：页面可能异步加载，JavaScript 探针每秒重跑一次，
        // 命中链接 / logo_position / 游戏名后通过 AndroidBridge 回调上报。
        // evaluateJavascript 结果经回调返回，不能用于判断"是否空"；maxWait 由 ViewModel 兜底，
        // 此处仅以固定次数上限自调度，避免轮询永不停止。
        ExtractionTiming.mark("onPageFinished")
        startPolling(view)
    }

    /**
     * JavaScript 探针：扫描 steamstatic 图片链接 + 解析 logo_position + 读取游戏名。
     *
     * 增强（提升稳定性）：
     *  - 同时匹配 `http(s)://` 与协议相对的 `//...steamstatic.com`（SteamDB 的 CSS 常用 `//`）。
     *  - 除 outerHTML 文本外，额外扫描 `<img>` 的 src / data-src / srcset，
     *    以及内联 `style` 与 `<style>/<link>` 样式表中的 `background-image: url(...)`，
     *    从而捕获首页横幅、库背景等常被懒加载或仅存在于 CSS 的资产（尤其是 page_bg_raw）。
     *  - 仅在确有链接时上报 onLinks；否则若命中 cf 验证则上报 onChallenge。
     */
    private fun getJs(): String = """
        (function(){
          try {
            var urls = {};
            // 内容提示 / 锁区阻断：SteamDB 对特定游戏默认隐藏全部美术资源，
            // 需点击确认按钮后才注入真实资产链接（library_hero / library_header / hero_capsule /
            // page_bg_raw / logo 等）。
            // 策略：移除"每次轮询都点击"的激进做法，改为「仅检测 + 仅当资源不足时兜底点击」：
            //  - detectAdultGate()：仅判断阻断按钮是否仍存在，不点击。
            //  - revealAdultContent()：真正执行点击（Load All Assets 类解锁按钮，作为兜底逻辑保留）。
            //  - assetsInsufficient()：判断已扫到的关键门控资产是否不足。
            // 仅当「阻断仍在 且 关键资产不足」时才调用 revealAdultContent()，避免无谓重复点击。
            function detectAdultGate(){
              try {
                if (document.querySelector('#agecheck, .agecheck, form[action*="agecheck"]')) return true;
                var btns = document.querySelectorAll('button, a, input[type="submit"], [role="button"]');
                for (var i=0;i<btns.length;i++){
                  var el = btns[i];
              var txt = (el.innerText || el.getAttribute('title') || el.getAttribute('aria-label') || el.value || '').toString().toLowerCase();
              // 匹配内容提示/年龄验证/锁区阻断按钮文本（所有游戏都可能出现，非仅特定类型游戏）
              if (/(load\s*(all\s*)?(asset|image|media)s?|age\s*check|i\s*am\s*over\s*18|i'?m\s*18|confirm\s*(my\s*)?age|view\s*adult|show\s*(all\s*)?(media|assets?|adult)|mature|nsfw)/i.test(txt)) {
                    return true;
                  }
                }
              } catch(e){}
              return false;
            }
            function revealAdultContent(){
              try {
                // SteamDB 特定：#agecheck 容器内的确认按钮
                var age = document.querySelector('#agecheck, .agecheck, form[action*="agecheck"]');
                if (age) {
                  var ab = age.querySelector('button, input[type="submit"], a, .btn');
                  if (ab) { ab.click(); return true; }
                }
                // 通用：文本强匹配成人 / 年龄确认 / 「Load all asset images」类按钮
                var btns = document.querySelectorAll('button, a, input[type="submit"], [role="button"]');
                for (var i=0;i<btns.length;i++){
                  var el = btns[i];
              var txt = (el.innerText || el.getAttribute('title') || el.getAttribute('aria-label') || el.value || '').toString().toLowerCase();
              // 匹配内容提示/年龄验证/锁区阻断按钮文本（所有游戏都可能出现，非仅特定类型游戏）
              if (/(load\s*(all\s*)?(asset|image|media)s?|age\s*check|i\s*am\s*over\s*18|i'?m\s*18|confirm\s*(my\s*)?age|view\s*adult|show\s*(all\s*)?(media|assets?|adult)|mature|nsfw)/i.test(txt)) {
                    el.click();
                    return true;
                  }
                }
              } catch(e){}
              return false;
            }
            function assetsInsufficient(list){
              // 被内容提示阻断隐藏的关键资产：缺任一即视为"资源不足"，需要兜底点击解锁
              var gated = ['library_hero', 'library_header', 'hero_capsule', 'page_bg_raw', 'logo_'];
              for (var i=0;i<gated.length;i++){
                var kw = gated[i], hit = false;
                for (var j=0;j<list.length;j++){ if (list[j].indexOf(kw) >= 0){ hit = true; break; } }
                if (!hit) return true;
              }
              return false;
            }
            function isAssetHost(u){
              // 接受全部 Steam 官方 CDN 域名：
              //  - steamstatic.com（含 shared.fastly.steamstatic.com / shared.akamai.steamstatic.com 等子域）
              //  - steamusercontent.com（社区头像/logo）、steamsecured.com（商店资源）
              return /steamstatic\.com|steamusercontent\.com|steamsecured\.com/.test(u);
            }
            function add(u){
              if(!u) return;
              if(u.indexOf('//')===0) u='https:'+u;
              if(u.indexOf('http')!==0) return;
              if(!isAssetHost(u)) return;
              urls[u]=1;
            }

            // ── Plan B / 更强壮探针：直接解析 SteamDB info 页 table.web-assets 结构 ──
            // 用户从 SteamDB 源代码确认资产以 <tr><td.span3>分组</td><td><table.web-assets>…</table></td></tr>
            // 渲染，每个资产按 语言(english/schinese/…) 与 分辨率(1x/2x) 列出真实哈希 URL。
            // 解析该结构，为所需的 6+ 张各选 1 个最优链接（语言 schinese>tchinese>english>…；分辨率 2x>1x）。
            function parseSteamDbAssets() {
              var buckets = {};
              function put(assetKey, lang, variant, url) {
                if (!assetKey || !url) return;
                if (!buckets[assetKey]) buckets[assetKey] = {};
                buckets[assetKey][lang + '_' + variant] = url;
              }
              var rows = document.querySelectorAll('tr');
              for (var ri = 0; ri < rows.length; ri++) {
                var tr = rows[ri];
                var keyTd = tr.querySelector(':scope > td.span3');
                if (!keyTd) continue;
                var group = (keyTd.textContent || '').trim().toLowerCase();
                var nested = tr.querySelector(':scope > td > table.web-assets');
                if (!nested) continue;
                var currentSub = null;
                var subRows = nested.querySelectorAll('tr');
                for (var si = 0; si < subRows.length; si++) {
                  var r = subRows[si];
                  if (r.classList.contains('web-assets-hr')) {
                    var h = (r.querySelector('td') ? r.querySelector('td').textContent : '') || '';
                    currentSub = h.replace(/[^a-z0-9_]/gi, '').toLowerCase();
                    continue;
                  }
                  var tds = r.querySelectorAll(':scope > td');
                  if (tds.length < 1) continue;
                  var first = (tds[0].textContent || '').trim();
                  var a = r.querySelector('a[href]');
                  if (!a) continue;
                  var url = a.href;
                  if (!/steamstatic\.com/i.test(url)) continue;
                  var lang = 'english', variant = 1;
                  var lm = first.match(/(image2x|image)\/([a-z0-9]+)/i);
                  if (lm) {
                    if (/2x/i.test(first)) variant = 2;
                    lang = (lm[2] || 'english').toLowerCase();
                  } else {
                    var lm2 = first.match(/^([a-z0-9]+)$/i);
                    if (lm2) lang = lm2[1].toLowerCase();
                  }
                  var assetKey;
                  if (group === 'header_image') {
                    assetKey = 'header';
                  } else if (group === 'assets') {
                    var f0 = first.replace(/_2x$/i, '').toLowerCase();
                    if (f0 === 'raw_page_background') assetKey = 'page_bg_raw';
                    else if (f0 === 'page_background') assetKey = 'page_bg';
                    else if (f0 === 'small_capsule') assetKey = 'capsule_small';
                    else if (f0 === 'main_capsule') assetKey = 'main_capsule';
                    else if (f0 === 'hero_capsule') assetKey = 'hero_capsule';
                    else if (f0 === 'library_capsule') assetKey = 'library_capsule';
                    else if (f0 === 'library_hero') assetKey = 'library_hero';
                    else if (f0 === 'header') assetKey = 'header';
                    else if (f0 === 'community_icon') assetKey = 'community_icon';
                    else assetKey = f0;
                  } else {
                    if (!currentSub) continue;
                    var sub = currentSub.replace(/_2x$/i, '');
                    assetKey = (sub === 'library_logo') ? 'logo' : sub;
                  }
                  put(assetKey, lang, variant, url);
                }
              }
              return buckets;
            }

            // 为所需资产按 语言顺序 × 分辨率(2x>1x) 各选 1 个最优链接，并附带诊断报告。
            function pickSteamDbAssets(buckets) {
              var needed = ['library_hero','library_capsule','library_header','logo','hero_capsule','capsule_small','header','page_bg_raw'];
              var order = ['schinese','tchinese','english','japanese','koreana','korean','french','german','spanish','russian','brazilian'];
              var urlsOut = [], report = [];
              for (var ni = 0; ni < needed.length; ni++) {
                var asset = needed[ni];
                var b = buckets[asset];
                if (!b) continue;
                var bestUrl = null, bestKey = null;
                for (var li = 0; li < order.length && !bestUrl; li++) {
                  for (var v = 2; v >= 1; v--) {
                    var key = order[li] + '_' + v;
                    if (b[key]) { bestUrl = b[key]; bestKey = key; break; }
                  }
                }
                if (!bestUrl) {
                  for (var k in b) { if (b.hasOwnProperty(k)) { bestUrl = b[k]; bestKey = k; break; } }
                }
                if (bestUrl) {
                  urlsOut.push(bestUrl);
                  report.push({asset: asset, url: bestUrl, picked: bestKey, options: Object.keys(b).length});
                }
              }
              return {urls: urlsOut, report: report};
            }
            var html = document.documentElement.outerHTML || '';
            var re = /(?:https:)?\/\/[a-z0-9.\-]*steamstatic\.com\/[^\s"'()]+\.(?:jpg|jpeg|png|webp)/gi;
            var found = html.match(re) || [];
            for (var i=0;i<found.length;i++){ add(found[i].replace(/&amp;/g,'&')); }
            // <img> src / data-src / srcset
            try {
              document.querySelectorAll('img').forEach(function(img){
                add(img.getAttribute('src'));
                add(img.getAttribute('data-src'));
                var ss = img.getAttribute('srcset');
                if(ss){ ss.split(',').forEach(function(p){ add(p.trim().split(/\s+/)[0]); }); }
              });
            } catch(e){}
            // 内联 style 的 background-image
            try {
              document.querySelectorAll('[style]').forEach(function(el){
                var s = el.getAttribute('style') || '';
                var r = /url\(\s*['"]?([^'")]+)['"]?\s*\)/gi, mm;
                while((mm = r.exec(s))) add(mm[1]);
              });
            } catch(e){}
            // <style> / <link> 样式表
            try {
              for (var i=0;i<document.styleSheets.length;i++){
                var rules = document.styleSheets[i].cssRules;
                if(!rules) continue;
                for (var j=0;j<rules.length;j++){
                  var css = rules[j].cssText || '';
                  var r2 = /url\(\s*['"]?([^'")]+)['"]?\s*\)/gi, mm2;
                  while((mm2 = r2.exec(css))) add(mm2[1]);
                }
              }
            } catch(e){}
            // <link rel=image_src>
            try {
              document.querySelectorAll('link[rel="image_src"]').forEach(function(l){ add(l.getAttribute('href')); });
            } catch(e){}

            var list = Object.keys(urls);

            // ── Plan B / 更强壮探针：直接解析 SteamDB info 页 table.web-assets 结构 ──
            // 为所需的 6+ 张各选 1 个最优链接（语言 schinese>tchinese>english…；分辨率 2x>1x），
            // 与扁平扫描结果合并回传（去重交给 AssetSelector）。即便扁平扫描漏抓，结构化解析也能稳拿真实链接。
            var structuredReport = [];
            var structuredUrls = [];
            try {
              var _buckets = parseSteamDbAssets();
              var _pick = pickSteamDbAssets(_buckets);
              structuredReport = _pick.report;
              structuredUrls = _pick.urls;
              if (structuredUrls.length) {
                try { AndroidBridge.onStructuredAssets(JSON.stringify(structuredReport)); } catch(e){}
              }
            } catch(e) {}

            var merged = structuredUrls.concat(list);
            var _seen = {};
            merged = merged.filter(function(u){ if(_seen[u]) return false; _seen[u]=1; return true; });
            if (merged.length > 0) {
              AndroidBridge.onLinks(JSON.stringify(merged));
            } else {
              // CF 检测分级（正式版增强：推翻"challenge-platform 专用"的旧误判，demo 原方案为单一复选框版）：
              //  · 强信号（立即短路 onChallenge）：挑战页与通过页互斥标记
              //      - 文本："just a moment" / "checking your browser"
              //      - DOM：#cf-content / .cf-browser-verification / #challenge-stage / form#challenge-form
              //  · 弱信号 challenge-platform 脚本：下移——仅当「扫完零链接」时才判，
              //    已拿到链接则绝不报 CF（正常页也含该脚本，误报会卡住正常提取）；
              //    绝不纳入 /cdn-cgi/、[data-ray]（常规资源路径，误报率极高）。
              var cf = false;
              try {
                var _body = (document.body ? document.body.innerText : '') || '';
                var _title = document.title || '';
                var _strong = /just a moment/i.test(_title) || /checking your browser/i.test(_body)
                  || document.querySelector('#cf-content, .cf-browser-verification, #challenge-stage, form#challenge-form');
                if (_strong) {
                  cf = true;
                } else {
                  var _weak = document.querySelector('script[src*="challenge-platform"]');
                  if (_weak) cf = true;
                }
              } catch(e) {}
              if (cf) AndroidBridge.onChallenge();
            }

            // 仅上报是否仍存在阻断按钮（不直接点击，移除"每次轮询都点"的激进策略）
            var gate = false;
            try { gate = detectAdultGate(); } catch(e){}
            try { AndroidBridge.onAdultGate(gate); } catch(e){}

            // 兜底：仅当阻断仍在 且 已抓到的关键门控资产不足时，才点击解锁按钮
            // （Load All Assets 点击逻辑保留为兜底，避免无谓重复点击）
            if (gate) {
              var insufficient = true;
              try { insufficient = assetsInsufficient(list); } catch(e){}
              if (insufficient) { try { revealAdultContent(); } catch(e){} }
            }

            // logo_position 解析
            try {
              function parseLogoPosition(block){
                if(!block) return null;
                var pin = (block.match(/pinned_position["']?\s*:\s*["']?\s*([^"',}\]]+)/) || [])[1];
                var w = parseFloat((block.match(/width_pct["']?\s*:\s*([0-9.]+%?)/) || [])[1]);
                var h = parseFloat((block.match(/height_pct["']?\s*:\s*([0-9.]+%?)/) || [])[1]);
                if(!pin) return null;
                return {pinned_position: pin, width_pct: w || 0, height_pct: h || 0};
              }
              var lp = null;
              // 1)【优先级第一：SteamDB 侧边栏/信息表格解析】（2026-07-25 提级 + 方案 C 裸键支持）
              //    依据：info 页可见 <tr><td>KEY</td><td>VALUE</td></tr> 表格是最可靠的证据源
              //    （商店 API 对锁区游戏可能不可达，不能依赖其兜底 logo_position）。
              //    覆盖三种 KEY 形态：带 "logo_position/" 前缀、裸键（pinned_position 等）、th 表头。
              //    按行扫描（同一行内 KEY→下一单元格 VALUE），兼容单元格内嵌套元素（textContent 拉平）。
              if (!lp) {
                try {
                  var rows = document.querySelectorAll('tr');
                  var _pin = null, _w = 0, _h = 0;
                  for (var ri = 0; ri < rows.length; ri++) {
                    var cells = rows[ri].querySelectorAll('td, th');
                    if (cells.length < 2) continue;
                    for (var ci = 0; ci + 1 < cells.length; ci++) {
                      var k = (cells[ci].textContent || '').toString().toLowerCase()
                                .replace(/logo_position[\/ ]?/g, '').trim();
                      var v = (cells[ci + 1].textContent || '').toString().trim();
                      if (!v) continue;
                      // 裸键匹配：不要求前缀，仅要求键名以目标字段结尾（防长句误伤）
                      if (/(^|\/|\s)pinned_position$/.test(k) && /^[A-Za-z_]+$/.test(v)) { _pin = v; }
                      else if (/(^|\/|\s)width_pct$/.test(k)) { _w = parseFloat(v) || _w; }
                      else if (/(^|\/|\s)height_pct$/.test(k)) { _h = parseFloat(v) || _h; }
                    }
                  }
                  if (_pin) lp = {pinned_position: _pin, width_pct: _w || 0, height_pct: _h || 0};
                } catch(e){}
              }
              // 1.5) 兜底扫描：不按行、直接遍历所有 td/th 取 nextElementSibling
              //      （防某些渲染分支 KEY/VALUE 不在同一 <tr> 的 cells 序列里）
              if (!lp) {
                try {
                  var tds = document.querySelectorAll('td, th');
                  var _pin2 = null, _w2 = 0, _h2 = 0;
                  for (var ti = 0; ti < tds.length; ti++) {
                    var cellKey = (tds[ti].textContent || '').toString().toLowerCase().replace(/logo_position[\/ ]?/g, '').trim();
                    var valCell = tds[ti].nextElementSibling;
                    var cellVal = (valCell ? (valCell.textContent || '') : '').toString().trim();
                    if (!cellVal) continue;
                    if (/pinned_position/.test(cellKey) && /^[A-Za-z_]+$/.test(cellVal)) { _pin2 = cellVal; }
                    else if (/width_pct/.test(cellKey)) { _w2 = parseFloat(cellVal) || _w2; }
                    else if (/height_pct/.test(cellKey)) { _h2 = parseFloat(cellVal) || _h2; }
                  }
                  if (_pin2) lp = {pinned_position: _pin2, width_pct: _w2 || 0, height_pct: _h2 || 0};
                } catch(e){}
              }
              // 2) 直接内联对象： logo_position: { ... }
              if (!lp) {
                var m2 = html.match(/logo_position["']?\s*:\s*\{[^}]*\}/);
                if (m2) lp = parseLogoPosition(m2[0]);
              }
              // 3) 平铺键写法（DevTools 常显示为 logo_position/pinned_position 等）：
              //    兼容 "pinned_position":"BottomLeft" 出现在任意位置的鲁棒扫描
              if (!lp) {
                var pin = (html.match(/["']?pinned_position["']?\s*:\s*["']?\s*([^"',}\]]+)/) || [])[1];
                if (pin) {
                  var w2 = parseFloat((html.match(/["']?width_pct["']?\s*:\s*([0-9.]+%?)/) || [])[1]);
                  var h2 = parseFloat((html.match(/["']?height_pct["']?\s*:\s*([0-9.]+%?)/) || [])[1]);
                  lp = {pinned_position: pin, width_pct: w2 || 0, height_pct: h2 || 0};
                }
              }
              // 4) 全局 JS 变量兜底（Next.js __NEXT_DATA__ / window.* 上的 JSON 对象）
              if (!lp) {
                try {
                  var scripts = document.querySelectorAll('script[type="application/json"], script#__NEXT_DATA__');
                  for (var s=0; s<scripts.length && !lp; s++){
                    lp = parseLogoPosition(scripts[s].textContent || '');
                  }
                } catch(e){}
                if (!lp) {
                  try {
                    var g = JSON.stringify(window);
                    lp = parseLogoPosition(g);
                  } catch(e){}
                }
              }
              if (lp) AndroidBridge.onLogoPosition(JSON.stringify(lp));
            } catch(e) {}

            // 游戏名
            try {
              var t = ((document.querySelector('h1') || {}).innerText) || ((document.querySelector('title') || {}).innerText) || document.title || '';
              AndroidBridge.onAppName((t || '').trim());
            } catch(e) {}
          } catch(e) {}
          return 'ok';
        })();
        """.trimIndent()

    // ---------- 轮询（链接 / logo / 游戏名 补全）----------

    private fun startPolling(view: WebView?) {
        stopPolling()
        var polls = 0
        pollRunnable = object : Runnable {
            override fun run() {
                if (polls >= MAX_POLLS) { ExtractionTiming.markMaxPolls(); pollRunnable = null; return }
                polls++
                ExtractionTiming.recordPollRound()
                runCatching { view?.evaluateJavascript(getJs(), null) }
                pollRunnable = this
                mainHandler.postDelayed(this, 1000)
            }
        }
        mainHandler.postDelayed(pollRunnable!!, 1000)
    }

    fun stopPolling() {
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
    }

    /** 恢复轮询（二次 CF 回卷后由 Activity 调用，重新持续扫描真实页面）。 */
    fun resumePolling(view: WebView?) = startPolling(view)

    /** 「重试」时立即重跑探针（logo/链接/应用名/CF 门控）。 */
    fun reprobe(view: WebView?) {
        runCatching { view?.evaluateJavascript(getJs(), null) }
    }
}
