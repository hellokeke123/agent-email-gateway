package com.agentgateway.service;

import com.agentgateway.entity.AppConfig;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.AppConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Web 管理面保存的对外公开地址 */
@Service
@RequiredArgsConstructor
public class AppConfigService {

    private final AppConfigMapper mapper;

    public String getBaseUrl() {
        AppConfig config = mapper.selectById(1);
        return config == null ? null : config.getBaseUrl();
    }

    public void saveBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        AppConfig config = mapper.selectById(1);
        if (config == null) {
            config = new AppConfig();
            config.setId(1);
            config.setBaseUrl(normalized);
            mapper.insert(config);
        } else {
            config.setBaseUrl(normalized);
            mapper.updateById(config);
        }
    }

    public void clearBaseUrl() {
        AppConfig config = mapper.selectById(1);
        if (config != null) {
            config.setBaseUrl(null);
            mapper.updateById(config);
        }
    }

    public boolean isPrivateWebhookUrlsAllowed() {
        AppConfig config = mapper.selectById(1);
        return config != null && Boolean.TRUE.equals(config.getAllowPrivateWebhookUrls());
    }

    public void savePrivateWebhookUrlsAllowed(boolean allowed) {
        AppConfig config = mapper.selectById(1);
        if (config == null) {
            config = new AppConfig();
            config.setId(1);
            config.setAllowPrivateWebhookUrls(allowed);
            mapper.insert(config);
        } else {
            config.setAllowPrivateWebhookUrls(allowed);
            mapper.updateById(config);
        }
    }

    public static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("公开地址不能为空");
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw ApiException.badRequest("公开地址必须是没有凭据、查询参数或片段的绝对 HTTP/HTTPS URL");
            }
            String path = uri.getRawPath();
            while (path != null && path.endsWith("/") && !path.isEmpty()) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(scheme.toLowerCase(Locale.ROOT), null, uri.getHost(), uri.getPort(), path, null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw ApiException.badRequest("公开地址必须是有效的绝对 HTTP/HTTPS URL");
        }
    }
}
