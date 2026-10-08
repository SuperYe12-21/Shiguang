package com.shiguang.storage;

import io.minio.errors.ErrorResponseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=(\\d*)-(\\d*)$", Pattern.CASE_INSENSITIVE);

    /** 对象名带随机 UUID，内容永不变，可长期缓存；避免回看时重复下载 */
    private static final String MEDIA_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final StorageService storageService;

    @RequestMapping(value = "/{*objectName}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void serve(@PathVariable String objectName,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        if (objectName == null || objectName.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        StorageService.ObjectStat stat;
        try {
            stat = storageService.stat(objectName);
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null ? "unknown" : e.errorResponse().code();
            log.warn("媒体对象读取失败[{}]: {} ({})", code, objectName, e.errorResponse() == null
                    ? e.getMessage() : e.errorResponse().message());
            response.sendError("NoSuchKey".equals(code)
                    ? HttpServletResponse.SC_NOT_FOUND
                    : HttpServletResponse.SC_BAD_GATEWAY);
            return;
        } catch (Exception e) {
            log.warn("媒体对象读取异常: {} ({})", objectName, e.toString());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
            return;
        }

        long size = stat.size();
        if (size <= 0) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(0);
            return;
        }
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(resolveContentType(objectName, stat.contentType()));
        response.setHeader("Cache-Control", MEDIA_CACHE_CONTROL);

        long start = 0;
        long end = size - 1;
        boolean partial = false;
        String range = request.getHeader("Range");
        if (range != null && !range.isBlank()) {
            Matcher matcher = RANGE_PATTERN.matcher(range.trim());
            if (matcher.matches()) {
                partial = true;
                if (matcher.group(1).isEmpty()) {
                    long suffix = Long.parseLong(matcher.group(2));
                    start = Math.max(0, size - Math.max(suffix, 1));
                } else {
                    start = Math.min(Long.parseLong(matcher.group(1)), size - 1);
                    end = matcher.group(2).isEmpty()
                            ? size - 1
                            : Math.min(Long.parseLong(matcher.group(2)), size - 1);
                }
                if (start > end) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + size);
                    return;
                }
            } else {
                partial = false;
            }
        }

        long length = end - start + 1;
        response.setHeader("Content-Length", String.valueOf(length));
        if (partial) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            return;
        }
        for (int attempt = 1; attempt <= 2; attempt++) {
            try (InputStream in = storageService.open(objectName, start, length)) {
                in.transferTo(response.getOutputStream());
                return;
            } catch (ErrorResponseException e) {
                String code = e.errorResponse() == null ? "unknown" : e.errorResponse().code();
                if (attempt < 2 && !response.isCommitted()) {
                    log.warn("媒体数据读取失败[{}]（第 {} 次，重试一次）: {}", code, attempt, objectName);
                    continue;
                }
                log.warn("媒体数据读取失败[{}]: {} ({})", code, objectName, e.errorResponse() == null
                        ? e.getMessage() : e.errorResponse().message());
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
                }
                return;
            } catch (IOException e) {
                if (attempt < 2 && !response.isCommitted()) {
                    log.warn("媒体输出中断（第 {} 次，重试一次）: {}", attempt, objectName);
                    continue;
                }
                if (response.isCommitted()) {
                    log.debug("媒体输出被客户端中断: {} ({})", objectName, e.toString());
                } else {
                    log.warn("媒体输出失败: {}", objectName, e);
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
                }
                return;
            } catch (RuntimeException e) {
                if (attempt < 2 && !response.isCommitted()) {
                    log.warn("媒体读取异常（第 {} 次，重试一次）: {} ({})", attempt, objectName, e.getMessage());
                    continue;
                }
                log.warn("媒体读取异常: {} ({})", objectName, e.toString());
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
                }
                return;
            }
        }
    }

    private static String resolveContentType(String objectName, String stored) {
        if (stored != null && !stored.isBlank() && !stored.startsWith("application/octet-stream")) {
            return stored;
        }
        String lower = objectName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }
}
