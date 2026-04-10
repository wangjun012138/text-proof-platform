package com.wangjun.text_proof_platform.modules.share.controller;

import com.wangjun.text_proof_platform.common.ApiResponse;
import com.wangjun.text_proof_platform.modules.share.dto.CreateShareResponse;
import com.wangjun.text_proof_platform.modules.share.dto.CreateTokenShareRequest;
import com.wangjun.text_proof_platform.modules.share.dto.CreateUserShareRequest;
import com.wangjun.text_proof_platform.modules.share.dto.SharedTextProofDetailResponse;
import com.wangjun.text_proof_platform.modules.share.dto.TextProofShareListItemResponse;
import com.wangjun.text_proof_platform.modules.share.service.TextProofShareService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/share")
public class TextProofShareController {

    private final TextProofShareService textProofShareService;

    public TextProofShareController(TextProofShareService textProofShareService) {
        this.textProofShareService = textProofShareService;
    }
    //按用户分享
    @PostMapping("/user")
    public ApiResponse<CreateShareResponse> createUserShare(@RequestBody @Valid CreateUserShareRequest req,
                                                            Principal principal) {
        //从 Principal 拿当前登录用户名
        String username = requireUsername(principal);
        CreateShareResponse response = textProofShareService.createUserShare(
                req.getTextProofId(),
                req.getTargetUsername(),
                req.getExpireDays(),
                username
        );
        return ApiResponse.success("User share created", response);
    }
    //按照token创建分享
    @PostMapping("/token")
    public ApiResponse<CreateShareResponse> createTokenShare(@RequestBody @Valid CreateTokenShareRequest req,
                                                             Principal principal) {
        String username = requireUsername(principal);
        CreateShareResponse response = textProofShareService.createTokenShare(
                req.getTextProofId(),
                req.getExpireDays(),
                username
        );
        return ApiResponse.success("Token share created", response);
    }
    //查看我自己发出的分享列表
    @GetMapping("/my/list")
    public ApiResponse<List<TextProofShareListItemResponse>> listMyShares(Principal principal) {
        String username = requireUsername(principal);
        return ApiResponse.success("Query succeeded", textProofShareService.listMyShares(username));
    }
    //撤销分享
    @PostMapping("/{id}/revoke")
    public ApiResponse<Void> revokeShare(@PathVariable Long id,
                                         Principal principal) {
        String username = requireUsername(principal);
        textProofShareService.revokeShare(id, username);
        return ApiResponse.success("Share revoked");
    }
    //给“被指定的那个用户”查看分享内容。
    @GetMapping("/user/{id}")
    public ApiResponse<SharedTextProofDetailResponse> getUserSharedProof(@PathVariable Long id,
                                                                         Principal principal) {
        String username = requireUsername(principal);
        return ApiResponse.success("Query succeeded", textProofShareService.getUserSharedProof(id, username));
    }
    //匿名也能访问 token 分享内容。
    @GetMapping("/token/{token}")
    public ApiResponse<SharedTextProofDetailResponse> getTokenSharedProof(@PathVariable String token) {
        return ApiResponse.success("Query succeeded", textProofShareService.getTokenSharedProof(token));
    }
    //登录用户下载别人分享给我的文件
    @GetMapping("/user/{id}/download")
    public ResponseEntity<Resource> downloadUserSharedFile(@PathVariable Long id,
                                                           Principal principal) {
        String username = requireUsername(principal);
        TextProofShareService.DownloadedFile file = textProofShareService.downloadUserSharedFile(id, username);
        return buildDownloadResponse(file);
    }
    //匿名用户用token下载
    @GetMapping("/token/{token}/download")
    public ResponseEntity<Resource> downloadTokenSharedFile(@PathVariable String token) {
        TextProofShareService.DownloadedFile file = textProofShareService.downloadTokenSharedFile(token);
        return buildDownloadResponse(file);
    }
    //下载响应构造器: 把一个已经准备好的,可下载文件对象,转换成真正的 HTTP 下载响应
    private ResponseEntity<Resource> buildDownloadResponse(TextProofShareService.DownloadedFile file) {
        //保存这次下载响应的文件类型
        MediaType mediaType;

        try {
            mediaType = file.mimeType() != null
                    //parseMediaType把字符串类型转换成 Spring 的 MediaType 对象。
                    ? MediaType.parseMediaType(file.mimeType())
                    //通用二进制文件流
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        //决定浏览器下载文件时显示的名字。
        String filename = file.originalFilename() != null
                ? file.originalFilename()
                : "download.bin";
        //构造HTTP 响应:
        return ResponseEntity.ok()
                //把前面解析好的文件类型设置到响应头
                .contentType(mediaType)
                //请把这个响应作为一个附件下载，并把文件名显示成指定的那个名字。
                //Content-Disposition 是一个 HTTP 响应头，用来告诉浏览器：我返回给你的这份内容，应该怎么处理。
                //inline（直接展示），attachment（触发下载）
                //.filename(filename):浏览器下载的默认文件名
                //.build().toString():把这个响应头对象真正构造成字符串，放进 header 里。
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                //真正的文件内容放到响应体里
                .body(file.resource());
    }
    //统一从 Principal 拿当前登录用户名
    private String requireUsername(Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Not logged in");
        }
        return principal.getName();
    }
}