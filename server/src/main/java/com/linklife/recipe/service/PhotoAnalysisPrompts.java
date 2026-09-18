package com.linklife.recipe.service;

public final class PhotoAnalysisPrompts {

    private PhotoAnalysisPrompts() {
    }

    public static String build(String dishName, String stepText, String tasteSummary) {
        String taste = tasteSummary == null || tasteSummary.isBlank() ? "无记录" : tasteSummary;
        return "你是烹饪助手。用户正在做「" + dishName + "」，当前步骤：" + stepText
                + "。用户口味偏好：" + taste + "。"
                + "请分析这张烹饪过程照片（调料用量、火候、色泽、操作手法是否正确），给出简短建议；"
                + "仅当照片暴露出与菜谱步骤直接相关的问题时，输出需要修改的步骤（只改受影响步骤的文本或时长）。\n"
                + "仅输出 JSON，格式：{\"advice\":\"50字内建议\","
                + "\"changes\":[{\"stepNo\":1,\"text\":\"修改后的步骤文本\",\"durationSec\":60}]}；"
                + "无需修改时 changes 为空数组 []；不要输出 JSON 以外的任何内容。";
    }
}
