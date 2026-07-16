package org.jenkinsci.plugins.ansible_tower.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.sf.json.JSONObject;
import org.apache.http.util.EntityUtils;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.conn.params.ConnManagerParams;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;

class TowerConnectorTest {

    @Test
    public void configureHttpTimeouts_appliesConnectPoolAndReadLimits() {
        BasicHttpParams params = new BasicHttpParams();

        TowerConnector.configureHttpTimeouts(params);

        MatcherAssert.assertThat(HttpConnectionParams.getConnectionTimeout(params), CoreMatchers.is(10000));
        MatcherAssert.assertThat(ConnManagerParams.getTimeout(params), CoreMatchers.is(10000L));
        MatcherAssert.assertThat(HttpConnectionParams.getSoTimeout(params), CoreMatchers.is(30000));
    }

    @Test
    public void transientTransportClassification_excludesTlsConfigurationFailures() {
        MatcherAssert.assertThat(
            TowerConnector.isTransientTransportFailure(new SocketTimeoutException("timed out")),
            CoreMatchers.is(true));
        MatcherAssert.assertThat(
            TowerConnector.isTransientTransportFailure(new SSLHandshakeException("bad certificate")),
            CoreMatchers.is(false));
    }

    @Test
    public void workflowEvents_processCompletedParallelNodesAcrossPagesWithoutDuplicates() throws Exception {
        AtomicInteger firstPageCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/ping/", exchange -> respond(exchange,
            "{\"version\":\"3.8.0\"}"));
        server.createContext("/api/v2/workflow_jobs/7/workflow_nodes/", exchange -> {
            if(exchange.getRequestURI().getQuery().contains("page=2")) {
                respond(exchange, workflowPage(null, node(3, "third", "successful")));
                return;
            }
            boolean firstRequest = firstPageCalls.incrementAndGet() == 1;
            respond(exchange, workflowPage(
                "/api/v2/workflow_jobs/7/workflow_nodes/?page=2",
                node(1, "first", firstRequest ? "running" : "successful"),
                node(2, "second", "successful")));
        });
        server.start();
        try {
            TowerConnector connector = new TowerConnector(
                "http://127.0.0.1:" + server.getAddress().getPort(), null, null,
                "test-token", false, false);

            Vector<String> firstImport = connector.getLogEventsOnce(7L, TowerConnector.WORKFLOW_TEMPLATE_TYPE);
            Vector<String> secondImport = connector.getLogEventsOnce(7L, TowerConnector.WORKFLOW_TEMPLATE_TYPE);

            MatcherAssert.assertThat(firstImport.toString(), CoreMatchers.containsString("second => successful"));
            MatcherAssert.assertThat(firstImport.toString(), CoreMatchers.containsString("third => successful"));
            MatcherAssert.assertThat(firstImport.toString().contains("first =>"), CoreMatchers.is(false));
            MatcherAssert.assertThat(secondImport.toString(), CoreMatchers.containsString("first => successful"));
            MatcherAssert.assertThat(secondImport.toString().contains("second =>"), CoreMatchers.is(false));
            MatcherAssert.assertThat(secondImport.toString().contains("third =>"), CoreMatchers.is(false));
        } finally {
            server.stop(0);
        }
    }

    private static String node(long id, String name, String status) {
        return "{\"id\":" + id + ",\"summary_fields\":{\"job\":{\"id\":" + (100 + id)
            + ",\"name\":\"" + name + "\",\"status\":\"" + status
            + "\"},\"unified_job_template\":{\"unified_job_type\":\"job\"}}}";
    }

    private static String workflowPage(String next, String... nodes) {
        String nextValue = next == null ? "null" : "\"" + next + "\"";
        return "{\"next\":" + nextValue + ",\"results\":[" + String.join(",", nodes) + "]}";
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        try(OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @Test
    public void failedGet_writesSanitizedOperationalMetadataToBuildConsole() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/ping/", exchange -> respond(exchange, "{\"version\":\"3.8.0\"}"));
        server.createContext("/api/v2/job_templates/", exchange -> respond(exchange, 502, "gateway failure"));
        server.start();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            TowerConnector connector = new TowerConnector(
                "http://127.0.0.1:" + server.getAddress().getPort(), null, null,
                "test-token", false, false);
            connector.setConsole(new PrintStream(output, true, StandardCharsets.UTF_8));

            connector.makeRequest(TowerConnector.GET,
                "/job_templates/?name=private-template", null, false);

            String console = output.toString(StandardCharsets.UTF_8);
            MatcherAssert.assertThat(console, CoreMatchers.containsString("method=GET"));
            MatcherAssert.assertThat(console, CoreMatchers.containsString("httpStatus=502"));
            MatcherAssert.assertThat(console, CoreMatchers.containsString("durationMs="));
            MatcherAssert.assertThat(console, CoreMatchers.containsString("name=<redacted>"));
            MatcherAssert.assertThat(console.contains("private-template"), CoreMatchers.is(false));
            MatcherAssert.assertThat(console.contains("test-token"), CoreMatchers.is(false));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void htmlGatewayFailure_isNotParsedOrCopiedIntoConsoleException() throws Exception {
        String html = "<html><style>secret-route-style</style><h1>Application is not available</h1></html>";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/ping/", exchange -> respond(exchange, "{\"version\":\"3.8.0\"}"));
        server.createContext("/api/v2/workflow_job_templates/8/",
            exchange -> respond(exchange, 503, html));
        server.start();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            TowerConnector connector = new TowerConnector(
                "http://127.0.0.1:" + server.getAddress().getPort(), null, null,
                "test-token", false, false);
            connector.setConsole(new PrintStream(output, true, StandardCharsets.UTF_8));

            AnsibleTowerException failure = org.junit.jupiter.api.Assertions.assertThrows(
                AnsibleTowerException.class,
                () -> connector.getJobTemplate("8", TowerConnector.WORKFLOW_TEMPLATE_TYPE));

            MatcherAssert.assertThat(failure.getMessage(), CoreMatchers.containsString("returned HTTP 503"));
            MatcherAssert.assertThat(failure.getMessage().contains("<html>"), CoreMatchers.is(false));
            String console = output.toString(StandardCharsets.UTF_8);
            MatcherAssert.assertThat(console, CoreMatchers.containsString("httpStatus=503"));
            MatcherAssert.assertThat(console.contains("<html>"), CoreMatchers.is(false));
            MatcherAssert.assertThat(console.contains("secret-route-style"), CoreMatchers.is(false));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void transientLaunchResponse_warnsThatOutcomeIsUnknownWithoutLoggingBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/ping/", exchange -> respond(exchange, "{\"version\":\"3.8.0\"}"));
        server.createContext("/api/v2/job_templates/12/launch/",
            exchange -> respond(exchange, 502, "gateway failure"));
        server.start();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            TowerConnector connector = new TowerConnector(
                "http://127.0.0.1:" + server.getAddress().getPort(), null, null,
                "test-token", false, false);
            connector.setConsole(new PrintStream(output, true, StandardCharsets.UTF_8));
            JSONObject body = new JSONObject();
            body.put("extra_vars", "TOP_SECRET_VALUE");

            connector.makeRequest(TowerConnector.POST,
                "/job_templates/12/launch/", body, false);

            String console = output.toString(StandardCharsets.UTF_8);
            MatcherAssert.assertThat(console, CoreMatchers.containsString("method=POST"));
            MatcherAssert.assertThat(console, CoreMatchers.containsString("httpStatus=502"));
            MatcherAssert.assertThat(console, CoreMatchers.containsString("launch outcome is unknown"));
            MatcherAssert.assertThat(console, CoreMatchers.containsString("Automatic retry was not performed"));
            MatcherAssert.assertThat(console.contains("TOP_SECRET_VALUE"), CoreMatchers.is(false));
            MatcherAssert.assertThat(console.contains("test-token"), CoreMatchers.is(false));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void projectSyncPost_isClassifiedAsHavingAnUncertainOutcome() {
        MatcherAssert.assertThat(
            TowerConnector.isOutcomeUncertainEndpoint("/projects/12/update/"), CoreMatchers.is(true));
        MatcherAssert.assertThat(
            TowerConnector.unknownOutcomeMessage("/projects/12/update/", "Jenkins received HTTP 503"),
            CoreMatchers.containsString("project sync outcome is unknown"));
    }

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

    @Test
    public void buildRetryLogMessage_containsOperationalMetadataWithoutPayload() {
        String message = TowerConnector.buildRetryLogMessage(
                "workflow_events_poll", 720828L, TowerConnector.WORKFLOW_TEMPLATE_TYPE,
                "/api/controller/v2/workflow_jobs/720828/workflow_nodes/", 502, 1, 5, 10000L);

        MatcherAssert.assertThat(message, CoreMatchers.is(
                "workflow_events_poll failed: jobId=720828, templateType=workflow, "
                + "endpoint=/api/controller/v2/workflow_jobs/720828/workflow_nodes/, "
                + "httpStatus=502, attempt=1/5, retryDelayMs=10000"));
    }

    @Test
    public void buildRetryExhaustedLogMessage_containsFinalAttemptMetadata() {
        String message = TowerConnector.buildRetryExhaustedLogMessage(
                "job_status_poll", 720828L, TowerConnector.JOB_TEMPLATE_TYPE,
                "/api/controller/v2/jobs/720828/", 504, 6);

        MatcherAssert.assertThat(message, CoreMatchers.is(
                "job_status_poll exhausted retries: jobId=720828, templateType=job, "
                + "endpoint=/api/controller/v2/jobs/720828/, httpStatus=504, attempts=6"));
    }
}
