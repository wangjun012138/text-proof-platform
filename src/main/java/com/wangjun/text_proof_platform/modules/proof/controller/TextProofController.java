package com.wangjun.text_proof_platform.modules.proof.controller;

import com.wangjun.text_proof_platform.common.ApiResponse;
import com.wangjun.text_proof_platform.modules.proof.dto.CreateTextProofRequest;
import com.wangjun.text_proof_platform.modules.proof.dto.TextProofDetailResponse;
import com.wangjun.text_proof_platform.modules.proof.dto.TextProofListItemResponse;
import com.wangjun.text_proof_platform.modules.proof.dto.UpdateTextProofRequest;
import com.wangjun.text_proof_platform.modules.proof.entity.TextProofAudit;
import com.wangjun.text_proof_platform.modules.proof.service.TextProofService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/proof")
public class TextProofController {

    private final TextProofService textProofService;

    public TextProofController(TextProofService textProofService) {
        this.textProofService = textProofService;
    }
    //接 JSON，创建短文本存证。
    @PostMapping("/text")
    public ApiResponse<Long> createTextProof(@RequestBody @Valid CreateTextProofRequest req,
                                             Principal principal) {
        String username = requireUsername(principal);
        Long id = textProofService.createTextProof(
                req.getSubject(),
                req.getContent(),
                username
        );
        return ApiResponse.success("Text proof created", id);
    }
    //接file，存储文件
    @PostMapping("/file")
    public ApiResponse<Long> createFileProof(@RequestParam
                                             @NotBlank(message = "Subject cannot be blank")
                                             @Size(max = 255, message = "Subject length cannot exceed 255 characters")
                                             String subject,
                                             @RequestPart("file") MultipartFile file,
                                             Principal principal) throws IOException {
        String username = requireUsername(principal);
        Long id = textProofService.createFileProof(subject, file, username);
        return ApiResponse.success("File proof created", id);
    }
    //查当前用户自己的列表。
    @GetMapping("/list")
    public ApiResponse<List<TextProofListItemResponse>> listMyProofs(Principal principal) {
        String username = requireUsername(principal);
        return ApiResponse.success("Query succeeded", textProofService.listMyProofs(username));
    }
    //查详情。
    @GetMapping("/{id}")
    public ApiResponse<TextProofDetailResponse> getDetail(@PathVariable Long id,
                                                          Principal principal) {
        String username = requireUsername(principal);
        return ApiResponse.success("Query succeeded", textProofService.getDetail(id, username));
    }
    //返回文件下载响应。
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id,
                                                 Principal principal) {
        String username = requireUsername(principal);
        TextProofService.DownloadedFile file = textProofService.downloadFile(id, username);

        MediaType mediaType;
        try {
            mediaType = file.mimeType() != null
                    ? MediaType.parseMediaType(file.mimeType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String filename = file.originalFilename() != null
                ? file.originalFilename()
                : "download.bin";

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(file.resource());
    }
    //更新文本存证。
    @PutMapping("/{id}/text")
    public ApiResponse<Long> updateTextProof(@PathVariable Long id,
                                             @RequestBody @Valid UpdateTextProofRequest req,
                                             Principal principal) {
        String username = requireUsername(principal);
        Long updatedId = textProofService.updateTextProof(
                id,
                req.getSubject(),
                req.getContent(),
                username
        );
        return ApiResponse.success("Text proof updated", updatedId);
    }
    //更新文件存证。
    @PutMapping("/{id}/file")
    public ApiResponse<Long> updateFileProof(@PathVariable Long id,
                                             @RequestParam
                                             @NotBlank(message = "Subject cannot be blank")
                                             @Size(max = 255, message = "Subject length cannot exceed 255 characters")
                                             String subject,
                                             @RequestPart("file") MultipartFile file,
                                             Principal principal) throws IOException {
        String username = requireUsername(principal);
        Long updatedId = textProofService.updateFileProof(id, subject, file, username);
        return ApiResponse.success("File proof updated", updatedId);
    }
    //删除存证。
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProof(@PathVariable Long id,
                                         Principal principal) {
        String username = requireUsername(principal);
        textProofService.deleteProof(id, username);
        return ApiResponse.success("Proof deleted");
    }
    //查看某条存证的历史版本
    @GetMapping("/{id}/history")
    public ApiResponse<List<TextProofAudit>> getHistory(@PathVariable Long id, Principal principal) {
        String username = requireUsername(principal);
        return ApiResponse.success("Query succeeded", textProofService.getProofHistory(id, username));
    }
    private String requireUsername(Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Not logged in");
        }
        return principal.getName();
    }

}