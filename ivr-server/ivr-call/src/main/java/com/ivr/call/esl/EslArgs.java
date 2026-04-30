package com.ivr.call.esl;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * FreeSWITCH ESL 命令参数消毒。
 *
 * <p>FS API 命令是空格分隔；TTS 用 `say::lang|engine|text` 是管道分隔。如果用户给的文本
 * 含 `\n`、`|`、控制字符，会破坏分隔语义。转接号码若含空格 / 特殊字符，会被解释成额外参数。
 * 调用前先过这两道闸：
 * <ul>
 *   <li>{@link #sanitizeText} —— 把控制字符 / 管道符替换成空格，避免 broadcast 参数错位。</li>
 *   <li>{@link #sanitizeTarget} —— 白名单仅允许电话 / SIP URI 常见字符；不符合返回 {@code null}，
 *       调用方应跳过命令并 log warn。</li>
 * </ul>
 */
final class EslArgs {

    /** SIP / PSTN 号码与基本 user@host 形式。 */
    private static final Pattern TARGET_PATTERN = Pattern.compile("[A-Za-z0-9+_*.@:-]+");
    private static final Pattern CONTROL_OR_PIPE = Pattern.compile("[\\p{Cntrl}|]");

    private EslArgs() {
    }

    static String sanitizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return CONTROL_OR_PIPE.matcher(raw).replaceAll(" ").trim();
    }

    /** 返回 null 表示非法 target，调用方应该拒绝命令。 */
    static String sanitizeTarget(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return TARGET_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }
}
