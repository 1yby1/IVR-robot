package com.ivr.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * {@link TtsAsrService} 的占位实现：synthesize 返空字符串，recognize 返固定文本。
 *
 * <p>仅用于无真实云 TTS / ASR 接入时让链路能跑通。下个迭代接阿里云 NLS / 讯飞实时识别时
 * 提供新 bean，本类带 {@code @ConditionalOnMissingBean} 自动让位。
 */
@Component
@ConditionalOnMissingBean(TtsAsrService.class)
public class StubTtsAsrService implements TtsAsrService {

    private static final Logger log = LoggerFactory.getLogger(StubTtsAsrService.class);
    private static final String STUB_RECOGNITION = "[ASR 占位文本]";

    @Override
    public String synthesize(String text, String voice) {
        log.warn("[Stub TTS] synthesize called text=\"{}\" voice={} — returning empty path; configure a real TtsAsrService bean", text, voice);
        return "";
    }

    @Override
    public String recognize(byte[] audio, String format) {
        int size = audio == null ? 0 : audio.length;
        log.warn("[Stub ASR] recognize called bytes={} format={} — returning placeholder; configure a real TtsAsrService bean", size, format);
        return STUB_RECOGNITION;
    }
}
