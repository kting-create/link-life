package com.linklife.ai.gateway;

import com.linklife.ai.entity.AiCallLog;
import com.linklife.ai.mapper.AiCallLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallLogger {

    private final AiCallLogMapper aiCallLogMapper;

    public void log(Long userId, String scene, String provider, String model,
                    boolean ok, String errorMsg) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setUserId(userId);
            entry.setScene(scene);
            entry.setProvider(provider);
            entry.setModel(model);
            entry.setOk(ok);
            entry.setErrorMsg(errorMsg);
            entry.setCreatedAt(LocalDateTime.now());
            aiCallLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("failed to write ai_call_log", e);
        }
    }
}
