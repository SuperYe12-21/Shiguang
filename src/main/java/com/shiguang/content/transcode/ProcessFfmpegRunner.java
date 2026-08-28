package com.shiguang.content.transcode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessFfmpegRunner implements FfmpegRunner {

    private static final long TIMEOUT_MINUTES = 10;

    private final TranscodeProperties props;

    @Override
    public void transcodeToMp4(Path input, Path output) {
        run(List.of(
                "-y", "-i", input.toString(),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k",
                "-movflags", "+faststart",
                output.toString()));
    }

    @Override
    public void extractCover(Path input, Path output) {
        run(List.of("-y", "-i", input.toString(), "-ss", "1", "-vframes", "1", "-q:v", "2", output.toString()));
    }

    private void run(List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(props.ffmpegPath());
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("FFmpeg 执行超时（" + TIMEOUT_MINUTES + " 分钟）");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("FFmpeg 执行失败，退出码 " + process.exitValue());
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 FFmpeg: " + props.ffmpegPath(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg 执行被中断", e);
        }
    }
}