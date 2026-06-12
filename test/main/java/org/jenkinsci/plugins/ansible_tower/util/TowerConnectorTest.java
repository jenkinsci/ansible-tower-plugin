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
                TowerConnector.buildEndpoint("jobs/", null),
                CoreMatchers.is("/api/v2/jobs/"));
    }

    @Test
    public void buildEndpoint_usesAAPControllerApiBasePath() {
        Assert.assertThat(
                TowerConnector.buildEndpoint("jobs/", TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is("/api/controller/v2/jobs/"));
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
    public void selectUIBaseURL_usesApiURLWhenDisplayURLIsBlank() {
        Assert.assertThat(
                TowerConnector.selectUIBaseURL("https://gateway.example.com/", ""),
                CoreMatchers.is("https://gateway.example.com"));
    }

    @Test
    public void selectUIBaseURL_prefersDisplayURL() {
        Assert.assertThat(
                TowerConnector.selectUIBaseURL("https://gateway.example.com", "https://controller.example.com/"),
                CoreMatchers.is("https://controller.example.com"));
    }

    @Test
    public void buildJobURL_keepsLegacyJobURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://tower.example.com", TowerConnector.API_BASE_PATH_LEGACY, 123, TowerConnector.JOB_TEMPLATE_TYPE),
                CoreMatchers.is("https://tower.example.com/#/jobs/123"));
    }

    @Test
    public void buildJobURL_keepsLegacyWorkflowURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://tower.example.com", TowerConnector.API_BASE_PATH_LEGACY, 456, TowerConnector.WORKFLOW_TEMPLATE_TYPE),
                CoreMatchers.is("https://tower.example.com/#/workflows/456"));
    }

    @Test
    public void buildJobURL_usesAAPControllerPlaybookOutputURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://controller.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 123, TowerConnector.JOB_TEMPLATE_TYPE),
                CoreMatchers.is("https://controller.example.com/execution/jobs/playbook/123/output"));
    }

    @Test
    public void buildJobURL_usesAAPControllerWorkflowOutputURL() {
        Assert.assertThat(
                TowerConnector.buildJobURL("https://controller.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 456, TowerConnector.WORKFLOW_TEMPLATE_TYPE),
                CoreMatchers.is("https://controller.example.com/execution/jobs/workflow/456/output"));
    }

    @Test
    public void buildProjectSyncURL_keepsLegacyProjectSyncURL() {
        Assert.assertThat(
                TowerConnector.buildProjectSyncURL("https://tower.example.com", TowerConnector.API_BASE_PATH_LEGACY, 789),
                CoreMatchers.is("https://tower.example.com/#/jobs/project/789"));
    }

    @Test
    public void buildProjectSyncURL_usesAAPControllerProjectUpdateOutputURL() {
        Assert.assertThat(
                TowerConnector.buildProjectSyncURL("https://controller.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 789),
                CoreMatchers.is("https://controller.example.com/execution/jobs/project_update/789/output"));
    }
}
