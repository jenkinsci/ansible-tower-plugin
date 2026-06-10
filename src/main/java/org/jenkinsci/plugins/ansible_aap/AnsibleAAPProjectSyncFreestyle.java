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
public class AnsibleAAPProjectSyncFreestyle extends Builder {

	private @Nonnull String aapServer     = DescriptorImpl.aapServer;
	private String aapCredentialsId       = "";
	private @Nonnull String project         = DescriptorImpl.project;
    private Boolean verbose                 = DescriptorImpl.verbose;
    private Boolean importAAPLogs			= DescriptorImpl.importAAPLogs;
    private Boolean removeColor				= DescriptorImpl.removeColor;

	@DataBoundConstructor
	public AnsibleAAPProjectSyncFreestyle(
			@Nonnull String aapServer, String aapCredentialsId, @Nonnull String project, Boolean verbose,
			Boolean importAAPLogs, Boolean removeColor
	) {
		this.aapServer = aapServer;
		this.aapCredentialsId = aapCredentialsId;
		this.project = project;
		this.verbose = verbose;
		this.importAAPLogs = importAAPLogs;
		this.removeColor = removeColor;
	}

	@Nonnull
	public String getAapServer() { return aapServer; }
	public String getAapCredentialsId() { return aapCredentialsId; }
	@Nonnull
	public String getProject() { return project; }
	public Boolean getVerbose() { return verbose; }
	public Boolean getImportAAPLogs() { return importAAPLogs; }
	public Boolean getRemoveColor() { return removeColor; }

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

        // There have been no options added to this task yet

		// here we just pass a map as we don't case for non pipeline jobs
		boolean runResult = runner.projectSync(
				listener.getLogger(), this.getAapServer(), this.getAapCredentialsId(), this.project,
				this.verbose, this.importAAPLogs, this.getRemoveColor(), envVars,
				build.getWorkspace(), build, new Properties(), false
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
		public static final String aapCredentialsId    	= "";
        public static final String project       			= "";
		public static final Boolean verbose       			= false;
		public static final Boolean importAAPLogs			= false;
		public static final Boolean removeColor				= false;
		public static final String templateType				= "job";
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
        public String getDisplayName() { return "Ansible Automation Platform Project Sync"; }

        public ListBoxModel doFillAapServerItems() {
			ListBoxModel items = new ListBoxModel();
			items.add(" - None -");
			for(AAPInstallation aapServer : AnsibleAAPGlobalConfig.get().getAapInstallation()) {
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
}
