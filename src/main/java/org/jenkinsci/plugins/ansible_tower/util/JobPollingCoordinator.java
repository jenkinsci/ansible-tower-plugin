package org.jenkinsci.plugins.ansible_tower.util;

import java.io.PrintStream;
import java.util.Vector;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerTransientException;

public final class JobPollingCoordinator {
    public static final String LOG_IMPORT_SKIPPED = "SKIPPED";
    public static final String LOG_IMPORT_DEFERRED = "DEFERRED";
    public static final String LOG_IMPORT_SUCCESS = "SUCCESS";
    public static final String LOG_IMPORT_PARTIAL = "PARTIAL";

    static final long STATUS_POLL_INTERVAL_MS = 5000L;
    static final long LOG_POLL_INTERVAL_MS = 10000L;
    static final long[] STATUS_RETRY_DELAYS_MS = {5000L, 10000L, 20000L, 30000L, 30000L};
    static final long[] LOG_RETRY_DELAYS_MS = {10000L, 20000L, 30000L};
    static final long[] FINAL_LOG_RETRY_DELAYS_MS = {2000L, 5000L};

    private static final String PREFIX = "[Ansible-Tower] ";

    interface Clock {
        long currentTimeMillis();
        void sleep(long milliseconds) throws InterruptedException;
    }

    interface JobOperations {
        TowerJobStatus pollStatus() throws AnsibleTowerException;
        Vector<String> pollLogs() throws AnsibleTowerException;
    }

    public static final class Result {
        private final boolean successful;
        private final String logImportResult;

        Result(boolean successful, String logImportResult) {
            this.successful = successful;
            this.logImportResult = logImportResult;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getLogImportResult() {
            return logImportResult;
        }
    }

    private static final class SystemClock implements Clock {
        @Override
        public long currentTimeMillis() {
            return System.nanoTime() / 1_000_000L;
        }

        @Override
        public void sleep(long milliseconds) throws InterruptedException {
            Thread.sleep(milliseconds);
        }
    }

    private final JobOperations job;
    private final PrintStream console;
    private final TowerLogger systemLogger;
    private final Clock clock;
    private final String logMode;
    private final boolean logsEnabled;

    public JobPollingCoordinator(TowerJob job, PrintStream console, String logMode) {
        this(new JobOperations() {
            @Override
            public TowerJobStatus pollStatus() throws AnsibleTowerException {
                return job.pollStatusOnce();
            }

            @Override
            public Vector<String> pollLogs() throws AnsibleTowerException {
                return job.getLogsOnce();
            }
        }, console, logMode, new SystemClock(), new TowerLogger());
    }

    JobPollingCoordinator(JobOperations job, PrintStream console, String logMode, Clock clock,
            TowerLogger systemLogger) {
        this.job = job;
        this.console = console;
        this.logMode = logMode;
        this.logsEnabled = !"false".equals(logMode);
        this.clock = clock;
        this.systemLogger = systemLogger;
    }

