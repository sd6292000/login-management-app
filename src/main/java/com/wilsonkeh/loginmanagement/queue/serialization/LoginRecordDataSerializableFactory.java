package com.wilsonkeh.loginmanagement.queue.serialization;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.wilsonkeh.loginmanagement.queue.LoginRecordTask;

/**
 * Hazelcast数据序列化工厂
 * 用于序列化登录记录任务
 */
public class LoginRecordDataSerializableFactory implements DataSerializableFactory {

    public static final int FACTORY_ID = 1;
    public static final int LOGIN_RECORD_TASK_TYPE = 1;

    @Override
    public IdentifiedDataSerializable create(int typeId) {
        switch (typeId) {
            case LOGIN_RECORD_TASK_TYPE:
                return new LoginRecordTask();
            default:
                throw new IllegalArgumentException("Unknown type ID: " + typeId);
        }
    }
} 