package org.jenkinsci.plugins.ansible_aap.util;

import org.jenkinsci.plugins.ansible_aap.exceptions.AnsibleAAPException;

import java.util.HashMap;
import java.util.Vector;
import java.io.Serializable;

public class AAPJob  implements Serializable {
    private long jobId = -1;
    private AAPConnector connection;
    private String templateType = null;
    private static final long serialVersionUID = -323790358606407805L;

    public AAPJob(AAPConnector connection) {
        this.connection = connection;
    }

    public void setTemplateType(String templateType) throws AnsibleAAPException {
        if (templateType == null || (!templateType.equalsIgnoreCase(AAPConnector.WORKFLOW_TEMPLATE_TYPE) && !templateType.equalsIgnoreCase(AAPConnector.JOB_TEMPLATE_TYPE))) {
            throw new AnsibleAAPException("Template type "+ templateType +" was invalid");
        }
        this.templateType = templateType;
    }
    public void setJobId(long jobId) { this.jobId = jobId; }
    public long getJobID() { return this.jobId; }

    @SuppressWarnings("unused")
    public boolean isComplete() throws AnsibleAAPException {
        if(this.jobId == -1L) { throw new AnsibleAAPException("Job ID was not set"); }
        return connection.isJobCompleted(this.jobId, this.templateType);
    }

    @SuppressWarnings("unused")
    public boolean wasSuccessful() throws AnsibleAAPException {
        if(this.jobId == -1L) { throw new AnsibleAAPException("Job ID was not set"); }
        return !connection.isJobFailed(this.jobId, this.templateType);
    }

    @SuppressWarnings("unused")
    public Vector<String> getLogs() throws AnsibleAAPException {
        if(this.jobId == -1L) { throw new AnsibleAAPException("Job ID was not set"); }
        return this.connection.getLogEvents(this.jobId, this.templateType);
    }

    public HashMap<String, String> getExports() throws AnsibleAAPException {
        if(this.jobId == -1L) { throw new AnsibleAAPException("Job ID was not set"); }
        return this.connection.getJenkinsExports();
    }

    public void cancelJob() throws AnsibleAAPException {
        if(this.jobId == -1L) { throw new AnsibleAAPException("Job ID was not set"); }
        this.connection.cancelJob(this.jobId, this.templateType);
        this.connection.releaseToken();
    }

    public void releaseToken() throws AnsibleAAPException {
        if(this.jobId == -1) { throw new AnsibleAAPException("Job ID was not set"); }
        this.connection.releaseToken();
    }
}