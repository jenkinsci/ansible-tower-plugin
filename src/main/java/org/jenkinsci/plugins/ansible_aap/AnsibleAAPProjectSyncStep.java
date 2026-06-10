package org.jenkinsci.plugins.ansible_aap;

/*
    This class is the pipeline step
    We simply take the data from Jenkins and call an AnsibleAAPRunner
 */

import com.google.inject.Inject;
import hudson.*;
import hudson.model.*;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.ansible_aap.util.GetUserPageCredentials;
import org.jenkinsci.plugins.ansible_aap.util.AAPInstallation;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.util.Properties;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.instanceOf;

public class AnsibleAAPProjectSyncStep extends AbstractStepImpl {
    private String aapServer              = "";
    private String aapCredentialsId       = "";
    private String project                  = "";
    private Boolean verbose                 = false;
    private Boolean importAAPLogs         = false;
    private Boolean removeColor             = false;
    private Boolean throwExceptionWhenFail  = true;
    private Boolean async                   = false;

    @DataBoundConstructor
    public AnsibleAAPProjectSyncStep(
            @Nonnull String aapServer, @Nonnull String aapCredentialsId, @Nonnull String project, Boolean verbose,
            Boolean importAAPLogs, Boolean removeColor, Boolean throwExceptionWhenFail, Boolean async
    ) {
        this.aapServer = aapServer;
        this.aapCredentialsId = aapCredentialsId;
        this.project = project;
        this.verbose = verbose;
        this.importAAPLogs = importAAPLogs;
        this.removeColor = removeColor;
        this.throwExceptionWhenFail = throwExceptionWhenFail;
        this.async = async;
    }

    @Nonnull
    public String getAapServer()              { return aapServer; }
    public String getAapCredentialsId()       { return aapCredentialsId; }
    @Nonnull
    public String getProject()                  { return project; }
    public Boolean getVerbose()                 { return verbose; }
    public Boolean getImportAAPLogs()         { return importAAPLogs; }
    public Boolean getRemoveColor()             { return removeColor; }
    public Boolean getThrowExceptionWhenFail()  { return throwExceptionWhenFail; }
    public Boolean getAsync()                   { return async; }

    @DataBoundSetter
    public void setAapServer(String aapServer) { this.aapServer = aapServer; }
    @DataBoundSetter
    public void setAapCredentialsId(String aapCredentialsId) { this.aapCredentialsId = aapCredentialsId; }
    @DataBoundSetter
    public void setProject(String project) { this.project = project; }
    @DataBoundSetter
    public void setVerbose(Boolean verbose) { this.verbose = verbose; }
    @DataBoundSetter
    public void setImportAAPLogs(Boolean importAAPLogs) { this.importAAPLogs = importAAPLogs; }
    @DataBoundSetter
    public void setRemoveColor(Boolean removeColor) { this.removeColor = removeColor; }
    @DataBoundSetter
    public void setThrowExceptionWhenFail(Boolean throwExceptionWhenFail) { this.throwExceptionWhenFail = throwExceptionWhenFail; }
    @DataBoundSetter
    public void setAsync(Boolean async) { this.async = async; }

    public boolean isGlobalColorAllowed() {
        System.out.println("Using the class is global color allowed");
        return true;
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public static final String aapServer              = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.aapServer;
        public static final String aapCredentialsId       = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.aapCredentialsId;
        public static final String project                  = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.project;
        public static final Boolean verbose                 = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.verbose;
        public static final Boolean importAAPLogs         = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.importAAPLogs;
        public static final Boolean removeColor             = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.removeColor;
        public static final Boolean throwExceptionWhenFail  = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.throwExceptionWhenFail;
        public static final Boolean async                   = AnsibleAAPProjectSyncFreestyle.DescriptorImpl.async;

        public DescriptorImpl() {
            super(AnsibleAAPProjectSyncStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "ansibleAAPProjectSync";
        }

        @Override
        public String getDisplayName() {
            return "Have Ansible Automation Platform update a AAP project";
        }

        public ListBoxModel doFillAapServerItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(" - None -");
            for (AAPInstallation aapServer : AnsibleAAPGlobalConfig.get().getAapInstallation()) {
                items.add(aapServer.getAapDisplayName());
            }
            return items;
        }

        public boolean isGlobalColorAllowed() {
            System.out.println("Using the descriptor is global color allowed");
            return true;
        }

        // This requires a POST method to protect from CSFR
        @POST
        public ListBoxModel doFillAapCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String aapCredentialsId) {
            return GetUserPageCredentials.getUserAvailableCredentials(item, aapCredentialsId);
        }
    }


    public static final class AnsibleAAPProjectSyncStepExecution extends AbstractSynchronousNonBlockingStepExecution<Properties> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient AnsibleAAPProjectSyncStep step;

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
                throw new AbortException("The Ansible Automation Platform Project Sync build step requires to be launched on a node");
            }

            AnsibleAAPRunner runner = new AnsibleAAPRunner();

            // Doing this will make the options optional in the pipeline step.
            String project = "";
            if(step.getProject() != null) { project = step.getProject(); }
            boolean verbose = false;
            if(step.getVerbose() != null) { verbose = step.getVerbose(); }
            boolean importAAPLogs = false;
            if(step.getImportAAPLogs() != null) { importAAPLogs = step.getImportAAPLogs(); }
            boolean removeColor = false;
            if(step.getRemoveColor() != null) { removeColor = step.getRemoveColor(); }
            boolean throwExceptionWhenFail = true;
            if(step.getThrowExceptionWhenFail() != null) { throwExceptionWhenFail = step.getThrowExceptionWhenFail(); }
            boolean async = false;
            if(step.getAsync() != null) { async = step.getAsync(); }
            Properties map = new Properties();
            boolean runResult = runner.projectSync(
                    listener.getLogger(), step.getAapServer(), step.getAapCredentialsId(), project, verbose,
                    importAAPLogs, removeColor, envVars, ws, run, map, async
            );
            if(!runResult && throwExceptionWhenFail) {
                throw new AbortException("Ansible Automation Platform Project Sync build step failed");
            }
            return map;
        }
    }
}
