package org.jenkinsci.plugins.ansible_tower;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import hudson.model.FreeStyleProject;
import hudson.EnvVars;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import jenkins.model.Jenkins;
import org.apache.http.client.HttpClient;
import org.jenkinsci.plugins.ansible_tower.util.TowerConnector;
import org.jenkinsci.plugins.ansible_tower.util.TowerInstallation;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerRequestException;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class PluginCompatibilityTest {

    @Test
    public void pluginLoadsDescriptorsAndHttpClientFromApiPlugin(JenkinsRule jenkinsRule) throws Exception {
        assertThat(jenkinsRule.jenkins.getPluginManager().getPlugin("ansible-tower"), notNullValue());
        assertThat(jenkinsRule.jenkins.getPluginManager().getPlugin("apache-httpcomponents-client-4-api"), notNullValue());
        assertThat(Jenkins.get().getDescriptor(AnsibleTower.class), instanceOf(AnsibleTower.DescriptorImpl.class));
        assertThat(Jenkins.get().getDescriptor(AnsibleTowerProjectSyncFreestyle.class),
                instanceOf(AnsibleTowerProjectSyncFreestyle.DescriptorImpl.class));
        assertThat(Jenkins.get().getDescriptor(AnsibleTowerProjectRevisionFreestyle.class),
                instanceOf(AnsibleTowerProjectRevisionFreestyle.DescriptorImpl.class));
        assertStep("ansibleTower", AnsibleTowerStep.DescriptorImpl.class);
        assertStep("ansibleTowerProjectSync", AnsibleTowerProjectSyncStep.DescriptorImpl.class);
        assertStep("ansibleTowerProjectRevision", AnsibleTowerProjectRevisionStep.DescriptorImpl.class);

        Class<?> httpClient = Jenkins.get().getPluginManager().uberClassLoader.loadClass(HttpClient.class.getName());
        Class<?> apiPluginHttpClient = Jenkins.get().getPluginManager()
                .getPlugin("apache-httpcomponents-client-4-api").classLoader.loadClass(HttpClient.class.getName());
        assertThat(apiPluginHttpClient, is(httpClient));
        assertThat(TowerConnector.class.getClassLoader().loadClass(HttpClient.class.getName()), is(httpClient));
    }

    @Test
    public void pipelineStepsRetainDataBoundValues(JenkinsRule jenkinsRule) {
        AnsibleTowerStep job = new AnsibleTowerStep("tower", "credential", "template", "check");
        job.setExtraVars("{\"key\":\"value\"}");
        job.setTowerLogLevel("full");
        job.setAsync(true);
        assertThat(job.getTowerServer(), is("tower"));
        assertThat(job.getTowerCredentialsId(), is("credential"));
        assertThat(job.getJobTemplate(), is("template"));
        assertThat(job.getJobType(), is("check"));
        assertThat(job.getExtraVars(), is("{\"key\":\"value\"}"));
        assertThat(job.getTowerLogLevel(), is("full"));
        assertThat(job.getAsync(), is(true));

        AnsibleTowerProjectSyncStep sync = new AnsibleTowerProjectSyncStep(
                "tower", "credential", "project", false, false, false, true, true);
        assertThat(sync.getProject(), is("project"));
        assertThat(sync.getThrowExceptionWhenFail(), is(true));
        assertThat(sync.getAsync(), is(true));

        AnsibleTowerProjectRevisionStep revision = new AnsibleTowerProjectRevisionStep(
                "tower", "credential", "project", "main", true, false);
        assertThat(revision.getRevision(), is("main"));
        assertThat(revision.getVerbose(), is(true));
        assertThat(revision.getThrowExceptionWhenFail(), is(false));
    }

    @Test
    public void runnerWritesMilestoneAndPreservesFailureForPipelineBoundary(JenkinsRule jenkinsRule) {
        AnsibleTowerGlobalConfig.get().setTowerInstallation(List.of());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AnsibleTowerRunner runner = new AnsibleTowerRunner();

        boolean result = runner.runJobTemplate(
            new PrintStream(output, true, StandardCharsets.UTF_8), "missing-tower", "", "deploy", "run",
            "", "", "", "", "", "", "", false, "false", false, new EnvVars(),
            "job", false, null, null, new Properties(), false);

        assertThat(result, is(false));
        assertThat(runner.getLastFailureMessage(),
            is("Ansible Tower server missing-tower does not exist in Jenkins configuration"));
        String console = output.toString(StandardCharsets.UTF_8);
        assertThat(console.contains("[Ansible-Tower] INFO: Starting job template operation"), is(true));
        assertThat(console.contains("[Ansible-Tower] ERROR: Ansible Tower server missing-tower"), is(true));
    }

    @Test
    public void runnerRecognizesConsoleDiagnosticsThroughWrappedCauses() {
        AnsibleTowerException diagnosed = new AnsibleTowerException("template lookup failed",
            new AnsibleTowerRequestException("GET returned HTTP 503"));
        AnsibleTowerException undiagnosed = new AnsibleTowerException("invalid template configuration");

        assertThat(AnsibleTowerRunner.hasConsoleDiagnostics(diagnosed), is(true));
        assertThat(AnsibleTowerRunner.hasConsoleDiagnostics(undiagnosed), is(false));
    }

    @Test
    public void freestyleConfigurationAndGlobalInstallationRoundTrip(JenkinsRule jenkinsRule) throws Exception {
        TowerInstallation installation = new TowerInstallation(
                "tower", "https://tower.example.com", "/api/controller/v2", "credential", true, true);
        AnsibleTowerGlobalConfig.get().setTowerInstallation(List.of(installation));
        AnsibleTowerGlobalConfig.get().save();

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        project.getBuildersList().add(new AnsibleTowerProjectSyncFreestyle(
                "tower", "credential", "project", true, true, false));
        project = jenkinsRule.configRoundtrip(project);
        AnsibleTowerProjectSyncFreestyle restored = project.getBuildersList()
                .get(AnsibleTowerProjectSyncFreestyle.class);
        assertThat(restored.getTowerServer(), is("tower"));
        assertThat(restored.getTowerCredentialsId(), is("credential"));
        assertThat(restored.getProject(), is("project"));
        assertThat(restored.getVerbose(), is(true));
        assertThat(restored.getImportTowerLogs(), is(true));
        assertThat(restored.getRemoveColor(), is(false));

        String xml = Jenkins.XSTREAM2.toXML(installation);
        TowerInstallation restoredInstallation = (TowerInstallation) Jenkins.XSTREAM2.fromXML(xml);
        assertThat(restoredInstallation.getTowerDisplayName(), is("tower"));
        assertThat(restoredInstallation.getTowerURL(), is("https://tower.example.com"));
        assertThat(restoredInstallation.getTowerApiBasePath(), is("/api/controller/v2"));
        assertThat(restoredInstallation.getTowerCredentialsId(), is("credential"));
        assertThat(restoredInstallation.getTowerTrustCert(), is(true));
        assertThat(restoredInstallation.getEnableDebugging(), is(true));
    }

    private static void assertStep(String functionName, Class<? extends StepDescriptor> type) {
        StepDescriptor descriptor = Jenkins.get().getExtensionList(StepDescriptor.class).stream()
                .filter(candidate -> functionName.equals(candidate.getFunctionName()))
                .findFirst()
                .orElseThrow();
        assertThat(descriptor, instanceOf(type));
    }
}
