package com.orbitworkbench.resume.application;

import com.orbitworkbench.resume.domain.ResumeDocumentRecord;
import com.orbitworkbench.resume.domain.ResumeVersionRecord;
import com.orbitworkbench.resume.infrastructure.mapper.ResumeMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** 归属解析与下载文件名清洗：导出与编辑两条路径必须共用，避免各写一套校验。 */
public final class ResumeSupport {

    private static final int FILE_NAME_MAX_CODEPOINTS = 60;

    private ResumeSupport() {
    }

    public static ResumeDocumentRecord requireDocument(ResumeMapper mapper, Long userId) {
        ResumeDocumentRecord document = mapper.findDocumentByUser(userId);
        if (document == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "还没有简历，请先生成初稿");
        }
        return document;
    }

    public static ResumeVersionRecord requireVersion(ResumeMapper mapper,
                                                     ResumeDocumentRecord document, Long versionId) {
        ResumeVersionRecord version = mapper.findVersion(versionId, document.getId());
        if (version == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "简历版本不存在");
        }
        return version;
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }

    /**
     * 文件名来自用户可控的简历标题：剔除控制字符、引号与路径分隔符，压掉空白，
     * 限长后再拼版本号，避免响应头注入与超长 Content-Disposition。
     */
    public static String fileName(String title, int versionNumber) {
        StringBuilder builder = new StringBuilder();
        String source = title == null ? "" : title;
        for (int index = 0; index < source.length(); index += 1) {
            char symbol = source.charAt(index);
            boolean forbidden = Character.isISOControl(symbol) || Character.isWhitespace(symbol)
                    || symbol == '"' || symbol == '\'' || symbol == '/' || symbol == '\\'
                    || symbol == ':' || symbol == '*' || symbol == '?' || symbol == '<'
                    || symbol == '>' || symbol == '|' || symbol == '%';
            if (!forbidden) {
                builder.append(symbol);
            }
        }
        String cleaned = builder.toString().strip();
        if (cleaned.codePointCount(0, cleaned.length()) > FILE_NAME_MAX_CODEPOINTS) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, FILE_NAME_MAX_CODEPOINTS));
        }
        if (cleaned.isBlank()) {
            cleaned = "resume";
        }
        return cleaned + "-v" + versionNumber + ".pdf";
    }
}
