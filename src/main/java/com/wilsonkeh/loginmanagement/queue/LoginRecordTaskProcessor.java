package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.service.LoginRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 登录记录任务处理器
 */
@Slf4j
@Component
public class LoginRecordTaskProcessor implements TaskProcessor<LoginRecordRequest> {

    @Autowired
    private LoginRecordService loginRecordService;

    @Override
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void processTask(Task<LoginRecordRequest> task) throws Exception {
        try {
            LoginRecordRequest request = task.getData();
            loginRecordService.createLoginRecord(request);
            log.debug("登录记录任务处理成功，taskId: {}, uid: {}", 
                     task.getTaskId(), request.uid());
        } catch (Exception e) {
            log.error("处理登录记录任务失败，taskId: {}, uid: {}, 错误: {}", 
                     task.getTaskId(), task.getData().uid(), e.getMessage(), e);
            throw e; // 重新抛出异常以便重试机制处理
        }
    }

    @Override
    public String getSupportedTaskType() {
        return "LOGIN_RECORD";
    }
} 