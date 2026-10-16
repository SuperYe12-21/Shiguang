package com.shiguang.storage;

import com.shiguang.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadOwnershipServiceTest {

    @Mock private StorageUploadMapper uploadMapper;
    @Mock private StorageService storageService;
    @InjectMocks private UploadOwnershipService ownership;
    private final Map<String, StorageUpload> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(uploadMapper.selectById(anyString())).thenAnswer(invocation -> rows.get(invocation.getArgument(0)));
        lenient().when(uploadMapper.lockByObjectName(anyString())).thenAnswer(invocation -> rows.get(invocation.getArgument(0)));
        lenient().when(uploadMapper.markDeletedIfUnreferenced(any(), anyString())).thenAnswer(invocation -> {
            rows.get(invocation.getArgument(1)).setDeleted(true);
            return 1;
        });
        lenient().doAnswer(invocation -> {
            StorageUpload row = invocation.getArgument(0);
            rows.put(row.getObjectName(), row);
            return 1;
        }).when(uploadMapper).insert(any(StorageUpload.class));
        lenient().when(storageService.presignPut(anyString(), anyString()))
                .thenAnswer(invocation -> new PresignResult(invocation.getArgument(0), "signed-url"));
    }

    @Test
    void unknownAndOtherOwnersKeysAreRejectedWithoutStorageAccess() {
        String key = ownership.presign(7L, UploadPurpose.IMAGE, "image/jpeg", "jpg").objectName();
        assertThatThrownBy(() -> ownership.validateAndBind(8L, key, UploadPurpose.IMAGE)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> ownership.validateAndBind(7L, "images/unknown.jpg", UploadPurpose.IMAGE)).isInstanceOf(BizException.class);
        verify(storageService, never()).stat(anyString());
    }

    @Test
    void otherOwnersAndUnregisteredLegacyObjectsAreNeverDeleted() {
        String key = ownership.presign(7L, UploadPurpose.IMAGE, "image/jpeg", null).objectName();
        ownership.deleteOwnedIfPresent(8L, key);
        ownership.deleteOwnedIfPresent(7L, "images/legacy-unregistered.jpg");
        verify(storageService, never()).deleteObject(anyString());
        assertThat(rows.get(key).getDeleted()).isFalse();
    }

    @Test
    void sourceCleanupDoesNotRequireObjectToStillExist() {
        String key = ownership.presign(7L, UploadPurpose.VIDEO, "video/mp4", null).objectName();
        ownership.deleteOwnedIfPresent(7L, key);
        ownership.deleteOwnedIfPresent(7L, key);
        assertThat(rows.get(key).getDeleted()).isTrue();
        assertThatThrownBy(() -> ownership.validateAndBind(7L, key, UploadPurpose.VIDEO)).isInstanceOf(BizException.class);
        verify(storageService, never()).stat(anyString());
    }

    @Test
    void storageDeletionWaitsUntilCommit() {
        String key = ownership.presign(7L, UploadPurpose.VIDEO, "video/mp4", null).objectName();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            ownership.deleteOwnedIfPresent(7L, key);
            verify(storageService, never()).deleteObject(key);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(storageService).deleteObject(key);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void derivedJpegInheritsCoverPurposeAndUploader() {
        String source = ownership.presign(7L, UploadPurpose.COVER, "image/heic", null).objectName();
        when(storageService.stat(source)).thenReturn(new StorageService.ObjectStat(100, "image/heic"));
        ownership.validateAndBind(7L, source, UploadPurpose.COVER);
        String jpeg = "images/2026-10-16/converted.jpg";
        when(storageService.stat(jpeg)).thenReturn(new StorageService.ObjectStat(300, "image/jpeg"));
        ownership.registerNormalized(7L, source, jpeg);
        assertThat(rows.get(jpeg).getUploaderId()).isEqualTo(7L);
        assertThat(rows.get(jpeg).getPurpose()).isEqualTo(UploadPurpose.COVER);
        assertThat(rows.get(jpeg).getBound()).isTrue();
        assertThatThrownBy(() -> ownership.validateAndBind(8L, jpeg, UploadPurpose.COVER)).isInstanceOf(BizException.class);
    }

    @Test
    void derivedObjectCannotTakeOverAnotherOwnersRegistration() {
        String key = ownership.presign(8L, UploadPurpose.VIDEO, "video/mp4", null).objectName();
        assertThatThrownBy(() -> ownership.registerDerived(7L, key, UploadPurpose.VIDEO)).isInstanceOf(BizException.class);
        assertThat(rows.get(key).getUploaderId()).isEqualTo(8L);
    }

    @Test
    void emptyFileAndWrongMimeAreRejected() {
        String key = ownership.presign(7L, UploadPurpose.IMAGE, "image/jpeg", null).objectName();
        when(storageService.stat(key)).thenReturn(new StorageService.ObjectStat(0, "image/jpeg"));
        assertThatThrownBy(() -> ownership.validateAndBind(7L, key, UploadPurpose.IMAGE)).isInstanceOf(BizException.class);
        when(storageService.stat(key)).thenReturn(new StorageService.ObjectStat(100, "image/png"));
        assertThatThrownBy(() -> ownership.validateAndBind(7L, key, UploadPurpose.IMAGE)).isInstanceOf(BizException.class);
    }
}
