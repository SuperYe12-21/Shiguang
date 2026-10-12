package com.shiguang.storage.image;

import java.nio.file.Path;

/** HEIC/HEIF → JPEG：Chromium 系浏览器解不了 HEIC，只能在服务端转掉 */
public interface HeifConverter {

    void toJpeg(Path input, Path output);
}
