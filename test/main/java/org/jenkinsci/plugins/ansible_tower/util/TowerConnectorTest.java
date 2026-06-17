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
}
