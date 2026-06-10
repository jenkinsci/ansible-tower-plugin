package org.jenkinsci.plugins.ansible_aap.util;

/*
    This class represents a AAP installation
 */

import static com.cloudbees.plugins.credentials.CredentialsMatchers.instanceOf;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import org.kohsuke.stapler.verb.POST;

import java.util.List;

public class AAPInstallation extends AbstractDescribableImpl<AAPInstallation> {
    private static final long getSerialVersionUID = 1L;

    private final String aapDisplayName;
    private final String aapURL;
    private final String aapDisplayURL;
    private String aapCredentialsId;
    private final boolean aapTrustCert;
    private final boolean enableDebugging;
    private Run run;

    @DataBoundConstructor
    public AAPInstallation(String aapDisplayName, String aapURL, String aapDisplayURL, String aapCredentialsId, boolean aapTrustCert, boolean enableDebugging) {
        this.aapDisplayName = aapDisplayName;
        this.aapCredentialsId = aapCredentialsId;
        this.aapURL = aapURL;
        this.aapDisplayURL = aapDisplayURL;
        this.aapTrustCert = aapTrustCert;
        this.enableDebugging = enableDebugging;
    }

    public String getAapDisplayName() {
        return this.aapDisplayName;
    }

    public String getAapURL() {
        return this.aapURL;
    }

    public String getAapDisplayURL() {
        return this.aapDisplayURL;
    }

    public String getAapCredentialsId() {
        return this.aapCredentialsId;
    }

    public boolean getAapTrustCert() {
        return this.aapTrustCert;
    }

    public boolean getEnableDebugging() {
        return this.enableDebugging;
    }

    public void setAapCredentialsId(String aapCredentialsId) {
        this.aapCredentialsId = aapCredentialsId;
    }

    public void setRun(Run run) {
        this.run = run;
    }

    public AAPConnector getAapConnector() {
        return AAPInstallation.getAapConnectorStatic(this.aapURL, this.aapCredentialsId, this.aapTrustCert,
                this.enableDebugging, this.run, this.aapDisplayURL);
    }

    public static AAPConnector getAapConnectorStatic(String aapURL, String aapCredentialsId, boolean trustCert,
                                                         boolean enableDebugging, Run run) {
        return getAapConnectorStatic(aapURL, aapCredentialsId, trustCert, enableDebugging, run, null);
    }

    public static AAPConnector getAapConnectorStatic(String aapURL, String aapCredentialsId, boolean trustCert,
                                                         boolean enableDebugging, Run run, String aapDisplayURL) {
        String username = null;
        String password = null;
        String oauth_token = null;
        if (StringUtils.isNotBlank(aapCredentialsId)) {
            List<StandardUsernamePasswordCredentials> credsList = getCredsList(StandardUsernamePasswordCredentials.class, run);
            for (StandardUsernamePasswordCredentials creds : credsList) {
                if (creds.getId().equals(aapCredentialsId)) {
                    username = creds.getUsername();
                    password = creds.getPassword().getPlainText();
                }
            }
            List<StringCredentials> secretList = getCredsList(StringCredentials.class, run);
            for (StringCredentials secret : secretList) {
                if (secret.getId().equals(aapCredentialsId)) {
                    oauth_token = secret.getSecret().getPlainText();
                }
            }
        }
        AAPConnector testConnector = new AAPConnector(aapURL, username, password, oauth_token, trustCert, enableDebugging, aapDisplayURL);
        return testConnector;
    }
    
    @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
    private static <C extends Credentials> List<C> getCredsList(Class<C> type, Run run) {
        List<C> credsList;

        if (run != null && run.getParent() != null) {
            credsList = CredentialsProvider.lookupCredentials(type,
                    run.getParent(), null, new DomainRequirement());
        } else {
            credsList = CredentialsProvider.lookupCredentials(type);
        }

        return credsList;
    }

    @Extension
    public static class AAPInstallationDescriptor extends Descriptor<AAPInstallation> {

        // This requires a POST method to protect from CSFR
        @POST
        public FormValidation doTestAapConnection(
                @QueryParameter("aapURL") final String aapURL,
                @QueryParameter("aapCredentialsId") final String aapCredentialsId,
                @QueryParameter("aapTrustCert") final boolean aapTrustCert,
                @QueryParameter("enableDebugging") final boolean enableDebugging
        ) {
            // Also, validate that we are an Administrator
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            AAPLogger.writeMessage("Starting to test connection with (" + aapURL + ") and (" + aapCredentialsId + ") and (" + aapTrustCert + ") with debugging (" + enableDebugging + ")");
            AAPConnector testConnector = AAPInstallation.getAapConnectorStatic(aapURL, aapCredentialsId, aapTrustCert, enableDebugging, null);
            try {
                testConnector.testConnection();
                return FormValidation.ok("Success");
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        // This requires a POST method to protect from CSFR
        @POST
        public ListBoxModel doFillAapCredentialsIdItems(@AncestorInPath Project project) {
            // Also, validate that we are an Administrator
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return new StandardListBoxModel().withEmptySelection().withMatching(
                    instanceOf(UsernamePasswordCredentials.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, project)
            ).withMatching(
                    instanceOf(StringCredentials.class),
                    CredentialsProvider.lookupCredentials(StringCredentials.class, project)
            );
        }

        @Override
        public String getDisplayName() {
            return "AAP Installation";
        }
    }
}

