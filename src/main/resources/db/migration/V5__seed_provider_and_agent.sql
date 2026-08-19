INSERT INTO provider_catalog (
    provider_code, display_name, adapter_type, default_protocols, capabilities_json,
    enabled, created_at, updated_at
) VALUES
    ('CUSTOM_OPENAI_COMPATIBLE', 'Custom OpenAI Compatible', 'OPENAI_COMPATIBLE',
     JSON_ARRAY('CHAT_COMPLETIONS', 'RESPONSES'),
     JSON_OBJECT('supportsChatCompletions', true, 'supportsResponses', true, 'supportsStreaming', true),
     1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('OPENAI', 'OpenAI', 'OPENAI',
     JSON_ARRAY('CHAT_COMPLETIONS', 'RESPONSES'),
     JSON_OBJECT('supportsChatCompletions', true, 'supportsResponses', true, 'supportsStreaming', true),
     1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('DEEPSEEK', 'DeepSeek', 'DEEPSEEK',
     JSON_ARRAY('CHAT_COMPLETIONS', 'RESPONSES'),
     JSON_OBJECT('supportsChatCompletions', true, 'supportsResponses', true, 'supportsStreaming', true),
     1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('XAI', 'xAI', 'XAI',
     JSON_ARRAY('CHAT_COMPLETIONS', 'RESPONSES'),
     JSON_OBJECT('supportsChatCompletions', true, 'supportsResponses', true, 'supportsStreaming', true),
     1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO prompt_template (name, purpose, created_at, updated_at)
VALUES ('技术学习任务', '基于任务目标和用户资料生成结构化学习笔记与练习题', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO prompt_version (template_id, version_number, content, variables_json, created_at)
SELECT id, 1,
       '你是一名技术学习助手。请根据用户目标与资料生成 Markdown 学习笔记，包含核心概念、关键步骤、常见误区、练习题和参考答案。',
       JSON_ARRAY('taskTitle', 'taskDescription', 'documents'),
       UTC_TIMESTAMP(6)
FROM prompt_template
WHERE name = '技术学习任务';

INSERT INTO agent_definition (
    name, description, status, default_connection_id, default_model_profile_id,
    prompt_version_id, configuration_json, created_at, updated_at
)
SELECT '技术学习 Agent',
       '根据任务目标和文本资料生成学习笔记与练习题',
       'ACTIVE',
       NULL,
       NULL,
       pv.id,
       JSON_OBJECT('moduleType', 'TECH_LEARNING'),
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6)
FROM prompt_version pv
JOIN prompt_template pt ON pt.id = pv.template_id
WHERE pt.name = '技术学习任务' AND pv.version_number = 1;

