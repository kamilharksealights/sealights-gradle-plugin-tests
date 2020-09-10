package io.sealights.gradle.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sealights.onpremise.agents.buildscanner.buildmap.service.proxy.api.BuildMappingRequest;
import io.sealights.onpremise.agents.buildscanner.buildmap.service.proxy.api.HashedMethodData;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.DefaultMockServerAPI;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.MockServer;
import io.sealights.onpremise.agents.infra.env.OsDetector;
import io.sealights.onpremise.agents.infra.types.Component;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

public class BuildScannerTests {

    private ObjectMapper objectMapper = getObjectMapper();
    private Map<String, List<String>> gradleToAndroidVersions = Map.of(
//            "6.1.1", List.of("4.0.1", "3.1.0"),
//            "5.6.4", List.of("3.6.4", "3.1.0"),
//            "4.10.3", List.of("3.3.3", "3.1.0"),
            "4.4.1", List.of("3.1.0")
    );

    private static final String TESTS_PROJECTS_DIR = "../tests-projects";
    private static String token;
    private static DefaultMockServerAPI mockServerAPI;

    @BeforeClass
    public static void beforeClass() {
        mockServerAPI = new DefaultMockServerAPI();
        MockServer.INSTANCE.run(mockServerAPI, ".");
        // TODO recommend latest version?
        mockServerAPI.setRecommendedVersion(Component.BUILD_SCANNER_COMPONENT_NAME, "3.0.1720", "https://agents.sealights.co/sealights-java/sealights-java-3.0.1720.zip");
        token = MockServer.INSTANCE.getToken(mockServerAPI);
    }

    @After
    public void tearDown() {
        MockServer.INSTANCE.shutdown();
    }

    @Test
    public void shouldScanJavaProject() throws IOException, InterruptedException {
        String projectName = "java-only-gradle-project";

        for (String gradleVersion : gradleToAndroidVersions.keySet()) {

            setGradleVersion(projectName, gradleVersion);
            executeBuild(projectName, "buildNameAbc");

            List<DefaultMockServerAPI.BuildMapping> agentEvents = mockServerAPI.getBuildMappings();

            // TODO add nice assertions
            List<String> methodNames = agentEvents.stream()
                    .flatMap(event -> convertToBuildMapping(event).getFiles().stream())
                    .flatMap(file -> file.getMethods().stream())
                    .map(HashedMethodData::getDisplayName)
                    .collect(Collectors.toList());

            assertThat(methodNames).containsExactly("public int add(int, int)", "public JavaOnlyExampleClass()");
        }
    }

    @SneakyThrows
    private BuildMappingRequest convertToBuildMapping(DefaultMockServerAPI.BuildMapping rawBuildMapping) {
        return objectMapper.readValue(objectMapper.writeValueAsString(rawBuildMapping), BuildMappingRequest.class);
    }

    private void executeBuild(String projectName, String buildName) throws IOException, InterruptedException {
        executeGradleCommand(
                projectName, "clean", "build",
                String.format("-DslToken=%s", token),
                String.format("-DbuildName=%s", buildName),
                "--stacktrace"
        );

    }

    private void setGradleVersion(String projectName, String gradleVersion) throws IOException, InterruptedException {
        executeGradleCommand(projectName, "wrapper", "--gradle-version", gradleVersion);
    }

    private void executeGradleCommand(String projectName, String ...args) throws IOException, InterruptedException {
        File projectDirFile = new File(TESTS_PROJECTS_DIR, projectName);
        String gradlewExecutableName = OsDetector.isWindows() ? "gradlew.bat" : "gradlew";
        String gradlew = new File(projectDirFile, gradlewExecutableName).getAbsolutePath();

        LinkedList<String> finalCommand = new LinkedList<>(Arrays.asList(args));
        finalCommand.addFirst(gradlew);

        System.out.println("running command: " + String.join(" ", finalCommand));

        ProcessBuilder processBuilder = new ProcessBuilder(finalCommand.toArray(new String[]{}));
        processBuilder.directory(projectDirFile);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String gradleOutput = readOutput(process);
        int status = process.waitFor();
        Assert.assertEquals("Gradle execution failed: " + gradleOutput,0, status);
        System.out.println(gradleOutput);
    }

    @NotNull
    private String readOutput(Process process) throws IOException {
        return IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

}
