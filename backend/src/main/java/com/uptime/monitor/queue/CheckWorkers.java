package com.uptime.monitor.queue;

import com.uptime.monitor.config.UptimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;

/**
 * Stream consumers in the "checkers" group. Each consumer reads one message at a time,
 * runs the check and acks it. Any instance can run workers; Redis spreads the messages.
 */
@Component
@ConditionalOnProperty(name = "uptime.worker-enabled", havingValue = "true", matchIfMissing = true)
public class CheckWorkers implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(CheckWorkers.class);

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final CheckRunner runner;
    private final int consumers;
    /** Unique per process, so restarted instances don't inherit a dead consumer's name. */
    private final String instance = ManagementFactory.getRuntimeMXBean().getName();

    public CheckWorkers(RedisConnectionFactory connectionFactory, CheckRunner runner, UptimeProperties props,
                        CheckQueue queue) { // CheckQueue first, so the group exists before reading
        this.runner = runner;
        this.consumers = props.workerThreads();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(consumers);
        executor.setMaxPoolSize(consumers);
        executor.setQueueCapacity(consumers); // room for new polls while stopped ones finish their last poll
        executor.setThreadNamePrefix("check-worker-");
        executor.initialize();

        this.container = StreamMessageListenerContainer.create(connectionFactory,
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .batchSize(1)
                        .executor(executor)
                        .serializer(StringRedisSerializer.UTF_8)
                        .errorHandler(e -> log.warn("Check worker error: {}", e.toString()))
                        .build());
        registerConsumers();
    }

    /** Once: the container re-runs registered consumers on every start(). */
    private void registerConsumers() {
        for (int i = 0; i < consumers; i++) {
            container.register(
                    StreamReadRequest.builder(StreamOffset.create(CheckQueue.STREAM, ReadOffset.lastConsumed()))
                            .consumer(Consumer.from(CheckQueue.GROUP, instance + "-" + i))
                            .autoAcknowledge(false) // ack only after the result is stored
                            .cancelOnError(e -> false) // keep consuming after a failed check or a Redis blip
                            .build(),
                    runner::run);
        }
    }

    @Override
    public void start() {
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public boolean isRunning() {
        return container.isRunning();
    }
}
