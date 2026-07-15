package org.jenkinsci.plugins.ansible_tower;

/*
    This class is the pipeline step
    We simply take the data from Jenkins and call an AnsibleTowerRunner
 */

import com.google.inject.Inject;
import hudson.*;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.ansible_tower.util.GetUserPageCredentials;
import org.jenkinsci.plugins.ansible_tower.util.TowerInstallation;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Properties;

public class AnsibleTowerProjectRevisionStep extends AbstractStepImpl {
    private String towerServer              = "";
    private String towerCredentialsId       = "";
    private String project                  = "";
    private String revision                 = "";
    private Boolean verbose                 = false;
    private Boolean throwExceptionWhenFail  = true;

    @DataBoundConstructor
    public AnsibleTowerProjectRevisionStep(
            @NonNull String towerServer, @NonNull String towerCredentialsId,
            @NonNull String project, String revision,
            Boolean verbose, Boolean throwExceptionWhenFail
    ) {
        this.towerServer = towerServer;
        this.towerCredentialsId = towerCredentialsId;
        this.project = project;
        this.revision = revision;
        this.verbose = verbose;
        this.throwExceptionWhenFail = throwExceptionWhenFail;
    }

    @NonNull
    public String getTowerServer()              { return towerServer; }
    public String getTowerCredentialsId()       { return towerCredentialsId; }
    @NonNull
    public String getProject()                  { return project; }
    public String getRevision()                  { return revision; }
    public Boolean getVerbose()                 { return verbose; }
    public Boolean getThrowExceptionWhenFail()  { return throwExceptionWhenFail; }

    @DataBoundSetter
    public void setTowerServer(String towerServer) { this.towerServer = towerServer; }
    @DataBoundSetter
    public void setTowerCredentialsId(String towerCredentialsId) { this.towerCredentialsId = towerCredentialsId; }
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
        public static final String towerServer              = AnsibleTowerProjectRevisionFreestyle.DescriptorImpl.towerServer;
        public static final String towerCredentialsId       = AnsibleTowerProjectRevisionFreestyle.DescriptorImpl.towerCredentialsId;
        public static final String project                  = AnsibleTowerProjectRevisionFreestyle.DescriptorImpl.project;
        public static final String revision                 = AnsibleTowerProjectRevisionFreestyle.DescriptorImpl.revision;
        public static final Boolean verbose                 = AnsibleTowerProjectRevisionFreestyle.DescriptorImpl.verbose;
        public static final Boolean throwExceptionWhenFail  = AnsibleTowerProjectRevisionFreestyle.DescriptorImpl.throwExceptionWhenFail;

        public DescriptorImpl() {
            super(AnsibleTowerProjectRevisionStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "ansibleTowerProjectRevision";
        }

        @Override
        public String getDisplayName() {
            return "Have Ansible Tower update a Tower project's revision";
        }

        public ListBoxModel doFillTowerServerItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(" - None -");
            for (TowerInstallation towerServer : AnsibleTowerGlobalConfig.get().getTowerInstallation()) {
                items.add(towerServer.getTowerDisplayName());
            }
            return items;
        }

        // This requires a POST method to protect from CSFR
        @POST
        public ListBoxModel doFillTowerCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String towerCredentialsId) {
            return GetUserPageCredentials.getUserAvailableCredentials(item, towerCredentialsId);
        }
    }


    public static final class AnsibleTowerProjectRevisionStepExecution extends AbstractSynchronousNonBlockingStepExecution<Properties> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient AnsibleTowerProjectRevisionStep step;

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
                throw new AbortException("The Ansible Tower Project Revision build step requires to be launched on a node");
            }

            AnsibleTowerRunner runner = new AnsibleTowerRunner();

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
                    listener.getLogger(), step.getTowerServer(), step.getTowerCredentialsId(),
                    project, revision,
                    verbose,
                    envVars, ws, run, map
            );
            if(!runResult && throwExceptionWhenFail) {
                throw new AbortException("Ansible Tower Project Revision build step failed");
            }
            return map;
        }
    }
}
