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
import org.jenkinsci.plugins.ansible_tower.util.*;
import org.jenkinsci.plugins.envinject.service.EnvInjectActionSetter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.PrintStream;
import java.util.*;

public class AnsibleTowerRunner {
    private TowerJob myJob = null;

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
    
    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    public boolean runJobTemplate(
            PrintStream logger, String towerServer, String towerCredentialsId, String jobTemplate, String jobType,
            String extraVars, String limit, String jobTags, String skipJobTags, String inventory, String credential, String scmBranch,
            boolean verbose, String importTowerLogs, boolean removeColor, EnvVars envVars, String templateType,
            boolean importWorkflowChildLogs, FilePath ws, Run<?, ?> run, Properties towerResults, boolean async
    ) {
        if (verbose) {
            logger.println("Beginning Ansible Tower Run on " + towerServer);
        }

        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if (towerConfigToRunOn == null) {
            logger.println("ERROR: Ansible tower server " + towerServer + " does not exist in Ansible Tower configuration");
            return false;
        }

        if(towerCredentialsId != null && !towerCredentialsId.equals("")) {
            towerConfigToRunOn.setTowerCredentialsId(towerCredentialsId);
        }

        if(run != null) {
            towerConfigToRunOn.setRun(run);
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();
        this.myJob = new TowerJob(myTowerConnection);
        try {
            this.myJob.setTemplateType(templateType);
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: "+ e);
            return false;
        }

        // Check the import logs settings
        if (!(importTowerLogs.matches("false") || importTowerLogs.matches("true") || importTowerLogs.matches("vars") || importTowerLogs.matches("full"))) {
            logger.println("ERROR: Import Tower Logs must be one of (false, true, vars or full)");
            return false;
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
                logger.println("Expanded extra vars to " + expandedExtraVars);
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
                logger.println("Expanded credentials to " + expandedCredential);
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
            if(!expandedSkipJobTags.startsWith(",")) {
                expandedSkipJobTags = "," + expandedSkipJobTags;
            }
        }

        // Get the job template.
        JSONObject template;
        try {
            template = myTowerConnection.getJobTemplate(expandedJobTemplate, templateType);
        } catch (AnsibleTowerException e) {
            logger.println("ERROR: Unable to lookup job template " + e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }


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
            if(template.containsKey("ask_scm_branch_on_launch")) {
                if(!template.getBoolean("ask_scm_branch_on_launch")) {
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

        try {
            this.myJob.setJobId(myTowerConnection.submitTemplate(template.getLong("id"), expandedExtraVars, expandedLimit, expandedJobTags, expandedSkipJobTags, jobType, expandedInventory, expandedCredential, expandedScmBranch, templateType));
        } catch (AnsibleTowerException e) {
            logger.println("ERROR: Unable to request job template invocation " + e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }

        String jobURL = myTowerConnection.getJobURL(this.myJob.getJobID(), templateType);
        logger.println("Template Job URL: " + jobURL);

        towerResults.put("JOB_ID", Long.toString(this.myJob.getJobID()));
        towerResults.put("JOB_URL", jobURL);

        if (async) {
            towerResults.put("job", this.myJob);
            myTowerConnection.releaseToken();
            return true;
        }

        boolean jobCompleted = false;
        // Assume the old logging behaviour (truncated logs) but we we are doing full logging or var logging then swtich to true
        if (importTowerLogs.matches("full") || importTowerLogs.matches("vars")) { myTowerConnection.setGetFullLogs(true); }
        while (!jobCompleted) {
            if(Thread.currentThread().isInterrupted()) {
                myTowerConnection.releaseToken();
                return this.cancelJob(logger);
            }

            // First log any events if the user wants them
            try {
                this.getJobLogs(importTowerLogs, logger);
            } catch (AnsibleTowerException e) {
                logger.println("ERROR: Failed to get job events from tower: " + e.getMessage());
                myTowerConnection.releaseToken();
                return false;
            }

           try {
                jobCompleted = this.myJob.isComplete();
            } catch (AnsibleTowerException e) {
                logger.println("ERROR: Failed to get job status from Tower: " + e.getMessage());
                myTowerConnection.releaseToken();
                return false;
            }
            if (!jobCompleted) {
                if(Thread.currentThread().isInterrupted()) {
                    myTowerConnection.releaseToken();
                    return this.cancelJob(logger);
                } else {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        myTowerConnection.releaseToken();
                        return this.cancelJob(logger);
                    }
                }
            }
        }
        // One final log of events (if we want them)
        // Note, that a job can complete long before Tower has finished consuming the logs. This can cause incomplete
        //    logs within Jenkins.
        try {
            this.getJobLogs(importTowerLogs, logger);
        } catch (AnsibleTowerException e) {
            logger.println("ERROR: Failed to get final job events from tower: " + e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }

        boolean wasSuccessful;
        try {
            wasSuccessful = this.myJob.wasSuccessful();
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Failed to get job compltion status: "+ e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }

        HashMap<String, String> jenkinsVariables;
        try {
            jenkinsVariables = this.myJob.getExports();
        } catch(AnsibleTowerException e) {
            logger.println("Failed to get exported variables: "+ e);
            myTowerConnection.releaseToken();
            return false;
        }
        for (Map.Entry<String, String> entrySet : jenkinsVariables.entrySet()) {
            if (verbose) {
                logger.println("Receiving from Jenkins job '" + entrySet.getKey() + "' with value '" + entrySet.getValue() + "'");
            }
            envVars.put(entrySet.getKey(), entrySet.getValue());
            towerResults.put(entrySet.getKey(), entrySet.getValue());
        }
        if (envVars.size() != 0) {
            Plugin envInjectPlugin = null;
            try {
                envInjectPlugin = Objects.requireNonNull(Jenkins.getInstance()).getPlugin("envinject");
            } catch(NullPointerException e) {
                // We don't care if we get a NPE here
            }

            if(envInjectPlugin != null) {
                EnvInjectActionSetter envInjectActionSetter = new EnvInjectActionSetter(ws);
                try {
                    envInjectActionSetter.addEnvVarsToRun(run, envVars);
                } catch (Exception e) {
                    logger.println("Unable to inject environment variables: " + e.getMessage());
                    myTowerConnection.releaseToken();
                    return false;
                }
            }
        }

        if(wasSuccessful) {
            logger.println("Tower completed the requested job");
        } else {
            logger.println("Tower failed to complete the requested job");
        }

        towerResults.put("JOB_RESULT", wasSuccessful ? "SUCCESS" : "FAILED");

        myTowerConnection.releaseToken();
        return wasSuccessful;
    }

    public void getJobLogs(String importTowerLogs, PrintStream logger) throws AnsibleTowerException {
        if (importTowerLogs.matches("false")) { return; }

        // If we are anything but false we have to pull the logs
        for (String event : this.myJob.getLogs()) {
            // However, if we are doing this for vars only then we don't need to display the logs
            if (! importTowerLogs.matches("vars")) {
                logger.println(event);
            }
        }
    }

    public boolean cancelJob(PrintStream logger) {
        logger.println("Attempting to cancel launched Tower job");
        try {
            this.myJob.cancelJob();
            logger.println("Job successfully canceled in Tower");
        } catch(AnsibleTowerException ae) {
            logger.println("Failed to cancel tower job: "+ ae);
        }
        return false;
    }

    public boolean cancelProjectSync(PrintStream logger, TowerProjectSync projectSync) {
        logger.println("Attempting to cancel project sync");
        try {
            projectSync.cancelSync();
            logger.println("Project sync successfullt canceled in Tower");
        } catch(AnsibleTowerException ae) {
            logger.println("Failed to cancel tower project sync: "+ ae);
        }
        return false;
    }

    public boolean projectSync(PrintStream logger, String towerServer, String towerCredentialsId, String projectName,
                               boolean verbose, boolean importTowerLogs, boolean removeColor, EnvVars envVars,
                               FilePath ws, Run<?, ?> run, Properties towerResults, boolean async) {

        if (verbose) {
            logger.println("Beginning Ansible Tower Project Sync on " + towerServer +" for "+ projectName);
        }

        // Get our Tower connector
        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if (towerConfigToRunOn == null) {
            logger.println("ERROR: Ansible tower server " + towerServer + " does not exist in Ansible Tower configuration");
            return false;
        }

        // Apply credential override if provided
        if(towerCredentialsId != null && !towerCredentialsId.equals("")) {
            towerConfigToRunOn.setTowerCredentialsId(towerCredentialsId);
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();

        myTowerConnection.setRemoveColor(removeColor);

        // Expand all of the parameters
        String expandedProject = envVars.expand(projectName);

        if (verbose) {
            if (expandedProject != null && !expandedProject.equals(projectName)) {
                logger.println("Expanded project to " + expandedProject);
            }
        }

        // Get the project
        TowerProject myProject = null;
        try {
            myProject = new TowerProject(expandedProject, myTowerConnection);
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Unable to lookup project: " + e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }

        // Make sure we can update the project
        try {
            if (!myProject.canUpdate()) {
                logger.println("ERROR: The requested project can not be synced, is it a manual project?");
                myTowerConnection.releaseToken();
                return false;
            }
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Failed to check if the project can be synced: "+ e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }

        if (verbose) {
            logger.println("Requesting tower to sync " + projectName + " template " + expandedProject);
        }

        // Request a project sync
        TowerProjectSync projectSync;
        try {
            projectSync = myProject.sync();
        } catch (AnsibleTowerException e) {
            logger.println("ERROR: Unable to request project sync invocation " + e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }

        String syncURL = projectSync.getURL();
        logger.println("Project Sync URL: "+ syncURL);
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
            if(Thread.currentThread().isInterrupted()) {
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
                    logger.println("ERROR: Failed to get project sync events from tower: " + e.getMessage());
                    myTowerConnection.releaseToken();
                    return false;
                }
            }
            try {
                syncCompleted = projectSync.isComplete();
            } catch (AnsibleTowerException e) {
                logger.println("ERROR: Failed to get project sync status from Tower: " + e.getMessage());
                myTowerConnection.releaseToken();
                return false;
            }
            if (!syncCompleted) {
                if(Thread.currentThread().isInterrupted()) {
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
        if(importTowerLogs) {
            try {
                for (String event : projectSync.getLogs()) {
                    logger.println(event);
                }
            } catch (AnsibleTowerException e) {
                logger.println("ERROR: Failed to get final project sync events from tower: " + e.getMessage());
                myTowerConnection.releaseToken();
                return false;
            }
        }

        boolean wasSuccessful;
        try {
            wasSuccessful = projectSync.wasSuccessful();
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Failed to get project sync compltion status: "+ e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }
        towerResults.put("SYNC_RESULT", wasSuccessful ? "SUCCESS" : "FAILED");

        // Project sync can not export jenkins variables so we don't need to check for them here

        if(wasSuccessful) {
            logger.println("Tower completed the requested project sync");
        } else {
            logger.println("Tower failed to complete the requested project sync");
        }

        myTowerConnection.releaseToken();
        return wasSuccessful;
    }

    public boolean projectRevision(PrintStream logger,
                                   String towerServer, String towerCredentialsId,
                                   String projectName, String revision,
                                   boolean verbose,
                                   EnvVars envVars, FilePath ws, Run<?, ?> run, Properties towerResults) {

        if (verbose) {
            logger.println("Beginning Ansible Tower Project Revision on " + towerServer +" for "+ projectName);
        }

        // Get our Tower connector
        AnsibleTowerGlobalConfig myConfig = new AnsibleTowerGlobalConfig();
        TowerInstallation towerConfigToRunOn = myConfig.getTowerInstallationByName(towerServer);
        if (towerConfigToRunOn == null) {
            logger.println("ERROR: Ansible tower server " + towerServer + " does not exist in Ansible Tower configuration");
            return false;
        }

        // Apply credential override if provided
        if(towerCredentialsId != null && !towerCredentialsId.equals("")) {
            towerConfigToRunOn.setTowerCredentialsId(towerCredentialsId);
        }

        TowerConnector myTowerConnection = towerConfigToRunOn.getTowerConnector();

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
        TowerProject myProject = null;
        try {
            myProject = new TowerProject(expandedProject, myTowerConnection);
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Unable to lookup project: " + e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }

        if (verbose) {
            logger.println("Requesting tower to update " + expandedProject + " revision to " + expandedRevision);
        }

        // Update project revision
        try {
            return myProject.updateRevision(expandedRevision);
        } catch(AnsibleTowerException e) {
            logger.println("ERROR: Unable to update project revision "+ e.getMessage());
            myTowerConnection.releaseToken();
            return false;
        }
    }
}