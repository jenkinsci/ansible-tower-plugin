package org.jenkinsci.plugins.ansible_aap;

/*
    This class is the pipeline step
    We simply take the data from Jenkins and call an AnsibleAAPRunner
 */

import com.google.inject.Inject;
import hudson.Launcher;
import hudson.model.*;
import hudson.*;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.ansible_aap.util.GetUserPageCredentials;
import org.jenkinsci.plugins.ansible_aap.util.AAPInstallation;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.util.Properties;

public class AnsibleAAPStep extends AbstractStepImpl {
    private String aapServer              = "";
    private String aapCredentialsId       = "";
    private String jobTemplate              = "";
    private String jobType                  = "run";
    private String extraVars                = "";
    private String limit                    = "";
    private String jobTags                  = "";
    private String skipJobTags              = "";
    private String inventory                = "";
    private String credential               = "";
    private String scmBranch                = "";
    private Boolean verbose                 = false;
    private Boolean importAAPLogs         = null;
    private String aapLogLevel            = null;
    private Boolean removeColor             = false;
    private String templateType             = "job";
    private Boolean importWorkflowChildLogs = false;
    private Boolean throwExceptionWhenFail  = true;
    private Boolean async                   = false;

    /* This dericated function will remain here in order for any thing calling our plugin to remain functional */
    @Deprecated
    public AnsibleAAPStep(
            @Nonnull String aapServer, @Nonnull String aapCredentialsId, @Nonnull String jobTemplate, String jobType, String extraVars, String jobTags,
            String skipJobTags, String limit, String inventory, String credential, String scmBranch, Boolean verbose,
            Boolean importAAPLogs, Boolean removeColor, String templateType, Boolean importWorkflowChildLogs,
            Boolean throwExceptionWhenFail, Boolean async
    ) {
        this.aapServer = aapServer;
        this.aapCredentialsId = aapCredentialsId;
        this.jobTemplate = jobTemplate;
        this.extraVars = extraVars;
        this.jobTags = jobTags;
        this.skipJobTags = skipJobTags;
        this.jobType = jobType;
        this.limit = limit;
        this.inventory = inventory;
        this.credential = credential;
        this.scmBranch = scmBranch;
        this.verbose = verbose;
        this.aapLogLevel = importAAPLogs.toString();
        this.importAAPLogs = importAAPLogs;
        this.removeColor = removeColor;
        this.templateType = templateType;
        this.importWorkflowChildLogs = importWorkflowChildLogs;
        this.throwExceptionWhenFail = throwExceptionWhenFail;
        this.async = async;
    }


    /** @since 0.16.0 */
    @DataBoundConstructor
    public AnsibleAAPStep(
            @Nonnull String aapServer, @Nonnull String aapCredentialsId, @Nonnull String jobTemplate, String jobType
    ) {
        this.aapServer = aapServer;
        this.aapCredentialsId = aapCredentialsId;
        this.jobTemplate = jobTemplate;
        this.jobType = jobType;
    }

    @Nonnull
    public String getAapServer()              { return aapServer; }
    @Nonnull
    public String getJobTemplate()              { return jobTemplate; }
    public String getAapCredentialsId()       { return aapCredentialsId; }
    public String getExtraVars()                { return extraVars; }
    public String getJobTags()                  { return jobTags; }
    public String getSkipJobTags()              { return skipJobTags; }
    public String getJobType()                  { return jobType;}
    public String getLimit()                    { return limit; }
    public String getInventory()                { return inventory; }
    public String getCredential()               { return credential; }
    public String getScmBranch()                { return scmBranch; }
    public Boolean getVerbose()                 { return verbose; }
    public Boolean getImportAAPLogs()         { return importAAPLogs; }
    public String getAapLogLevel()            { return aapLogLevel; }
    public Boolean getRemoveColor()             { return removeColor; }
    public String getTemplateType()             { return templateType; }
    public Boolean getImportWorkflowChildLogs() { return importWorkflowChildLogs; }
    public Boolean getThrowExceptionWhenFail()  { return throwExceptionWhenFail; }
    public Boolean getAsync()                   { return async; }

