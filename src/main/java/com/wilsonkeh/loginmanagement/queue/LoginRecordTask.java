package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录记录任务
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LoginRecordTask implements Task<LoginRecordRequest> {
    
    private final String taskId;
    private final LoginRecordRequest data;
    private final String taskType;
    private final String deduplicationKey;
    private final int priority;
    private final long createdTime;
    private final boolean discardable;

    public LoginRecordTask(LoginRecordRequest data) {
        this.taskId = generateTaskId(data);
        this.data = data;
        this.taskType = "LOGIN_RECORD";
        this.deduplicationKey = data.uid(); // 使用uid作为去重键
        this.priority = 0; // 默认优先级
        this.createdTime = System.currentTimeMillis();
        this.discardable = false; // 登录记录不可丢弃
    }

    public LoginRecordTask(LoginRecordRequest data, int priority) {
        this.taskId = generateTaskId(data);
        this.data = data;
        this.taskType = "LOGIN_RECORD";
        this.deduplicationKey = data.uid();
        this.priority = priority;
        this.createdTime = System.currentTimeMillis();
        this.discardable = false;
    }

    private String generateTaskId(LoginRecordRequest data) {
        return "login_" + data.uid() + "_" + System.currentTimeMillis();
    }

    @Override
    public String getTaskId() {
        return taskId;
    }

    @Override
    public LoginRecordRequest getData() {
        return data;
    }

    @Override
    public String getTaskType() {
        return taskType;
    }

    @Override
    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public long getCreatedTime() {
        return createdTime;
    }

    @Override
    public boolean isDiscardable() {
        return discardable;
    }
} 