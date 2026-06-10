package org.jenkinsci.plugins.ansible_aap;

/*
    This class is a bridge between the Jenkins workflow/plugin step and AAPConnector.
    The intention is to abstract the "work" from the two Jenkins classes
 */

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Plugin;
import hudson.model.Run;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.ansible_aap.exceptions.AnsibleAAPException;
import org.jenkinsci.plugins.ansible_aap.util.*;
import org.jenkinsci.plugins.envinject.service.EnvInjectActionSetter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.PrintStream;
import java.util.*;

public class AnsibleAAPRunner {
    private AAPJob myJob = null;

    public boolean runJobTemplate(
            PrintStream logger, String aapServer, String aapCredentialsId, String jobTemplate, String jobType,
            String extraVars, String limit, String jobTags, String skipJobTags, String inventory, String credential, String scmBranch,
            boolean verbose, String importAAPLogs, boolean removeColor, EnvVars envVars, String templateType,
            boolean importWorkflowChildLogs, FilePath ws, Run<?, ?> run, Properties aapResults
    ) {
        return this.runJobTemplate(logger, aapServer, aapCredentialsId, jobTemplate, jobType, extraVars, limit,
                jobTags, skipJobTags, inventory, credential, scmBranch, verbose, importAAPLogs, removeColor, envVars,
                templateType, importWorkflowChildLogs, ws, run, aapResults, false);
    }
    
    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    public boolean runJobTemplate(
            PrintStream logger, String aapServer, String aapCredentialsId, String jobTemplate, String jobType,
            String extraVars, String limit, String jobTags, String skipJobTags, String inventory, String credential, String scmBranch,
            boolean verbose, String importAAPLogs, boolean removeColor, EnvVars envVars, String templateType,
            boolean importWorkflowChildLogs, FilePath ws, Run<?, ?> run, Properties aapResults, boolean async
    ) {
        if (verbose) {
            logger.println("Beginning Ansible Automation Platform Run on " + aapServer);
        }

        AnsibleAAPGlobalConfig myConfig = new AnsibleAAPGlobalConfig();
        AAPInstallation aapConfigToRunOn = myConfig.getAapInstallationByName(aapServer);
        if (aapConfigToRunOn == null) {
            logger.println("ERROR: Ansible aap server " + aapServer + " does not exist in Ansible Automation Platform configuration");
            return false;
        }

        if (aapCredentialsId != null && !aapCredentialsId.equals("")) {
            aapConfigToRunOn.setAapCredentialsId(aapCredentialsId);
        }

        if (run != null) {
            aapConfigToRunOn.setRun(run);
        }

        AAPConnector myAAPConnection = aapConfigToRunOn.getAapConnector();
        this.myJob = new AAPJob(myAAPConnection);
        try {
            this.myJob.setTemplateType(templateType);
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: " + e);
            return false;
        }

        // Check the import logs settings
        if (!(importAAPLogs.matches("false") || importAAPLogs.matches("true") || importAAPLogs.matches("vars") || importAAPLogs.matches("full"))) {
            logger.println("ERROR: Import AAP Logs must be one of (false, true, vars or full)");
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
            if (!expandedSkipJobTags.startsWith(",")) {
                expandedSkipJobTags = "," + expandedSkipJobTags;
            }
        }

        // Get the job template.
        JSONObject template;
        try {
            template = myAAPConnection.getJobTemplate(expandedJobTemplate, templateType);
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Unable to lookup job template " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }


        if (jobType != null && template.containsKey("ask_job_type_on_launch") && !template.getBoolean("ask_job_type_on_launch")) {
            logger.println("[WARNING]: Job type defined but prompt for job type on launch is not set in aap job");
        }
        if (expandedExtraVars != null && template.containsKey("ask_variables_on_launch") && !template.getBoolean("ask_variables_on_launch")) {
            logger.println("[WARNING]: Extra variables defined but prompt for variables on launch is not set in aap job");
        }
        if (expandedLimit != null && template.containsKey("ask_limit_on_launch") && !template.getBoolean("ask_limit_on_launch")) {
            logger.println("[WARNING]: Limit defined but prompt for limit on launch is not set in aap job");
        }
        if (expandedJobTags != null && template.containsKey("ask_tags_on_launch") && !template.getBoolean("ask_tags_on_launch")) {
            logger.println("[WARNING]: Job Tags defined but prompt for tags on launch is not set in aap job");
        }
        if (expandedSkipJobTags != null && template.containsKey("ask_skip_tags_on_launch") && !template.getBoolean("ask_skip_tags_on_launch")) {
            logger.println("[WARNING]: Skip Job Tags defined but prompt for tags on launch is not set in aap job");
        }
        if (expandedInventory != null && template.containsKey("ask_inventory_on_launch") && !template.getBoolean("ask_inventory_on_launch")) {
            logger.println("[WARNING]: Inventory defined but prompt for inventory on launch is not set in aap job");
        }
        if (expandedCredential != null && template.containsKey("ask_credential_on_launch") && !template.getBoolean("ask_credential_on_launch")) {
            logger.println("[WARNING]: Credential defined but prompt for credential on launch is not set in aap job");
        }
        if (expandedScmBranch != null) {
            if (template.containsKey("ask_scm_branch_on_launch")) {
                if (!template.getBoolean("ask_scm_branch_on_launch")) {
                    logger.println("[WARNING]: SCM Branch defined but pompt for SCM back on launch is not set in aap job");
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

        myAAPConnection.setRemoveColor(removeColor);
        myAAPConnection.setGetWorkflowChildLogs(importWorkflowChildLogs);


        if (verbose) {
            logger.println("Requesting aap to run " + templateType + " template " + expandedJobTemplate);
        }

        try {
            this.myJob.setJobId(myAAPConnection.submitTemplate(template.getLong("id"), expandedExtraVars, expandedLimit, expandedJobTags, expandedSkipJobTags, jobType, expandedInventory, expandedCredential, expandedScmBranch, templateType));
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Unable to request job template invocation " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }
        try {
            myAAPConnection.detectJobUIURLMode(this.myJob.getJobID(), templateType);
        } catch (AnsibleAAPException e) {
            if (verbose) {
                logger.println("[WARNING]: Unable to detect Ansible Automation Platform UI URL mode, using legacy URL format: " + e.getMessage());
            }
        }

        String jobURL = myAAPConnection.getJobURL(this.myJob.getJobID(), templateType);
        if (myAAPConnection.isAapControllerUIURL(this.myJob.getJobID(), templateType) && !myAAPConnection.hasDisplayURL()) {
            logger.println("[WARNING]: AAP Controller UI was detected but Display URL is not configured; using AAP URL as the UI base");
        }
        logger.println("Template Job URL: " + jobURL);

        aapResults.put("JOB_ID", Long.toString(this.myJob.getJobID()));
        aapResults.put("JOB_URL", jobURL);

        if (async) {
            aapResults.put("job", this.myJob);
            myAAPConnection.releaseToken();
            return true;
        }

        boolean jobCompleted = false;
        // Assume the old logging behaviour (truncated logs) but we we are doing full logging or var logging then swtich to true
        if (importAAPLogs.matches("full") || importAAPLogs.matches("vars")) {
            myAAPConnection.setGetFullLogs(true);
        }
        while (!jobCompleted) {
            if (Thread.currentThread().isInterrupted()) {
                myAAPConnection.releaseToken();
                return this.cancelJob(logger);
            }

            // First log any events if the user wants them
            try {
                this.getJobLogs(importAAPLogs, logger);
            } catch (AnsibleAAPException e) {
                logger.println("ERROR: Failed to get job events from aap: " + e.getMessage());
                myAAPConnection.releaseToken();
                return false;
            }

            try {
                jobCompleted = this.myJob.isComplete();
            } catch (AnsibleAAPException e) {
                logger.println("ERROR: Failed to get job status from AAP: " + e.getMessage());
                myAAPConnection.releaseToken();
                return false;
            }
            if (!jobCompleted) {
                if (Thread.currentThread().isInterrupted()) {
                    myAAPConnection.releaseToken();
                    return this.cancelJob(logger);
                } else {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        myAAPConnection.releaseToken();
                        return this.cancelJob(logger);
                    }
                }
            }
        }
        // One final log of events (if we want them)
        // Note, that a job can complete long before AAP has finished consuming the logs. This can cause incomplete
        //    logs within Jenkins.
        try {
            this.getJobLogs(importAAPLogs, logger);
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Failed to get final job events from aap: " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }

        boolean wasSuccessful;
        try {
            wasSuccessful = this.myJob.wasSuccessful();
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Failed to get job compltion status: " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }

        HashMap<String, String> jenkinsVariables;
        try {
            jenkinsVariables = this.myJob.getExports();
        } catch (AnsibleAAPException e) {
            logger.println("Failed to get exported variables: " + e);
            myAAPConnection.releaseToken();
            return false;
        }
        for (Map.Entry<String, String> entrySet : jenkinsVariables.entrySet()) {
            if (verbose) {
                logger.println("Receiving from Jenkins job '" + entrySet.getKey() + "' with value '" + entrySet.getValue() + "'");
            }
            envVars.put(entrySet.getKey(), entrySet.getValue());
            aapResults.put(entrySet.getKey(), entrySet.getValue());
        }
        if (envVars.size() != 0) {
            Plugin envInjectPlugin = Jenkins.getInstance() != null ? Jenkins.getInstance().getPlugin("envinject") : null;
            if (envInjectPlugin != null) {
                EnvInjectActionSetter envInjectActionSetter = new EnvInjectActionSetter(ws);
                try {
                    envInjectActionSetter.addEnvVarsToRun(run, envVars);
                } catch (Exception e) {
                    logger.println("Unable to inject environment variables: " + e.getMessage());
                    myAAPConnection.releaseToken();
                    return false;
                }
            }

            if (envInjectPlugin != null) {
                EnvInjectActionSetter envInjectActionSetter = new EnvInjectActionSetter(ws);
                try {
                    envInjectActionSetter.addEnvVarsToRun(run, envVars);
                } catch (Exception e) {
                    logger.println("Unable to inject environment variables: " + e.getMessage());
                    myAAPConnection.releaseToken();
                    return false;
                }
            }
        }

        if (wasSuccessful) {
            logger.println("AAP completed the requested job");
        } else {
            logger.println("AAP failed to complete the requested job");
        }

        aapResults.put("JOB_RESULT", wasSuccessful ? "SUCCESS" : "FAILED");

        myAAPConnection.releaseToken();
        return wasSuccessful;
    }

    public void getJobLogs(String importAAPLogs, PrintStream logger) throws AnsibleAAPException {
        if (importAAPLogs.matches("false")) {
            return;
        }

        // If we are anything but false we have to pull the logs
        for (String event : this.myJob.getLogs()) {
            // However, if we are doing this for vars only then we don't need to display the logs
            if (!importAAPLogs.matches("vars")) {
                logger.println(event);
            }
        }
    }

    public boolean cancelJob(PrintStream logger) {
        logger.println("Attempting to cancel launched AAP job");
        try {
            this.myJob.cancelJob();
            logger.println("Job successfully canceled in AAP");
        } catch (AnsibleAAPException ae) {
            logger.println("Failed to cancel aap job: " + ae);
        }
        return false;
    }

    public boolean cancelProjectSync(PrintStream logger, AAPProjectSync projectSync) {
        logger.println("Attempting to cancel project sync");
        try {
            projectSync.cancelSync();
            logger.println("Project sync successfullt canceled in AAP");
        } catch (AnsibleAAPException ae) {
            logger.println("Failed to cancel aap project sync: " + ae);
        }
        return false;
    }

    public boolean projectSync(PrintStream logger, String aapServer, String aapCredentialsId, String projectName,
                               boolean verbose, boolean importAAPLogs, boolean removeColor, EnvVars envVars,
                               FilePath ws, Run<?, ?> run, Properties aapResults, boolean async) {

        if (verbose) {
            logger.println("Beginning Ansible Automation Platform Project Sync on " + aapServer + " for " + projectName);
        }

        // Get our AAP connector
        AnsibleAAPGlobalConfig myConfig = new AnsibleAAPGlobalConfig();
        AAPInstallation aapConfigToRunOn = myConfig.getAapInstallationByName(aapServer);
        if (aapConfigToRunOn == null) {
            logger.println("ERROR: Ansible aap server " + aapServer + " does not exist in Ansible Automation Platform configuration");
            return false;
        }

        // Apply credential override if provided
        if (aapCredentialsId != null && !aapCredentialsId.equals("")) {
            aapConfigToRunOn.setAapCredentialsId(aapCredentialsId);
        }

        AAPConnector myAAPConnection = aapConfigToRunOn.getAapConnector();

        myAAPConnection.setRemoveColor(removeColor);

        // Expand all of the parameters
        String expandedProject = envVars.expand(projectName);

        if (verbose) {
            if (expandedProject != null && !expandedProject.equals(projectName)) {
                logger.println("Expanded project to " + expandedProject);
            }
        }

        // Get the project
        AAPProject myProject = null;
        try {
            myProject = new AAPProject(expandedProject, myAAPConnection);
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Unable to lookup project: " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }

        // Make sure we can update the project
        try {
            if (!myProject.canUpdate()) {
                logger.println("ERROR: The requested project can not be synced, is it a manual project?");
                myAAPConnection.releaseToken();
                return false;
            }
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Failed to check if the project can be synced: " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }

        if (verbose) {
            logger.println("Requesting aap to sync " + projectName + " template " + expandedProject);
        }

        // Request a project sync
        AAPProjectSync projectSync;
        try {
            projectSync = myProject.sync();
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Unable to request project sync invocation " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }

        String syncURL = projectSync.getURL();
        if (projectSync.usesAAPControllerUI() && !myAAPConnection.hasDisplayURL()) {
            logger.println("[WARNING]: AAP Controller UI was detected but Display URL is not configured; using AAP URL as the UI base");
        }
        logger.println("Project Sync URL: " + syncURL);
        aapResults.put("SYNC_ID", projectSync.getID());
        aapResults.put("SYNC_URL", syncURL);

        // If we are async, we can just return the project sync object
        if (async) {
            aapResults.put("sync", projectSync);
            myAAPConnection.releaseToken();
            return true;
        }

        // Otherwise we can monitor the project sync
        boolean syncCompleted = false;
        while (!syncCompleted) {
            if (Thread.currentThread().isInterrupted()) {
                myAAPConnection.releaseToken();
                return this.cancelProjectSync(logger, projectSync);
            }

            // First log any events if the user wants them
            if (importAAPLogs) {
                try {
                    for (String event : projectSync.getLogs()) {
                        logger.println(event);
                    }
                } catch (AnsibleAAPException e) {
                    logger.println("ERROR: Failed to get project sync events from aap: " + e.getMessage());
                    myAAPConnection.releaseToken();
                    return false;
                }
            }
            try {
                syncCompleted = projectSync.isComplete();
            } catch (AnsibleAAPException e) {
                logger.println("ERROR: Failed to get project sync status from AAP: " + e.getMessage());
                myAAPConnection.releaseToken();
                return false;
            }
            if (!syncCompleted) {
                if (Thread.currentThread().isInterrupted()) {
                    myAAPConnection.releaseToken();
                    return this.cancelProjectSync(logger, projectSync);
                } else {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        myAAPConnection.releaseToken();
                        return this.cancelProjectSync(logger, projectSync);
                    }
                }
            }
        }
        // One final log of events (if we want them)
        // Note, that a job can complete long before AAP has finished consuming the logs. This can cause incomplete
        //    logs within Jenkins.
        if (importAAPLogs) {
            try {
                for (String event : projectSync.getLogs()) {
                    logger.println(event);
                }
            } catch (AnsibleAAPException e) {
                logger.println("ERROR: Failed to get final project sync events from aap: " + e.getMessage());
                myAAPConnection.releaseToken();
                return false;
            }
        }

        boolean wasSuccessful;
        try {
            wasSuccessful = projectSync.wasSuccessful();
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Failed to get project sync compltion status: " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }
        aapResults.put("SYNC_RESULT", wasSuccessful ? "SUCCESS" : "FAILED");

        // Project sync can not export jenkins variables so we don't need to check for them here

        if (wasSuccessful) {
            logger.println("AAP completed the requested project sync");
        } else {
            logger.println("AAP failed to complete the requested project sync");
        }

        myAAPConnection.releaseToken();
        return wasSuccessful;
    }

    public boolean projectRevision(PrintStream logger,
                                   String aapServer, String aapCredentialsId,
                                   String projectName, String revision,
                                   boolean verbose,
                                   EnvVars envVars, FilePath ws, Run<?, ?> run, Properties aapResults) {

        if (verbose) {
            logger.println("Beginning Ansible Automation Platform Project Revision on " + aapServer + " for " + projectName);
        }

        // Get our AAP connector
        AnsibleAAPGlobalConfig myConfig = new AnsibleAAPGlobalConfig();
        AAPInstallation aapConfigToRunOn = myConfig.getAapInstallationByName(aapServer);
        if (aapConfigToRunOn == null) {
            logger.println("ERROR: Ansible aap server " + aapServer + " does not exist in Ansible Automation Platform configuration");
            return false;
        }

        // Apply credential override if provided
        if (aapCredentialsId != null && !aapCredentialsId.equals("")) {
            aapConfigToRunOn.setAapCredentialsId(aapCredentialsId);
        }

        AAPConnector myAAPConnection = aapConfigToRunOn.getAapConnector();

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
        AAPProject myProject = null;
        try {
            myProject = new AAPProject(expandedProject, myAAPConnection);
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Unable to lookup project: " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }

        if (verbose) {
            logger.println("Requesting aap to update " + expandedProject + " revision to " + expandedRevision);
        }

        // Update project revision
        try {
            return myProject.updateRevision(expandedRevision);
        } catch (AnsibleAAPException e) {
            logger.println("ERROR: Unable to update project revision " + e.getMessage());
            myAAPConnection.releaseToken();
            return false;
        }
    }
}
