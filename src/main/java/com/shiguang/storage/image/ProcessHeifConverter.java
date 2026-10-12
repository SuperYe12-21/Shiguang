package com.shiguang.storage.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 调 libheif 的 heif-convert 命令（Ubuntu: apt install libheif-examples） */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessHeifConverter implements HeifConverter {

    private static final long TIMEOUT_SECONDS = 30;

    /** 质量 90：再高对手机照片没意义，反而更慢 */
    private static final String QUALITY = "90";

    private final ImageProperties props;

    @Override
    public void toJpeg(Path input, Path output) {
        List<String> command = new ArrayList<>();
        command.add(props.heifConvertPath());
        command.add("-q");
        command.add(QUALITY);
        command.add(input.toString());
        command.add(output.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("heif-convert 执行超时");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("heif-convert 执行失败，退出码 " + process.exitValue());
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 heif-convert: " + props.heifConvertPath(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("heif-convert 执行被中断", e);
        }
    }
}
