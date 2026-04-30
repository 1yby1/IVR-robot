package com.ivr.engine.channel;

/**
 * 通话信道控制抽象。封装 FreeSWITCH ESL 命令，让节点 handler 不直接依赖 ivr-call 模块。
 *
 * <p>默认实现 {@link LoggingCallChannel} 仅打印；下个迭代由 ivr-call 提供 FreeSWITCH 实现。
 */
public interface CallChannel {

    /** 接听电话。真实 FreeSWITCH 实现会发送 uuid_answer；日志实现只记录动作。 */
    void answer(String callUuid);

    /** 播放语音：text 非空走 TTS，audioUrl 非空播音频文件，二者择一。 */
    void playback(String callUuid, PlaybackRequest request);

    /**
     * 启动 DTMF 收集。返回后立即让流程引擎暂停；ESL DTMF 事件由
     * {@code GatewayCallService.onDtmf} 喂回 executor。
     */
    void collectDtmf(String callUuid, DtmfCollectRequest request);

    /**
     * 启动一句话语音识别（录音 + ASR）。返回后立即让流程引擎暂停；
     * 上层组件（FreeSwitchCallChannel + TtsAsrService）拿到识别结果后调
     * {@code FlowExecutor.resumeWithAsr(callUuid, text)} 喂回。
     */
    void collectAsr(String callUuid, AsrCollectRequest request);

    /** 转人工 / 转外部号码（FreeSWITCH bridge 或 transfer）。 */
    void transfer(String callUuid, String target);

    /** 录制留言。 */
    void record(String callUuid, RecordRequest request);

    /** 主动挂机。 */
    void hangup(String callUuid, String reason);

    record PlaybackRequest(String text, String audioUrl, String voice) {}

    record DtmfCollectRequest(int maxDigits, int timeoutSeconds, String terminator) {}

    record AsrCollectRequest(int maxSeconds, String language, String prompt) {}

    record RecordRequest(int maxSeconds, String filePath) {}
}
