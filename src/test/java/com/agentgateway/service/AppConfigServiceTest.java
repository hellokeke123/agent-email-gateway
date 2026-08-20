package com.agentgateway.service;

import com.agentgateway.entity.AppConfig;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.AppConfigMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConfigServiceTest {

    @Test
    void privateWebhookUrlsDefaultToFalseWhenConfigIsMissing() {
        AppConfigMapper mapper = mock(AppConfigMapper.class);
        when(mapper.selectById(1)).thenReturn(null);

        assertFalse(new AppConfigService(mapper).isPrivateWebhookUrlsAllowed());
    }

    @Test
    void privateWebhookUrlsCanBeEnabledDisabledAndReenabled() {
        AppConfigMapper mapper = mock(AppConfigMapper.class);
        AppConfig config = new AppConfig();
        config.setId(1);
        config.setAllowPrivateWebhookUrls(false);
        when(mapper.selectById(1)).thenReturn(config);
        AppConfigService service = new AppConfigService(mapper);

        service.savePrivateWebhookUrlsAllowed(true);
        assertTrue(service.isPrivateWebhookUrlsAllowed());
        service.savePrivateWebhookUrlsAllowed(false);
        assertFalse(service.isPrivateWebhookUrlsAllowed());
        service.savePrivateWebhookUrlsAllowed(true);
        assertTrue(service.isPrivateWebhookUrlsAllowed());
        verify(mapper, org.mockito.Mockito.times(3)).updateById(config);
    }

    @Test
    void privateWebhookUrlsArePersistedWhenCreatingSingletonConfig() {
        AppConfigMapper mapper = mock(AppConfigMapper.class);
        when(mapper.selectById(1)).thenReturn(null);
        AppConfigService service = new AppConfigService(mapper);

        service.savePrivateWebhookUrlsAllowed(true);

        verify(mapper).insert(any(AppConfig.class));
    }

    @Test
    void normalizeBaseUrl_preservesPathAndRemovesTrailingSlashes() {
        assertEquals("https://gateway.example.com/proxy", AppConfigService.normalizeBaseUrl("HTTPS://gateway.example.com/proxy///"));
        assertEquals("http://gateway.example.com:8080", AppConfigService.normalizeBaseUrl("http://gateway.example.com:8080/"));
    }

    @Test
    void normalizeBaseUrl_rejectsUnsafeOrIncompleteUrls() {
        for (String invalid : new String[]{"/gateway", "ftp://gateway.example.com", "https://user@gateway.example.com", "https://gateway.example.com?q=1", "https://gateway.example.com#part"}) {
            assertThrows(ApiException.class, () -> AppConfigService.normalizeBaseUrl(invalid));
        }
    }
}
