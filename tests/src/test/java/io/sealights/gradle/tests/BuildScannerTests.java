package io.sealights.gradle.tests;

import io.sealights.onpremise.agentevents.eventservice.proxy.api.AgentEventRequest;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.DefaultMockServerAPI;
import io.sealights.onpremise.agents.infra.component.tests.mockserver.MockServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BuildScannerTests {

    private static final String TESTS_PROJECTS_DIR = "../tests-projects";
    private String[] gradleVersions = new String[] { //todo should come from env var
            "6.6.1",
    };

    private static String token;
    private static DefaultMockServerAPI mockServerAPI;

    @BeforeClass
    public static void beforeClass() throws Exception {
        mockServerAPI = new DefaultMockServerAPI();
        MockServer.INSTANCE.run(mockServerAPI, "/home/kamil/workspace/sealights/projects/sealights-gradle-plugin-tests/tests");
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

            List<Object> agentEvents = mockServerAPI.getBuildMaps();
            System.out.println();
        }
    }

    private void executeBuild(String projectName, String buildName) throws IOException, InterruptedException {
        executeGradleCommand(Arrays.asList(
                String.format(":%s:%s", projectName, "clean"),
                String.format(":%s:%s", projectName, "build"),
                String.format("-DslToken=%s -DbuildName=%s", token, buildName))
        );

    }

    private void setGradleVersion(String gradleVersion) throws IOException, InterruptedException {
        executeGradleCommand(Arrays.asList("wrapper", "--gradle-version", gradleVersion));
    }

    private String executeGradleCommand(List<String> command) throws IOException, InterruptedException {
        File projectDirFile = new File(TESTS_PROJECTS_DIR);
        String gradlew = new File(projectDirFile, "gradlew").getAbsolutePath();
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
