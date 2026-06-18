package org.jenkinsci.plugins.ansible_tower;

/*
    This class manages the list of Tower installations in the Global config section
 */

import hudson.Extension;
import hudson.XmlFile;
import hudson.util.XStream2;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.ansible_tower.util.TowerInstallation;

@Extension
public class AnsibleTowerGlobalConfig extends GlobalConfiguration {

    private List<TowerInstallation> towerInstallations = new ArrayList<TowerInstallation>();

    private static final XStream2 XSTREAM2 = new XStream2();

    public AnsibleTowerGlobalConfig() {
        load();
    }

    @Override
    protected XmlFile getConfigFile() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) return null;
        File rootDir = j.getRootDir();
        File xmlFile = new File(rootDir, "org.jenkinsci.plugins.ansible_tower.AnsibleTower.xml");
        return new XmlFile(XSTREAM2, xmlFile);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json)
            throws FormException
    {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public static AnsibleTowerGlobalConfig get() {
        return GlobalConfiguration.all().get(AnsibleTowerGlobalConfig.class);
    }

    public List<TowerInstallation> getTowerInstallation() {
        return towerInstallations;
    }

    public TowerInstallation getTowerInstallationByName(String name) {
        for(TowerInstallation installation : towerInstallations) {
            if(installation.getTowerDisplayName().equals(name)) { return installation; }
        }
        return null;
    }

    public void setTowerInstallation(List<TowerInstallation> towerInstallations) {
        this.towerInstallations = towerInstallations;
    }

}

