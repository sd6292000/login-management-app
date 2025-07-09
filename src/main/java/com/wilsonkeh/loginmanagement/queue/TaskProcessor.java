package com.wilsonkeh.loginmanagement.queue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 任务处理器接口
 * @param <T> 任务数据类型
 */
public interface TaskProcessor<T> {
    
    /**
     * 处理单个任务
     * @param task 任务对象
     * @throws Exception 处理异常
     */
    void processTask(Task<T> task) throws Exception;
    
    /**
     * 批量处理任务（串行处理）
     * @param tasks 任务列表
     * @throws Exception 处理异常
     */
    default void processBatch(List<Task<T>> tasks) throws Exception {
        for (Task<T> task : tasks) {
            processTask(task);
        }
    }
    
    /**
     * 并行批量处理任务（使用固定线程池）
     * @param tasks 任务列表
     * @param threadPoolSize 线程池大小
     * @throws Exception 处理异常
     */
    default void processBatchParallel(List<Task<T>> tasks, int threadPoolSize) throws Exception {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        
        if (threadPoolSize <= 1) {
            // 如果线程池大小为1或更小，使用串行处理
            processBatch(tasks);
            return;
        }
        
        // For scheduled jobs, it's better to create a new ExecutorService each time to avoid resource leaks and thread exhaustion,
        // unless you have a strong reason to share a thread pool (e.g., for resource control or reuse).
        // Here, we keep the ExecutorService local to the method and use try-with-resources for proper shutdown.
        try (ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize)) {
            int batchSize = Math.max(1, tasks.size() / threadPoolSize);
            List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < tasks.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, tasks.size());
                List<Task<T>> batch = tasks.subList(i, endIndex);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        for (Task<T> task : batch) {
                            processTask(task);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process batch tasks", e);
                    }
                }, executor);

                futures.add(future);
            }

            // Wait for all tasks to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        
        } catch (Exception e) {
            throw new Exception("并行批量处理任务失败", e);
        }
    }
    
    /**
     * 获取处理器支持的任务类型
     */
    String getSupportedTaskType();
    
    /**
     * 验证任务是否可以被处理
     */
    default boolean canProcess(Task<T> task) {
        return getSupportedTaskType().equals(task.getTaskType());
    }
} 