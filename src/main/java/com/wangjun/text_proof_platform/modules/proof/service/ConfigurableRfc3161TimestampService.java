package com.wangjun.text_proof_platform.modules.proof.service;

import org.bouncycastle.tsp.*;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Locale;

@Service
public class ConfigurableRfc3161TimestampService implements Rfc3161TimestampService {
    //配置对象，用来读取配置文件里 RFC3161 的参数，
    private final Rfc3161Properties properties;

    //Java 自带的 HTTP 客户端，用来向外部 TSA 服务发请求。
    private final HttpClient httpClient;

    public ConfigurableRfc3161TimestampService(Rfc3161Properties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public TimestampResult timestamp(byte[] digest) {
        String mode = properties.getMode() == null
                ? "OFF"
                : properties.getMode().trim().toUpperCase(Locale.ROOT);

        return switch (mode) {
            case "OFF" -> TimestampResult.none();//直接返回“未启用时间戳”。
            case "LOCAL_DEMO" -> localDemo(digest);
            case "FREETSA" -> freeTsa(digest);
            default -> TimestampResult.failed("UNKNOWN", "Unsupported RFC3161 mode: " + mode);
        };
    }
    //不连外部服务，生成一个本地模拟结果。
    private TimestampResult localDemo(byte[] digest) {
        LocalDateTime now = LocalDateTime.now();
        String token = Base64.getEncoder().encodeToString(
                ("LOCAL_DEMO|" + bytesToHex(digest) + "|" + now).getBytes()
        );
        return TimestampResult.stamped("LOCAL_DEMO", token, now);
    }
    //调用真实 TSA 服务。
    private TimestampResult freeTsa(byte[] digest) {
        try {
            TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
            generator.setCertReq(true);
            //nonce：给每次请求附加一个一次性标识，使客户端能够验证响应确实对应当前请求，而不是旧请求或其他请求的结果。
            BigInteger nonce = BigInteger.valueOf(System.currentTimeMillis());
            TimeStampRequest tsRequest = generator.generate(TSPAlgorithms.SHA256, digest, nonce);
            //准备把RFC3161请求发送到外部TSA
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/timestamp-query")
                    .header("Accept", "application/timestamp-reply")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(tsRequest.getEncoded()))
                    .build();
            //真正向 TSA 服务发请求，并拿到字节数组响应。
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                return TimestampResult.failed("FREETSA", "TSA HTTP status = " + response.statusCode());
            }
            //把响应字节解析成 RFC3161 响应对象。
            TimeStampResponse tsResponse = new TimeStampResponse(response.body());
            //验证响应tsResponse和原始tsRequest是否匹配。
            tsResponse.validate(tsRequest);
            //

            if (tsResponse.getFailInfo() != null && tsResponse.getFailInfo().intValue() != 0) {
                return TimestampResult.failed("FREETSA", "TSA returned failInfo=" + tsResponse.getFailInfo().intValue());
            }
            //获取时间戳令牌
            TimeStampToken token = tsResponse.getTimeStampToken();
            if (token == null) {
                return TimestampResult.failed("FREETSA", "No timestamp token returned");
            }
            //从时间戳 token 里取出 TSA 生成时间戳的时间 genTime，再转成本地时区的 LocalDateTime。
            LocalDateTime timestampAt = LocalDateTime.ofInstant(
                    token.getTimeStampInfo().getGenTime().toInstant(),
                    ZoneId.systemDefault()
            );
            //token 的二进制内容转成 Base64 字符串,方便存入数据库
            String tokenBase64 = Base64.getEncoder().encodeToString(token.getEncoded());
            return TimestampResult.stamped("FREETSA", tokenBase64, timestampAt);

        } catch (Exception e) {
            return TimestampResult.failed("FREETSA", e.getMessage());
        }
    }
    //把字节数组转成十六进制字符串。摘要转成可读字符串拼进模拟 token.
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}