package org.jenkinsci.plugins.ansible_tower.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerTransientException;
import org.junit.jupiter.api.Test;

class JobPollingCoordinatorTest {

    @Test
    void transientLogsDoNotBlockStatusAndRecoverDuringFinalImport() throws Exception {
        FakeOperations operations = new FakeOperations(
            Arrays.asList(running(), running(), completed(false)), 2);

        JobPollingCoordinator.Result result = coordinator(operations, "full").waitForCompletion();

        assertThat(result.isSuccessful(), is(true));
        assertThat(result.getLogImportResult(), is(JobPollingCoordinator.LOG_IMPORT_SUCCESS));
        assertThat(operations.statusCalls, is(3));
        assertThat(operations.logCalls, is(3));
    }

    @Test
    void exhaustedLogImportDoesNotChangeSuccessfulAapResult() throws Exception {
        FakeOperations operations = new FakeOperations(
            Arrays.asList(running(), completed(false)), Integer.MAX_VALUE);

        JobPollingCoordinator.Result result = coordinator(operations, "full").waitForCompletion();

        assertThat(result.isSuccessful(), is(true));
        assertThat(result.getLogImportResult(), is(JobPollingCoordinator.LOG_IMPORT_PARTIAL));
        assertThat(operations.statusCalls, is(2));
        assertThat(operations.logCalls, is(4));
    }

    @Test
    void varsModeAlsoReturnsPartialWhenFinalEventsAreUnavailable() throws Exception {
        FakeOperations operations = new FakeOperations(
            Arrays.asList(completed(false)), Integer.MAX_VALUE);

        JobPollingCoordinator.Result result = coordinator(operations, "vars").waitForCompletion();

        assertThat(result.isSuccessful(), is(true));
        assertThat(result.getLogImportResult(), is(JobPollingCoordinator.LOG_IMPORT_PARTIAL));
    }

    @Test
    void disabledLogsAreSkippedWithoutCallingLogEndpoint() throws Exception {
        FakeOperations operations = new FakeOperations(
            Arrays.asList(running(), completed(false)), 0);

        JobPollingCoordinator.Result result = coordinator(operations, "false").waitForCompletion();

        assertThat(result.getLogImportResult(), is(JobPollingCoordinator.LOG_IMPORT_SKIPPED));
        assertThat(operations.logCalls, is(0));
    }

    @Test
    void aapFailureIsReturnedAsJobFailure() throws Exception {
        FakeOperations operations = new FakeOperations(Arrays.asList(completed(true)), 0);

        JobPollingCoordinator.Result result = coordinator(operations, "false").waitForCompletion();

        assertThat(result.isSuccessful(), is(false));
    }

    @Test
    void statusFailsAfterInitialRequestAndFiveRetries() {
        FakeOperations operations = new FakeOperations(new ArrayList<TowerJobStatus>(), 0);
        operations.statusFailures = Integer.MAX_VALUE;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JobPollingCoordinator coordinator = coordinator(operations, "false", output);

        assertThrows(AnsibleTowerTransientException.class, coordinator::waitForCompletion);
        assertThat(operations.statusCalls, is(6));
        assertThat(output.toString(), containsString("exhausted retries after 6 attempts"));
    }

    @Test
    void transientStatusRecoveryContinuesPolling() throws Exception {
        FakeOperations operations = new FakeOperations(Arrays.asList(completed(false)), 0);
        operations.statusFailures = 2;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        JobPollingCoordinator.Result result = coordinator(operations, "false", output).waitForCompletion();

        assertThat(result.isSuccessful(), is(true));
        assertThat(operations.statusCalls, is(3));
        assertThat(output.toString(), containsString("recovered after 2 transient failure(s)"));
    }

    @Test
    void seventySecondJobStaysWithinExpectedRequestBudget() throws Exception {
        List<TowerJobStatus> statuses = new ArrayList<TowerJobStatus>();
        for(int i = 0; i < 14; i++) { statuses.add(running()); }
        statuses.add(completed(false));
        FakeOperations operations = new FakeOperations(statuses, 0);

        JobPollingCoordinator.Result result = coordinator(operations, "full").waitForCompletion();

        assertThat(result.isSuccessful(), is(true));
        assertThat(operations.statusCalls, is(15));
        assertThat(operations.logCalls, is(8));
    }

    private static JobPollingCoordinator coordinator(FakeOperations operations, String mode) {
        return coordinator(operations, mode, new ByteArrayOutputStream());
    }

    private static JobPollingCoordinator coordinator(FakeOperations operations, String mode,
            ByteArrayOutputStream output) {
        return new JobPollingCoordinator(operations, new PrintStream(output), mode,
            new FakeClock(), new TowerLogger());
    }

    private static TowerJobStatus running() {
        return new TowerJobStatus(false, false);
    }

    private static TowerJobStatus completed(boolean failed) {
        return new TowerJobStatus(true, failed);
    }

    private static final class FakeClock implements JobPollingCoordinator.Clock {
        private long now;

        @Override
        public long currentTimeMillis() {
            return now;
        }

        @Override
        public void sleep(long milliseconds) {
            now += milliseconds;
        }
    }

    private static final class FakeOperations implements JobPollingCoordinator.JobOperations {
        private final List<TowerJobStatus> statuses;
        private final int transientLogFailures;
        private int statusFailures;
        private int statusCalls;
        private int logCalls;

        FakeOperations(List<TowerJobStatus> statuses, int transientLogFailures) {
            this.statuses = statuses;
            this.transientLogFailures = transientLogFailures;
        }

        @Override
        public TowerJobStatus pollStatus() throws AnsibleTowerException {
            statusCalls++;
            if(statusCalls <= statusFailures) {
                throw new AnsibleTowerTransientException("gateway unavailable", 503);
            }
            return statuses.get(Math.min(statusCalls - statusFailures - 1, statuses.size() - 1));
        }

        @Override
        public Vector<String> pollLogs() throws AnsibleTowerException {
            logCalls++;
            if(logCalls <= transientLogFailures) {
                throw new AnsibleTowerTransientException("gateway unavailable", 502);
            }
            Vector<String> events = new Vector<String>();
            events.add("event " + logCalls);
            return events;
        }
    }
}
