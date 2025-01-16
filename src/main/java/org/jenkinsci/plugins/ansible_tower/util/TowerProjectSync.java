package org.jenkinsci.plugins.ansible_tower.util;

import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Vector;

public class TowerProjectSync implements Serializable {
    private TowerConnector connection = null;
    private TowerProject projectReference = null;
    private JSONObject syncData = null;
    private long lastLogId = 0L;

    public TowerProjectSync(TowerConnector connection, TowerProject projectReference) throws AnsibleTowerException {
        this.connection = connection;
        this.projectReference = projectReference;

        HttpResponse response = connection.makeRequest(connection.POST, projectReference.getProjectSyncURL(), null, false);
        if (response.getStatusLine().getStatusCode() != 202 && response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unexpected error code returned when launching project sync (" + response.getStatusLine().getStatusCode() + ")");
        }
        try {
            String json = EntityUtils.toString(response.getEntity());
            this.syncData = JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleTowerException("Unable to read project sync launch response and convert it into json: " + ioe.getMessage());
        }
    }

    private void loadSync(int method)  throws AnsibleTowerException {
        HttpResponse response = this.connection.makeRequest(method, syncData.getString("url"), null, false);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unexpected error code returned when loading project sync (" + response.getStatusLine().getStatusCode() + ")");
        }
        try {
            String json = EntityUtils.toString(response.getEntity());
            this.syncData = JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleTowerException("Unable to read project sync response and convert it into json: " + ioe.getMessage());
        }
    }

    @SuppressWarnings("unused")
    public boolean isComplete() throws AnsibleTowerException {
        loadSync(connection.GET);
        if(
                !syncData.containsKey("finished") ||
                syncData.getString("finished") == null || syncData.getString("finished").equalsIgnoreCase("null")
        ) {
            return false;
        } else {
            return true;
        }
    }

    @SuppressWarnings("unused")
    public boolean wasSuccessful() throws AnsibleTowerException {
        loadSync(connection.GET);
        if(syncData.containsKey("failed")) {
            return !syncData.getBoolean("failed");
        }
        throw new AnsibleTowerException("Did not get a failed status from project sync!");
    }

    @SuppressWarnings("unused")
    public Vector<String> getLogs() throws AnsibleTowerException {
        Vector<String> events = new Vector<String>();
        boolean keepChecking = true;
        while(keepChecking) {
            String apiURL = syncData.getJSONObject("related").getString("events") +"?id__gt="+ this.lastLogId;
            HttpResponse response = connection.makeRequest(connection.GET, apiURL, null, false);

            if (response.getStatusLine().getStatusCode() == 200) {
                JSONObject responseObject;
                String json;
                try {
                    json = EntityUtils.toString(response.getEntity());
                    responseObject = JSONObject.fromObject(json);
                } catch (IOException ioe) {
                    throw new AnsibleTowerException("Unable to read project sync event response and convert it into json: " + ioe.getMessage());
                }

                if(responseObject.containsKey("next") && responseObject.getString("next") == null || responseObject.getString("next").equalsIgnoreCase("null")) {
                    keepChecking = false;
                }
                if (responseObject.containsKey("results")) {
                    for (Object anEvent : responseObject.getJSONArray("results")) {
                        long eventId = ((JSONObject) anEvent).getLong("id");
                        String stdOut = ((JSONObject) anEvent).getString("stdout");
                        events.addAll(connection.logLine(stdOut));
                        if (eventId > this.lastLogId) { this.lastLogId = eventId; }
                    }
                }
            } else {
                throw new AnsibleTowerException("Unexpected error code returned when getting project sync events (" + response.getStatusLine().getStatusCode() + ")");
            }
        }
        return events;
    }

    public void cancelSync() throws AnsibleTowerException {
        HttpResponse response = this.connection.makeRequest(connection.POST, syncData.getJSONObject("related").getString("cancel"), null, false);
        this.connection.releaseToken();
        // 202 is the expected response so we can ignore this
        // 405 can mean that the project sync can't be canceled
        if (response.getStatusLine().getStatusCode() != 202 && response.getStatusLine().getStatusCode() != 405) {
            throw new AnsibleTowerException("Unexpected response code from project sync cancel ("+ response.getStatusLine().getStatusCode() +")");
        }
    }

    public String getURL() {
        return connection.getURL() +"/#/jobs/project/"+ syncData.getLong("id");
    }

    public long getID() {
        return syncData.getLong("id");
    }

    public void releaseToken() throws AnsibleTowerException {
        this.connection.releaseToken();
    }
}