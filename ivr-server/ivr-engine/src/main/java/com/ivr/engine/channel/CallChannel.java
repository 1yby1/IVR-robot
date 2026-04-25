package com.ivr.engine.channel;

/**
 * 通话信道控制抽象。封装 FreeSWITCH ESL 命令，让节点 handler 不直接依赖 ivr-call 模块。
 *
 * <p>默认实现 {@link LoggingCallChannel} 仅打印；下个迭代由 ivr-call 提供 FreeSWITCH 实现。
 */
public interface CallChannel {

    /** 播放语音：text 非空走 TTS，audioUrl 非空播音频文件，二者择一。 */
    void playback(String callUuid, PlaybackRequest request);

    /**
     * 启动 DTMF 收集。返回后立即让流程引擎暂停；ESL DTMF 事件由
     * {@code GatewayCallService.onDtmf} 喂回 executor。
     */
    void collectDtmf(String callUuid, DtmfCollectRequest request);

    /** 转人工 / 转外部号码（FreeSWITCH bridge 或 transfer）。 */
    void transfer(String callUuid, String target);

    /** 录制留言。 */
    void record(String callUuid, RecordRequest request);

    /** 主动挂机。 */
    void hangup(String callUuid, String reason);

    record PlaybackRequest(String text, String audioUrl, String voice) {}

    record DtmfCollectRequest(int maxDigits, int timeoutSeconds, String terminator) {}

    record RecordRequest(int maxSeconds, String filePath) {}
}
