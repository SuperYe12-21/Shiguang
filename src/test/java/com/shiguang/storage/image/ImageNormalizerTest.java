package com.shiguang.storage.image;

import com.shiguang.common.BizException;
import com.shiguang.storage.StorageException;
import com.shiguang.storage.StorageService;
import com.shiguang.storage.UploadOwnershipService;
import com.shiguang.storage.UploadPurpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageNormalizerTest {

    private static final String HEIC_OBJECT = "images/2026-10-10/from-iphone.jpeg";
    private static final String JPEG_OBJECT = "images/2026-10-10/normal.jpg";

    /** iPhone 拍出来就是这种：ftyp 盒 + major brand = heic */
    private static final byte[] HEIC_HEADER = ftypHeader("heic");

    private static final byte[] JPEG_HEADER = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01,
            0x00, 0x00, 0x00, 0x00};

    private static final byte[] CONVERTED = "converted-jpeg-bytes".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private StorageService storageService;

    @Mock
    private HeifConverter heifConverter;

    @Mock
    private UploadOwnershipService uploadOwnershipService;

    @InjectMocks
    private ImageNormalizer normalizer;

    @Test
    void heicIsConvertedToJpegAndOriginalRemoved() throws Exception {
        givenHeicObject();
        givenConverterWritesJpeg();

        String result = normalizer.normalize(7L, HEIC_OBJECT, UploadPurpose.IMAGE);

        assertThat(result).startsWith("images/").endsWith(".jpg").isNotEqualTo(HEIC_OBJECT);
        verify(storageService).putObject(eq(result), any(File.class), eq("image/jpeg"));
        verify(uploadOwnershipService).registerNormalized(7L, HEIC_OBJECT, result);
        verify(uploadOwnershipService).deleteOwnedIfPresent(7L, HEIC_OBJECT);
    }

    @Test
    void uploadedBytesAreTheConvertedJpeg() throws Exception {
        givenHeicObject();
        givenConverterWritesJpeg();
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        doAnswer(invocation -> {
            uploaded.set(Files.readAllBytes(invocation.getArgument(1, File.class).toPath()));
            return null;
        }).when(storageService).putObject(anyString(), any(File.class), anyString());

        normalizer.normalize(7L, HEIC_OBJECT, UploadPurpose.IMAGE);

        assertThat(uploaded.get()).isEqualTo(CONVERTED);
    }

    @Test
    void realJpegIsLeftUntouched() {
        when(storageService.open(JPEG_OBJECT, 0, 32)).thenReturn(new ByteArrayInputStream(JPEG_HEADER));

        assertThat(normalizer.normalize(7L, JPEG_OBJECT, UploadPurpose.IMAGE)).isEqualTo(JPEG_OBJECT);
        verify(uploadOwnershipService).validateAndBind(7L, JPEG_OBJECT, UploadPurpose.IMAGE);
        verify(heifConverter, never()).toJpeg(any(), any());
        verify(storageService, never()).putObject(anyString(), any(), anyString());
    }

    @Test
    void externalUrlCannotBypassOwnershipValidation() {
        String url = "https://shiguang-bucket.oss-cn-beijing.aliyuncs.com/images/a.jpeg";

        doThrow(new BizException("文件对象名非法")).when(uploadOwnershipService)
                .validateAndBind(7L, url, UploadPurpose.IMAGE);
        assertThatThrownBy(() -> normalizer.normalize(7L, url, UploadPurpose.IMAGE)).isInstanceOf(BizException.class);
        verifyNoInteractions(storageService, heifConverter);
    }

    @Test
    void blankIsLeftUntouched() {
        assertThat(normalizer.normalize(7L, null, UploadPurpose.IMAGE)).isNull();
        assertThat(normalizer.normalize(7L, "  ", UploadPurpose.IMAGE)).isEqualTo("  ");
        verifyNoInteractions(storageService, heifConverter);
    }

    @Test
    void unreadableObjectIsPassedThrough() {
        when(storageService.open(JPEG_OBJECT, 0, 32)).thenThrow(StorageException.notFound(JPEG_OBJECT));

        assertThat(normalizer.normalize(7L, JPEG_OBJECT, UploadPurpose.IMAGE)).isEqualTo(JPEG_OBJECT);
        verify(heifConverter, never()).toJpeg(any(), any());
    }

    @Test
    void converterFailureRaisesFriendlyError() throws Exception {
        givenHeicObject();
        doThrow(new IllegalStateException("heif-convert not installed")).when(heifConverter).toJpeg(any(), any());

        assertThatThrownBy(() -> normalizer.normalize(7L, HEIC_OBJECT, UploadPurpose.IMAGE))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HEIC");
        verify(storageService, never()).putObject(anyString(), any(), anyString());
    }

    @Test
    void normalizeAllKeepsOrderAndOnlyTouchesHeic() throws Exception {
        when(storageService.open("images/ok.jpg", 0, 32)).thenReturn(new ByteArrayInputStream(JPEG_HEADER));
        givenHeicObject();
        givenConverterWritesJpeg();

        List<String> result = normalizer.normalizeAll(7L, Arrays.asList("images/ok.jpg", HEIC_OBJECT));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("images/ok.jpg");
        assertThat(result.get(1)).endsWith(".jpg").isNotEqualTo(HEIC_OBJECT);
    }

    @Test
    void normalizeAllWithEmptyInputReturnsAsIs() {
        assertThat(normalizer.normalizeAll(7L, null)).isNull();
        assertThat(normalizer.normalizeAll(7L, List.of())).isEmpty();
        verifyNoInteractions(storageService, heifConverter);
    }

    @Test
    void headerDetectionCoversHeifBrandsOnly() {
        assertThat(ImageNormalizer.isHeifHeader(HEIC_HEADER)).isTrue();
        // iPhone 常见：major brand 是通用 mif1，真正的 heic 在 compatible brands 里
        assertThat(ImageNormalizer.isHeifHeader(ftypHeader("mif1", "heic"))).isTrue();
        // AVIF 也是 ftyp 盒，但 Chromium 自己能显示，不转
        assertThat(ImageNormalizer.isHeifHeader(ftypHeader("avif"))).isFalse();
        assertThat(ImageNormalizer.isHeifHeader(JPEG_HEADER)).isFalse();
        assertThat(ImageNormalizer.isHeifHeader(new byte[]{1, 2, 3})).isFalse();
        assertThat(ImageNormalizer.isHeifHeader(null)).isFalse();
    }

    private void givenHeicObject() {
        when(storageService.open(HEIC_OBJECT, 0, 32)).thenReturn(new ByteArrayInputStream(HEIC_HEADER));
        when(storageService.getObject(HEIC_OBJECT)).thenReturn(new ByteArrayInputStream(HEIC_HEADER));
    }

    private void givenConverterWritesJpeg() throws Exception {
        doAnswer(invocation -> {
            Files.write(invocation.getArgument(1, Path.class), CONVERTED);
            return null;
        }).when(heifConverter).toJpeg(any(Path.class), any(Path.class));
    }

    /** 组一个 ISO-BMFF ftyp 头：4 字节长度 + ftyp + major brand + minor version + compatible brands */
    private static byte[] ftypHeader(String majorBrand, String... compatibleBrands) {
        byte[] head = new byte[16 + compatibleBrands.length * 4];
        head[3] = (byte) head.length;
        head[4] = 'f';
        head[5] = 't';
        head[6] = 'y';
        head[7] = 'p';
        System.arraycopy(majorBrand.getBytes(StandardCharsets.US_ASCII), 0, head, 8, 4);
        for (int i = 0; i < compatibleBrands.length; i++) {
            System.arraycopy(compatibleBrands[i].getBytes(StandardCharsets.US_ASCII), 0, head, 16 + i * 4, 4);
        }
        return head;
    }
}
