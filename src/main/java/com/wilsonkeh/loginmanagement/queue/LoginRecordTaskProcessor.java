package com.wilsonkeh.loginmanagement.queue;

import com.wilsonkeh.loginmanagement.dto.LoginRecordRequest;
import com.wilsonkeh.loginmanagement.service.LoginRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

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
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void processBatch(List<Task<LoginRecordRequest>> tasks) throws Exception {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        try {
            // 提取所有请求数据
            List<LoginRecordRequest> requests = tasks.stream()
                    .map(Task::getData)
                    .collect(Collectors.toList());

            // 使用批量处理
            List<com.wilsonkeh.loginmanagement.dto.LoginRecordResponse> responses = 
                    loginRecordService.createLoginRecordsBatch(requests);

            log.info("批量处理登录记录任务成功，处理数量: {}, 成功数量: {}", 
                     tasks.size(), responses.size());

        } catch (Exception e) {
            log.error("批量处理登录记录任务失败，任务数量: {}, 错误: {}", 
                     tasks.size(), e.getMessage(), e);
            throw e; // 重新抛出异常以便重试机制处理
        }
    }

    @Override
    public String getSupportedTaskType() {
        return "LOGIN_RECORD";
    }
} 