    @DataBoundSetter
    public void setAapServer(String aapServer) { this.aapServer = aapServer; }
    @DataBoundSetter
    public void setJobTemplate(String jobTemplate) { this.jobTemplate = jobTemplate; }
    @DataBoundSetter
    public void setAapCredentialsId(String aapCredentialsId) { this.aapCredentialsId = aapCredentialsId; }
     @DataBoundSetter
    public void setExtraVars(String extraVars) { this.extraVars = extraVars; }
    @DataBoundSetter
    public void setJobTags(String jobTags) { this.jobTags = jobTags; }
    @DataBoundSetter
    public void setSkipJobTags(String skipJobTags) { this.skipJobTags = skipJobTags; }
    @DataBoundSetter
    public void setJobType(String jobType) { this.jobType = jobType; }
    @DataBoundSetter
    public void setLimit(String limit) { this.limit = limit; }
    @DataBoundSetter
    public void setInventory(String inventory) { this.inventory = inventory; }
    @DataBoundSetter
    public void setCredential(String credential) { this.credential = credential; }
    @DataBoundSetter
	public void setScmBranch(String scmBranch) { this.scmBranch = scmBranch; }
	@DataBoundSetter
    public void setVerbose(Boolean verbose) { this.verbose = verbose; }
    @DataBoundSetter
    public void setImportAAPLogs(Boolean importAAPLogs) {
        this.importAAPLogs = importAAPLogs;
        this.aapLogLevel = importAAPLogs.toString();
    }
    @DataBoundSetter
    public void setAapLogLevel(String importAAPLogs) { this.aapLogLevel = importAAPLogs; }
    @DataBoundSetter
    public void setRemoveColor(Boolean removeColor) { this.removeColor = removeColor; }
    @DataBoundSetter
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    @DataBoundSetter
    public void setImportWorkflowChildLogs(Boolean importWorkflowChildLogs) { this.importWorkflowChildLogs = importWorkflowChildLogs; }
    @DataBoundSetter
    public void setThrowExceptionWhenFail(Boolean throwExceptionWhenFail) { this.throwExceptionWhenFail = throwExceptionWhenFail; }
    @DataBoundSetter
    public void setAsync(Boolean async) { this.async = async; }

