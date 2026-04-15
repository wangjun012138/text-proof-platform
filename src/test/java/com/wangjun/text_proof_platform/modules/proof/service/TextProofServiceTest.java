package com.wangjun.text_proof_platform.modules.proof.service;

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

    @InjectMocks
    private TextProofService textProofService;

    @Captor
    private ArgumentCaptor<TextProof> proofCaptor;

    @Captor
    private ArgumentCaptor<TextProofAudit> auditCaptor;

    @Test
    void createFileProofShouldDeleteStoredFileWhenTimestampingFails() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        ProofStorageService.StoredFile storedFile = new ProofStorageService.StoredFile(
                "stored-proof.txt",
                "proof.txt",
                5L,
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

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
    }

    @Test
    void createFileProofShouldSaveInitialVersionAudit() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        ProofStorageService.StoredFile storedFile = new ProofStorageService.StoredFile(
                "stored-proof.txt",
                "proof.txt",
                5L,
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        LocalDateTime timestampAt = LocalDateTime.of(2026, 4, 15, 12, 0);

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
        verify(proofStorageService, never()).deleteStoredFile(anyString());
    }
}
