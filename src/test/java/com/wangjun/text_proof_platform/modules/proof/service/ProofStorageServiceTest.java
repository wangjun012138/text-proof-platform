package com.wangjun.text_proof_platform.modules.proof.service;

import com.wangjun.text_proof_platform.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProofStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeShouldRandomizePathPreserveExtensionAndLoadResource() throws Exception {
        ProofStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evidence.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        ProofStorageService.StoredFile stored = service.store(file);

        assertThat(stored.getRelativePath()).endsWith(".txt");
        assertThat(stored.getRelativePath()).doesNotContain("evidence");
        assertThat(Files.readString(tempDir.resolve(stored.getRelativePath()))).isEqualTo("hello");

        Resource resource = service.loadAsResource(stored.getRelativePath());
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(5L);
    }

    @Test
    void storeShouldRejectEmptyFile() {
        ProofStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("File path cannot be empty");
    }

    @Test
    void loadShouldRejectPathTraversal() {
        ProofStorageService service = newService();

        assertThatThrownBy(() -> service.loadAsResource("../outside.txt"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid file path");
    }

    @Test
    void deleteShouldNotDeleteOutsideRoot() throws Exception {
        ProofStorageService service = newService();
        Path outside = tempDir.getParent().resolve("outside-proof-storage-test.txt");
        Files.writeString(outside, "keep", StandardCharsets.UTF_8);
        try {
            service.deleteStoredFile("../outside-proof-storage-test.txt");

            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    private ProofStorageService newService() {
        ProofStorageProperties properties = new ProofStorageProperties();
        properties.setRoot(tempDir.toString());
        return new ProofStorageService(properties);
    }
}
