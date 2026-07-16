package org.jenkinsci.plugins.ansible_tower;

/*
    This class is a bridge between the Jenkins workflow/plugin step and TowerConnector.
    The intention is to abstract the "work" from the two Jenkins classes
 */

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Plugin;
import hudson.model.Run;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerRequestException;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerTransientException;
import org.jenkinsci.plugins.ansible_tower.util.*;
import org.jenkinsci.plugins.envinject.service.EnvInjectActionSetter;

import java.io.PrintStream;
import java.util.*;

public class AnsibleTowerRunner {
    private TowerJob myJob = null;
    private String lastFailureMessage = "Ansible Tower operation failed";

    public String getLastFailureMessage() { return lastFailureMessage; }

    private boolean fail(PrintStream console, String message) {
        lastFailureMessage = TowerLogger.sanitizeMessage(message);
        console.println("[Ansible-Tower] ERROR: " + lastFailureMessage);
        return false;
    }

    private void milestone(PrintStream console, String message) {
        console.println("[Ansible-Tower] INFO: " + TowerLogger.sanitizeMessage(message));
    }

    public boolean runJobTemplate(
            PrintStream logger, String towerServer, String towerCredentialsId, String jobTemplate, String jobType,
            String extraVars, String limit, String jobTags, String skipJobTags, String inventory, String credential, String scmBranch,
            boolean verbose, String importTowerLogs, boolean removeColor, EnvVars envVars, String templateType,
            boolean importWorkflowChildLogs, FilePath ws, Run<?, ?> run, Properties towerResults
    ) {
        return this.runJobTemplate(logger, towerServer, towerCredentialsId, jobTemplate, jobType, extraVars, limit,
                jobTags, skipJobTags, inventory, credential, scmBranch, verbose, importTowerLogs, removeColor, envVars,
                templateType, importWorkflowChildLogs, ws, run, towerResults, false);
    }
    
