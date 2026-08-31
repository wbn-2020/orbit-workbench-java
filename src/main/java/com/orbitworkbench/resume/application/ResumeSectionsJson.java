package com.orbitworkbench.resume.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.resume.application.ResumeSections.Model;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;

/** sections_json 与 source_snapshot_json 的唯一读写入口，顺带把体积上限钉在这里。 */
@Component
public class ResumeSectionsJson {

    private final ObjectMapper objectMapper;

    public ResumeSectionsJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Model model) {
        try {
            String json = objectMapper.writeValueAsString(model);
            if (json.getBytes(StandardCharsets.UTF_8).length > ResumeSections.MAX_JSON_BYTES) {
                throw ResumeSections.invalid("简历区块序列化后超过 512 KB");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw ResumeSections.invalid("简历区块无法序列化");
        }
    }

    public Model read(String json) {
        if (json == null || json.isBlank()) {
            throw ResumeSections.invalid("简历正文为空");
        }
        try {
            return objectMapper.readValue(json, Model.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "简历正文结构已损坏，请从历史版本复制为新草稿后重试");
        }
    }

    public String writeNullable(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
