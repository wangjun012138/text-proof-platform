package com.wangjun.text_proof_platform.modules.proof.service;

import com.wangjun.text_proof_platform.modules.audit.model.AuditEvent;
import com.wangjun.text_proof_platform.modules.audit.service.AuditLogAsyncService;
import com.wangjun.text_proof_platform.modules.proof.entity.TextProof;
import com.wangjun.text_proof_platform.modules.proof.entity.TextProofAudit;
import com.wangjun.text_proof_platform.modules.proof.repository.TextProofAuditRepository;
import com.wangjun.text_proof_platform.modules.proof.repository.TextProofRepository;
import com.wangjun.text_proof_platform.modules.share.repository.TextProofShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextProofServiceTest {

    @Mock
    private TextProofRepository textProofRepository;

    @Mock
    private ProofHashService proofHashService;

    @Mock
    private ProofStorageService proofStorageService;

    @Mock
    private Rfc3161TimestampService rfc3161TimestampService;

    @Mock
    private TextProofShareRepository textProofShareRepository;

    @Mock
    private TextProofAuditRepository textProofAuditRepository;

    @Mock
    private AuditLogAsyncService auditLogAsyncService;

    @InjectMocks
    private TextProofService textProofService;

    @Captor
    private ArgumentCaptor<TextProof> proofCaptor;

    @Captor
    private ArgumentCaptor<TextProofAudit> auditCaptor;

    @Captor
    private ArgumentCaptor<AuditEvent> auditEventCaptor;

    @Test
    void createFileProofShouldDeleteStoredFileWhenTimestampingFails() throws IOException {
        MockMultipartFile file = proofFile();
        ProofStorageService.StoredFile storedFile = storedProofFile();

        when(proofStorageService.store(file)).thenReturn(storedFile);
        when(proofHashService.digest(any(byte[].class))).thenReturn(new byte[]{1, 2, 3});
        when(proofHashService.sha256Hex(any(byte[].class))).thenReturn("hash");
        when(rfc3161TimestampService.timestamp(any(byte[].class)))
                .thenThrow(new IllegalStateException("timestamp failed"));

        assertThatThrownBy(() -> textProofService.createFileProof("subject", file, "alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("timestamp failed");

        verify(proofStorageService).deleteStoredFile("stored-proof.txt");
        verify(textProofRepository, never()).save(any(TextProof.class));
        verify(textProofAuditRepository, never()).save(any(TextProofAudit.class));
        verify(auditLogAsyncService, never()).publish(any(AuditEvent.class));
    }

    @Test
    void createFileProofShouldSaveInitialVersionAuditAndPublishAuditEventWhenNoTransactionSynchronization() throws IOException {
        MockMultipartFile file = proofFile();
        ProofStorageService.StoredFile storedFile = storedProofFile();
        LocalDateTime timestampAt = LocalDateTime.of(2026, 4, 15, 12, 0);

        stubSuccessfulFileCreate(file, storedFile, timestampAt);

        Long proofId = textProofService.createFileProof("subject", file, "alice");

        assertThat(proofId).isEqualTo(99L);
        verify(textProofRepository).save(proofCaptor.capture());
        verify(textProofAuditRepository).save(auditCaptor.capture());

        TextProof savedProof = proofCaptor.getValue();
        assertThat(savedProof.getVersionNo()).isEqualTo(1);
        assertThat(savedProof.getFilePath()).isEqualTo("stored-proof.txt");

        TextProofAudit audit = auditCaptor.getValue();
        assertThat(audit.getProofId()).isEqualTo(99L);
        assertThat(audit.getVersionNo()).isEqualTo(1);
        assertThat(audit.getAuditAction()).isEqualTo("CREATED");
        assertThat(audit.getRfc3161Status()).isEqualTo("STAMPED");
        verify(auditLogAsyncService).publish(auditEventCaptor.capture());

        AuditEvent event = auditEventCaptor.getValue();
        assertThat(event.getUsername()).isEqualTo("alice");
        assertThat(event.getAction()).isEqualTo("PROOF_FILE_CREATE");
        assertThat(event.getTargetType()).isEqualTo("PROOF");
        assertThat(event.getTargetId()).isEqualTo(99L);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getMessage()).isEqualTo("用户创建文件存证：proof.txt");
        verify(proofStorageService, never()).deleteStoredFile(anyString());
    }

    @Test
    void createFileProofShouldPublishAuditEventOnlyAfterTransactionCommit() throws IOException {
        MockMultipartFile file = proofFile();
        ProofStorageService.StoredFile storedFile = storedProofFile();
        LocalDateTime timestampAt = LocalDateTime.of(2026, 4, 15, 12, 0);

        stubSuccessfulFileCreate(file, storedFile, timestampAt);

        TransactionSynchronizationManager.initSynchronization();
        try {
            Long proofId = textProofService.createFileProof("subject", file, "alice");

            assertThat(proofId).isEqualTo(99L);
            verify(auditLogAsyncService, never()).publish(any(AuditEvent.class));

            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());

            verify(auditLogAsyncService).publish(auditEventCaptor.capture());
            AuditEvent event = auditEventCaptor.getValue();
            assertThat(event.getAction()).isEqualTo("PROOF_FILE_CREATE");
            assertThat(event.getTargetId()).isEqualTo(99L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MockMultipartFile proofFile() {
        return new MockMultipartFile(
                "file",
                "proof.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
    }

    private ProofStorageService.StoredFile storedProofFile() {
        return new ProofStorageService.StoredFile(
                "stored-proof.txt",
                "proof.txt",
                5L,
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
    }

    private void stubSuccessfulFileCreate(MockMultipartFile file,
                                          ProofStorageService.StoredFile storedFile,
                                          LocalDateTime timestampAt) throws IOException {
        when(proofStorageService.store(file)).thenReturn(storedFile);
        when(proofHashService.digest(any(byte[].class))).thenReturn(new byte[]{1, 2, 3});
        when(proofHashService.sha256Hex(any(byte[].class))).thenReturn("hash");
        when(rfc3161TimestampService.timestamp(any(byte[].class)))
                .thenReturn(TimestampResult.stamped("OFF", "token", timestampAt));
        when(textProofRepository.save(any(TextProof.class))).thenAnswer(invocation -> {
            TextProof proof = invocation.getArgument(0);
            proof.setId(99L);
            proof.setCreatedAt(timestampAt);
            proof.setUpdatedAt(timestampAt);
            return proof;
        });
    }
}
