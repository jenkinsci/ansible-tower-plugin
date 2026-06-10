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

/**
 * @author Janario Oliveira
 */
public class AnsibleAAPProjectRevisionFreestyle extends Builder {

	private @Nonnull String aapServer     = DescriptorImpl.aapServer;
	private String aapCredentialsId       = "";
	private @Nonnull String project         = DescriptorImpl.project;
	private String revision                 = DescriptorImpl.revision;
    private Boolean verbose                 = DescriptorImpl.verbose;
	private Boolean throwExceptionWhenFail  = true;

	@DataBoundConstructor
	public AnsibleAAPProjectRevisionFreestyle(
			@Nonnull String aapServer, String aapCredentialsId,
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
	public String getAapServer() { return aapServer; }
	public String getAapCredentialsId() { return aapCredentialsId; }
	@Nonnull
	public String getProject() { return project; }
	public String getRevision() { return revision; }
	public Boolean getVerbose() { return verbose; }
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
		boolean runResult = runner.projectRevision(
				listener.getLogger(), this.getAapServer(), this.getAapCredentialsId(),
				this.project, this.revision,
				this.verbose,
				envVars, build.getWorkspace(), build, new Properties()
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
		public static final String revision       			= "";
		public static final Boolean verbose       			= false;
		public static final Boolean throwExceptionWhenFail  = true;

        public DescriptorImpl() {
            load();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() { return "Ansible Automation Platform Project Revision"; }

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
