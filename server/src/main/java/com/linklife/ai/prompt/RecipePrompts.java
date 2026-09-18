package com.linklife.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.entity.RecipeFeedback;
import java.util.List;

public final class RecipePrompts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RecipePrompts() {
    }

    private static final String JSON_SHAPE = """
            {
              "servings": 2,
              "totalMinutes": 40,
              "ingredients": [{"name":"食材名","amount":"500g"}],
              "seasonings": [{"name":"调料名","amount":"2勺"}],
              "steps": [{"no":1,"text":"步骤描述","durationSec":300}],
              "tips": "小贴士"
            }""";

    public static String generate(String dishName, Integer servings, List<PantryItem> pantry,
                                  String tasteSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为菜品「").append(dishName).append("」编写一份家庭菜谱。\n");
        sb.append("只输出一个 JSON 对象，不要输出任何解释文字或 markdown 代码围栏，结构如下：\n");
        sb.append(JSON_SHAPE).append('\n');
        sb.append("要求：步骤 4~10 步且每步标注 durationSec（秒）；")
          .append("用量用家庭可操作表述（如“2勺”“500g”）。\n");
        if (servings != null) {
            sb.append("几人食：").append(servings).append("。\n");
        }
        if (pantry != null && !pantry.isEmpty()) {
            List<String> seasonings = pantry.stream()
                    .filter(i -> "SEASONING".equals(i.getType()))
                    .map(PantryItem::getName).toList();
            List<String> ingredients = pantry.stream()
                    .filter(i -> "INGREDIENT".equals(i.getType()))
                    .map(PantryItem::getName).toList();
            if (!seasonings.isEmpty()) {
                sb.append("用户现有调料（调味尽量使用这些）：")
                        .append(String.join("、", seasonings)).append("。\n");
            }
            if (!ingredients.isEmpty()) {
                sb.append("用户现有食材（可优先使用）：")
                        .append(String.join("、", ingredients)).append("。\n");
            }
        }
        if (tasteSummary != null && !tasteSummary.isBlank()) {
            sb.append("用户口味画像（必须遵守忌口等约束）：").append(tasteSummary).append('\n');
        }
        return sb.toString();
    }

    public static String iterate(String dishName, RecipeContent current,
                                 List<RecipeFeedback> feedbacks) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是菜品「").append(dishName).append("」的当前菜谱 JSON：\n");
        sb.append(toJson(current)).append('\n');
        sb.append("用户近期反馈：\n");
        for (RecipeFeedback f : feedbacks) {
            sb.append("- ").append(f.getScore()).append(" 星");
            if (f.getComment() != null && !f.getComment().isBlank()) {
                sb.append("：").append(f.getComment());
            }
            sb.append('\n');
        }
        sb.append("请基于反馈输出改进后的菜谱。只输出一个 JSON 对象，")
          .append("不要输出任何解释文字或 markdown 代码围栏，结构如下：\n");
        sb.append("{\"recipe\": <同上述菜谱结构>, \"taste_summary\": ")
          .append("{\"summary\": \"口味画像总结\", \"tags\": [\"标签\"]}}\n");
        sb.append("要求：针对反馈调整，未提及的部分保持稳定；")
          .append("taste_summary 总结用户口味偏好与忌口。\n");
        return sb.toString();
    }

    private static String toJson(RecipeContent content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
