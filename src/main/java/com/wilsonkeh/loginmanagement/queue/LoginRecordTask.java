package com.wilsonkeh.loginmanagement.queue;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.queue.serialization.LoginRecordDataSerializableFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 登录记录任务
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LoginRecordTask implements Task<LoginRecordRequest>, IdentifiedDataSerializable {
    
    private String taskId;
    private LoginRecordRequest data;
    private String taskType;
    private String deduplicationKey;
    private int priority;
    private long createdTime;
    private boolean discardable;

    // 默认构造函数，用于Hazelcast序列化
    public LoginRecordTask() {
    }

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

    // Hazelcast序列化方法
    @Override
    public int getFactoryId() {
        return LoginRecordDataSerializableFactory.FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return LoginRecordDataSerializableFactory.LOGIN_RECORD_TASK_TYPE;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeString(taskId);
        out.writeObject(data);
        out.writeString(taskType);
        out.writeString(deduplicationKey);
        out.writeInt(priority);
        out.writeLong(createdTime);
        out.writeBoolean(discardable);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        taskId = in.readString();
        data = in.readObject();
        taskType = in.readString();
        deduplicationKey = in.readString();
        priority = in.readInt();
        createdTime = in.readLong();
        discardable = in.readBoolean();
    }
} 