    public boolean isGlobalColorAllowed() {
        return true;
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public static final String aapServer              = AnsibleAAP.DescriptorImpl.aapServer;
        public static final String aapCredentialsId       = AnsibleAAP.DescriptorImpl.aapCredentialsId;
        public static final String jobTemplate              = AnsibleAAP.DescriptorImpl.jobTemplate;
        public static final String jobType                  = AnsibleAAP.DescriptorImpl.jobType;
        public static final String extraVars                = AnsibleAAP.DescriptorImpl.extraVars;
        public static final String limit                    = AnsibleAAP.DescriptorImpl.limit;
        public static final String jobTags                  = AnsibleAAP.DescriptorImpl.jobTags;
        public static final String skipJobTags              = AnsibleAAP.DescriptorImpl.skipJobTags;
        public static final String inventory                = AnsibleAAP.DescriptorImpl.inventory;
        public static final String credential               = AnsibleAAP.DescriptorImpl.credential;
        public static final String scmBranch                = AnsibleAAP.DescriptorImpl.scmBranch;
        public static final Boolean verbose                 = AnsibleAAP.DescriptorImpl.verbose;
        public static final String aapLogLevel            = AnsibleAAP.DescriptorImpl.importAAPLogs;
        public static final Boolean removeColor             = AnsibleAAP.DescriptorImpl.removeColor;
        public static final String templateType             = AnsibleAAP.DescriptorImpl.templateType;
        public static final Boolean importWorkflowChildLogs = AnsibleAAP.DescriptorImpl.importWorkflowChildLogs;
        public static final Boolean throwExceptionWhenFail  = AnsibleAAP.DescriptorImpl.throwExceptionWhenFail;
        public static final Boolean async                   = AnsibleAAP.DescriptorImpl.async;

        public DescriptorImpl() {
            super(AnsibleAAPStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "ansibleAAP";
        }

        @Override
        public String getDisplayName() {
            return "Have Ansible Automation Platform run a job template";
        }

        public ListBoxModel doFillAapServerItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(" - None -");
            for (AAPInstallation aapServer : AnsibleAAPGlobalConfig.get().getAapInstallation()) {
                items.add(aapServer.getAapDisplayName());
            }
            return items;
        }

        public ListBoxModel doFillTemplateTypeItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("job");
            items.add("workflow");
            return items;
        }
        public ListBoxModel doFillJobTypeItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("run");
            items.add("check");
            return items;
        }

        public ListBoxModel doFillAapLogLevelItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Do not import", "false");
            items.add("Import Truncated Logs", "true");
            items.add("Import Full Logs", "full");
            items.add("Process Variables Only", "vars");
            return items;
        }

        public boolean isGlobalColorAllowed() {
            return true;
        }

        // This requires a POST method to protect from CSFR
        @POST
        public ListBoxModel doFillAapCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String aapCredentialsId) {
            return GetUserPageCredentials.getUserAvailableCredentials(item, aapCredentialsId);
        }
    }


    public static final class AnsibleAAPStepExecution extends AbstractSynchronousNonBlockingStepExecution<Properties> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient AnsibleAAPStep step;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient Run<?,?> run;

        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient EnvVars envVars;

        @StepContextParameter
        private transient Computer computer;

        @Override
        protected Properties run() throws AbortException {
            if ((computer == null) || (computer.getNode() == null)) {
                throw new AbortException("The Ansible Automation Platform build step requires to be launched on a node");
            }

            AnsibleAAPRunner runner = new AnsibleAAPRunner();

            // Doing this will make the options optional in the pipeline step.
            String aapCredentialsId = "";
            if(step.getAapCredentialsId() != null) { aapCredentialsId = step.getAapCredentialsId(); }
            String extraVars = "";
            if(step.getExtraVars() != null) { extraVars = step.getExtraVars(); }
            String limit = "";
            if(step.getLimit() != null) { limit = step.getLimit(); }
            String tags = "";
            if(step.getJobTags() != null) { tags = step.getJobTags(); }
            String skipTags = "";
            if(step.getSkipJobTags() != null) { skipTags = step.getSkipJobTags(); }
            String jobType = "run";
            if(step.getJobType() != null){ jobType = step.getJobType();}
            String inventory = "";
            if(step.getInventory() != null) { inventory = step.getInventory(); }
            String credential = "";
            if(step.getCredential() != null) { credential = step.getCredential(); }
            String scmBranch = "";
            if(step.getScmBranch() != null) { scmBranch = step.getScmBranch(); }
            boolean verbose = false;
            if(step.getVerbose() != null) { verbose = step.getVerbose(); }
            String aapLogLevel = "false";
            if(step.getAapLogLevel() != null) { aapLogLevel = step.getAapLogLevel(); }
            boolean removeColor = false;
            if(step.getRemoveColor() != null) { removeColor = step.getRemoveColor(); }
            String templateType = "job";
            if(step.getTemplateType() != null) { templateType = step.getTemplateType(); }
            boolean importWorkflowChildLogs = false;
            if(step.getImportWorkflowChildLogs() != null) { importWorkflowChildLogs = step.getImportWorkflowChildLogs(); }
            boolean throwExceptionWhenFail = true;
            if(step.getThrowExceptionWhenFail() != null) { throwExceptionWhenFail = step.getThrowExceptionWhenFail(); }
            boolean async = false;
            if(step.getAsync() != null) { async = step.getAsync(); }
            Properties map = new Properties();
            boolean runResult = runner.runJobTemplate(
                    listener.getLogger(), step.getAapServer(), aapCredentialsId, step.getJobTemplate(), jobType, extraVars,
                    limit, tags, skipTags, inventory, credential, scmBranch, verbose, aapLogLevel, removeColor, envVars,
                    templateType, importWorkflowChildLogs, ws, run, map, async
            );
            if(!runResult && throwExceptionWhenFail) {
                throw new AbortException("Ansible Automation Platform build step failed");
            }
            return map;
        }
    }
}

