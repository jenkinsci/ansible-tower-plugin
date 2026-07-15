package org.jenkinsci.plugins.ansible_tower.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.sf.json.JSONObject;
import org.apache.http.util.EntityUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

class TowerConnectorTest {

    @Test
    public void createJsonEntity_encodesNonAsciiContentAsUtf8() throws IOException {
        JSONObject body = new JSONObject();
        body.put("message", "Tiếng Việt");

        byte[] content = EntityUtils.toByteArray(TowerConnector.createJsonEntity(body));

        MatcherAssert.assertThat(content, CoreMatchers.is(body.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void normalizeBaseURL_removesTrailingSlash() {
        MatcherAssert.assertThat(
                TowerConnector.normalizeBaseURL("https://tower.example.com/"),
                CoreMatchers.is("https://tower.example.com"));
    }

    @Test
    public void normalizeApiBasePath_defaultsToLegacy() {
        MatcherAssert.assertThat(
                TowerConnector.normalizeApiBasePath(null),
                CoreMatchers.is(TowerConnector.API_BASE_PATH_LEGACY));
    }

    @Test
    public void normalizeApiBasePath_addsLeadingSlashAndRemovesTrailingSlash() {
        MatcherAssert.assertThat(
                TowerConnector.normalizeApiBasePath("api/controller/v2/"),
                CoreMatchers.is(TowerConnector.API_BASE_PATH_AAP_CONTROLLER));
    }

    @Test
    public void buildEndpoint_usesLegacyApiBasePathByDefault() {
        MatcherAssert.assertThat(
                TowerConnector.buildEndpoint("workflow_job_templates/8/", null),
                CoreMatchers.is("/api/v2/workflow_job_templates/8/"));
    }

    @Test
    public void buildEndpoint_usesAAPControllerApiBasePath() {
        MatcherAssert.assertThat(
                TowerConnector.buildEndpoint("workflow_job_templates/8/", TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is("/api/controller/v2/workflow_job_templates/8/"));
    }

    @Test
    public void buildEndpoint_keepsAbsoluteApiEndpoint() {
        MatcherAssert.assertThat(
                TowerConnector.buildEndpoint("/api/v2/jobs/", TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is("/api/v2/jobs/"));
    }

    @Test
    public void buildOAuthTokenEndpoint_usesLegacyApiBasePathByDefault() {
        MatcherAssert.assertThat(
                TowerConnector.buildOAuthTokenEndpoint(null),
                CoreMatchers.is("/api/v2/tokens/"));
    }

    @Test
    public void buildOAuthTokenEndpoint_usesGatewayForAAPControllerApiBasePath() {
        MatcherAssert.assertThat(
                TowerConnector.buildOAuthTokenEndpoint(TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is(TowerConnector.API_GATEWAY_TOKEN_ENDPOINT));
    }

    @Test
    public void shouldProbeOAuthSupport_keepsLegacyProbe() {
        MatcherAssert.assertThat(
                TowerConnector.shouldProbeOAuthSupport(TowerConnector.API_BASE_PATH_LEGACY),
                CoreMatchers.is(true));
    }

    @Test
    public void shouldProbeOAuthSupport_skipsProbeForAAPControllerMode() {
        MatcherAssert.assertThat(
                TowerConnector.shouldProbeOAuthSupport(TowerConnector.API_BASE_PATH_AAP_CONTROLLER),
                CoreMatchers.is(false));
    }

    @Test
    public void buildJobURL_keepsLegacyJobURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 8, "job"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/8"));
    }

    @Test
    public void buildJobURL_usesLegacyWorkflowJobURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 770889, "workflow_job"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/workflow/770889"));
    }

    @Test
    public void buildJobURL_usesLegacyWorkflowAliasURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 770889, TowerConnector.WORKFLOW_TEMPLATE_TYPE),
                CoreMatchers.is("https://ansible.example.com/#/jobs/workflow/770889"));
    }

    @Test
    public void buildJobURL_keepsLegacyProjectUpdateURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 9, "project_update"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/project/9"));
    }

    @Test
    public void buildJobURL_keepsLegacyInventoryUpdateURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://ansible.example.com", TowerConnector.API_BASE_PATH_LEGACY, 10, "inventory_update"),
                CoreMatchers.is("https://ansible.example.com/#/jobs/inventory/10"));
    }

    @Test
    public void buildJobURL_usesAAPControllerPlaybookOutputURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 8, "job"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/playbook/8/output"));
    }

    @Test
    public void buildJobURL_usesAAPControllerWorkflowOutputURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 7, "workflow_job"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/workflow/7/output"));
    }

    @Test
    public void buildJobURL_usesAAPControllerProjectUpdateOutputURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 9, "project_update"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/project_update/9/output"));
    }

    @Test
    public void buildJobURL_usesAAPControllerInventoryUpdateOutputURL() {
        MatcherAssert.assertThat(
                TowerConnector.buildJobURL("https://aap.example.com", TowerConnector.API_BASE_PATH_AAP_CONTROLLER, 10, "inventory_update"),
                CoreMatchers.is("https://aap.example.com/execution/jobs/inventory_update/10/output"));
    }

    @Test
    public void isTransientGatewayStatus_onlyRetriesGatewayFailures() {
        MatcherAssert.assertThat(TowerConnector.isTransientGatewayStatus(502), CoreMatchers.is(true));
        MatcherAssert.assertThat(TowerConnector.isTransientGatewayStatus(503), CoreMatchers.is(true));
        MatcherAssert.assertThat(TowerConnector.isTransientGatewayStatus(504), CoreMatchers.is(true));
        MatcherAssert.assertThat(TowerConnector.isTransientGatewayStatus(500), CoreMatchers.is(false));
        MatcherAssert.assertThat(TowerConnector.isTransientGatewayStatus(200), CoreMatchers.is(false));
    }
}
