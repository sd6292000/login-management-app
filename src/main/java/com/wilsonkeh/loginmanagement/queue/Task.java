package com.wilsonkeh.loginmanagement.queue;

import java.io.Serializable;

/**
 * 通用任务接口
 * @param <T> 任务数据类型
 */
public interface Task<T> extends Serializable {
    
    /**
     * 获取任务ID
     */
    String getTaskId();
    
    /**
     * 获取任务数据
     */
    T getData();
    
    /**
     * 获取任务类型
     */
    String getTaskType();
    
    /**
     * 获取去重键（用于去重）
     */
    default String getDeduplicationKey() {
        return getTaskId();
    }
    
    /**
     * 获取任务优先级（数字越小优先级越高）
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 获取任务创建时间
     */
    default long getCreatedTime() {
        return System.currentTimeMillis();
    }
    
    /**
     * 任务是否可以被丢弃（当队列满时）
     */
    default boolean isDiscardable() {
        return false;
    }
} 