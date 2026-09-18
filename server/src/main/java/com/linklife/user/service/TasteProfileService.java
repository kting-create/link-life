package com.linklife.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.user.dto.TasteSummary;
import com.linklife.user.entity.UserProfile;
import com.linklife.user.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TasteProfileService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserProfileMapper userProfileMapper;

    public Map<String, Object> get(long userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null || profile.getTastePrefs() == null) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(profile.getTastePrefs(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.warn("failed to parse taste_prefs for user {}", userId, e);
            return Map.of();
        }
    }

    public String getSummary(long userId) {
        Object summary = get(userId).get("summary");
        return summary == null ? null : summary.toString();
    }

    @Transactional
    public void applySummary(long userId, TasteSummary summary) {
        if (summary == null || isBlank(summary.summary())
                && (summary.tags() == null || summary.tags().isEmpty())) {
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(Map.of(
                    "summary", orEmpty(summary.summary()),
                    "tags", summary.tags() == null ? List.of() : summary.tags()));
            UserProfile profile = userProfileMapper.selectById(userId);
            if (profile == null) {
                profile = new UserProfile();
                profile.setUserId(userId);
                profile.setTastePrefs(json);
                profile.setCreatedAt(LocalDateTime.now());
                userProfileMapper.insert(profile);
            } else {
                profile.setTastePrefs(json);
                userProfileMapper.updateById(profile);
            }
        } catch (Exception e) {
            // 画像沉淀失败不阻断主流程
            log.warn("failed to save taste profile for user {}", userId, e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
