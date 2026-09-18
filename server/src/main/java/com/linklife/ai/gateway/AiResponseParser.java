package com.linklife.ai.gateway;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;

public final class AiResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AiResponseParser() {
    }

    /** 剥 markdown 围栏并截取首个 { 到末个 } 的 JSON 文本。 */
    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\s*", "");
            int fenceEnd = s.lastIndexOf("```");
            if (fenceEnd >= 0) {
                s = s.substring(0, fenceEnd);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
        return s.substring(start, end + 1);
    }

    public static <T> T parse(String raw, Class<T> type) {
        try {
            return MAPPER.readValue(extractJson(raw), type);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
    }
}
