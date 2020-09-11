package io.sealights.gradle.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sealights.onpremise.agents.buildscanner.buildmap.service.proxy.api.BuildMappingRequest;
import io.sealights.onpremise.agents.buildscanner.buildmap.service.proxy.api.HashedMethodData;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.DefaultMockServerAPI;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.MockServer;
import io.sealights.onpremise.agents.infra.env.OsDetector;
import io.sealights.onpremise.agents.infra.types.Component;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

public class BuildScannerTests {

    private static final String METADATA_BUILD_KEY = "build";
    private ObjectMapper objectMapper = getObjectMapper();

    private Map<String, List<String>> gradleToAndroidPluginVersions = Map.of(
            "6.1.1", List.of("4.0.1", "3.1.0"),
            "5.6.4", List.of("3.6.4", "3.1.0"),
            "4.10.3", List.of("3.3.3", "3.1.0")
    );

    private static final String TESTS_PROJECTS_DIR = "../tests-projects";
    private static String token;
    private static DefaultMockServerAPI mockServerAPI;

    @BeforeClass
    public static void beforeClass() {
        mockServerAPI = new DefaultMockServerAPI();
        MockServer.INSTANCE.run(mockServerAPI, ".");
        mockServerAPI.setRecommendedVersion(Component.BUILD_SCANNER_COMPONENT_NAME, "3.0.1720", "https://agents.sealights.co/sealights-java/sealights-java-3.0.1720.zip");
        token = MockServer.INSTANCE.getToken(mockServerAPI);
    }

    @AfterClass
    public static void tearDown() {
        MockServer.INSTANCE.shutdown();
    }

    @Test
    public void shouldScanJavaProject() throws Exception {
        String projectName = "java-only-gradle-project";
        for (String gradleVersion : gradleToAndroidPluginVersions.keySet()) {
            setGradleVersion(projectName, gradleVersion);
            String buildName = "java-build-" + gradleVersion;

            executeBuild(projectName, buildName);

            List<DefaultMockServerAPI.BuildMapping> agentEvents = mockServerAPI.getBuildMappings();
            assertThat(extractMethodNames(agentEvents, buildName)).containsExactly("public int add(int, int)", "public JavaOnlyExampleClass()");
        }
    }

    @Test
    public void shouldScanKotlinProject() throws Exception {
        String projectName = "kotlin-gradle-project";
        for (String gradleVersion : gradleToAndroidPluginVersions.keySet()) {
            setGradleVersion(projectName, gradleVersion);
            String buildName = "kotlin-build-" + gradleVersion;

            executeBuild(projectName, buildName);

            List<DefaultMockServerAPI.BuildMapping> agentEvents = mockServerAPI.getBuildMappings();
            assertThat(extractMethodNames(agentEvents, buildName)).containsExactly("public final int multiply(int, int)", "public KotlinExampleClass()");
        }
    }
    @Test
    public void shouldScanJavaAndroidProjects() throws Exception {
        String projectName = "java-only-android-project";
        for (String gradleVersion : gradleToAndroidPluginVersions.keySet()) {
            setGradleVersion(projectName, gradleVersion);

            for (String androidPluginVersion : gradleToAndroidPluginVersions.get(gradleVersion)) {
                String buildName = "android-build-" + gradleVersion;

                executeAndroidBuild(projectName, buildName, androidPluginVersion);

                List<DefaultMockServerAPI.BuildMapping> agentEvents = mockServerAPI.getBuildMappings();
                assertThat(extractMethodNames(agentEvents, buildName)).
                        containsOnly("public void javaMethod()", "protected void onCreate(Bundle)", "public MainActivity()");
            }
        }
    }

    @NotNull
    private List<String> extractMethodNames(List<DefaultMockServerAPI.BuildMapping> agentEvents, String buildName) {
        List<String> methodNames = agentEvents.stream()
                .filter(e -> e.getMeta().get(METADATA_BUILD_KEY).equals(buildName))
                .flatMap(event -> convertToBuildMapping(event).getFiles().stream())
                .flatMap(file -> file.getMethods().stream())
                .map(HashedMethodData::getDisplayName)
                .collect(Collectors.toList());
        return methodNames;
    }

    @SneakyThrows
    private BuildMappingRequest convertToBuildMapping(DefaultMockServerAPI.BuildMapping rawBuildMapping) {
        return objectMapper.readValue(objectMapper.writeValueAsString(rawBuildMapping), BuildMappingRequest.class);
    }

    private void executeBuild(String projectName, String buildName) throws Exception {
        executeGradleCommand(
                projectName, "clean", "build",
                String.format("-DslToken=%s", token),
                String.format("-DbuildName=%s", buildName)
        );
    }

    private void executeAndroidBuild(String projectName, String buildName, String androidPluginVersion) throws Exception {
        executeGradleCommand(
                projectName, "clean", "compileDebugSources",
                String.format("-DslToken=%s", token),
                String.format("-DbuildName=%s", buildName),
                String.format("-DandroidPluginVersion=%s", androidPluginVersion)
        );
    }


    private void setGradleVersion(String projectName, String gradleVersion) throws Exception {
        executeGradleCommand(projectName, "wrapper", "--gradle-version", gradleVersion);
    }

    private void executeGradleCommand(String projectName, String ...args) throws Exception {
        File projectDirFile = new File(TESTS_PROJECTS_DIR, projectName);
        String gradlewExecutableName = OsDetector.isWindows() ? "gradlew.bat" : "gradlew";
        String gradlew = new File(projectDirFile, gradlewExecutableName).getAbsolutePath();

        LinkedList<String> finalCommand = new LinkedList<>(Arrays.asList(args));
        finalCommand.addFirst(gradlew);

        System.out.println("running command: " + String.join(" ", finalCommand));

        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.directory(projectDirFile);
        ProcessExecutor command = processExecutor.command(finalCommand).destroyOnExit();
        command.redirectOutput(System.out);
        command.redirectError(System.err);
        ProcessResult result = command.execute();
        int status = result.getExitValue();

        Assert.assertEquals("Gradle execution failed. See logs above.", 0, status);
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

}
