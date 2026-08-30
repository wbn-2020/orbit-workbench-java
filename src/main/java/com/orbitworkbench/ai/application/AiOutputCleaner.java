package com.orbitworkbench.ai.application;

/**
 * 模型输出清洗与结构化片段提取。
 *
 * <p>清洗只做确定性的文本规整（去围栏、去题号/标签前缀、只保留第一问、按句末截断），
 * 不做语义改写；提取 JSON 时按括号配平扫描并跳过字符串字面量，
 * 避免"首个 { 到末个 } "把尾部散文里的括号一起吞进来导致整次解析失败。
 */
public final class AiOutputCleaner {

    private static final int MAX_SCAN_CHARS = 200_000;

    private AiOutputCleaner() {
    }

    /**
     * 清洗出题结果：去掉 Markdown 围栏与列表/题号/"追问："等前缀，多问只留第一问，
     * 并按句子边界截到 maxLength（不在词中间硬切）。
     */
    public static String cleanQuestion(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String text = stripCodeFence(raw);
        String[] lines = text.split("\\R");
        String firstMeaningful = null;
        for (String line : lines) {
            String candidate = stripLinePrefixes(line);
            if (candidate.isEmpty()) {
                continue;
            }
            if (firstMeaningful == null) {
                firstMeaningful = candidate;
                continue;
            }
            // 第一问已成形而模型又续了第二问：只保留第一问，避免一次抛出多题
            if (endsWithQuestionMark(firstMeaningful)) {
                break;
            }
            firstMeaningful = joinLines(firstMeaningful, candidate);
        }
        String question = firstMeaningful == null ? "" : firstMeaningful;
        question = question.replaceAll("\\s+", " ").trim();
        question = stripWrappingQuotes(question);
        return truncateAtBoundary(question, maxLength);
    }

    /** 提取第一个配平的 JSON 对象；不存在时返回 null。 */
    public static String extractJsonObject(String raw) {
        return extractBalanced(raw, '{', '}');
    }

    /** 提取第一个配平的 JSON 数组；不存在时返回 null。 */
    public static String extractJsonArray(String raw) {
        return extractBalanced(raw, '[', ']');
    }

    /** 去掉 ```json ... ``` 围栏，只保留内部内容（若整段被围栏包裹）。 */
    public static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0) {
            return text;
        }
        String body = text.substring(firstLineEnd + 1);
        int fenceEnd = body.lastIndexOf("```");
        return fenceEnd >= 0 ? body.substring(0, fenceEnd).trim() : body.trim();
    }

    /** 单行摘要：压成一行并截断，用于错误提示，避免把模型原文整段带进日志与库。 */
    public static String summarize(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String flat = raw.replaceAll("\\s+", " ").trim();
        return flat.length() <= maxLength ? flat : flat.substring(0, maxLength) + "…";
    }

    /** 按列宽裁剪但保留换行，用于允许多行的正文字段。 */
    public static String truncate(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= maxLength ? raw : raw.substring(0, maxLength) + "…";
    }

    private static String extractBalanced(String raw, char open, char close) {
        if (raw == null) {
            return null;
        }
        String text = stripCodeFence(raw);
        if (text.length() > MAX_SCAN_CHARS) {
            text = text.substring(0, MAX_SCAN_CHARS);
        }
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index += 1) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == open) {
                if (depth == 0) {
                    start = index;
                }
                depth += 1;
            } else if (current == close && depth > 0) {
                depth -= 1;
                if (depth == 0 && start >= 0) {
                    return text.substring(start, index + 1);
                }
            }
        }
        return null;
    }

    private static String stripLinePrefixes(String line) {
        String value = line == null ? "" : line.trim();
        String previous;
        do {
            previous = value;
            value = value.replaceFirst("^(#{1,6}\\s*)", "")
                    .replaceFirst("^>\\s*", "")
                    .replaceFirst("^[-*+]\\s+", "")
                    .replaceFirst("^(\\*{1,3}|_{2})\\s*", "")
                    .replaceFirst("\\s*(\\*{1,3}|_{2})$", "")
                    .replaceFirst("^\\d+[.、)]\\s*", "")
                    .replaceFirst("^第[0-9一二三四五六七八九十]+[题问][:：.、]?\\s*", "")
                    .replaceFirst("^(追问|问题|提问|下一题|题目|Q\\d*)[:：]\\s*", "")
                    .replaceFirst("^(追问|问题|提问|下一题|题目|Q\\d*)\\s+", "")
                    .trim();
        } while (!value.equals(previous) && !value.isEmpty());
        return value;
    }

    private static String joinLines(String first, String second) {
        return first.isEmpty() ? second : first + " " + second;
    }

    private static boolean endsWithQuestionMark(String value) {
        return value.endsWith("？") || value.endsWith("?");
    }

    private static String stripWrappingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        char head = value.charAt(0);
        char tail = value.charAt(value.length() - 1);
        boolean paired = (head == '"' && tail == '"') || (head == '“' && tail == '”')
                || (head == '‘' && tail == '’') || (head == '《' && tail == '》')
                || (head == '\'' && tail == '\'');
        return paired ? value.substring(1, value.length() - 1).trim() : value;
    }

    private static String truncateAtBoundary(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        String window = value.substring(0, maxLength);
        int boundary = Math.max(Math.max(window.lastIndexOf('。'), window.lastIndexOf('？')),
                Math.max(window.lastIndexOf('?'), window.lastIndexOf('!')));
        if (boundary >= maxLength / 2) {
            return window.substring(0, boundary + 1);
        }
        return window.trim() + "…";
    }
}
