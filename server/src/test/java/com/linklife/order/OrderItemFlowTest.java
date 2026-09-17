package com.linklife.order;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OrderItemFlowTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createCircle(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试圈\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private void join(String ownerToken, String memberToken, long circleId) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/circles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String inviteCode = ((java.util.List<?>) JsonPath.read(list.getResponse().getContentAsString(),
                "$.data[?(@.id == " + circleId + ")].inviteCode")).get(0).toString();
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk());
    }

    private long createSheet(String token, long circleId, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/order/sheets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"title\":\"" + title
                                + "\",\"items\":[{\"dishName\":\"番茄炒蛋\",\"note\":\"少油\"}]}"))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private long firstItemId(long sheetId, String token) throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(detail.getResponse().getContentAsString(),
                "$.data.items[0].id").toString());
    }

    @Test
    void claimAutoProgressesSheetAndManualStatusFlow() throws Exception {
        String alice = login("flow-alice-1");
        String bob = login("flow-bob-1");
        long circleId = createCircle(alice);
        join(alice, bob, circleId);
        long sheetId = createSheet(alice, circleId, "状态机");
        long itemId = firstItemId(sheetId, alice);

        // bob 认领 → sheet 自动 IN_PROGRESS
        mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemStatus").value("CLAIMED"))
                .andExpect(jsonPath("$.data.claimantNickname").isNotEmpty());
        mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // CLAIMED → COOKING → DONE
        mockMvc.perform(post("/api/order/items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemStatus\":\"COOKING\"}"))
                .andExpect(jsonPath("$.data.itemStatus").value("COOKING"));
        mockMvc.perform(post("/api/order/items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemStatus\":\"DONE\"}"))
                .andExpect(jsonPath("$.data.itemStatus").value("DONE"));

        // DONE 后不能再释放
        mockMvc.perform(post("/api/order/items/" + itemId + "/release")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));
    }

    @Test
    void releaseByClaimantOnlyAndNonOpenClaimRejected() throws Exception {
        String alice = login("flow-alice-2");
        String bob = login("flow-bob-2");
        String carol = login("flow-carol-2");
        long circleId = createCircle(alice);
        join(alice, bob, circleId);
        join(alice, carol, circleId);
        long sheetId = createSheet(alice, circleId, "权限");
        long itemId = firstItemId(sheetId, alice);

        // 非 OPEN 认领拒绝：carol 直接对 OPEN 认领成功，再对同一 item 认领失败
        mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                        .header("Authorization", "Bearer " + carol))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));

        // 非认领人不能 release / status
        mockMvc.perform(post("/api/order/items/" + itemId + "/release")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(3005));
        mockMvc.perform(post("/api/order/items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemStatus\":\"COOKING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(3005));

        // 认领人可释放回 OPEN，且 sheet 不回退（保持 IN_PROGRESS）
        mockMvc.perform(post("/api/order/items/" + itemId + "/release")
                        .header("Authorization", "Bearer " + carol))
                .andExpect(jsonPath("$.data.itemStatus").value("OPEN"));
        mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.items[0].itemStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.items[0].claimantId").value(
                        org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].claimantNickname").value(
                        org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void addItemToSharedAndInProgressButNotCompleted() throws Exception {
        String alice = login("flow-alice-3");
        long circleId = createCircle(alice);
        long sheetId = createSheet(alice, circleId, "加菜");
        long itemId = firstItemId(sheetId, alice);

        // SHARED 可加菜
        mockMvc.perform(post("/api/order/items")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetId\":" + sheetId + ",\"dishName\":\"可乐鸡翅\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemStatus").value("OPEN"));

        // 认领后 IN_PROGRESS 仍可加菜
        mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/order/items")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetId\":" + sheetId + ",\"dishName\":\"凉拌黄瓜\"}"))
                .andExpect(status().isOk());
    }
}
