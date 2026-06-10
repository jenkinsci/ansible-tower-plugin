package org.jenkinsci.plugins.ansible_aap;

/*
    This class manages the list of AAP installations in the Global config section
 */

import hudson.Extension;
import hudson.XmlFile;
import hudson.util.XStream2;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.ansible_aap.util.AAPInstallation;

@Extension
public class AnsibleAAPGlobalConfig extends GlobalConfiguration {

    private List<AAPInstallation> aapInstallations = new ArrayList<AAPInstallation>();

    private static final XStream2 XSTREAM2 = new XStream2();

    public AnsibleAAPGlobalConfig() {
        load();
    }

    @Override
    protected XmlFile getConfigFile() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) return null;
        File rootDir = j.getRootDir();
        File xmlFile = new File(rootDir, "org.jenkinsci.plugins.ansible_aap.AnsibleAAP.xml");
        return new XmlFile(XSTREAM2, xmlFile);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json)
            throws FormException
    {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public static AnsibleAAPGlobalConfig get() {
        return GlobalConfiguration.all().get(AnsibleAAPGlobalConfig.class);
    }

    public List<AAPInstallation> getAapInstallation() {
        return aapInstallations;
    }

    public AAPInstallation getAapInstallationByName(String name) {
        for(AAPInstallation installation : aapInstallations) {
            if(installation.getAapDisplayName().equals(name)) { return installation; }
        }
        return null;
    }

    public void setAapInstallation(List<AAPInstallation> aapInstallations) {
        this.aapInstallations = aapInstallations;
    }

}

