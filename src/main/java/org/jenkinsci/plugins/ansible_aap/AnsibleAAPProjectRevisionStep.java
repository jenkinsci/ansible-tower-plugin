package org.jenkinsci.plugins.ansible_aap;

/*
    This class is the pipeline step
    We simply take the data from Jenkins and call an AnsibleAAPRunner
 */

import com.google.inject.Inject;
import hudson.*;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
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

public class AnsibleAAPProjectRevisionStep extends AbstractStepImpl {
    private String aapServer              = "";
    private String aapCredentialsId       = "";
    private String project                  = "";
    private String revision                 = "";
    private Boolean verbose                 = false;
    private Boolean throwExceptionWhenFail  = true;

    @DataBoundConstructor
    public AnsibleAAPProjectRevisionStep(
            @Nonnull String aapServer, @Nonnull String aapCredentialsId,
            @Nonnull String project, String revision,
            Boolean verbose, Boolean throwExceptionWhenFail
    ) {
        this.aapServer = aapServer;
        this.aapCredentialsId = aapCredentialsId;
        this.project = project;
        this.revision = revision;
        this.verbose = verbose;
        this.throwExceptionWhenFail = throwExceptionWhenFail;
    }

    @Nonnull
    public String getAapServer()              { return aapServer; }
    public String getAapCredentialsId()       { return aapCredentialsId; }
    @Nonnull
    public String getProject()                  { return project; }
    public String getRevision()                  { return revision; }
    public Boolean getVerbose()                 { return verbose; }
    public Boolean getThrowExceptionWhenFail()  { return throwExceptionWhenFail; }

    @DataBoundSetter
    public void setAapServer(String aapServer) { this.aapServer = aapServer; }
    @DataBoundSetter
    public void setAapCredentialsId(String aapCredentialsId) { this.aapCredentialsId = aapCredentialsId; }
    @DataBoundSetter
    public void setProject(String project) { this.project = project; }
    @DataBoundSetter
    public void setRevision(String revision) { this.revision = revision; }
    @DataBoundSetter
    public void setVerbose(Boolean verbose) { this.verbose = verbose; }
    @DataBoundSetter
    public void setThrowExceptionWhenFail(Boolean throwExceptionWhenFail) { this.throwExceptionWhenFail = throwExceptionWhenFail; }

    @Extension(optional = true)
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public static final String aapServer              = AnsibleAAPProjectRevisionFreestyle.DescriptorImpl.aapServer;
        public static final String aapCredentialsId       = AnsibleAAPProjectRevisionFreestyle.DescriptorImpl.aapCredentialsId;
        public static final String project                  = AnsibleAAPProjectRevisionFreestyle.DescriptorImpl.project;
        public static final String revision                 = AnsibleAAPProjectRevisionFreestyle.DescriptorImpl.revision;
        public static final Boolean verbose                 = AnsibleAAPProjectRevisionFreestyle.DescriptorImpl.verbose;
        public static final Boolean throwExceptionWhenFail  = AnsibleAAPProjectRevisionFreestyle.DescriptorImpl.throwExceptionWhenFail;

        public DescriptorImpl() {
            super(AnsibleAAPProjectRevisionStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "ansibleAAPProjectRevision";
        }

        @Override
        public String getDisplayName() {
            return "Have Ansible Automation Platform update a AAP project's revision";
        }

        public ListBoxModel doFillAapServerItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(" - None -");
            for (AAPInstallation aapServer : AnsibleAAPGlobalConfig.get().getAapInstallation()) {
                items.add(aapServer.getAapDisplayName());
            }
            return items;
        }

        // This requires a POST method to protect from CSFR
        @POST
        public ListBoxModel doFillAapCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String aapCredentialsId) {
            return GetUserPageCredentials.getUserAvailableCredentials(item, aapCredentialsId);
        }
    }


    public static final class AnsibleAAPProjectRevisionStepExecution extends AbstractSynchronousNonBlockingStepExecution<Properties> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient AnsibleAAPProjectRevisionStep step;

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
                throw new AbortException("The Ansible Automation Platform Project Revision build step requires to be launched on a node");
            }

            AnsibleAAPRunner runner = new AnsibleAAPRunner();

            // Doing this will make the options optional in the pipeline step.
            String project = "";
            if(step.getProject() != null) { project = step.getProject(); }
            String revision = "";
            if(step.getRevision() != null) { revision = step.getRevision(); }
            boolean verbose = false;
            if(step.getVerbose() != null) { verbose = step.getVerbose(); }
            boolean throwExceptionWhenFail = true;
            if(step.getThrowExceptionWhenFail() != null) { throwExceptionWhenFail = step.getThrowExceptionWhenFail(); }
            Properties map = new Properties();
            boolean runResult = runner.projectRevision(
                    listener.getLogger(), step.getAapServer(), step.getAapCredentialsId(),
                    project, revision,
                    verbose,
                    envVars, ws, run, map
            );
            if(!runResult && throwExceptionWhenFail) {
                throw new AbortException("Ansible Automation Platform Project Revision build step failed");
            }
            return map;
        }
    }
}

