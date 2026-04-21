package com.wangjun.text_proof_platform.modules.share.service;

import com.wangjun.text_proof_platform.common.BadRequestException;
import com.wangjun.text_proof_platform.common.ResourceNotFoundException;
import com.wangjun.text_proof_platform.modules.proof.entity.TextProof;
import com.wangjun.text_proof_platform.modules.proof.repository.TextProofRepository;
import com.wangjun.text_proof_platform.modules.proof.service.ProofStorageService;
import com.wangjun.text_proof_platform.modules.share.dto.CreateShareResponse;
import com.wangjun.text_proof_platform.modules.share.entity.TextProofShare;
import com.wangjun.text_proof_platform.modules.share.repository.TextProofShareRepository;
import com.wangjun.text_proof_platform.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextProofShareServiceTest {

    @Mock
    private TextProofShareRepository textProofShareRepository;

    @Mock
    private TextProofRepository textProofRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProofStorageService proofStorageService;

    @InjectMocks
    private TextProofShareService textProofShareService;

    @Captor
    private ArgumentCaptor<TextProofShare> shareCaptor;

    @Test
    void createUserShareShouldRejectMissingTargetUser() {
        TextProof proof = fileProof();
        when(textProofRepository.findByIdAndOwnerUsername(10L, "alice"))
                .thenReturn(Optional.of(proof));
        when(userRepository.existsByUsername("bob")).thenReturn(false);

        assertThatThrownBy(() -> textProofShareService.createUserShare(10L, "bob", 7, "alice"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Target user not found");

        verify(textProofShareRepository, never()).save(any(TextProofShare.class));
    }

    @Test
    void createUserShareShouldSaveOwnerAndTarget() {
        TextProof proof = fileProof();
        when(textProofRepository.findByIdAndOwnerUsername(10L, "alice"))
                .thenReturn(Optional.of(proof));
        when(userRepository.existsByUsername("bob")).thenReturn(true);
        when(textProofShareRepository.save(any(TextProofShare.class))).thenAnswer(invocation -> {
            TextProofShare share = invocation.getArgument(0);
            share.setId(77L);
            return share;
        });

        CreateShareResponse response = textProofShareService.createUserShare(10L, "bob", 7, "alice");

        assertThat(response.getShareId()).isEqualTo(77L);
        assertThat(response.getShareType()).isEqualTo("USER");
        assertThat(response.getTargetUsername()).isEqualTo("bob");
        assertThat(response.getShareToken()).isNull();

        verify(textProofShareRepository).save(shareCaptor.capture());
        TextProofShare saved = shareCaptor.getValue();
        assertThat(saved.getTextProofId()).isEqualTo(10L);
        assertThat(saved.getOwnerUsername()).isEqualTo("alice");
        assertThat(saved.getTargetUsername()).isEqualTo("bob");
        assertThat(saved.getExpireAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void userShareShouldHideRecordFromWrongTargetUser() {
        TextProofShare share = activeUserShare();
        when(textProofShareRepository.findById(99L)).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> textProofShareService.getUserSharedProof(99L, "mallory"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Share record not found");

        verify(textProofRepository, never()).findById(any());
    }

    @Test
    void revokedShareShouldBeRejected() {
        TextProofShare share = activeUserShare();
        share.setRevoked(true);
        when(textProofShareRepository.findById(99L)).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> textProofShareService.getUserSharedProof(99L, "bob"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Share revoked");
    }

    @Test
    void expiredTokenShareShouldBeRejected() {
        TextProofShare share = activeTokenShare();
        share.setExpireAt(LocalDateTime.now().minusSeconds(1));
        when(textProofShareRepository.findByShareToken("token")).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> textProofShareService.getTokenSharedProof("token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Share expired");
    }

    @Test
    void tokenSharedFileDownloadShouldLoadOnlyActiveFileProof() {
        TextProofShare share = activeTokenShare();
        TextProof proof = fileProof();
        ByteArrayResource resource = new ByteArrayResource("file".getBytes(StandardCharsets.UTF_8));
        when(textProofShareRepository.findByShareToken("token")).thenReturn(Optional.of(share));
        when(textProofRepository.findById(10L)).thenReturn(Optional.of(proof));
        when(proofStorageService.loadAsResource("stored.txt")).thenReturn(resource);

        TextProofShareService.DownloadedFile file = textProofShareService.downloadTokenSharedFile("token");

        assertThat(file.originalFilename()).isEqualTo("proof.txt");
        assertThat(file.mimeType()).isEqualTo("text/plain");
        assertThat(file.resource()).isSameAs(resource);
    }

    @Test
    void tokenSharedDownloadShouldRejectTextProof() {
        TextProofShare share = activeTokenShare();
        TextProof proof = fileProof();
        proof.setContentType("TEXT");
        proof.setFilePath(null);
        when(textProofShareRepository.findByShareToken("token")).thenReturn(Optional.of(share));
        when(textProofRepository.findById(10L)).thenReturn(Optional.of(proof));

        assertThatThrownBy(() -> textProofShareService.downloadTokenSharedFile("token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Shared item is not a file");
    }

    private TextProof fileProof() {
        TextProof proof = new TextProof();
        proof.setId(10L);
        proof.setOwnerUsername("alice");
        proof.setSubject("subject");
        proof.setContentType("FILE");
        proof.setFilePath("stored.txt");
        proof.setOriginalFilename("proof.txt");
        proof.setMimeType("text/plain");
        proof.setFileSize(4L);
        proof.setContentHash("hash");
        proof.setCreatedAt(LocalDateTime.now());
        proof.setUpdatedAt(LocalDateTime.now());
        proof.setVersionNo(1);
        return proof;
    }

    private TextProofShare activeUserShare() {
        TextProofShare share = new TextProofShare();
        share.setId(99L);
        share.setTextProofId(10L);
        share.setOwnerUsername("alice");
        share.setShareType("USER");
        share.setTargetUsername("bob");
        share.setExpireAt(LocalDateTime.now().plusDays(1));
        return share;
    }

    private TextProofShare activeTokenShare() {
        TextProofShare share = new TextProofShare();
        share.setId(99L);
        share.setTextProofId(10L);
        share.setOwnerUsername("alice");
        share.setShareType("TOKEN");
        share.setShareToken("token");
        share.setExpireAt(LocalDateTime.now().plusDays(1));
        return share;
    }
}
