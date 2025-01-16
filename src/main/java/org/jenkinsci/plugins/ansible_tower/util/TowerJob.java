package org.jenkinsci.plugins.ansible_tower.util;

import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;

import java.util.HashMap;
import java.util.Vector;
import java.io.Serializable;

public class TowerJob  implements Serializable {
    private long jobId = -1;
    private TowerConnector connection;
    private String templateType = null;
    private static final long serialVersionUID = -323790358606407805L;

    public TowerJob(TowerConnector connection) {
        this.connection = connection;
    }

    public void setTemplateType(String templateType) throws AnsibleTowerException {
        if (templateType == null || (!templateType.equalsIgnoreCase(TowerConnector.WORKFLOW_TEMPLATE_TYPE) && !templateType.equalsIgnoreCase(TowerConnector.JOB_TEMPLATE_TYPE))) {
            throw new AnsibleTowerException("Template type "+ templateType +" was invalid");
        }
        this.templateType = templateType;
    }
    public void setJobId(long jobId) { this.jobId = jobId; }
    public long getJobID() { return this.jobId; }

    @SuppressWarnings("unused")
    public boolean isComplete() throws AnsibleTowerException {
        if(this.jobId == -1L) { throw new AnsibleTowerException("Job ID was not set"); }
        return connection.isJobCompleted(this.jobId, this.templateType);
    }

    @SuppressWarnings("unused")
    public boolean wasSuccessful() throws AnsibleTowerException {
        if(this.jobId == -1L) { throw new AnsibleTowerException("Job ID was not set"); }
        return !connection.isJobFailed(this.jobId, this.templateType);
    }

    @SuppressWarnings("unused")
    public Vector<String> getLogs() throws AnsibleTowerException {
        if(this.jobId == -1L) { throw new AnsibleTowerException("Job ID was not set"); }
        return this.connection.getLogEvents(this.jobId, this.templateType);
    }

    public HashMap<String, String> getExports() throws AnsibleTowerException {
        if(this.jobId == -1L) { throw new AnsibleTowerException("Job ID was not set"); }
        return this.connection.getJenkinsExports();
    }

    public void cancelJob() throws AnsibleTowerException {
        if(this.jobId == -1L) { throw new AnsibleTowerException("Job ID was not set"); }
        this.connection.cancelJob(this.jobId, this.templateType);
        this.connection.releaseToken();
    }

    public void releaseToken() throws AnsibleTowerException {
        if(this.jobId == -1) { throw new AnsibleTowerException("Job ID was not set"); }
        this.connection.releaseToken();
    }
}