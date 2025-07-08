package com.wilsonkeh.loginmanagement.service;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;

public interface LoginRecordQueueService {
    
    /**
     * 将登录记录请求加入队列
     * @param request 登录记录请求
     * @return 是否成功加入队列
     */
    boolean enqueueLoginRecord(LoginRecordRequest request);
    
    /**
     * 将登录记录请求加入队列（带优先级）
     * @param request 登录记录请求
     * @param priority 优先级（数字越小优先级越高）
     * @return 是否成功加入队列
     */
    boolean enqueueLoginRecord(LoginRecordRequest request, int priority);
    
    /**
     * 获取当前队列大小
     * @return 队列中的任务数量
     */
    int getQueueSize();
    
    /**
     * 获取去重后的队列大小
     * @return 去重后的任务数量
     */
    int getDeduplicatedQueueSize();
    
    /**
     * 获取队列统计信息
     * @return 统计信息字符串
     */
    String getQueueStatistics();
} 