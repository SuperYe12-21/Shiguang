package com.shiguang.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class StorageUrlCompatibilityTest {

    @Test
    void legacyHttpsAddressesRemainReadableAcrossStorageProviders() {
        String url = "https://legacy.example.com/images/original.jpg";
        assertThat(mock(MinioStorageService.class, CALLS_REAL_METHODS).publicUrl(url)).isEqualTo(url);
        assertThat(mock(OssStorageService.class, CALLS_REAL_METHODS).publicUrl(url)).isEqualTo(url);
    }
}