    public boolean runJobTemplate(
            PrintStream logger, String towerServer, String towerCredentialsId, String jobTemplate, String jobType,
            String extraVars, String limit, String jobTags, String skipJobTags, String inventory, String credential, String scmBranch,
            boolean verbose, String importTowerLogs, boolean removeColor, EnvVars envVars, String templateType,
            boolean importWorkflowChildLogs, FilePath ws, Run<?, ?> run, Properties towerResults, boolean async
    ) {
        milestone(logger, "Starting job template operation: server=" + towerServer
            + ", template=" + jobTemplate + ", templateType=" + templateType);
        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if (towerConfigToRunOn == null) {
            return fail(logger, "Ansible Tower server " + towerServer + " does not exist in Jenkins configuration");
        }

        if (towerCredentialsId != null && !towerCredentialsId.equals("")) {
            towerConfigToRunOn.setTowerCredentialsId(towerCredentialsId);
        }

        if (run != null) {
            towerConfigToRunOn.setRun(run);
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();
        myTowerConnection.setConsole(logger);
        this.myJob = new TowerJob(myTowerConnection);
        try {
            this.myJob.setTemplateType(templateType);
        } catch (AnsibleTowerException e) {
            return fail(logger, "Invalid template type: " + e.getMessage());
        }

        // Check the import logs settings
        if (!(importTowerLogs.matches("false") || importTowerLogs.matches("true") || importTowerLogs.matches("vars") || importTowerLogs.matches("full"))) {
            return fail(logger, "Import Tower Logs must be one of false, true, vars, or full");
        }

        // If they came in empty then set them to null so that we don't pass a nothing through
        if (jobTemplate != null && jobTemplate.equals("")) {
            jobTemplate = null;
        }
        if (extraVars != null && extraVars.equals("")) {
            extraVars = null;
        }
        if (limit != null && limit.equals("")) {
            limit = null;
        }
        if (jobTags != null && jobTags.equals("")) {
            jobTags = null;
        }
        if (skipJobTags != null && skipJobTags.equals("")) {
            skipJobTags = null;
        }
        if (inventory != null && inventory.equals("")) {
            inventory = null;
        }
        if (credential != null && credential.equals("")) {
            credential = null;
        }
        if (scmBranch != null && scmBranch.equals("")) {
            scmBranch = null;
        }

        // Expand all of the parameters
        String expandedJobTemplate = envVars.expand(jobTemplate);
        String expandedExtraVars = envVars.expand(extraVars);
        String expandedLimit = envVars.expand(limit);
        String expandedJobTags = envVars.expand(jobTags);
        String expandedSkipJobTags = envVars.expand(skipJobTags);
        String expandedInventory = envVars.expand(inventory);
        String expandedCredential = envVars.expand(credential);
        String expandedScmBranch = envVars.expand(scmBranch);

        if (verbose) {
            if (expandedJobTemplate != null && !expandedJobTemplate.equals(jobTemplate)) {
                logger.println("Expanded job template to " + expandedJobTemplate);
            }
            if (expandedExtraVars != null && !expandedExtraVars.equals(extraVars)) {
                logger.println("Expanded extra vars from the Jenkins environment");
            }
            if (expandedLimit != null && !expandedLimit.equals(limit)) {
                logger.println("Expanded limit to " + expandedLimit);
            }
            if (expandedJobTags != null && !expandedJobTags.equals(jobTags)) {
                logger.println("Expanded job tags to " + expandedJobTags);
            }
            if (expandedSkipJobTags != null && !expandedSkipJobTags.equals(skipJobTags)) {
                logger.println("Expanded skip job tags to " + expandedSkipJobTags);
            }
            if (expandedInventory != null && !expandedInventory.equals(inventory)) {
                logger.println("Expanded inventory to " + expandedInventory);
            }
            if (expandedCredential != null && !expandedCredential.equals(credential)) {
                logger.println("Expanded launch credential reference from the Jenkins environment");
            }
            if (expandedScmBranch != null && !expandedScmBranch.equals(scmBranch)) {
                logger.println("Expanded scmBranch to " + expandedScmBranch);
            }
        }

        if (expandedJobTags != null && expandedJobTags.equalsIgnoreCase("")) {
            if (!expandedJobTags.startsWith(",")) {
                expandedJobTags = "," + expandedJobTags;
            }
        }

        if (expandedSkipJobTags != null && expandedSkipJobTags.equalsIgnoreCase("")) {
            if (!expandedSkipJobTags.startsWith(",")) {
                expandedSkipJobTags = "," + expandedSkipJobTags;
            }
        }

        milestone(logger, "Resolving job template: template=" + jobTemplate);
        // Get the job template.
        JSONObject template;
        try {
            template = myTowerConnection.getJobTemplate(expandedJobTemplate, templateType);
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            if(e instanceof AnsibleTowerRequestException || e instanceof AnsibleTowerTransientException) {
                return fail(logger, "Job was not launched because the " + templateType
                    + " template lookup request failed");
            }
            return fail(logger, "Unable to lookup job template; the job was not launched: " + e.getMessage());
        }
        milestone(logger, "Job template resolved: templateId=" + template.getLong("id"));


        if (jobType != null && template.containsKey("ask_job_type_on_launch") && !template.getBoolean("ask_job_type_on_launch")) {
            logger.println("[WARNING]: Job type defined but prompt for job type on launch is not set in tower job");
        }
        if (expandedExtraVars != null && template.containsKey("ask_variables_on_launch") && !template.getBoolean("ask_variables_on_launch")) {
            logger.println("[WARNING]: Extra variables defined but prompt for variables on launch is not set in tower job");
        }
        if (expandedLimit != null && template.containsKey("ask_limit_on_launch") && !template.getBoolean("ask_limit_on_launch")) {
            logger.println("[WARNING]: Limit defined but prompt for limit on launch is not set in tower job");
        }
        if (expandedJobTags != null && template.containsKey("ask_tags_on_launch") && !template.getBoolean("ask_tags_on_launch")) {
            logger.println("[WARNING]: Job Tags defined but prompt for tags on launch is not set in tower job");
        }
        if (expandedSkipJobTags != null && template.containsKey("ask_skip_tags_on_launch") && !template.getBoolean("ask_skip_tags_on_launch")) {
            logger.println("[WARNING]: Skip Job Tags defined but prompt for tags on launch is not set in tower job");
        }
        if (expandedInventory != null && template.containsKey("ask_inventory_on_launch") && !template.getBoolean("ask_inventory_on_launch")) {
            logger.println("[WARNING]: Inventory defined but prompt for inventory on launch is not set in tower job");
        }
        if (expandedCredential != null && template.containsKey("ask_credential_on_launch") && !template.getBoolean("ask_credential_on_launch")) {
            logger.println("[WARNING]: Credential defined but prompt for credential on launch is not set in tower job");
        }
        if (expandedScmBranch != null) {
            if (template.containsKey("ask_scm_branch_on_launch")) {
                if (!template.getBoolean("ask_scm_branch_on_launch")) {
                    logger.println("[WARNING]: SCM Branch defined but pompt for SCM back on launch is not set in tower job");
                }
            } else {
                logger.println("[WARNING]: SCM Branch defined but job template does not appear to support SCM branch on launch");
            }
        }
        // Here are some more options we may want to use someday
        //    "ask_diff_mode_on_launch": false,
        //    "ask_skip_tags_on_launch": false,
        //    "ask_job_type_on_launch": false,
        //    "ask_verbosity_on_launch": false,

        myTowerConnection.setRemoveColor(removeColor);
        myTowerConnection.setGetWorkflowChildLogs(importWorkflowChildLogs);


        if (verbose) {
            logger.println("Requesting tower to run " + templateType + " template " + expandedJobTemplate);
        }

        milestone(logger, "Launching job template: templateId=" + template.getLong("id"));
        try {
            this.myJob.setJobId(myTowerConnection.submitTemplate(template.getLong("id"), expandedExtraVars, expandedLimit, expandedJobTags, expandedSkipJobTags, jobType, expandedInventory, expandedCredential, expandedScmBranch, templateType));
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Unable to confirm job launch: " + e.getMessage());
        }

        String jobURL = myTowerConnection.getJobURL(this.myJob.getJobID(), templateType);
        milestone(logger, "Job accepted by controller: jobId=" + this.myJob.getJobID());
        logger.println("[Ansible-Tower] Template Job URL: " + jobURL);

        towerResults.put("JOB_ID", Long.toString(this.myJob.getJobID()));
        towerResults.put("JOB_URL", jobURL);

        if (async) {
            towerResults.put("job", this.myJob);
            towerResults.put("LOG_IMPORT_RESULT", JobPollingCoordinator.LOG_IMPORT_DEFERRED);
            myTowerConnection.releaseToken();
            return true;
        }

        // Preserve the existing full/vars event parsing behavior.
        if (importTowerLogs.matches("full") || importTowerLogs.matches("vars")) {
            myTowerConnection.setGetFullLogs(true);
        }
        JobPollingCoordinator.Result pollingResult;
        try {
            pollingResult = new JobPollingCoordinator(this.myJob, logger, importTowerLogs)
                .waitForCompletion();
        } catch (InterruptedException interrupted) {
            boolean result = this.cancelJob(logger);
            Thread.currentThread().interrupt();
            return result;
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Failed to poll job status from Tower: " + e.getMessage());
        }

        boolean wasSuccessful = pollingResult.isSuccessful();
        towerResults.put("LOG_IMPORT_RESULT", pollingResult.getLogImportResult());

        HashMap<String, String> jenkinsVariables;
        try {
            jenkinsVariables = this.myJob.getExports();
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Failed to get exported variables: " + e.getMessage());
        }
        for (Map.Entry<String, String> entrySet : jenkinsVariables.entrySet()) {
            if (verbose) {
                logger.println("Receiving exported variable '" + entrySet.getKey() + "' from Tower job");
            }
            envVars.put(entrySet.getKey(), entrySet.getValue());
            towerResults.put(entrySet.getKey(), entrySet.getValue());
        }
        if (envVars.size() != 0) {
            Plugin envInjectPlugin = Jenkins.getInstance() != null ? Jenkins.getInstance().getPlugin("envinject") : null;
            if (envInjectPlugin != null) {
                EnvInjectActionSetter envInjectActionSetter = new EnvInjectActionSetter(ws);
                try {
                    envInjectActionSetter.addEnvVarsToRun(run, envVars);
                } catch (Exception e) {
                    myTowerConnection.releaseToken();
                    return fail(logger, "Unable to inject environment variables: " + e.getMessage());
                }
            }

            if (envInjectPlugin != null) {
                EnvInjectActionSetter envInjectActionSetter = new EnvInjectActionSetter(ws);
                try {
                    envInjectActionSetter.addEnvVarsToRun(run, envVars);
                } catch (Exception e) {
                    myTowerConnection.releaseToken();
                    return fail(logger, "Unable to inject environment variables: " + e.getMessage());
                }
            }
        }

        if (wasSuccessful) {
            milestone(logger, "Job completed: jobId=" + this.myJob.getJobID() + ", result=SUCCESS");
        } else {
            fail(logger, "Job completed: jobId=" + this.myJob.getJobID() + ", result=FAILED");
        }

        towerResults.put("JOB_RESULT", wasSuccessful ? "SUCCESS" : "FAILED");

        myTowerConnection.releaseToken();
        return wasSuccessful;
    }

    public void getJobLogs(String importTowerLogs, PrintStream logger) throws AnsibleTowerException {
        if (importTowerLogs.matches("false")) {
            return;
        }

        // If we are anything but false we have to pull the logs
        for (String event : this.myJob.getLogs()) {
            // However, if we are doing this for vars only then we don't need to display the logs
            if (!importTowerLogs.matches("vars")) {
                logger.println(event);
            }
        }
    }

    public boolean cancelJob(PrintStream logger) {
        milestone(logger, "Attempting to cancel launched Tower job");
        try {
            this.myJob.cancelJob();
            return fail(logger, "Tower job was canceled after the Jenkins operation was interrupted");
        } catch (AnsibleTowerException ae) {
            return fail(logger, "Failed to cancel Tower job after interruption: " + ae.getMessage());
        }
    }

    public boolean cancelProjectSync(PrintStream logger, TowerProjectSync projectSync) {
        milestone(logger, "Attempting to cancel project sync");
        try {
            projectSync.cancelSync();
            return fail(logger, "Tower project sync was canceled after the Jenkins operation was interrupted");
        } catch (AnsibleTowerException ae) {
            return fail(logger, "Failed to cancel Tower project sync after interruption: " + ae.getMessage());
        }
    }

    public boolean projectSync(PrintStream logger, String towerServer, String towerCredentialsId, String projectName,
                               boolean verbose, boolean importTowerLogs, boolean removeColor, EnvVars envVars,
                               FilePath ws, Run<?, ?> run, Properties towerResults, boolean async) {

        milestone(logger, "Starting project sync: server=" + towerServer + ", project=" + projectName);
        // Get our Tower connector
        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if (towerConfigToRunOn == null) {
            return fail(logger, "Ansible Tower server " + towerServer + " does not exist in Jenkins configuration");
        }

        // Apply credential override if provided
        if (towerCredentialsId != null && !towerCredentialsId.equals("")) {
            towerConfigToRunOn.setTowerCredentialsId(towerCredentialsId);
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();
        myTowerConnection.setConsole(logger);

        myTowerConnection.setRemoveColor(removeColor);

        // Expand all of the parameters
        String expandedProject = envVars.expand(projectName);

        if (verbose) {
            if (expandedProject != null && !expandedProject.equals(projectName)) {
                logger.println("Expanded project to " + expandedProject);
            }
        }

        // Get the project
        milestone(logger, "Resolving project: project=" + expandedProject);
        TowerProject myProject = null;
        try {
            myProject = new TowerProject(expandedProject, myTowerConnection);
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Unable to lookup project; project sync was not launched: " + e.getMessage());
        }
        milestone(logger, "Project resolved: project=" + expandedProject);

        // Make sure we can update the project
        try {
            if (!myProject.canUpdate()) {
                myTowerConnection.releaseToken();
                return fail(logger, "The requested project cannot be synced; it may be a manual project");
            }
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Failed to check whether the project can be synced: " + e.getMessage());
        }

        if (verbose) {
            logger.println("Requesting tower to sync " + projectName + " template " + expandedProject);
        }

        // Request a project sync
        milestone(logger, "Requesting project sync: project=" + expandedProject);
        TowerProjectSync projectSync;
        try {
            projectSync = myProject.sync();
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Unable to confirm project sync launch: " + e.getMessage());
        }

        String syncURL = projectSync.getURL();
        milestone(logger, "Project sync accepted: syncId=" + projectSync.getID());
        logger.println("[Ansible-Tower] Project Sync URL: " + syncURL);
        towerResults.put("SYNC_ID", projectSync.getID());
        towerResults.put("SYNC_URL", syncURL);

        // If we are async, we can just return the project sync object
        if (async) {
            towerResults.put("sync", projectSync);
            myTowerConnection.releaseToken();
            return true;
        }

        // Otherwise we can monitor the project sync
        boolean syncCompleted = false;
        while (!syncCompleted) {
            if (Thread.currentThread().isInterrupted()) {
                myTowerConnection.releaseToken();
                return this.cancelProjectSync(logger, projectSync);
            }

            // First log any events if the user wants them
            if (importTowerLogs) {
                try {
                    for (String event : projectSync.getLogs()) {
                        logger.println(event);
                    }
                } catch (AnsibleTowerException e) {
                    myTowerConnection.releaseToken();
                    return fail(logger, "Failed to get project sync events from Tower: " + e.getMessage());
                }
            }
            try {
                syncCompleted = projectSync.isComplete();
            } catch (AnsibleTowerException e) {
                myTowerConnection.releaseToken();
                return fail(logger, "Failed to get project sync status from Tower: " + e.getMessage());
            }
            if (!syncCompleted) {
                if (Thread.currentThread().isInterrupted()) {
                    myTowerConnection.releaseToken();
                    return this.cancelProjectSync(logger, projectSync);
                } else {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        myTowerConnection.releaseToken();
                        return this.cancelProjectSync(logger, projectSync);
                    }
                }
            }
        }
        // One final log of events (if we want them)
        // Note, that a job can complete long before Tower has finished consuming the logs. This can cause incomplete
        //    logs within Jenkins.
        if (importTowerLogs) {
            try {
                for (String event : projectSync.getLogs()) {
                    logger.println(event);
                }
            } catch (AnsibleTowerException e) {
                myTowerConnection.releaseToken();
                return fail(logger, "Failed to get final project sync events from Tower: " + e.getMessage());
            }
        }

        boolean wasSuccessful;
        try {
            wasSuccessful = projectSync.wasSuccessful();
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Failed to get project sync completion status: " + e.getMessage());
        }
        towerResults.put("SYNC_RESULT", wasSuccessful ? "SUCCESS" : "FAILED");

        // Project sync can not export jenkins variables so we don't need to check for them here

        if (wasSuccessful) {
            milestone(logger, "Project sync completed: syncId=" + projectSync.getID() + ", result=SUCCESS");
        } else {
            fail(logger, "Project sync completed: syncId=" + projectSync.getID() + ", result=FAILED");
        }

        myTowerConnection.releaseToken();
        return wasSuccessful;
    }

    public boolean projectRevision(PrintStream logger,
                                   String towerServer, String towerCredentialsId,
                                   String projectName, String revision,
                                   boolean verbose,
                                   EnvVars envVars, FilePath ws, Run<?, ?> run, Properties towerResults) {

        milestone(logger, "Starting project revision: server=" + towerServer + ", project=" + projectName);
        // Get our Tower connector
        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if (towerConfigToRunOn == null) {
            return fail(logger, "Ansible Tower server " + towerServer + " does not exist in Jenkins configuration");
        }

        // Apply credential override if provided
        if (towerCredentialsId != null && !towerCredentialsId.equals("")) {
            towerConfigToRunOn.setTowerCredentialsId(towerCredentialsId);
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();
        myTowerConnection.setConsole(logger);

        // Expand all of the parameters
        String expandedProject = envVars.expand(projectName);
        String expandedRevision = envVars.expand(revision);

        if (verbose) {
            if (expandedProject != null && !expandedProject.equals(projectName)) {
                logger.println("Expanded project to " + expandedProject);
            }
            if (expandedRevision != null && !expandedRevision.equals(revision)) {
                logger.println("Expanded revision to " + expandedRevision);
            }
        }

        // Get the project (this will also validates the project exists)
        milestone(logger, "Resolving project: project=" + expandedProject);
        TowerProject myProject = null;
        try {
            myProject = new TowerProject(expandedProject, myTowerConnection);
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Unable to lookup project; revision was not updated: " + e.getMessage());
        }
        milestone(logger, "Project resolved: project=" + expandedProject);

        if (verbose) {
            logger.println("Requesting tower to update " + expandedProject + " revision to " + expandedRevision);
        }

        // Update project revision
        milestone(logger, "Updating project revision: project=" + expandedProject);
        try {
            boolean updated = myProject.updateRevision(expandedRevision);
            if(updated) { milestone(logger, "Project revision updated: project=" + expandedProject); }
            return updated;
        } catch (AnsibleTowerException e) {
            myTowerConnection.releaseToken();
            return fail(logger, "Unable to update project revision: " + e.getMessage());
        }
    }
}
