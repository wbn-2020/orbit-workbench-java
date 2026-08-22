package com.orbitworkbench.workflow.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WorkflowGraphValidator {

    public static final int MAX_NODES = 50;
    public static final int MAX_EDGES = 100;
    public static final int MAX_CONDITIONS = 8;

    private static final Set<String> NODE_TYPES = Set.of(
            "START", "AGENT", "TOOL", "APPROVAL", "CONDITION", "END");
    private static final Set<String> OPERATORS = Set.of(
            "EQUALS", "NOT_EQUALS", "EXISTS", "NOT_EXISTS");
    private static final Set<String> SCALAR_TYPES = Set.of(
            "java.lang.String", "java.lang.Boolean",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
            "java.lang.Long", "java.lang.Float", "java.lang.Double");

    public ValidationResult validate(Graph graph) {
        List<String> errors = new ArrayList<>();
        if (graph == null || graph.nodes() == null || graph.edges() == null) {
            return new ValidationResult(List.of("nodes 和 edges 不能为空"));
        }
        if (graph.nodes().isEmpty()) {
            errors.add("workflow 至少需要一个节点");
        }
        if (graph.nodes().size() > MAX_NODES) {
            errors.add("workflow 节点数不能超过 " + MAX_NODES);
        }
        if (graph.edges().size() > MAX_EDGES) {
            errors.add("workflow 边数不能超过 " + MAX_EDGES);
        }

        Map<String, Node> nodes = new LinkedHashMap<>();
        int startCount = 0;
        int endCount = 0;
        int conditionCount = 0;
        for (Node node : graph.nodes()) {
            if (node == null) {
                errors.add("节点不能为 null");
                continue;
            }
            String key = node.key();
            if (key == null || key.isBlank()) {
                errors.add("节点 key 不能为空");
                continue;
            }
            if (nodes.putIfAbsent(key, node) != null) {
                errors.add("节点 key 重复: " + key);
                continue;
            }
            if (!NODE_TYPES.contains(node.type())) {
                errors.add("节点类型不支持: " + key);
            }
            if (node.name() == null || node.name().isBlank()) {
                errors.add("节点名称不能为空: " + key);
            }
            if ("START".equals(node.type())) {
                startCount++;
            } else if ("END".equals(node.type())) {
                endCount++;
            } else if ("CONDITION".equals(node.type())) {
                conditionCount++;
            }
            validateConfig(node, errors);
        }
        if (startCount != 1) {
            errors.add("workflow 必须且只能有一个 START 节点");
        }
        if (endCount != 1) {
            errors.add("workflow 必须且只能有一个 END 节点");
        }
        if (conditionCount > MAX_CONDITIONS) {
            errors.add("CONDITION 节点数不能超过 " + MAX_CONDITIONS);
        }

        Map<String, List<Edge>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Set<String> routes = new HashSet<>();
        for (String key : nodes.keySet()) {
            outgoing.put(key, new ArrayList<>());
            indegree.put(key, 0);
        }
        for (Edge edge : graph.edges()) {
            if (edge == null) {
                errors.add("边不能为 null");
                continue;
            }
            if (!nodes.containsKey(edge.from())) {
                errors.add("边来源节点不存在: " + edge.from());
                continue;
            }
            if (!nodes.containsKey(edge.to())) {
                errors.add("边目标节点不存在: " + edge.to());
                continue;
            }
            if (edge.from().equals(edge.to())) {
                errors.add("不允许节点自环: " + edge.from());
                continue;
            }
            String branch = normalizeBranch(edge.branch());
            String routeKey = edge.from() + "\u0000" + edge.to() + "\u0000" + branch;
            if (!routes.add(routeKey)) {
                errors.add("重复的 workflow 边: " + edge.from() + " -> " + edge.to());
                continue;
            }
            Edge normalized = new Edge(edge.from(), edge.to(), branch, edge.order());
            outgoing.get(edge.from()).add(normalized);
            indegree.compute(edge.to(), (ignored, value) -> value + 1);
        }

        Node start = nodes.values().stream()
                .filter(node -> "START".equals(node.type()))
                .findFirst()
                .orElse(null);
        Node end = nodes.values().stream()
                .filter(node -> "END".equals(node.type()))
                .findFirst()
                .orElse(null);
        if (start != null && indegree.getOrDefault(start.key(), 0) != 0) {
            errors.add("START 节点不能有入边");
        }
        if (end != null && !outgoing.getOrDefault(end.key(), List.of()).isEmpty()) {
            errors.add("END 节点不能有出边");
        }
        if (start != null && outgoing.getOrDefault(start.key(), List.of()).isEmpty()) {
            errors.add("START 节点必须有出边");
        }
        if (end != null && indegree.getOrDefault(end.key(), 0) == 0) {
            errors.add("END 节点必须有入边");
        }

        for (Node node : nodes.values()) {
            List<Edge> edges = outgoing.getOrDefault(node.key(), List.of());
            if (!"CONDITION".equals(node.type())) {
                if (edges.size() > 1) {
                    errors.add("非 CONDITION 节点最多只能有一条出边: " + node.key());
                }
                for (Edge edge : edges) {
                    if (!"DEFAULT".equals(edge.branch())) {
                        errors.add("非 CONDITION 节点只能使用 DEFAULT 分支: " + node.key());
                    }
                }
            } else {
                Set<String> branches = new LinkedHashSet<>();
                edges.forEach(edge -> branches.add(edge.branch()));
                if (edges.size() != 2
                        || !branches.equals(Set.of("TRUE", "FALSE"))) {
                    errors.add("CONDITION 节点必须有 TRUE 和 FALSE 两条出边: " + node.key());
                }
            }
            if (!"START".equals(node.type())
                    && indegree.getOrDefault(node.key(), 0) == 0) {
                errors.add("节点不可从 START 到达: " + node.key());
            }
            if (!"END".equals(node.type()) && edges.isEmpty()) {
                errors.add("非 END 节点必须有出边: " + node.key());
            }
        }

        if (start != null) {
            Set<String> reachable = reachable(start.key(), outgoing);
            for (String key : nodes.keySet()) {
                if (!reachable.contains(key)) {
                    errors.add("节点不可从 START 到达: " + key);
                }
            }
        }
        if (hasCycle(nodes.keySet(), outgoing, indegree)) {
            errors.add("workflow 不允许存在环");
        }
        return new ValidationResult(errors);
    }

    public List<Node> topologicalOrder(Graph graph) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            nodes.put(node.key(), node);
        }
        Map<String, List<Edge>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String key : nodes.keySet()) {
            outgoing.put(key, new ArrayList<>());
            indegree.put(key, 0);
        }
        for (Edge edge : graph.edges()) {
            outgoing.get(edge.from()).add(edge);
            indegree.compute(edge.to(), (ignored, value) -> value + 1);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        List<Node> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String key = queue.removeFirst();
            ordered.add(nodes.get(key));
            for (Edge edge : outgoing.get(key)) {
                int next = indegree.compute(edge.to(), (ignored, value) -> value - 1);
                if (next == 0) {
                    queue.addLast(edge.to());
                }
            }
        }
        if (ordered.size() != nodes.size()) {
            throw new IllegalArgumentException("workflow 图存在环，无法生成运行顺序");
        }
        return ordered;
    }

    private void validateConfig(Node node, List<String> errors) {
        Map<String, Object> config = node.config() == null ? Map.of() : node.config();
        String type = node.type();
        if (type == null) {
            return;
        }
        switch (type) {
            case "START", "END" -> {
                requireKeys(node.key(), config, Set.of(), errors);
            }
            case "AGENT" -> {
                requireKeys(node.key(), config, Set.of("agentVersionId"), errors);
                if (positiveLong(config.get("agentVersionId")) == null) {
                    errors.add("AGENT 节点必须配置正整数 agentVersionId: " + node.key());
                }
            }
            case "TOOL" -> {
                requireKeys(node.key(), config,
                        Set.of("toolCode", "toolVersion", "arguments"), errors);
                String toolCode = text(config.get("toolCode"));
                if (toolCode == null || toolCode.length() > 64) {
                    errors.add("TOOL 节点必须配置合法 toolCode: " + node.key());
                }
                Object version = config.get("toolVersion");
                if (version != null && positiveLong(version) == null) {
                    errors.add("TOOL 节点 toolVersion 必须为正整数: " + node.key());
                }
                Object arguments = config.get("arguments");
                if (arguments != null && !(arguments instanceof Map<?, ?>)) {
                    errors.add("TOOL 节点 arguments 必须是 JSON 对象: " + node.key());
                }
            }
            case "APPROVAL" -> {
                requireKeys(node.key(), config, Set.of("title", "instructions"), errors);
                String title = text(config.get("title"));
                if (title == null || title.length() > 128) {
                    errors.add("APPROVAL 节点必须配置合法 title: " + node.key());
                }
                String instructions = text(config.get("instructions"));
                if (instructions != null && instructions.length() > 2000) {
                    errors.add("APPROVAL 节点 instructions 不能超过 2000 个字符: " + node.key());
                }
            }
            case "CONDITION" -> {
                requireKeys(node.key(), config,
                        Set.of("source", "field", "operator", "value"), errors);
                if (!"INPUT".equals(text(config.get("source")))) {
                    errors.add("CONDITION source 只支持 INPUT: " + node.key());
                }
                String field = text(config.get("field"));
                if (field == null || field.length() > 128
                        || !field.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) {
                    errors.add("CONDITION field 必须是简单字段路径: " + node.key());
                }
                String operator = text(config.get("operator"));
                if (!OPERATORS.contains(operator)) {
                    errors.add("CONDITION operator 不受支持: " + node.key());
                }
                if (("EQUALS".equals(operator) || "NOT_EQUALS".equals(operator))
                        && !isScalar(config.get("value"))) {
                    errors.add("CONDITION EQUALS/NOT_EQUALS 必须配置标量 value: " + node.key());
                }
                if (("EXISTS".equals(operator) || "NOT_EXISTS".equals(operator))
                        && config.containsKey("value")) {
                    errors.add("CONDITION EXISTS/NOT_EXISTS 不允许配置 value: " + node.key());
                }
            }
            default -> {
                // Node type errors are reported by the graph-level validation.
            }
        }
    }

    private void requireKeys(String nodeKey,
                             Map<String, Object> config,
                             Set<String> allowed,
                             List<String> errors) {
        for (String key : config.keySet()) {
            if (!allowed.contains(key)) {
                errors.add("节点配置字段不受支持: " + nodeKey + "." + key);
            }
        }
    }

    private Set<String> reachable(String start,
                                  Map<String, List<Edge>> outgoing) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (Edge edge : outgoing.getOrDefault(current, List.of())) {
                queue.addLast(edge.to());
            }
        }
        return visited;
    }

    private boolean hasCycle(Set<String> keys,
                             Map<String, List<Edge>> outgoing,
                             Map<String, Integer> originalIndegree) {
        Map<String, Integer> indegree = new HashMap<>(originalIndegree);
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String key : keys) {
            if (indegree.getOrDefault(key, 0) == 0) {
                queue.add(key);
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (Edge edge : outgoing.getOrDefault(current, List.of())) {
                int next = indegree.compute(edge.to(), (ignored, value) -> value - 1);
                if (next == 0) {
                    queue.addLast(edge.to());
                }
            }
        }
        return visited != keys.size();
    }

    private String normalizeBranch(String branch) {
        return branch == null || branch.isBlank()
                ? "DEFAULT" : branch.trim().toUpperCase();
    }

    private String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private Long positiveLong(Object value) {
        if (value instanceof Number number) {
            long result = number.longValue();
            return result > 0 && number.doubleValue() == result ? result : null;
        }
        return null;
    }

    private boolean isScalar(Object value) {
        return value != null && SCALAR_TYPES.contains(value.getClass().getName());
    }

    public record Graph(List<Node> nodes, List<Edge> edges) {
    }

    public record Node(String key,
                       String type,
                       String name,
                       Map<String, Object> config,
                       Map<String, Object> position) {
    }

    public record Edge(String from,
                       String to,
                       String branch,
                       Integer order) {
    }

    public record ValidationResult(List<String> errors) {
        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }
}
