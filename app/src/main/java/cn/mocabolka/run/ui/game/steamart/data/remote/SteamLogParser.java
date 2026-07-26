package cn.mocabolka.run.ui.game.steamart.data.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android logcat 解析器：专门提取 Steam 美术提取链路的失败信号。
 *
 * 关注两类日志（与 {@code SteamAssetDownloader} / {@code SteamCdnAssets} 对应）：
 *  1) 下载失败 —— {@code SteamAssetDownloader} 报告的 HTTP 非 2xx（重点 HTTP 404）。
 *     形如：{@code W SteamAssetDownloader: 下载失败 LIBRARY_CAPSULE: HTTP 404}
 *  2) DNS 解析 —— 针对 {@code shared.akamai.steamstatic.com} 等 host 的 {@code DNS_CACHE} 解析过程，
 *     捕获 netd / DNS 结果码（形如 {@code result_code:408} 或 {@code result_code=408}）。
 *
 * 解析器纯正则实现，容忍标准 logcat 行结构：
 *   - threadtime 格式：{@code MM-DD HH:MM:SS.mmm  pid  tid  P  Tag: message}
 *   - brief 格式：{@code P/Tag: message}
 * 也兼容只含 {@code Tag: message} 的片段。
 *
 * 用法：
 *   List<SteamLogParser.ParsedEntry> entries = SteamLogParser.parse(logcatText);
 *   List<SteamLogParser.ParsedEntry> fails  = SteamLogParser.extractFailures(logcatText);
 */
public final class SteamLogParser {

    // ---- 正则 -------------------------------------------------------------

    /** 下载失败行：捕获资产种类与 HTTP 状态码。 */
    private static final Pattern DOWNLOAD_FAIL = Pattern.compile(
            "SteamAssetDownloader.*?下载失败\\s+(?<kind>[A-Za-z_]+)\\s*:?\\s*HTTP\\s+(?<code>\\d{3})",
            Pattern.CASE_INSENSITIVE);

    /** 从任意含 URL 的行里回收主机名（steamstatic 域）。 */
    private static final Pattern HOST_IN_URL = Pattern.compile(
            "https?://(?<host>[\\w.-]*steamstatic\\.com)[^\\s]*", Pattern.CASE_INSENSITIVE);

    /** DNS_CACHE / netd 解析行：捕获 steamstatic 主机名与 result_code。 */
    private static final Pattern DNS_CACHE = Pattern.compile(
            "(?:DNS_CACHE|netd|DnsResolver)[^\\n]*?(?<host>[\\w.-]*steamstatic\\.com)[^\\n]*?result_code[:=]?\\s*(?<rc>\\d+)",
            Pattern.CASE_INSENSITIVE);

    /** 宽松 result_code 提取（用于无显式 host 的兜底行）。 */
    private static final Pattern RESULT_CODE = Pattern.compile(
            "result_code[:=]?\\s*(?<rc>\\d+)", Pattern.CASE_INSENSITIVE);

    /** threadtime 行：MM-DD HH:MM:SS.mmm  pid  tid  P  Tag: msg */
    private static final Pattern LINE_THREADTIME = Pattern.compile(
            "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+\\d+\\s+\\w\\s+(\\S+):\\s+(.*)$");

    /** brief 行：P/Tag: msg */
    private static final Pattern LINE_BRIEF = Pattern.compile(
            "^\\w/(\\S+):\\s+(.*)$");

    // ---- 模型 -------------------------------------------------------------

    public enum EntryType { DOWNLOAD_FAILURE, DNS_RESOLUTION, UNKNOWN }

    /** 单条日志的解析结果模型。 */
    public static final class ParsedEntry {
        public final EntryType type;
        public final String rawLine;
        public final String tag;
        /** 解析到的主机名（steamstatic 域），无则 null。 */
        public final String hostname;
        /** HTTP 状态码，-1 表示本行无。 */
        public final int httpStatusCode;
        /** DNS 结果码（如 408），-1 表示本行无。 */
        public final int dnsResultCode;
        /** 下载失败时的资产种类（如 LIBRARY_CAPSULE），无则 null。 */
        public final String assetKind;
        /** 是否捕获到错误/失败状态（非 2xx 或 DNS 异常码）。 */
        public final boolean failure;

