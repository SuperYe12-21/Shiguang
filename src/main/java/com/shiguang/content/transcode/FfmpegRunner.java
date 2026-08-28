package com.shiguang.content.transcode;

import java.nio.file.Path;

public interface FfmpegRunner {

    void transcodeToMp4(Path input, Path output);

    void extractCover(Path input, Path output);
}
