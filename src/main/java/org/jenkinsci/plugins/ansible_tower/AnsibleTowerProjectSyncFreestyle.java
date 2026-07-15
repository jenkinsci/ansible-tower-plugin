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
import org.jenkinsci.plugins.ansible_tower.util.GetUserPageCredentials;
import org.jenkinsci.plugins.ansible_tower.util.TowerInstallation;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Properties;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.instanceOf;

/**
 * @author Janario Oliveira
 */
public class AnsibleTowerProjectSyncFreestyle extends Builder {

	private @NonNull String towerServer     = DescriptorImpl.towerServer;
	private String towerCredentialsId       = "";
	private @NonNull String project         = DescriptorImpl.project;
    private Boolean verbose                 = DescriptorImpl.verbose;
    private Boolean importTowerLogs			= DescriptorImpl.importTowerLogs;
    private Boolean removeColor				= DescriptorImpl.removeColor;

	@DataBoundConstructor
	public AnsibleTowerProjectSyncFreestyle(
			@NonNull String towerServer, String towerCredentialsId, @NonNull String project, Boolean verbose,
			Boolean importTowerLogs, Boolean removeColor
	) {
		this.towerServer = towerServer;
		this.towerCredentialsId = towerCredentialsId;
		this.project = project;
		this.verbose = verbose;
		this.importTowerLogs = importTowerLogs;
		this.removeColor = removeColor;
	}

	@NonNull
	public String getTowerServer() { return towerServer; }
	public String getTowerCredentialsId() { return towerCredentialsId; }
	@NonNull
	public String getProject() { return project; }
	public Boolean getVerbose() { return verbose; }
	public Boolean getImportTowerLogs() { return importTowerLogs; }
	public Boolean getRemoveColor() { return removeColor; }

	@DataBoundSetter
	public void setTowerServer(String towerServer) { this.towerServer = towerServer; }
	@DataBoundSetter
	public void setTowerCredentialsId(String towerCredentialsId) { this.towerCredentialsId = towerCredentialsId; }
	@DataBoundSetter
	public void setProject(String project) { this.project = project; }
	@DataBoundSetter
	public void setVerbose(Boolean verbose) { this.verbose = verbose; }
	@DataBoundSetter
	public void setImportTowerLogs(Boolean importTowerLogs) { this.importTowerLogs = importTowerLogs; }
	@DataBoundSetter
	public void setRemoveColor(Boolean removeColor) { this.removeColor = removeColor; }

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

        // There have been no options added to this task yet

		// here we just pass a map as we don't case for non pipeline jobs
		boolean runResult = runner.projectSync(
				listener.getLogger(), this.getTowerServer(), this.getTowerCredentialsId(), this.project,
				this.verbose, this.importTowerLogs, this.getRemoveColor(), envVars,
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
        public static final String towerServer    			= "";
		public static final String towerCredentialsId    	= "";
        public static final String project       			= "";
		public static final Boolean verbose       			= false;
		public static final Boolean importTowerLogs			= false;
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
        public String getDisplayName() { return "Ansible Tower Project Sync"; }

        public ListBoxModel doFillTowerServerItems() {
			ListBoxModel items = new ListBoxModel();
			items.add(" - None -");
			for(TowerInstallation towerServer : AnsibleTowerGlobalConfig.get().getTowerInstallation()) {
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
}
