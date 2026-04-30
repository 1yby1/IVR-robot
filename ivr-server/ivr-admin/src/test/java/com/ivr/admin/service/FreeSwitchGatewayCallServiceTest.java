package com.ivr.admin.service;

import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.entity.IvrHotline;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.admin.mapper.IvrHotlineMapper;
import com.ivr.ai.TtsAsrService;
import com.ivr.engine.FlowExecutor;
import com.ivr.engine.session.FlowSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreeSwitchGatewayCallServiceTest {

    private IvrHotlineMapper hotlineMapper;
    private IvrFlowMapper flowMapper;
    private FlowExecutor flowExecutor;
    private CallRecordService callRecordService;
    private TtsAsrService ttsAsrService;
    private Executor asrExecutor;

    @TempDir
    Path recordingDir;

    @BeforeEach
    void setUp() {
        hotlineMapper = mock(IvrHotlineMapper.class);
        flowMapper = mock(IvrFlowMapper.class);
        flowExecutor = mock(FlowExecutor.class);
        callRecordService = mock(CallRecordService.class);
        ttsAsrService = mock(TtsAsrService.class);
        // 同步执行器：让单测里的"丢线程池"等价于直接跑，断言 ASR 调用到位即可
        asrExecutor = Runnable::run;
    }

    private FreeSwitchGatewayCallService newService(long maxBytes) {
        return new FreeSwitchGatewayCallService(
                hotlineMapper, flowMapper, flowExecutor, callRecordService, ttsAsrService,
                asrExecutor, recordingDir.toString(), maxBytes);
    }

    private void stubAsrWaiting(String callUuid) {
        FlowSession session = new FlowSession();
        session.setSessionId(callUuid);
        session.setStatus(FlowSession.STATUS_WAITING);
        session.setWaitingFor("asr");
        when(flowExecutor.sessionOf(callUuid)).thenReturn(session);
    }

    @Test
    void onRecordStop_recognizesFileInsideRecordingDir() throws Exception {
        Path file = Files.write(recordingDir.resolve("call-1.wav"), new byte[]{1, 2, 3});
        FreeSwitchGatewayCallService service = newService(32L * 1024 * 1024);
        stubAsrWaiting("call-1");
        when(ttsAsrService.recognize(any(), eq("wav"))).thenReturn("我想查账单");

        service.onRecordStop("call-1", file.toString());

        verify(ttsAsrService, times(1)).recognize(any(), eq("wav"));
        verify(flowExecutor, times(1)).resumeWithAsr(eq("call-1"), eq("我想查账单"));
    }

    @Test
    void onRecordStop_rejectsFileOutsideRecordingDir() throws Exception {
        Path outside = Files.createTempFile("outside-", ".wav");
        try {
            Files.write(outside, new byte[]{1, 2, 3});
            FreeSwitchGatewayCallService service = newService(32L * 1024 * 1024);
            stubAsrWaiting("call-2");

            service.onRecordStop("call-2", outside.toString());

            verify(ttsAsrService, never()).recognize(any(), anyString());
            // 路径被拒后仍会喂 resumeWithAsr 空文本，让流程走 fallback
            verify(flowExecutor, times(1)).resumeWithAsr(eq("call-2"), eq(""));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void onRecordStop_rejectsOversizedFile() throws Exception {
        Path big = Files.write(recordingDir.resolve("big.wav"), new byte[64]);
        FreeSwitchGatewayCallService service = newService(32);

        stubAsrWaiting("call-3");
        service.onRecordStop("call-3", big.toString());

        verify(ttsAsrService, never()).recognize(any(), anyString());
        verify(flowExecutor, times(1)).resumeWithAsr(eq("call-3"), eq(""));
    }

    @Test
    void onRecordStop_skipsWhenNotWaitingForAsr() {
        FreeSwitchGatewayCallService service = newService(32L * 1024 * 1024);
        FlowSession session = new FlowSession();
        session.setStatus(FlowSession.STATUS_WAITING);
        session.setWaitingFor("dtmf");
        when(flowExecutor.sessionOf("call-4")).thenReturn(session);

        service.onRecordStop("call-4", recordingDir.resolve("any.wav").toString());

        verify(ttsAsrService, never()).recognize(any(), anyString());
        verify(flowExecutor, never()).resumeWithAsr(anyString(), anyString());
    }

    @Test
    void onHangup_unknownCallLogsWithoutThrowing() {
        FreeSwitchGatewayCallService service = newService(32L * 1024 * 1024);
        when(callRecordService.tryFinishCall(eq("ghost"), anyString(), anyString())).thenReturn(false);

        service.onHangup("ghost", "NORMAL_CLEARING");

        verify(callRecordService).tryFinishCall("ghost", "completed", "");
    }

    @Test
    void onInboundCall_truncatesLongCallerCallee() {
        FreeSwitchGatewayCallService service = newService(32L * 1024 * 1024);
        IvrHotline hotline = new IvrHotline();
        hotline.setId(1L);
        hotline.setHotline("4001");
        hotline.setFlowId(2L);
        when(hotlineMapper.selectOne(any())).thenReturn(hotline);

        IvrFlow flow = new IvrFlow();
        flow.setId(2L);
        flow.setStatus(1);
        flow.setDeleted(0);
        flow.setCurrentVersion(1);
        flow.setFlowCode("c");
        flow.setFlowName("n");
        when(flowMapper.selectById(2L)).thenReturn(flow);

        String longCaller = "1".repeat(64);
        service.onInboundCall("uuid-x", longCaller, "4001");

        // 32 字符上限被强制
        verify(callRecordService).startCall(eq("uuid-x"), eq("1".repeat(32)), eq("4001"), eq(2L), eq(1));
    }
}
