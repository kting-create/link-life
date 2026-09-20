package com.linklife.auth;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class WxLoginConcurrencyTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private AuthService authService;

    @Test
    void concurrentFirstLoginYieldsSameUser() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-conc-1", "unionid-conc-1"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<AuthTokens> login = () -> authService.wxLogin("js-code");
        List<Future<AuthTokens>> results = pool.invokeAll(List.of(login, login));
        AuthTokens a = results.get(0).get();
        AuthTokens b = results.get(1).get();
        assertEquals(a.user().id(), b.user().id());
        pool.shutdownNow();
    }
}
