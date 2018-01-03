package org.jenkinsci.plugins.ansible_tower.util;

/*
    This class handles all of the connections (api calls) to Tower itself
 */

import com.google.common.net.HttpHeaders;
import net.sf.json.JSONArray;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.*;

import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerItemDoesNotExist;

public class TowerConnector {
    private static final int GET = 1;
    private static final int POST = 2;
    public static final String JOB_TEMPLATE_TYPE = "job";
    public static final String WORKFLOW_TEMPLATE_TYPE = "workflow";
    private static final String ARTIFACTS = "artifacts";

    private String url = null;
    private String username = null;
    private String password = null;
    private boolean trustAllCerts = true;
    private TowerLogger logger = new TowerLogger();
    private Vector<Integer> displayedEvents = new Vector<Integer>();
    private Vector<Integer> displayedWorkflowNode = new Vector<Integer>();
    private boolean logTowerEvents = false;
    private PrintStream jenkinsLogger = null;
    private boolean removeColor = true;
    private HashMap<String, String> jenkinsExports = new HashMap<String, String>();

    public TowerConnector(String url, String username, String password) {
        this(url, username, password, false, false);
    }

    public TowerConnector(String url, String username, String password, Boolean trustAllCerts, Boolean debug) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.trustAllCerts = trustAllCerts;
        this.setDebug(debug);
        logger.logMessage("Creating a test connector with "+ username +"@"+ url);
    }

    public void setTrustAllCerts(boolean trustAllCerts) {
        this.trustAllCerts = trustAllCerts;
    }
    public void setLogTowerEvents(boolean logTowerEvents) { this.logTowerEvents = logTowerEvents; }
    public void setJenkinsLogger(PrintStream jenkinsLogger) { this.jenkinsLogger = jenkinsLogger;}
    public void setDebug(boolean debug) {
        logger.setDebugging(debug);
    }
    public void setRemoveColor(boolean removeColor) { this.removeColor = removeColor;}
    public HashMap<String, String> getJenkinsExports() { return jenkinsExports; }

    private DefaultHttpClient getHttpClient() throws AnsibleTowerException {
        if(trustAllCerts) {
            logger.logMessage("Forcing cert trust");
            TrustingSSLSocketFactory sf;
            try {
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(null, null);
                sf = new TrustingSSLSocketFactory(trustStore);
            } catch(Exception e) {
                throw new AnsibleTowerException("Unable to create trusting SSL socket factory");
            }
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } else {
            return new DefaultHttpClient();
        }
    }

    private HttpResponse makeRequest(int requestType, String endpoint) throws AnsibleTowerException {
        return makeRequest(requestType, endpoint, null);
    }

    private HttpResponse makeRequest(int requestType, String endpoint, JSONObject body) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        // Parse the URL
        URI myURI;
        try {
            myURI = new URI(url+endpoint);
        } catch(Exception e) {
            throw new AnsibleTowerException("URL issue: "+ e.getMessage());
        }

        logger.logMessage("building request to "+ myURI.toString());

        HttpUriRequest request;
        if(requestType == GET) {
            request = new HttpGet(myURI);
        } else if(requestType ==  POST) {
            HttpPost myPost = new HttpPost(myURI);
            if(body != null && !body.isEmpty()) {
                try {
                    StringEntity bodyEntity = new StringEntity(body.toString());
                    myPost.setEntity(bodyEntity);
                } catch(UnsupportedEncodingException uee) {
                    throw new AnsibleTowerException("Unable to encode body as JSON: "+ uee.getMessage());
                }
            }
            request = myPost;
            request.setHeader("Content-Type", "application/json");
        } else {
            throw new AnsibleTowerException("The requested method is unknown");
        }


        if(this.username != null || this.password != null) {
            logger.logMessage("Adding auth for "+ this.username);
            String auth = this.username + ":" + this.password;
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
            String authHeader = "Basic " + new String(encodedAuth, Charset.forName("UTF-8"));
            request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }

        DefaultHttpClient httpClient = getHttpClient();
        HttpResponse response;
        try {
            response = httpClient.execute(request);
        } catch(Exception e) {
            throw new AnsibleTowerException("Unable to make tower request: "+ e.getMessage());
        }

        if(response.getStatusLine().getStatusCode() == 404) {
            throw new AnsibleTowerItemDoesNotExist("The item does not exist");
        } else if(response.getStatusLine().getStatusCode() == 401) {
            throw new AnsibleTowerException("Username/password invalid");
        }

        return response;
    }


    public void testConnection() throws AnsibleTowerException {
        if(url == null) { throw new AnsibleTowerException("The URL is undefined"); }

        HttpResponse response = makeRequest(GET, "/api/v1/ping/");

        if(response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unexpected error code returned from test connection ("+ response.getStatusLine().getStatusCode() +")");
        }
        logger.logMessage("Connection successfully tested");
    }

    public String convertPotentialStringToID(String idToCheck, String api_endpoint) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        try {
            Integer.parseInt(idToCheck);
            // We got an ID so lets see if we can load that item
            HttpResponse response = makeRequest(GET, api_endpoint + idToCheck +"/");
            JSONObject responseObject;
            try {
                responseObject = JSONObject.fromObject(EntityUtils.toString(response.getEntity()));
                if(!responseObject.containsKey("id")) {
                    throw new AnsibleTowerItemDoesNotExist("Did not get an ID back from the request");
                }
            } catch (IOException ioe) {
                throw new AnsibleTowerException(ioe.getMessage());
            }
            return idToCheck;
        } catch(NumberFormatException nfe) {
            // We were probably given a name, lets try and resolve the name to an ID
            HttpResponse response = makeRequest(GET, api_endpoint);
            JSONObject responseObject;
            try {
                responseObject = JSONObject.fromObject(EntityUtils.toString(response.getEntity()));
            } catch (IOException ioe) {
                throw new AnsibleTowerException("Unable to convert response for all items into json: " + ioe.getMessage());
            }
            // If we didn't get results, fail
            if(!responseObject.containsKey("results")) {
                throw new AnsibleTowerException("Response for items does not contain results");
            }

            // Start with an invalid id
            int foundID = -1;
            // Loop over the results, if one of the items has the name copy its ID
            // If there are more than one job with the same name, fail
            for(Object returnedItem : responseObject.getJSONArray("results")) {
                if(((JSONObject) returnedItem).getString("name").equals(idToCheck)) {
                    if(foundID != -1) {
                        throw new AnsibleTowerException("The item "+ idToCheck +" is not unique");
                    } else {
                        foundID = ((JSONObject) returnedItem).getInt("id");
                    }
                }
            }

            // If we found no name, fail
            if(foundID == -1) {
                throw new AnsibleTowerItemDoesNotExist("Unable to find item named "+ idToCheck);
            }

            // Turn the single jobID we found into the jobTemplate
            return ""+ foundID;
        }

    }

    public JSONObject getJobTemplate(String jobTemplate, String templateType) throws AnsibleTowerException {
        if(jobTemplate == null || jobTemplate.isEmpty()) {
            throw new AnsibleTowerException("Template can not be null");
        }

        checkTemplateType(templateType);
        String apiEndPoint = "/api/v1/job_templates/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) {
            apiEndPoint = "/api/v1/workflow_job_templates/";
        }

        try {
            jobTemplate = convertPotentialStringToID(jobTemplate, apiEndPoint);
        } catch(AnsibleTowerItemDoesNotExist atidne) {
            String ucTemplateType = templateType.replaceFirst(templateType.substring(0,1), templateType.substring(0,1).toUpperCase());
            throw new AnsibleTowerException(ucTemplateType +" template does not exist in tower");
        } catch(AnsibleTowerException ate) {
            throw new AnsibleTowerException("Unable to find "+ templateType +" template: "+ ate.getMessage());
        }

        // Now get the job template to we can check the options being passed in
        HttpResponse response = makeRequest(GET, apiEndPoint + jobTemplate + "/");
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unexpected error code returned when getting template (" + response.getStatusLine().getStatusCode() + ")");
        }
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
            return JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleTowerException("Unable to read template response and convert it into json: " + ioe.getMessage());
        }
    }


    public int submitTemplate(int jobTemplate, String extraVars, String limit, String jobTags, String inventory, String credential, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        String apiEndPoint = "/api/v1/job_templates/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) {
            apiEndPoint = "/api/v1/workflow_job_templates/";
        }

        JSONObject postBody = new JSONObject();
        // I decided not to check if these were integers.
        // This way, Tower can throw an error if it needs to
        // And, in the future, if you can reference objects in tower via a tag/name we don't have to undo work here
        if(inventory != null && !inventory.isEmpty()) {
            try {
                inventory = convertPotentialStringToID(inventory, "/api/v1/inventories/");
            } catch(AnsibleTowerItemDoesNotExist atidne) {
                throw new AnsibleTowerException("Inventory "+ inventory +" does not exist in tower");
            } catch(AnsibleTowerException ate) {
                throw new AnsibleTowerException("Unable to find inventory: "+ ate.getMessage());
            }
            postBody.put("inventory", inventory);
        }
        if(credential != null && !credential.isEmpty()) {
            try {
                credential = convertPotentialStringToID(credential, "/api/v1/credentials/");
            } catch(AnsibleTowerItemDoesNotExist ateide) {
                throw new AnsibleTowerException("Credential "+ credential +" does not exist in tower");
            } catch(AnsibleTowerException ate) {
                throw new AnsibleTowerException("Unable to find credential "+ credential +": "+ ate.getMessage());
            }
            postBody.put("credential", credential);
        }
        if(limit != null && !limit.isEmpty()) {
            postBody.put("limit", limit);
        }
        if(jobTags != null && !jobTags.isEmpty()) {
            postBody.put("job_tags", jobTags);
        }
        if(extraVars != null && !extraVars.isEmpty()) {
            postBody.put("extra_vars", extraVars);
        }
        HttpResponse response = makeRequest(POST, apiEndPoint + jobTemplate + "/launch/", postBody);

        if(response.getStatusLine().getStatusCode() == 201) {
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch (IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: " + ioe.getMessage());
            }

            if (responseObject.containsKey("id")) {
                return responseObject.getInt("id");
            }
            logger.logMessage(json);
            throw new AnsibleTowerException("Did not get an ID from the request. Template response can be found in the jenkins.log");
        } else if(response.getStatusLine().getStatusCode() == 400) {
            String json = null;
            JSONObject responseObject = null;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(Exception e) {
                logger.logMessage("Unable to parse 400 respomnse from json to get details: "+ e.getMessage());
                logger.logMessage(json);
            }

            /*
                Types of things that might come back:
                {"extra_vars":["Must be valid JSON or YAML."],"variables_needed_to_start":["'my_var' value missing"]}
                {"credential":["Invalid pk \"999999\" - object does not exist."]}
                {"inventory":["Invalid pk \"99999999\" - object does not exist."]}

                Note: we are only testing for extra_vars as the other items should be checked during convertPotentialStringToID
            */

            if(responseObject != null && responseObject.containsKey("extra_vars")) {
                throw new AnsibleTowerException("Extra vars are bad: "+ responseObject.getString("extra_vars"));
            } else {
                throw new AnsibleTowerException("Tower received a bad request (400 response code)\n" + json);
            }
        } else {
            throw new AnsibleTowerException("Unexpected error code returned ("+ response.getStatusLine().getStatusCode() +")");
        }
    }

    public void checkTemplateType(String templateType) throws AnsibleTowerException {
        if(templateType.equalsIgnoreCase(JOB_TEMPLATE_TYPE)) { return; }
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) { return; }
        throw new AnsibleTowerException("Template type can only be '"+ JOB_TEMPLATE_TYPE +"' or '"+ WORKFLOW_TEMPLATE_TYPE+"'");
    }

    public boolean isJobCommpleted(int jobID, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        String apiEndpoint = "/api/v1/jobs/"+ jobID +"/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) { apiEndpoint = "/api/v1/workflow_jobs/"+ jobID +"/"; }
        HttpResponse response = makeRequest(GET, apiEndpoint);

        if(response.getStatusLine().getStatusCode() == 200) {
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            if (responseObject.containsKey("finished")) {
                String finished = responseObject.getString("finished");
                if(finished == null || finished.equalsIgnoreCase("null")) {
                    return false;
                } else {
                    // Since we were finished we will now also check for stats
                    if(responseObject.containsKey(ARTIFACTS)) {
                        logger.logMessage("Processing artifacts");
                        JSONObject artifacts = responseObject.getJSONObject(ARTIFACTS);
                        if(artifacts.containsKey("JENKINS_EXPORT")) {
                            JSONArray exportVariables = artifacts.getJSONArray("JENKINS_EXPORT");
                            Iterator<JSONObject> listIterator = exportVariables.iterator();
                            while(listIterator.hasNext()) {
                                JSONObject entry = listIterator.next();
                                Iterator<String> keyIterator = entry.keys();
                                while(keyIterator.hasNext()) {
                                    String key = keyIterator.next();
                                    jenkinsExports.put(key, entry.getString(key));
                                }
                            }
                        }
                    }
                    return true;
                }
            }
            logger.logMessage(json);
            throw new AnsibleTowerException("Did not get a failed status from the request. Job response can be found in the jenkins.log");
        } else {
            throw new AnsibleTowerException("Unexpected error code returned (" + response.getStatusLine().getStatusCode() + ")");
        }
    }


    public void logEvents(int jobID, String templateType, boolean importWorkflowChildLogs) throws AnsibleTowerException {
        checkTemplateType(templateType);
        if(templateType.equalsIgnoreCase(JOB_TEMPLATE_TYPE)) {
            logJobEvents(jobID);
        } else if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)){
            logWorkflowEvents(jobID, importWorkflowChildLogs);
        } else {
            throw new AnsibleTowerException("Tower Connector does not know how to log events for a "+ templateType);
        }
    }

    private static String UNIFIED_JOB_TYPE = "unified_job_type";
    private static String UNIFIED_JOB_TEMPLATE = "unified_job_template";

    private void logWorkflowEvents(int jobID, boolean importWorkflowChildLogs) throws AnsibleTowerException {
        HttpResponse response = makeRequest(GET, "/api/v1/workflow_jobs/"+ jobID +"/workflow_nodes/");

        if(response.getStatusLine().getStatusCode() == 200) {
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            logger.logMessage(json);

            if(responseObject.containsKey("results")) {
                for(Object anEventObject : responseObject.getJSONArray("results")) {
                    JSONObject anEvent = (JSONObject) anEventObject;
                    Integer eventId = anEvent.getInt("id");
                    if(displayedWorkflowNode.contains(eventId)) { continue; }

                    if(!anEvent.containsKey("summary_fields")) { continue; }

                    JSONObject summaryFields = anEvent.getJSONObject("summary_fields");
                    if(!summaryFields.containsKey("job")) { continue; }
                    if(!summaryFields.containsKey(UNIFIED_JOB_TEMPLATE)) { continue; }

                    JSONObject templateType = summaryFields.getJSONObject(UNIFIED_JOB_TEMPLATE);
                    if(!templateType.containsKey(UNIFIED_JOB_TYPE)) { continue; }

                    JSONObject job = summaryFields.getJSONObject("job");
                    if(
                            !job.containsKey("status") ||
                            job.getString("status").equalsIgnoreCase("running") ||
                            job.getString("status").equalsIgnoreCase("pending")
                    ) {
                        continue;
                    }

                    displayedWorkflowNode.add(eventId);
                    jenkinsLogger.println(job.getString("name") +" => "+ job.getString("status") +" "+ this.getJobURL(job.getInt("id"), JOB_TEMPLATE_TYPE));

                    if(importWorkflowChildLogs) {
                        if(templateType.getString(UNIFIED_JOB_TYPE).equalsIgnoreCase("job")) {
                            // We only need to call this once because the job is completed at this point
                            logJobEvents(job.getInt("id"));
                        } else if(templateType.getString(UNIFIED_JOB_TYPE).equalsIgnoreCase("project_update")) {
                            logProjectSync(job.getInt("id"));
                        } else if(templateType.getString(UNIFIED_JOB_TYPE).equalsIgnoreCase("inventory_update")) {
                            logInventorySync(job.getInt("id"));
                        } else {
                            jenkinsLogger.println("Unknown job type in workflow: "+ templateType.getString(UNIFIED_JOB_TYPE));
                        }
                    }
                    // Print two spaces to put some space between this and the next task.
                    jenkinsLogger.println("");
                    jenkinsLogger.println("");
                }
            }
        } else {
            throw new AnsibleTowerException("Unexpected error code returned ("+ response.getStatusLine().getStatusCode() +")");
        }

    }

    private void logLine(String output) throws AnsibleTowerException {
        String[] lines = output.split("\\r\\n");
        for(String line : lines) {
            if(removeColor) {
                // This regex was found on https://stackoverflow.com/questions/14652538/remove-ascii-color-codes
                line = removeColor(line);
            }
            if(logTowerEvents) {
                jenkinsLogger.println(line);
            }
            // Even if we don't log, we are going to see if this line contains the string JENKINS_EXPORT VAR=value
            if(line.matches("^.*JENKINS_EXPORT.*$")) {
                // The value might have some ansi color on it so we need to force the removal  of it
                String[] entities = removeColor(line).split("=", 2);
                entities[0] = entities[0].replaceAll(".*JENKINS_EXPORT ", "");
                entities[1] = entities[1].replaceAll("\"$", "");
                jenkinsExports.put( entities[0], entities[1]);
            }
        }
    }

    private String removeColor(String coloredLine) {
        return coloredLine.replaceAll("\u001B\\[[;\\d]*m", "");
    }


    private void logInventorySync(int syncID) throws AnsibleTowerException {
        String apiURL = "/api/v1/inventory_updates/"+ syncID +"/";
        HttpResponse response = makeRequest(GET, apiURL);
        if(response.getStatusLine().getStatusCode() == 200) {
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            logger.logMessage(json);

            if(responseObject.containsKey("result_stdout")) {
                logLine(responseObject.getString("result_stdout"));
            }
        } else {
            throw new AnsibleTowerException("Unexpected error code returned ("+ response.getStatusLine().getStatusCode() +")");
        }
    }


    private void logProjectSync(int syncID) throws AnsibleTowerException {
        String apiURL = "/api/v1/project_updates/"+ syncID +"/";
        HttpResponse response = makeRequest(GET, apiURL);
        if(response.getStatusLine().getStatusCode() == 200) {
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            logger.logMessage(json);

            if(responseObject.containsKey("result_stdout")) {
                logLine(responseObject.getString("result_stdout"));
            }
        } else {
            throw new AnsibleTowerException("Unexpected error code returned ("+ response.getStatusLine().getStatusCode() +")");
        }
    }

    private void logJobEvents(int jobID) throws AnsibleTowerException {
        String apiURL = "/api/v1/jobs/" + jobID + "/job_events/";
        HttpResponse response = makeRequest(GET, apiURL);

        if(response.getStatusLine().getStatusCode() == 200) {
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            logger.logMessage(json);

            if(responseObject.containsKey("results")) {
                for(Object anEvent : responseObject.getJSONArray("results")) {
                    Integer eventId = ((JSONObject) anEvent).getInt("id");
                    String stdOut = ((JSONObject) anEvent).getString("stdout");
                    if(!displayedEvents.contains(eventId)) {
                        displayedEvents.add(eventId);
                        logLine(stdOut);
                    }
                }
            }
        } else {
            throw new AnsibleTowerException("Unexpected error code returned ("+ response.getStatusLine().getStatusCode() +")");
        }
    }

    public boolean isJobFailed(int jobID, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        String apiEndPoint = "/api/v1/jobs/"+ jobID +"/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) { apiEndPoint = "/api/v1/workflow_jobs/"+ jobID +"/"; }
        HttpResponse response = makeRequest(GET, apiEndPoint);

        if(response.getStatusLine().getStatusCode() == 200) {
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            if (responseObject.containsKey("failed")) {
                return responseObject.getBoolean("failed");
            }
            logger.logMessage(json);
            throw new AnsibleTowerException("Did not get a failed status from the request. Job response can be found in the jenkins.log");
        } else {
            throw new AnsibleTowerException("Unexpected error code returned (" + response.getStatusLine().getStatusCode() + ")");
        }
    }

    public String getJobURL(int myJobID, String templateType) {
        String returnURL = url +"/#/";
        if (templateType.equalsIgnoreCase(TowerConnector.JOB_TEMPLATE_TYPE)) {
            returnURL += "jobs";
        } else {
            returnURL += "workflows";
        }
        returnURL += "/"+ myJobID;
        return returnURL;
    }
}
