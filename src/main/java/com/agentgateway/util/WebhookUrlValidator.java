package com.agentgateway.util;

import com.agentgateway.exception.ApiException;
import com.agentgateway.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates webhook destinations at registration and immediately before delivery.
 * DNS cannot be pinned with {@link java.net.HttpURLConnection}; revalidation narrows,
 * but cannot completely eliminate, DNS-rebinding risk in standard Java.
 */
@Component
@RequiredArgsConstructor
public class WebhookUrlValidator {
    private final AppConfigService appConfigService;

    public URI validate(String url) {
        boolean allowPrivate = appConfigService.isPrivateWebhookUrlsAllowed();
        try {
            URI uri = URI.create(url).normalize();
            String scheme = uri.getScheme();
            if ((!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawAuthority() == null || uri.getRawPath() == null) {
                throw ApiException.badRequest("Webhook URL must be an absolute HTTP/HTTPS URL without user info");
            }
            if (uri.getFragment() != null) {
                throw ApiException.badRequest("Webhook URL must not contain a fragment");
            }
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                throw ApiException.badRequest("Webhook host cannot be resolved");
            }
            boolean privateDestinationsOnly = true;
            for (InetAddress address : addresses) {
                if (!isAllowed(address, allowPrivate)) {
                    throw ApiException.badRequest("Webhook URL must resolve only to globally routable IP addresses or enabled private test ranges");
                }
                privateDestinationsOnly &= !isPublic(address);
            }
            if ("http".equalsIgnoreCase(scheme) && (!allowPrivate || !privateDestinationsOnly)) {
                throw ApiException.badRequest("HTTP webhook URLs are allowed only for enabled local/private test ranges");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Webhook URL is invalid");
        } catch (UnknownHostException e) {
            throw ApiException.badRequest("Webhook host cannot be resolved");
        }
    }

    private boolean isAllowed(InetAddress address, boolean allowPrivate) {
        if (isPublic(address)) {
            return true;
        }
        if (!allowPrivate) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isAllowedPrivateIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            if (isIpv4Mapped(bytes)) {
                return isAllowedPrivateIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
            }
            return address.isLoopbackAddress() || (bytes[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    private boolean isAllowedPrivateIpv4(byte[] bytes) {
        int a = Byte.toUnsignedInt(bytes[0]);
        int b = Byte.toUnsignedInt(bytes[1]);
        return a == 127 || a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
    }

    private boolean isPublic(InetAddress address) {
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            if (isIpv4Mapped(bytes)) {
                return isPublicIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
            }
            return !address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isLinkLocalAddress()
                    && !address.isSiteLocalAddress() && !address.isMulticastAddress()
                    && (bytes[0] & 0xfe) != 0xfc  // fc00::/7 ULA
                    && !isDocumentationIpv6(bytes);
        }
        return false;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        int a = Byte.toUnsignedInt(bytes[0]);
        int b = Byte.toUnsignedInt(bytes[1]);
        int c = Byte.toUnsignedInt(bytes[2]);
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127)       // shared address space
                && !(a == 169 && b == 254)
                && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && b == 0 && c == 0)           // IETF protocol assignments
                && !(a == 192 && b == 0 && c == 2)           // TEST-NET-1
                && !(a == 192 && b == 88 && c == 99)         // 6to4 relay anycast
                && !(a == 192 && b == 168)
                && !(a == 198 && (b == 18 || b == 19))       // benchmark tests
                && !(a == 198 && b == 51 && c == 100)        // TEST-NET-2
                && !(a == 203 && b == 0 && c == 113);        // TEST-NET-3
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) if (bytes[i] != 0) return false;
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private boolean isDocumentationIpv6(byte[] bytes) {
        return bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == (byte) 0xb8;
    }
}
