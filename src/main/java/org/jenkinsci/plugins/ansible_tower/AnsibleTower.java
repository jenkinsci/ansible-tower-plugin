package org.jenkinsci.plugins.ansible_tower;

/*
        This class is the standard workflow step
        We simply take the data from Jenkins and call an AnsibleTowerRunner
 */

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.ansible_tower.util.TowerInstallation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

/**
 * @author Janario Oliveira
 */
public class AnsibleTower extends Builder {

	private @Nonnull String towerServer     = DescriptorImpl.towerServer;
	private @Nonnull String jobTemplate     = DescriptorImpl.jobTemplate;
	private String towerCredentialsId       = DescriptorImpl.towerCredentialsId;
	private String extraVars                = DescriptorImpl.extraVars;
	private String jobTags                  = DescriptorImpl.jobTags;
	private String skipJobTags              = DescriptorImpl.skipJobTags;
	private String jobType                  = DescriptorImpl.jobType;
    private String limit                    = DescriptorImpl.limit;
    private String inventory                = DescriptorImpl.inventory;
    private String credential               = DescriptorImpl.credential;
    private Boolean verbose                 = DescriptorImpl.verbose;
    private Boolean importTowerLogs			= DescriptorImpl.importTowerLogs;
    private Boolean removeColor				= DescriptorImpl.removeColor;
	private String templateType				= DescriptorImpl.templateType;
	private Boolean importWorkflowChildLogs	= DescriptorImpl.importWorkflowChildLogs;

	@DataBoundConstructor
	public AnsibleTower(
			@Nonnull String towerServer, @Nonnull String jobTemplate, String towerCredentialsId, String jobType,
			String extraVars, String jobTags, String skipJobTags, String limit, String inventory, String credential,
			Boolean verbose, Boolean importTowerLogs, Boolean removeColor, String templateType,
			Boolean importWorkflowChildLogs
	) {
		this.towerServer = towerServer;
		this.jobTemplate = jobTemplate;
		this.towerCredentialsId = towerCredentialsId;
		this.extraVars = extraVars;
		this.jobTags = jobTags;
		this.skipJobTags = skipJobTags;
		this.jobType = jobType;
		this.limit = limit;
		this.inventory = inventory;
		this.credential = credential;
		this.verbose = verbose;
		this.importTowerLogs = importTowerLogs;
		this.removeColor = removeColor;
		this.templateType = templateType;
		this.importWorkflowChildLogs = importWorkflowChildLogs;
	}

	@Nonnull
	public String getTowerServer() { return towerServer; }
	@Nonnull
	public String getJobTemplate() { return jobTemplate; }
	public String getTowerCredentialsId() { return towerCredentialsId; }
	public String getExtraVars() { return extraVars; }
	public String getJobTags() { return jobTags; }
	public String getSkipJobTags() { return skipJobTags; }
	public String getJobType() { return jobType; }
	public String getLimit() { return limit; }
	public String getInventory() { return inventory; }
	public String getCredential() { return credential; }
	public Boolean getVerbose() { return verbose; }
	public Boolean getImportTowerLogs() { return importTowerLogs; }
	public Boolean getRemoveColor() { return removeColor; }
	public String getTemplateType() { return templateType; }
	public Boolean getImportWorkflowChildLogs() { return importWorkflowChildLogs; }

	@DataBoundSetter
	public void setTowerServer(String towerServer) { this.towerServer = towerServer; }
	@DataBoundSetter
	public void setJobTemplate(String jobTemplate) { this.jobTemplate = jobTemplate; }
	@DataBoundSetter
	public void setTowerCredentialsId(String towerCredentials) { this.towerCredentialsId = towerCredentialsId; }
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
	public void setVerbose(Boolean verbose) { this.verbose = verbose; }
	@DataBoundSetter
	public void setImportTowerLogs(Boolean importTowerLogs) { this.importTowerLogs = importTowerLogs; }
	@DataBoundSetter
	public void setRemoveColor(Boolean removeColor) { this.removeColor = removeColor; }
	@DataBoundSetter
	public void setTemplateType(String templateType) { this.templateType = templateType; }
	@DataBoundSetter
	public void setImportWorkflowChildLogs(Boolean importWorkflowChildLogs) { this.importWorkflowChildLogs = importWorkflowChildLogs; }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException
	{
		AnsibleTowerRunner runner = new AnsibleTowerRunner();
		EnvVars envVars = build.getEnvironment(listener);

		//
		// When adding a new option, you need to check if its null.
		// An existing job will not have the new fields set so null will get passed through if you don't
		//
		String templateType = "job";
		if(this.getTemplateType() != null) { templateType = this.getTemplateType(); }
		boolean importWorkflowChildLogs = false;
		if(this.getImportWorkflowChildLogs() != null) { importWorkflowChildLogs = this.getImportWorkflowChildLogs(); }

		// here we just pass a map as we don't case for non pipeline jobs
		boolean runResult = runner.runJobTemplate(
				listener.getLogger(), this.getTowerServer(), this.towerCredentialsId, this.getJobTemplate(),
				this.getJobType(),this.getExtraVars(), this.getLimit(), this.getJobTags(), this.getSkipJobTags(),
				this.getInventory(), this.getCredential(), this.verbose, this.importTowerLogs, this.getRemoveColor(),
				envVars, templateType, importWorkflowChildLogs, build.getWorkspace(), build, new Properties()
		);
		if(runResult) {
			build.setResult(Result.SUCCESS);
		} else {
			build.setResult(Result.FAILURE);
		}

		return runResult;
    }

	@Extension(optional = true)
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public static final String towerServer    			= "";
        public static final String jobTemplate    			= "";
        public static final String towerCredentialsId   	= "";
		public static final String extraVars      			= "";
		public static final String limit          			= "";
        public static final String jobTags        			= "";
		public static final String skipJobTags        		= "";
		public static final String jobType					= "run";
		public static final String inventory      			= "";
		public static final String credential     			= "";
		public static final Boolean verbose       			= false;
		public static final Boolean importTowerLogs			= false;
		public static final Boolean removeColor				= false;
		public static final String templateType				= "job";
		public static final Boolean importWorkflowChildLogs	= false;
		public static final Boolean throwExceptionWhenFail  = true;
		public static final boolean async                   = false;

        public DescriptorImpl() {
            load();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() { return "Ansible Tower"; }

        public ListBoxModel doFillTowerServerItems() {
			ListBoxModel items = new ListBoxModel();
			items.add(" - None -");
			for(TowerInstallation towerServer : AnsibleTowerGlobalConfig.get().getTowerInstallation()) {
				items.add(towerServer.getTowerDisplayName());
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

        // Some day I'd like to be able to make all of these dropdowns from querying the tower API
		// Maybe not in real time because that would be slow when loading a the configure job
        /*
        public ListBoxModel doFillPlaybookItems() {
        	ListBoxModel items = new ListBoxModel();
			items.add(" - None -");
            return null;
        }
        */
    }
}
