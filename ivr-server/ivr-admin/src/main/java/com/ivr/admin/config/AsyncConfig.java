package com.ivr.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池。
 *
 * <p>{@code ivrAsrExecutor}：从 FreeSWITCH ESL 事件线程接到 RECORD_STOP 后，把"读音频文件 +
 * 调远程 ASR + 推进流程"丢到这里执行，避免阻塞 ESL 事件吞吐。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "ivrAsrExecutor", destroyMethod = "shutdown")
    public Executor ivrAsrExecutor(
            @Value("${ivr.freeswitch.asr-executor.core-pool-size:4}") int corePoolSize,
            @Value("${ivr.freeswitch.asr-executor.max-pool-size:16}") int maxPoolSize,
            @Value("${ivr.freeswitch.asr-executor.queue-capacity:128}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ivr-asr-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
