package org.jenkinsci.plugins.ansible_aap.util;

import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.ansible_aap.exceptions.AnsibleAAPException;
import org.jenkinsci.plugins.ansible_aap.exceptions.AnsibleAAPItemDoesNotExist;

import java.io.IOException;
import java.io.Serializable;

public class AAPProject implements Serializable {
    String projectName;
    String apiEndPoint = "/projects/";
    String projectID = "";
    AAPConnector myConnector = null;
    JSONObject projectData = null;
    JSONObject updateResponse = null;

    public AAPProject(String projectName, AAPConnector myConnector) throws AnsibleAAPException {
        this.projectName = projectName;
        this.myConnector = myConnector;
        if(projectName == null || projectName.isEmpty()) {
            throw new AnsibleAAPException("Template can not be null");
        }

        try {
            this.projectID = myConnector.convertPotentialStringToID(projectName, this.apiEndPoint);
        } catch(AnsibleAAPItemDoesNotExist atidne) {
            throw new AnsibleAAPException("Project "+ projectName +" does not exist in aap");
        } catch(AnsibleAAPException ate) {
            throw new AnsibleAAPException("Unable to find project "+ projectName +": "+ ate.getMessage());
        }

        // Now that we have an ID, get the project from its ID.
        HttpResponse response;
        try {
            response = myConnector.makeRequest(myConnector.GET, apiEndPoint + projectID + "/", null, false);
        } catch(AnsibleAAPException e) {
            throw new AnsibleAAPException("Failed to load project information for "+ projectName +": "+ e.getMessage());
        }
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleAAPException("Unexpected error code returned when getting project (" + response.getStatusLine().getStatusCode() + ")");
        }
        try {
            String json = EntityUtils.toString(response.getEntity());
            projectData = JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleAAPException("Unable to read project response and convert it into json: " + ioe.getMessage());
        }
    }

    public String getProjectSyncURL() {
        return this.projectData.getJSONObject("related").getString("update");
    }

    public boolean canUpdate() throws AnsibleAAPException {
        if(updateResponse == null) {
            if (!projectData.containsKey("related") || !projectData.getJSONObject("related").containsKey("update")) {
                return false;
            }

            HttpResponse response = myConnector.makeRequest(myConnector.GET, projectData.getJSONObject("related").getString("update"), null, false);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new AnsibleAAPException("Unexpected error code return when getting project update (" + response.getStatusLine().getStatusCode() + ")");
            }
            try {
                this.updateResponse = JSONObject.fromObject(EntityUtils.toString(response.getEntity()));
            } catch (IOException ioe) {
                throw new AnsibleAAPException("Unable to read project update response and convert it into json: " + ioe.getMessage());
            }
        }
        return this.updateResponse.getBoolean("can_update");
    }

    public boolean updateRevision(String revision) throws AnsibleAAPException {
        // Attempt to update the project ID

        String finalEndPoint = this.apiEndPoint + projectID +"/";
        JSONObject patchBody = new JSONObject();
        patchBody.put("scm_branch", revision);

        HttpResponse response;
        try {
            response = myConnector.makeRequest(myConnector.PATCH, finalEndPoint, patchBody, false);
        } catch(AnsibleAAPException e) {
            throw new AnsibleAAPException("Failed to update project revision for "+ projectName +": "+ e.getMessage());
        }
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleAAPException("Unexpected response code returned when updating project (" + response.getStatusLine().getStatusCode() + ")");
        }

        // If we made it down here we were successful so we can return
        return true;
    }

    public AAPProjectSync sync() throws AnsibleAAPException {
        AAPProjectSync mySync = new AAPProjectSync(this.myConnector, this);
        return mySync;
    }

    public void releaseToken() throws AnsibleAAPException {
        myConnector.releaseToken();
    }
}
