# P3 AI 菜谱小样本验证（真实调用，DEEPSEEK_API_KEY 就绪后执行）

前置：`deploy/.env` 已配 `DEEPSEEK_API_KEY`/`DEEPSEEK_MODEL=deepseek-flash`；
`cd deploy && docker compose up -d --build`；`curl http://localhost/api/health` 通过。

## 登录拿 token（小程序测试号环境，任意 openid 走 wx-login 不可行时用 dev 通道——
若仅小程序登录可用，用真机/开发者工具登录一次，从开发者工具 Network 面板复制 accessToken）

TOKEN=<accessToken>
BASE=http://localhost

## 1. 准备：建圈
curl -s -X POST $BASE/api/circles -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"验证圈"}'
# 记下 data.id → CIRCLE_ID

## 2. 调料架约束
curl -s -X POST $BASE/api/me/pantry -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"type":"SEASONING","name":"家传红烧汁"}'

## 3. 生成 3~5 道菜（家常菜 / 带忌口 / 冷门菜各一），逐个观察流式输出：
curl -N -s -X POST $BASE/api/recipes/generate -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"circleId":'"$CIRCLE_ID"',"dishName":"红烧肉"}'

### 检查项（每道菜记录）：
- [ ] delta 流为打字机推进（nginx 不缓冲）
- [ ] 最终 done 事件正常收到
- [ ] GET /api/recipes/{id} 返回结构完整：servings/totalMinutes/ingredients/seasonings/steps/tips
- [ ] 步骤 4~10 步、每步有 durationSec、用量可操作（"2勺""500g"）
- [ ] 调料架中"家传红烧汁"出现在 seasonings（约束生效）
- [ ] content JSON 无围栏/杂质（若解析失败会收到 error 5004 —— 记录原始现象）

## 4. 反馈迭代闭环
curl -s -X POST $BASE/api/recipes/{id}/feedback -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"score":3,"comment":"偏淡了，汤太多"}'
curl -N -s -X POST $BASE/api/recipes/{id}/iterate -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"comment":"做咸一点，收汁"}'

### 检查项：
- [ ] 新版本 version=2，change_note 为提交的 comment
- [ ] 对比 v1/v2 content：反馈点（咸淡/汤汁）被修正，未提及部分基本稳定
- [ ] GET /api/me/taste-profile 出现 summary/tags 且合理

## 5. ai_call_log 抽查
docker exec deploy-mysql-1 mysql -ulinklife -plinklife-prod linklife \
  -e "SELECT scene, ok, prompt_tokens, completion_tokens, user_id FROM ai_call_log ORDER BY id DESC LIMIT 10;"
# [ ] tokens 非空（阻塞调用）；user_id 非 NULL

## 6. 版本上限 / 回滚 / 手动编辑
# [ ] PUT 手动编辑 → v3(MANUAL_EDIT)；POST rollback version=1 → 指针回 1
# [ ] 补齐到 5 版后再迭代 → error 5002

## 结果记录：追加到 docs/TODO.md 随手记录区；prompt 问题现场修正后重跑对应项
