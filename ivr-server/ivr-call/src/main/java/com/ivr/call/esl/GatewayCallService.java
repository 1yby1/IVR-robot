package com.ivr.call.esl;

public interface GatewayCallService {

    void onInboundCall(String callUuid, String caller, String callee);

    void onAnswered(String callUuid);

    void onDtmf(String callUuid, String digit);

    void onHangup(String callUuid, String hangupCause);

    /**
     * 录音完成事件回调（FreeSWITCH RECORD_STOP）。
     *
     * <p>由 admin 实现：判断当前 session 是否处于 ASR 等待 → 调 ASR 识别 →
     * {@code FlowExecutor.resumeWithAsr}；否则视为 voicemail / record 节点的录音落地。
     *
     * @param recordFile FreeSWITCH 视角的录音文件绝对路径（容器/主机路径需通过共享 volume 对齐）
     */
    void onRecordStop(String callUuid, String recordFile);
}
