package com.uptime.monitor.scheduling;

import com.uptime.monitor.config.UptimeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** Bounded pool and queue: a slow site can't exhaust threads or memory. */
    @Bean
    ThreadPoolTaskExecutor checkExecutor(UptimeProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.workerThreads());
        executor.setMaxPoolSize(props.workerThreads());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix("check-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
