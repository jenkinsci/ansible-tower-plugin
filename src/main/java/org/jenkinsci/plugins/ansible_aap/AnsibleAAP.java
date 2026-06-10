package org.jenkinsci.plugins.ansible_aap;

/*
        This class is the standard workflow step
        We simply take the data from Jenkins and call an AnsibleAAPRunner
 */

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.ansible_aap.util.GetUserPageCredentials;
import org.jenkinsci.plugins.ansible_aap.util.AAPInstallation;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Properties;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.instanceOf;

/**
 * @author Janario Oliveira
 */
public class AnsibleAAP extends Builder {

	private @Nonnull String aapServer     = DescriptorImpl.aapServer;
	private @Nonnull String jobTemplate     = DescriptorImpl.jobTemplate;
	private String aapCredentialsId       = DescriptorImpl.aapCredentialsId;
	private String extraVars                = DescriptorImpl.extraVars;
	private String jobTags                  = DescriptorImpl.jobTags;
	private String skipJobTags              = DescriptorImpl.skipJobTags;
	private String jobType                  = DescriptorImpl.jobType;
    private String limit                    = DescriptorImpl.limit;
    private String inventory                = DescriptorImpl.inventory;
    private String credential               = DescriptorImpl.credential;
	private String scmBranch                = DescriptorImpl.scmBranch;
    private Boolean verbose                 = DescriptorImpl.verbose;
    private String importAAPLogs			= DescriptorImpl.importAAPLogs;
    private Boolean removeColor				= DescriptorImpl.removeColor;
	private String templateType				= DescriptorImpl.templateType;
	private Boolean importWorkflowChildLogs	= DescriptorImpl.importWorkflowChildLogs;

	/* Legacy constructor from 0.15.0 */
	public AnsibleAAP(
			@Nonnull String aapServer, @Nonnull String jobTemplate, String aapCredentialsId, String jobType,
			String extraVars, String jobTags, String skipJobTags, String limit, String inventory, String credential, String scmBranch,
			Boolean verbose, Boolean importAAPLogs, Boolean removeColor, String templateType,
			Boolean importWorkflowChildLogs
	) {
		this.aapServer = aapServer;
		this.jobTemplate = jobTemplate;
		this.aapCredentialsId = aapCredentialsId;
		this.extraVars = extraVars;
		this.jobTags = jobTags;
		this.skipJobTags = skipJobTags;
		this.jobType = jobType;
		this.limit = limit;
		this.inventory = inventory;
		this.credential = credential;
		this.scmBranch = scmBranch;
		this.verbose = verbose;
		this.importAAPLogs = importAAPLogs.toString();
		this.removeColor = removeColor;
		this.templateType = templateType;
		this.importWorkflowChildLogs = importWorkflowChildLogs;
	}

	@DataBoundConstructor
	public AnsibleAAP(
			@Nonnull String aapServer, @Nonnull String jobTemplate, String aapCredentialsId, String jobType,
			String extraVars, String jobTags, String skipJobTags, String limit, String inventory, String credential, String scmBranch,
			Boolean verbose, String importAAPLogs, Boolean removeColor, String templateType,
			Boolean importWorkflowChildLogs
	) {
		this.aapServer = aapServer;
		this.jobTemplate = jobTemplate;
		this.aapCredentialsId = aapCredentialsId;
		this.extraVars = extraVars;
		this.jobTags = jobTags;
		this.skipJobTags = skipJobTags;
		this.jobType = jobType;
		this.limit = limit;
		this.inventory = inventory;
		this.credential = credential;
		this.scmBranch = scmBranch;
		this.verbose = verbose;
		this.importAAPLogs = importAAPLogs;
		this.removeColor = removeColor;
		this.templateType = templateType;
		this.importWorkflowChildLogs = importWorkflowChildLogs;
	}

	@Nonnull
	public String getAapServer() { return aapServer; }
	@Nonnull
	public String getJobTemplate() { return jobTemplate; }
	public String getAapCredentialsId() { return aapCredentialsId; }
	public String getExtraVars() { return extraVars; }
	public String getJobTags() { return jobTags; }
	public String getSkipJobTags() { return skipJobTags; }
	public String getJobType() { return jobType; }
	public String getLimit() { return limit; }
	public String getInventory() { return inventory; }
	public String getCredential() { return credential; }
	public String getScmBranch() { return scmBranch; }
	public Boolean getVerbose() { return verbose; }
	public String getImportAAPLogs() { return importAAPLogs; }
	public Boolean getRemoveColor() { return removeColor; }
	public String getTemplateType() { return templateType; }
	public Boolean getImportWorkflowChildLogs() { return importWorkflowChildLogs; }

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
	public void setImportAAPLogs(Boolean importAAPLogs) { this.importAAPLogs = importAAPLogs.toString(); }
	@DataBoundSetter
	public void setImportAAPLogs(String importAAPLogs) { this.importAAPLogs = importAAPLogs; }
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
		AnsibleAAPRunner runner = new AnsibleAAPRunner();
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
				listener.getLogger(), this.getAapServer(), this.aapCredentialsId, this.getJobTemplate(),
				this.getJobType(),this.getExtraVars(), this.getLimit(), this.getJobTags(), this.getSkipJobTags(),
				this.getInventory(), this.getCredential(), this.getScmBranch(), this.verbose, this.importAAPLogs, this.getRemoveColor(),
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
        public static final String aapServer    			= "";
        public static final String jobTemplate    			= "";
        public static final String aapCredentialsId   	= "";
		public static final String extraVars      			= "";
		public static final String limit          			= "";
        public static final String jobTags        			= "";
		public static final String skipJobTags        		= "";
		public static final String jobType					= "run";
		public static final String inventory      			= "";
		public static final String credential     			= "";
		public static final String scmBranch     			= "";
		public static final Boolean verbose       			= false;
		public static final String importAAPLogs			= "false";
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
        public String getDisplayName() { return "Ansible Automation Platform"; }

        public ListBoxModel doFillAapServerItems() {
			ListBoxModel items = new ListBoxModel();
			items.add(" - None -");
			for(AAPInstallation aapServer : AnsibleAAPGlobalConfig.get().getAapInstallation()) {
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

		@POST
		public ListBoxModel doFillAapCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String aapCredentialsId) {
            return GetUserPageCredentials.getUserAvailableCredentials(item, aapCredentialsId);
		}

		public ListBoxModel doFillImportAAPLogsItems() {
        	ListBoxModel items = new ListBoxModel();
        	items.add("Do not import", "false");
			items.add("Import Truncated Logs", "true");
			items.add("Import Full Logs", "full");
			items.add("Process Variables Only", "vars");
			return items;
		}

		// Some day I'd like to be able to make all of these dropdowns from querying the aap API
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
