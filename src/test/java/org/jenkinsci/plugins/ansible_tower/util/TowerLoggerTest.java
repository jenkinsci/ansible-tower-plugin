package org.jenkinsci.plugins.ansible_tower.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.logging.Handler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TowerLoggerTest {
    private final Logger julLogger = Logger.getLogger(TowerLogger.class.getName());
    private final RecordingHandler handler = new RecordingHandler();
    private boolean originalUseParentHandlers;
    private Level originalLevel;

    @BeforeEach
    void attachHandler() {
        originalUseParentHandlers = julLogger.getUseParentHandlers();
        originalLevel = julLogger.getLevel();
        julLogger.setUseParentHandlers(false);
        julLogger.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
        julLogger.addHandler(handler);
    }

    @AfterEach
    void detachHandler() {
        julLogger.removeHandler(handler);
        julLogger.setLevel(originalLevel);
        julLogger.setUseParentHandlers(originalUseParentHandlers);
    }

    @Test
    void debug_doesNotLogWhenDebuggingIsDisabled() {
        new TowerLogger().debug("hidden");

        assertThat(handler.record, nullValue());
    }

    @Test
    void debug_usesFineLevelAndJenkinsCompatibleLoggerWhenEnabled() {
        TowerLogger towerLogger = new TowerLogger();
        towerLogger.setDebugging(true);

        towerLogger.debug("request completed");

        assertThat(handler.record.getLoggerName(), is(TowerLogger.class.getName()));
        assertThat(handler.record.getLevel(), is(Level.FINE));
        assertThat(handler.record.getMessage(), is("{0}{1}"));
        assertThat(handler.record.getParameters(), is(new Object[] {"[Ansible-Tower] ", "request completed"}));
    }

    @SuppressWarnings("deprecation")
    @Test
    void logMessage_keepsCompatibilityByDelegatingToDebug() {
        TowerLogger towerLogger = new TowerLogger();
        towerLogger.setDebugging(true);

        towerLogger.logMessage("legacy diagnostic");

        assertThat(handler.record.getLevel(), is(Level.FINE));
    }

    @Test
    void warning_logsWhenDebuggingIsDisabled() {
        new TowerLogger().warning("gateway failure");

        assertThat(handler.record.getLevel(), is(Level.WARNING));
    }

    @Test
    void severe_logsWhenDebuggingIsDisabled() {
        new TowerLogger().severe("retries exhausted");

        assertThat(handler.record.getLevel(), is(Level.SEVERE));
    }

    @Test
    void writeMessage_logsWithoutDebugFlag() {
        TowerLogger.writeMessage("connection test");

        assertThat(handler.record.getLevel(), is(Level.INFO));
    }

    @Test
    void consoleMessages_areVisibleWithoutDebuggingAndUseConsistentSeverity() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TowerLogger towerLogger = new TowerLogger(new PrintStream(output, true, StandardCharsets.UTF_8));

        towerLogger.consoleInfo("resolving template");
        towerLogger.consoleWarning("gateway unavailable");
        towerLogger.consoleError("lookup failed");

        String console = output.toString(StandardCharsets.UTF_8);
        assertThat(console.contains("[Ansible-Tower] INFO: resolving template"), is(true));
        assertThat(console.contains("[Ansible-Tower] WARNING: gateway unavailable"), is(true));
        assertThat(console.contains("[Ansible-Tower] ERROR: lookup failed"), is(true));
    }

    @Test
    void sanitizers_removeQueryValuesAndCredentialMaterial() {
        assertThat(TowerLogger.sanitizeEndpoint(
            "/api/controller/v2/job_templates/?name=private-template&page=2"),
            is("/api/controller/v2/job_templates/?name=<redacted>&page=<redacted>"));
        assertThat(TowerLogger.sanitizeMessage(
            "authorization=Bearer-secret password=hunter2 token=abc failure"),
            is("authorization=<redacted> password=<redacted> token=<redacted> failure"));
        assertThat(TowerLogger.sanitizeUrl(
            "https://user:password@aap.example.com/api/controller/v2/jobs/?name=private"),
            is("https://aap.example.com/api/controller/v2/jobs/?name=<redacted>"));
    }

    @Test
    void consoleErrorsAlsoSanitizeTheSystemLogRecord() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TowerLogger towerLogger = new TowerLogger(new PrintStream(output, true, StandardCharsets.UTF_8));

        towerLogger.consoleError("request failed token=secret-value");

        assertThat(handler.record.getParameters(),
            is(new Object[] {"[Ansible-Tower] ", "request failed token=<redacted>"}));
        assertThat(output.toString(StandardCharsets.UTF_8).contains("secret-value"), is(false));
    }

    @Test
    void unexpectedFailuresDoNotCopyRuntimeMessagesIntoSystemDiagnostics() {
        String detail = TowerLogger.reportUnexpected("template lookup",
            new IllegalArgumentException("<html>private upstream response</html>"));

        assertThat(detail.contains("<html>"), is(false));
        assertThat(handler.record.getMessage().contains("<html>"), is(false));
        assertThat(handler.record.getThrown().getMessage(),
            is("IllegalArgumentException during template lookup"));
    }

    private static class RecordingHandler extends Handler {
        private LogRecord record;

        @Override
        public void publish(LogRecord record) {
            this.record = record;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
