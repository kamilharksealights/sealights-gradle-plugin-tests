package io.sealights.gradle.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sealights.onpremise.agents.buildscanner.buildmap.service.proxy.api.BuildMappingRequest;
import io.sealights.onpremise.agents.buildscanner.buildmap.service.proxy.api.HashedMethodData;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.DefaultMockServerAPI;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.MockServer;
import io.sealights.onpremise.agents.infra.env.OsDetector;
import io.sealights.onpremise.agents.infra.types.Component;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

public class BuildScannerTests {

    private static final String TESTS_PROJECTS_DIR = "../tests-projects";
    private String[] gradleVersions = new String[] { //todo should come from env var
            "6.6.1",
    };

    private static String token;
    private static DefaultMockServerAPI mockServerAPI;

    private static ObjectMapper objectMapper = getObjectMapper();

    private static ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        mockServerAPI = new DefaultMockServerAPI();
        MockServer.INSTANCE.run(mockServerAPI, ".");
        // TODO recommend latest version?
        mockServerAPI.setRecommendedVersion(Component.BUILD_SCANNER_COMPONENT_NAME, "3.0.1720", "https://agents.sealights.co/sealights-java/sealights-java-3.0.1720.zip");
        token = MockServer.INSTANCE.getToken(mockServerAPI);
    }

    @After
    public void tearDown() throws Exception {
        MockServer.INSTANCE.shutdown();
    }

    @Test
    public void shouldScanJavaProject() throws IOException, InterruptedException {
        String projectName = "java-only-gradle-project";
        for (String gradleVersion : gradleVersions) {
            setGradleVersion(gradleVersion);
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
        executeGradleCommand(Arrays.asList(
                String.format(":%s:%s", projectName, "clean"),
                String.format(":%s:%s", projectName, "build"),
                String.format("-DslToken=%s", token),
                String.format("-DbuildName=%s", buildName))
        );

    }

    private void setGradleVersion(String gradleVersion) throws IOException, InterruptedException {
        executeGradleCommand(Arrays.asList("wrapper", "--gradle-version", gradleVersion));
    }

    private String executeGradleCommand(List<String> command) throws IOException, InterruptedException {
        File projectDirFile = new File(TESTS_PROJECTS_DIR);
        String gradlewExecutableName = OsDetector.isWindows() ? "gradlew.bat" : "gradlew";
        String gradlew = new File(projectDirFile, gradlewExecutableName).getAbsolutePath();
        ArrayList<String> finalCommand = new ArrayList<>();
        finalCommand.add(gradlew);
        finalCommand.addAll(command);
        ProcessBuilder processBuilder = new ProcessBuilder(finalCommand.toArray(new String[]{}));
        processBuilder.directory(projectDirFile);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        Scanner s = new Scanner(process.getInputStream());
        StringBuilder text = new StringBuilder();
        while (s.hasNextLine()) {
            text.append(s.nextLine());
            text.append("\n");
        }
        s.close();
        int status = process.waitFor();
        Assert.assertEquals("Gradle execution failed: " + text.toString(),0, status);
        return text.toString();
    }
}
