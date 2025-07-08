package com.wilsonkeh.loginmanagement.queue;

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
     * 批量处理任务
     * @param tasks 任务列表
     * @throws Exception 处理异常
     */
    default void processBatch(java.util.List<Task<T>> tasks) throws Exception {
        for (Task<T> task : tasks) {
            processTask(task);
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