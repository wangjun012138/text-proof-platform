package com.wangjun.text_proof_platform.modules.audit.service;

import com.wangjun.text_proof_platform.modules.audit.entity.AuditLog;
import com.wangjun.text_proof_platform.modules.audit.model.AuditEvent;
import com.wangjun.text_proof_platform.modules.audit.repository.AuditLogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuditLogAsyncService {

    /**
     * 消费者数量。
     *
     * 这里保留 2 个消费者线程，满足“多线程异步消费”的要求。
     */
    private static final int CONSUMER_COUNT = 2;

    /**
     * 每个分片队列的容量。
     *
     * 总容量约等于 CONSUMER_COUNT * QUEUE_CAPACITY_PER_PARTITION。
     */
    private static final int QUEUE_CAPACITY_PER_PARTITION = 500;

    /**
     * 停机时最多等待多久。
     */
    private static final long SHUTDOWN_WAIT_SECONDS = 30;

    /**
     * 分片队列。
     *
     * 不是 1 个队列给 2 个线程抢，而是 2 个队列分别对应 2 个消费者。
     * 同一个业务对象会被路由到同一个队列，从而保证同一对象的日志顺序。
     */
    @SuppressWarnings("unchecked")
    private final BlockingQueue<AuditEvent>[] auditQueues = new BlockingQueue[CONSUMER_COUNT];

    /**
     * 后台消费者线程池。
     */
    private final ExecutorService consumerPool = Executors.newFixedThreadPool(
            CONSUMER_COUNT,
            new ThreadFactory() {
                private int index = 0;

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("audit-log-worker-" + index++);
                    thread.setDaemon(false);
                    return thread;
                }
            }
    );

    /**
     * 是否继续接收新的审计事件。
     *
     * 应用关闭时先设置为 false，避免关闭过程中继续塞新日志。
     */
    private volatile boolean accepting = true;

    /**
     * 消费者是否继续运行。
     *
     * running=false 后，消费者不会立刻退出，
     * 而是继续把自己负责的队列消费完。
     */
    private volatile boolean running = true;

    private final AuditLogRepository auditLogRepository;

    public AuditLogAsyncService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;

        for (int i = 0; i < CONSUMER_COUNT; i++) {
            auditQueues[i] = new LinkedBlockingQueue<>(QUEUE_CAPACITY_PER_PARTITION);
        }
    }

    /**
     * Spring 容器启动后，启动 2 个后台消费者线程。
     *
     * 每个消费者只消费自己对应的队列。
     */
    @PostConstruct
    public void startConsumers() {
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            final int partitionIndex = i;
            consumerPool.submit(() -> consumeLoop(partitionIndex));
        }

        log.info("异步审计日志消费者线程已启动，消费者数量={}", CONSUMER_COUNT);
    }

    /**
     * 业务线程调用这个方法发布审计事件。
     *
     * 核心逻辑：
     * 1. 根据 AuditEvent 的 targetType + targetId 计算分片；
     * 2. 同一个业务对象永远进入同一个队列；
     * 3. 不同业务对象可以进入不同队列，被不同消费者并行处理。
     */
    public void publish(AuditEvent event) {
        if (event == null) {
            return;
        }

        if (!accepting) {
            log.warn("应用正在关闭，拒绝新的审计事件：{}", event);
            return;
        }

        int partitionIndex = calculatePartitionIndex(event);
        BlockingQueue<AuditEvent> queue = auditQueues[partitionIndex];

        boolean offered = queue.offer(event);

        if (!offered) {
            log.error(
                    "审计日志队列已满，丢弃审计事件，partitionIndex={}, event={}",
                    partitionIndex,
                    event
            );
        }
    }

    /**
     * 后台消费者循环。
     *
     * 每个消费者只消费自己的队列。
     * 退出条件：
     * running=false 且当前分片队列已经为空。
     */
    private void consumeLoop(int partitionIndex) {
        BlockingQueue<AuditEvent> queue = auditQueues[partitionIndex];

        log.info("审计日志消费者启动，partitionIndex={}", partitionIndex);

        while (running || !queue.isEmpty()) {
            try {
                AuditEvent event = queue.poll(1, TimeUnit.SECONDS);

                if (event == null) {
                    continue;
                }

                saveAuditLog(event);

            } catch (InterruptedException e) {
                /**
                 * 关闭时可能被 interrupt 唤醒。
                 *
                 * 注意：
                 * 这里不能直接 break。
                 * 如果 running=false，但是队列里还有数据，
                 * while 条件会继续让它消费完剩余日志。
                 */
                if (!running) {
                    log.info("审计日志消费者收到关闭信号，准备排空队列，partitionIndex={}", partitionIndex);
                    continue;
                }

                Thread.currentThread().interrupt();
                log.warn("审计日志消费者线程异常中断，partitionIndex={}", partitionIndex);
                break;

            } catch (Exception e) {
                /**
                 * 单条日志写入失败，不能让整个消费者线程死亡。
                 */
                log.error("异步写入审计日志失败，partitionIndex={}", partitionIndex, e);
            }
        }

        log.info(
                "审计日志消费者退出，partitionIndex={}，剩余队列数量={}",
                partitionIndex,
                queue.size()
        );
    }

    /**
     * 按业务对象计算分片。
     *
     * 同一个 targetType + targetId 会固定进入同一个队列。
     *
     * 例如：
     * PROOF:1 的 CREATE / UPDATE / DELETE 永远进入同一个队列，
     * 因此会被同一个消费者顺序处理。
     */
    private int calculatePartitionIndex(AuditEvent event) {
        String key = buildPartitionKey(event);
        return Math.floorMod(key.hashCode(), CONSUMER_COUNT);
    }

    /**
     * 构造分片 key。
     *
     * 优先使用 targetType + targetId。
     * 如果 targetId 为空，则退化使用 username + action。
     */
    private String buildPartitionKey(AuditEvent event) {
        if (event.getTargetId() != null) {
            return event.getTargetType() + ":" + event.getTargetId();
        }

        return event.getUsername() + ":" + event.getAction();
    }

    /**
     * 把事件转成实体并落数据库。
     */
    private void saveAuditLog(AuditEvent event) {
        AuditLog logEntity = new AuditLog();
        logEntity.setUsername(event.getUsername());
        logEntity.setAction(event.getAction());
        logEntity.setTargetType(event.getTargetType());
        logEntity.setTargetId(event.getTargetId());
        logEntity.setResult(event.getResult());
        logEntity.setIp(event.getIp());
        logEntity.setMessage(event.getMessage());
        logEntity.setCreatedAt(
                event.getEventTime() == null ? LocalDateTime.now() : event.getEventTime()
        );

        auditLogRepository.save(logEntity);
    }

    /**
     * 应用关闭时：
     * 1. 停止接收新事件；
     * 2. 通知消费者准备退出；
     * 3. 等待两个消费者尽量消费完各自队列；
     * 4. 不再直接 shutdownNow 强杀，避免队列剩余日志被丢弃。
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭异步审计日志服务，当前队列剩余数量={}", totalQueueSize());

        // 1. 不再接收新的审计事件
        accepting = false;

        // 2. 通知消费者准备停止
        // 但是消费者会继续消费完自己队列中的剩余事件
        running = false;

        // 3. 关闭线程池，不再接收新的消费者任务
        consumerPool.shutdown();

        try {
            boolean terminated = consumerPool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);

            if (!terminated) {
                /**
                 * 不建议直接 shutdownNow。
                 *
                 * shutdownNow 会中断消费者线程，
                 * 如果此时队列中还有日志，就可能丢失。
                 *
                 * 这里选择记录错误日志，让你知道停机时仍有日志没有处理完。
                 */
                log.error(
                        "异步审计日志消费者在 {} 秒内未完全退出，当前队列剩余数量={}。可能是数据库写入过慢或阻塞。",
                        SHUTDOWN_WAIT_SECONDS,
                        totalQueueSize()
                );
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待审计日志消费者线程关闭时被中断，当前队列剩余数量={}", totalQueueSize());
        }

        log.info("异步审计日志服务关闭完成，当前队列剩余数量={}", totalQueueSize());
    }

    private int totalQueueSize() {
        int total = 0;
        for (BlockingQueue<AuditEvent> queue : auditQueues) {
            total += queue.size();
        }
        return total;
    }
}