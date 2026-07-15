package org.jenkinsci.plugins.ansible_tower.util;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

public class TowerConnectorTest {

    @Test
    public void normalizeBaseURL_removesTrailingSlash() {
        Assert.assertThat(
                TowerConnector.normalizeBaseURL("https://tower.example.com/"),
                CoreMatchers.is("https://tower.example.com"));
    }

    @Test
    public void normalizeApiBasePath_defaultsToLegacy() {
        Assert.assertThat(
                TowerConnector.normalizeApiBasePath(null),
                CoreMatchers.is(TowerConnector.API_BASE_PATH_LEGACY));
    }

    @Test
    public void normalizeApiBasePath_addsLeadingSlashAndRemovesTrailingSlash() {
        Assert.assertThat(
                TowerConnector.normalizeApiBasePath("api/controller/v2/"),
                CoreMatchers.is(TowerConnector.API_BASE_PATH_AAP_CONTROLLER));
    }

    @Test
    public void buildEndpoint_usesLegacyApiBasePathByDefault() {
        Assert.assertThat(
                TowerConnector.buildEndpoint("workflow_job_templates/8/", null),
                CoreMatchers.is("/api/v2/workflow_job_templates/8/"));
    }

    @Test
    public void buildEndpoint_usesAAPControllerApiBasePath() {
        Assert.assertThat(
                TowerConnector.buildEndpoint("workflow_job_templates/8/", TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is("/api/controller/v2/workflow_job_templates/8/"));
    }

    @Test
    public void buildEndpoint_keepsAbsoluteApiEndpoint() {
        Assert.assertThat(
                TowerConnector.buildEndpoint("/api/v2/jobs/", TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is("/api/v2/jobs/"));
    }

    @Test
    public void buildOAuthTokenEndpoint_usesLegacyApiBasePathByDefault() {
        Assert.assertThat(
                TowerConnector.buildOAuthTokenEndpoint(null),
                CoreMatchers.is("/api/v2/tokens/"));
    }

    @Test
    public void buildOAuthTokenEndpoint_usesGatewayForAAPControllerApiBasePath() {
        Assert.assertThat(
                TowerConnector.buildOAuthTokenEndpoint(TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is(TowerConnector.API_GATEWAY_TOKEN_ENDPOINT));
    }

    @Test
    public void shouldProbeOAuthSupport_keepsLegacyProbe() {
        Assert.assertThat(
                TowerConnector.shouldProbeOAuthSupport(TowerConnector.API_BASE_PATH_LEGACY),
                CoreMatchers.is(true));
    }

    @Test
    public void shouldProbeOAuthSupport_skipsProbeForAAPControllerMode() {
        Assert.assertThat(
                TowerConnector.shouldProbeOAuthSupport(TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is(false));
    }

    @Test
    public void buildJobURL_keepsLegacyJobURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 8, "job"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/8"));
    }

    @Test
    public void buildJobURL_usesLegacyWorkflowJobURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 770889, "workflow_job"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/workflow/770889"));
    }

    @Test
    public void buildJobURL_usesLegacyWorkflowAliasURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 770889, TowerConnector.WORKFLOW_TEMPLATE_TYPE),
                CoreMatchers.is("https://ansible.example.com/#/jobs/workflow/770889"));
    }

    @Test
    public void buildJobURL_keepsLegacyProjectUpdateURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 9, "project_update"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/project/9"));
    }

    @Test
    public void buildJobURL_keepsLegacyInventoryUpdateURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 10, "inventory_update"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/inventory/10"));
    }

    @Test
    public void buildJobURL_usesAAPControllerPlaybookOutputURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 8, "job"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/playbook/8/output"));
    }

    @Test
    public void buildJobURL_usesAAPControllerWorkflowOutputURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 7, "workflow_job"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/workflow/7/output"));
    }

    @Test
    public void buildJobURL_usesAAPControllerProjectUpdateOutputURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 9, "project_update"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/project_update/9/output"));
    }

    @Test
    public void buildJobURL_usesAAPControllerInventoryUpdateOutputURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 10, "inventory_update"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/inventory_update/10/output"));
    }

    @Test
    public void isTransientGatewayStatus_onlyRetriesGatewayFailures() {
        Assert.assertThat(TowerConnector.isTransientGatewayStatus(502), CoreMatchers.is(true));
        Assert.assertThat(TowerConnector.isTransientGatewayStatus(503), CoreMatchers.is(true));
        Assert.assertThat(TowerConnector.isTransientGatewayStatus(504), CoreMatchers.is(true));
        Assert.assertThat(TowerConnector.isTransientGatewayStatus(500), CoreMatchers.is(false));
        Assert.assertThat(TowerConnector.isTransientGatewayStatus(200), CoreMatchers.is(false));
    }
}