        ParsedEntry(EntryType type, String rawLine, String tag, String hostname,
                    int httpStatusCode, int dnsResultCode, String assetKind, boolean failure) {
            this.type = type;
            this.rawLine = rawLine;
            this.tag = tag;
            this.hostname = hostname;
            this.httpStatusCode = httpStatusCode;
            this.dnsResultCode = dnsResultCode;
            this.assetKind = assetKind;
            this.failure = failure;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ParsedEntry{type=").append(type);
            if (hostname != null) sb.append(", host=").append(hostname);
            if (httpStatusCode >= 0) sb.append(", http=").append(httpStatusCode);
            if (dnsResultCode >= 0) sb.append(", dnsRc=").append(dnsResultCode);
            if (assetKind != null) sb.append(", kind=").append(assetKind);
            sb.append(", failure=").append(failure).append('}');
            return sb.toString();
        }
    }

    // ---- 公共 API ---------------------------------------------------------

    /** 解析单行 logcat；无关行返回 null。 */
    public static ParsedEntry parseLine(String line) {
        if (line == null) return null;
        String tag = extractTag(line);
        String body = extractBody(line, tag);

        // 1) 下载失败
        Matcher df = DOWNLOAD_FAIL.matcher(body);
        if (df.find()) {
            int code = Integer.parseInt(df.group("code"));
            String kind = df.group("kind");
            String host = firstGroup(HOST_IN_URL, body, "host");
            boolean fail = code < 200 || code > 299;
            return new ParsedEntry(EntryType.DOWNLOAD_FAILURE, line, tag, host,
                    code, -1, kind, fail);
        }

        // 2) DNS 解析
        Matcher dns = DNS_CACHE.matcher(body);
        if (dns.find()) {
            String host = dns.group("host");
            int rc = Integer.parseInt(dns.group("rc"));
            // DNS 结果码非 0（如 408 超时）视为失败
            boolean fail = rc != 0;
            return new ParsedEntry(EntryType.DNS_RESOLUTION, line, tag, host,
                    -1, rc, null, fail);
        }

        return null;
    }

    /** 解析整段 logcat，仅返回相关条目。 */
    public static List<ParsedEntry> parse(String logcat) {
        List<ParsedEntry> out = new ArrayList<>();
        if (logcat == null) return out;
        for (String line : logcat.split("\\R")) {
            ParsedEntry e = parseLine(line);
            if (e != null) out.add(e);
        }
        return out;
    }

    /** 仅提取「失败」条目（下载非 2xx 或 DNS 异常码）。 */
    public static List<ParsedEntry> extractFailures(String logcat) {
        List<ParsedEntry> out = new ArrayList<>();
        for (ParsedEntry e : parse(logcat)) {
            if (e.failure) out.add(e);
        }
        return out;
    }

    /** 生成人类可读的汇总（含失败计数与按主机分组的 404 统计）。 */
    public static String summarize(String logcat) {
        List<ParsedEntry> all = parse(logcat);
        List<ParsedEntry> fails = extractFailures(logcat);
        StringBuilder sb = new StringBuilder();
        sb.append("解析条目: ").append(all.size())
          .append(", 失败条目: ").append(fails.size()).append('\n');
        int http404 = 0;
        int dnsFail = 0;
        for (ParsedEntry e : fails) {
            if (e.type == EntryType.DOWNLOAD_FAILURE && e.httpStatusCode == 404) http404++;
            if (e.type == EntryType.DNS_RESOLUTION) dnsFail++;
        }
        sb.append("  HTTP 404 下载失败: ").append(http404).append('\n');
        sb.append("  DNS 解析失败: ").append(dnsFail).append('\n');
        if (!fails.isEmpty()) {
            sb.append("  失败明细:\n");
            for (ParsedEntry e : fails) {
                sb.append("    - ").append(e).append('\n');
            }
        }
        return sb.toString();
    }

    // ---- 内部工具 ---------------------------------------------------------

    private static String extractTag(String line) {
        Matcher m = LINE_THREADTIME.matcher(line);
        if (m.matches()) return m.group(1);
        m = LINE_BRIEF.matcher(line);
        if (m.matches()) return m.group(1);
        return "";
    }

    private static String extractBody(String line, String tag) {
        if (!tag.isEmpty() && line.contains(tag + ":")) {
            int i = line.indexOf(tag + ":");
            return line.substring(i + tag.length() + 1).trim();
        }
        return line;
    }

    private static String firstGroup(Pattern p, String text, String name) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(name) : null;
    }

    private SteamLogParser() {}
}
