package com.ivr.ai;

/**
 * 语音合成 & 语音识别服务（Sprint 3 填充）。
 *
 * <p>对接阿里云 NLS（一句话识别 + 长文本合成）或讯飞 WebSocket 实时识别。
 * 当前仅占位接口，Sprint 2 阶段用 FreeSWITCH mod_tts_commandline 简化走通链路。
 */
public interface TtsAsrService {

    /** 文本 → 音频文件路径，返回服务器本地路径供 FreeSWITCH playback 播放 */
    String synthesize(String text, String voice);

    /** 识别音频为文本（一句话识别） */
    String recognize(byte[] audio, String format);
}
