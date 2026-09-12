package com.orbitworkbench.ai.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 系统提示词目录：prompt 正文外置于 {@code resources/prompts/*.txt}，代码只保留引用。
 *
 * <p>加载是类路径静态读取，UTF-8 解码后原样返回（不 trim、不追加换行），
 * 因此文件字节与旧 Java 文本块必须逐字节一致——面试报告的 SCORING_RULE_VERSION
 * 以提示词字节参与哈希，任何改写都会让历史报告版本对不上（V30 可比性原则）。
 * 对应的字节一致性由 PromptCatalogTest 在构建期锁死。
 */
public final class PromptCatalog {

    private static final String DIRECTORY = "/prompts/";

    private PromptCatalog() {
    }

    /** 读取 prompts 目录下的文件；缺失或为空视为启动期错误，直接抛异常。 */
    public static String load(String name) {
        String path = DIRECTORY + name + ".txt";
        try (InputStream stream = PromptCatalog.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("缺少提示词资源: " + path);
            }
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (text.isBlank()) {
                throw new IllegalStateException("提示词资源为空: " + path);
            }
            return text;
        } catch (IOException exception) {
            throw new IllegalStateException("读取提示词资源失败: " + path, exception);
        }
    }
}
