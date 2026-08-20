package com.agentgateway.util;

import com.agentgateway.exception.ApiException;
import com.agentgateway.service.AppConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookUrlValidatorTest {

    @Test
    void rejectsPrivateDestinationsByDefault() {
        WebhookUrlValidator validator = validator(false);

        assertRejected(validator, "https://127.0.0.1/hook");
        assertRejected(validator, "https://10.0.0.1/hook");
        assertRejected(validator, "https://172.16.0.1/hook");
        assertRejected(validator, "https://192.168.0.1/hook");
        assertRejected(validator, "https://[fc00::1]/hook");
        assertRejected(validator, "https://[::1]/hook");
    }

    @Test
    void permitsOnlyNarrowPrivateTestRangesWhenEnabled() {
        WebhookUrlValidator validator = validator(true);

        assertAllowed(validator, "https://127.0.0.1/hook");
        assertAllowed(validator, "http://127.0.0.1/hook");
        assertAllowed(validator, "http://10.0.0.1/hook");
        assertAllowed(validator, "https://172.16.0.1/hook");
        assertAllowed(validator, "https://192.168.0.1/hook");
        assertAllowed(validator, "https://[fc00::1]/hook");
        assertAllowed(validator, "https://[::1]/hook");
    }

    @Test
    void rejectsDangerousAndReservedDestinationsInBothModes() {
        for (boolean allowPrivate : new boolean[]{false, true}) {
            WebhookUrlValidator validator = validator(allowPrivate);
            assertRejected(validator, "https://0.0.0.0/hook");
            assertRejected(validator, "https://169.254.1.1/hook");
            assertRejected(validator, "https://100.64.0.1/hook");
            assertRejected(validator, "https://192.0.2.1/hook");
            assertRejected(validator, "https://198.18.0.1/hook");
            assertRejected(validator, "https://203.0.113.1/hook");
            assertRejected(validator, "https://224.0.0.1/hook");
            assertRejected(validator, "https://[fe80::1]/hook");
            assertRejected(validator, "https://[2001:db8::1]/hook");
            assertRejected(validator, "https://[ff00::1]/hook");
        }
    }

    @Test
    void rejectsHttpUnlessAllAddressesAreEnabledPrivateRanges() {
        assertRejected(validator(false), "http://127.0.0.1/hook");
        assertRejected(validator(true), "http://8.8.8.8/hook");
    }

    private WebhookUrlValidator validator(boolean allowPrivate) {
        AppConfigService appConfigService = mock(AppConfigService.class);
        when(appConfigService.isPrivateWebhookUrlsAllowed()).thenReturn(allowPrivate);
        return new WebhookUrlValidator(appConfigService);
    }

    private void assertAllowed(WebhookUrlValidator validator, String url) {
        assertDoesNotThrow(() -> validator.validate(url));
    }

    private void assertRejected(WebhookUrlValidator validator, String url) {
        assertThrows(ApiException.class, () -> validator.validate(url));
    }
}
