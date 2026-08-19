package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MyBatisMapperXmlTest {

    private static final Path JAVA_SOURCE_ROOT =
            Path.of("src", "main", "java");
    private static final Path MAPPER_ROOT =
            Path.of("src", "main", "resources", "sqlmapper");
    private static final Set<String> STATEMENT_ELEMENTS =
            Set.of("select", "insert", "update", "delete");

    @Test
    void allMapperXmlFilesParseAndRegisterDeclaredElements() throws Exception {
        List<Path> mapperFiles = mapperFiles();
        Configuration configuration = new Configuration();

        for (Path mapperFile : mapperFiles) {
            String resource = resourceName(mapperFile);
            try (InputStream input = Files.newInputStream(mapperFile)) {
                new XMLMapperBuilder(
                        input,
                        configuration,
                        resource,
                        configuration.getSqlFragments())
                        .parse();
            }
        }

        Set<String> namespaces = new HashSet<>();
        Map<String, Path> mapperFilesByNamespace = new HashMap<>();
        for (Path mapperFile : mapperFiles) {
            MapperDeclaration declaration = declaration(mapperFile, configuration);
            assertTrue(namespaces.add(declaration.namespace()),
                    () -> "Duplicate mapper namespace: " + declaration.namespace());
            mapperFilesByNamespace.put(declaration.namespace(), mapperFile);
            assertTrue(configuration.hasMapper(Class.forName(declaration.namespace())),
                    () -> "Mapper namespace was not bound: " + declaration.namespace());
            assertFalse(declaration.statementIds().isEmpty(),
                    () -> "Mapper has no SQL statements: " + resourceName(mapperFile));

            for (String resultMapId : declaration.resultMapIds()) {
                assertTrue(configuration.hasResultMap(declaration.namespace() + "." + resultMapId),
                        () -> "Missing resultMap " + declaration.namespace() + "." + resultMapId);
            }
            for (String statementId : declaration.statementIds()) {
                assertTrue(configuration.hasStatement(
                                declaration.namespace() + "." + statementId,
                                false),
                        () -> "Missing statement " + declaration.namespace() + "." + statementId);
            }
        }

        List<Class<?>> mapperInterfaces = mapperInterfaces();
        Set<String> mapperInterfaceNames = mapperInterfaces.stream()
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(mapperInterfaceNames.equals(namespaces),
                () -> "Mapper interfaces and XML namespaces differ. Interfaces="
                        + mapperInterfaceNames + ", XML namespaces=" + namespaces);

        for (Class<?> mapperInterface : mapperInterfaces) {
            assertTrue(mapperInterface.isInterface(),
                    () -> "Mapper type is not an interface: " + mapperInterface.getName());
            Path mapperFile = mapperFilesByNamespace.get(mapperInterface.getName());
            assertTrue(mapperFile != null,
                    () -> "Missing XML for mapper interface: " + mapperInterface.getName());
            assertTrue(mapperFile.getFileName().toString()
                            .equals(mapperInterface.getSimpleName() + ".xml"),
                    () -> "Mapper XML must use the interface name: "
                            + mapperInterface.getName() + " -> " + resourceName(mapperFile));

            List<Method> methods = Stream.of(mapperInterface.getDeclaredMethods())
                    .filter(method -> Modifier.isAbstract(method.getModifiers()))
                    .toList();
            Set<String> methodNames = methods.stream()
                    .map(Method::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(methodNames.size() == methods.size(),
                    () -> "Mapper methods must not be overloaded: " + mapperInterface.getName());
            for (String methodName : methodNames) {
                assertTrue(configuration.hasStatement(
                                mapperInterface.getName() + "." + methodName,
                                false),
                        () -> "Missing XML statement for mapper method: "
                                + mapperInterface.getName() + "." + methodName);
            }
        }

        assertAll(
                () -> assertFalse(mapperFiles.isEmpty(), "No MyBatis mapper XML files found"),
                () -> assertFalse(mapperInterfaces.isEmpty(), "No MyBatis mapper interfaces found"),
                () -> assertTrue(namespaces.size() == mapperFiles.size(),
                        "Every mapper XML must declare one unique namespace"),
                () -> assertFalse(configuration.getResultMapNames().isEmpty(),
                        "No resultMap was registered"),
                () -> assertFalse(configuration.getMappedStatementNames().isEmpty(),
                        "No mapped statement was registered"));
    }

    private List<Path> mapperFiles() throws IOException {
        assertTrue(Files.isDirectory(MAPPER_ROOT),
                () -> "Mapper directory does not exist: " + MAPPER_ROOT.toAbsolutePath());
        try (Stream<Path> paths = Files.walk(MAPPER_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        }
    }

    private List<Class<?>> mapperInterfaces() throws IOException {
        assertTrue(Files.isDirectory(JAVA_SOURCE_ROOT),
                () -> "Java source directory does not exist: "
                        + JAVA_SOURCE_ROOT.toAbsolutePath());
        try (Stream<Path> paths = Files.walk(JAVA_SOURCE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Mapper.java"))
                    .filter(path -> path.toString().replace('\\', '/')
                            .contains("/infrastructure/mapper/"))
                    .sorted()
                    .map(this::className)
                    .map(this::loadClass)
                    .toList();
        }
    }

    private String className(Path sourceFile) {
        String relative = JAVA_SOURCE_ROOT.relativize(sourceFile)
                .toString()
                .replace('\\', '.')
                .replace('/', '.');
        return relative.substring(0, relative.length() - ".java".length());
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Mapper interface was not compiled: " + className,
                    exception);
        }
    }

    private MapperDeclaration declaration(Path mapperFile, Configuration configuration)
            throws IOException {
        try (InputStream input = Files.newInputStream(mapperFile)) {
            XPathParser parser = new XPathParser(
                    input,
                    true,
                    configuration.getVariables(),
                    new XMLMapperEntityResolver());
            XNode mapper = parser.evalNode("/mapper");
            String namespace = mapper.getStringAttribute("namespace");
            assertTrue(namespace != null && !namespace.isBlank(),
                    () -> "Missing namespace in " + resourceName(mapperFile));

            List<String> resultMapIds = mapper.evalNodes("resultMap").stream()
                    .map(node -> node.getStringAttribute("id"))
                    .toList();
            List<String> statementIds = mapper.getChildren().stream()
                    .filter(node -> STATEMENT_ELEMENTS.contains(node.getName()))
                    .map(node -> node.getStringAttribute("id"))
                    .toList();
            return new MapperDeclaration(namespace, resultMapIds, statementIds);
        }
    }

    private String resourceName(Path mapperFile) {
        return MAPPER_ROOT.relativize(mapperFile).toString().replace('\\', '/');
    }

    private record MapperDeclaration(
            String namespace,
            List<String> resultMapIds,
            List<String> statementIds) {
    }
}