    public Result waitForCompletion() throws AnsibleTowerException, InterruptedException {
        long nextStatusPoll = clock.currentTimeMillis();
        long nextLogPoll = nextStatusPoll;
        int statusFailures = 0;
        int logFailures = 0;

        while(true) {
            if(Thread.interrupted()) {
                throw new InterruptedException("Interrupted while polling Tower job");
            }

            long now = clock.currentTimeMillis();
            if(now >= nextStatusPoll) {
                try {
                    TowerJobStatus status = job.pollStatus();
                    if(statusFailures > 0) {
                        recovered("job status", statusFailures);
                    }
                    statusFailures = 0;
                    if(status.isCompleted()) {
                        String logResult = logsEnabled ? importFinalLogs(logFailures) : LOG_IMPORT_SKIPPED;
                        return new Result(!status.isFailed(), logResult);
                    }
                    nextStatusPoll = now + STATUS_POLL_INTERVAL_MS;
                } catch(AnsibleTowerTransientException transientFailure) {
                    statusFailures++;
                    if(statusFailures > STATUS_RETRY_DELAYS_MS.length) {
                        exhausted("job status", transientFailure, statusFailures);
                        throw transientFailure;
                    }
                    long delay = STATUS_RETRY_DELAYS_MS[statusFailures - 1];
                    retry("job status", transientFailure, statusFailures,
                        STATUS_RETRY_DELAYS_MS.length, delay);
                    nextStatusPoll = now + delay;
                }
            }

            now = clock.currentTimeMillis();
            if(logsEnabled && now >= nextLogPoll) {
                try {
                    printEvents(job.pollLogs());
                    if(logFailures > 0) {
                        recovered("job events", logFailures);
                    }
                    logFailures = 0;
                    nextLogPoll = now + LOG_POLL_INTERVAL_MS;
                } catch(AnsibleTowerTransientException transientFailure) {
                    logFailures++;
                    int delayIndex = Math.min(logFailures - 1, LOG_RETRY_DELAYS_MS.length - 1);
                    long delay = LOG_RETRY_DELAYS_MS[delayIndex];
                    retry("job events", transientFailure, logFailures, -1, delay);
                    nextLogPoll = now + delay;
                } catch(AnsibleTowerException permanentLogFailure) {
                    warn("Job event import failed and will be retried after completion: "
                        + permanentLogFailure.getMessage());
                    logFailures++;
                    nextLogPoll = Long.MAX_VALUE;
                }
            }

            long nextWakeup = Math.min(nextStatusPoll, logsEnabled ? nextLogPoll : Long.MAX_VALUE);
            long sleepMillis = Math.max(1L, nextWakeup - clock.currentTimeMillis());
            clock.sleep(sleepMillis);
        }
    }

    private String importFinalLogs(int previousFailures) throws InterruptedException {
        for(int attempt = 0; attempt <= FINAL_LOG_RETRY_DELAYS_MS.length; attempt++) {
            try {
                printEvents(job.pollLogs());
                if(previousFailures + attempt > 0) {
                    recovered("final job events", previousFailures + attempt);
                }
                return LOG_IMPORT_SUCCESS;
            } catch(AnsibleTowerTransientException transientFailure) {
                if(attempt == FINAL_LOG_RETRY_DELAYS_MS.length) {
                    warn("final job events exhausted retries; AAP job completed, but event import "
                        + "is incomplete after " + (attempt + 1) + " final attempts: "
                        + describe(transientFailure));
                    return LOG_IMPORT_PARTIAL;
                }
                long delay = FINAL_LOG_RETRY_DELAYS_MS[attempt];
                retry("final job events", transientFailure, attempt + 1,
                    FINAL_LOG_RETRY_DELAYS_MS.length, delay);
                clock.sleep(delay);
            } catch(AnsibleTowerException permanentFailure) {
                warn("AAP job completed, but final event import is incomplete: "
                    + permanentFailure.getMessage());
                return LOG_IMPORT_PARTIAL;
            }
        }
        return LOG_IMPORT_PARTIAL;
    }

    private void printEvents(Vector<String> events) {
        if("vars".equals(logMode)) {
            return;
        }
        for(String event : events) {
            console.println(event);
        }
    }

    private void retry(String operation, AnsibleTowerTransientException failure, int attempt,
            int maximumAttempts, long delay) {
        String maximum = maximumAttempts < 0 ? "ongoing" : Integer.toString(maximumAttempts);
        warn(operation + " request failed: " + describe(failure) + ", retry=" + attempt + "/"
            + maximum + ", retryDelayMs=" + delay);
    }

    private void recovered(String operation, int failures) {
        String message = operation + " request recovered after " + failures + " transient failure(s)";
        systemLogger.info(message);
        console.println(PREFIX + "INFO: " + message);
    }

    private void exhausted(String operation, AnsibleTowerTransientException failure, int attempts) {
        String message = operation + " request exhausted retries after " + attempts
            + " attempts: " + describe(failure);
        systemLogger.severe(message);
        console.println(PREFIX + "ERROR: " + message);
    }

    private void warn(String message) {
        systemLogger.warning(message);
        console.println(PREFIX + "WARNING: " + message);
    }

    private static String describe(AnsibleTowerTransientException failure) {
        if(failure.getStatusCode() > 0) {
            return failure.getMessage() + ", httpStatus=" + failure.getStatusCode();
        }
        return failure.getMessage();
    }
}
