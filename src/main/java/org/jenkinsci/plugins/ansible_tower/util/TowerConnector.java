package org.jenkinsci.plugins.ansible_tower.util;

/*
    This class handles all of the connections (api calls) to Tower itself
 */

import com.google.common.net.HttpHeaders;
import net.sf.json.JSONArray;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerDoesNotSupportAuthtoken;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
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
    private static String API_VERSION = "v2";

    private String authToken = null;
    private String url = null;
    private String username = null;
    private String password = null;
    private String oauthToken = null;
    private TowerVersion towerVersion = null;
    private boolean trustAllCerts = true;
    private TowerLogger logger = new TowerLogger();
    HashMap<Integer, Integer> logIdForWorkflows = new HashMap<Integer, Integer>();
    HashMap<Integer, Integer> logIdForJobs = new HashMap<Integer, Integer>();

    private boolean logTowerEvents = false;
    private PrintStream jenkinsLogger = null;
    private boolean removeColor = true;
    private HashMap<String, String> jenkinsExports = new HashMap<String, String>();


    public TowerConnector(String url, String username, String password) { this(url, username, password, null, false, false); }

    public TowerConnector(String url, String username, String password, String oauthToken, Boolean trustAllCerts, Boolean debug) {
        // Credit to https://stackoverflow.com/questions/7438612/how-to-remove-the-last-character-from-a-string
        if(url != null && url.length() > 0 && url.charAt(url.length() - 1) == '/') {
            url = url.substring(0, (url.length() - 1));
        }
        this.url = url;
        this.username = username;
        this.password = password;
        this.oauthToken = oauthToken;
        this.trustAllCerts = trustAllCerts;
        this.setDebug(debug);
        try {
            this.getVersion();
        } catch(AnsibleTowerException ate) {
            logger.logMessage("Failed to get connection to get version; auth errors may ensue "+ ate);
        }
        logger.logMessage("Created a connector with "+ username +"@"+ url);
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

    private String buildEndpoint(String endpoint) {
        String full_endpoint = "/api/"+ API_VERSION;
        if(!endpoint.startsWith("/")) { full_endpoint += "/"; }
        full_endpoint += endpoint;
        return full_endpoint;
    }

    private HttpResponse makeRequest(int requestType, String endpoint) throws AnsibleTowerException {
        return makeRequest(requestType, endpoint, null, false);
    }

    private HttpResponse makeRequest(int requestType, String endpoint, JSONObject body) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        return makeRequest(requestType, endpoint, body, false);
    }

    private HttpResponse makeRequest(int requestType, String endpoint, JSONObject body, boolean noAuth) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        // Parse the URL
        URI myURI;
        try {
            myURI = new URI(url+buildEndpoint(endpoint));
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


        if(!noAuth) {
            if (this.oauthToken != null) {
                logger.logMessage("Adding oauth token");
                request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.oauthToken);
            } else if (this.username != null || this.password != null) {
                if (this.authToken == null) {
                    try {
                        this.authToken = getAuthToken();
                    } catch (AnsibleTowerDoesNotSupportAuthtoken dneat) {
                        logger.logMessage("Tower does not support authtoken, reverting to basic auth");
                        logger.logMessage(dneat.getMessage());
                        this.authToken = "BasicAuth";
                    }
                }

                if (this.authToken != null && !this.authToken.equals("BasicAuth")) {
                    logger.logMessage("Adding token auth for " + this.username);
                    if (this.towerVersion != null && (
                            // This is for actual Tower
                            this.towerVersion.is_greater_or_equal("3.3.0") || (
                                    // This is for AWX, too bad if you are running Tower 1.X or AWX v2 (which does not exist yet)
                                    !this.towerVersion.is_greater_or_equal("2.0.0") &&
                                            this.towerVersion.is_greater_or_equal("1.0.7")
                            )
                    )
                            ) {
                        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.authToken);
                    } else {
                        logger.logMessage("Setting legacy Token");
                        request.setHeader(HttpHeaders.AUTHORIZATION, "Token " + this.authToken);
                    }
                } else {
                    logger.logMessage("Adding basic auth for " + this.username);
                    request.setHeader(HttpHeaders.AUTHORIZATION, this.getBasicAuthString());
                }
            }
        }

        DefaultHttpClient httpClient = getHttpClient();
        HttpResponse response;
        try {
            response = httpClient.execute(request);
        } catch(Exception e) {
            throw new AnsibleTowerException("Unable to make tower request: "+ e.getMessage());
        }

        logger.logMessage("Request completed with ("+ response.getStatusLine().getStatusCode() +")");
        if(response.getStatusLine().getStatusCode() == 404) {
            throw new AnsibleTowerItemDoesNotExist("The item does not exist");
        } else if(response.getStatusLine().getStatusCode() == 401) {
            throw new AnsibleTowerException("Username/password invalid");
        }

        return response;
    }


    public void getVersion() throws AnsibleTowerException {
        // The version is housed on the poing page which is openly accessable
        HttpResponse response = makeRequest(GET, "ping/", null, true);
        if(response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unexpected error code returned from ping connection ("+ response.getStatusLine().getStatusCode() +")");
        }
        logger.logMessage("Ping page loaded");

        JSONObject responseObject;
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
            responseObject = JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleTowerException("Unable to read ping response and convert it into json: " + ioe.getMessage());
        }

        if (responseObject.containsKey("version")) {
            logger.logMessage("Successfully got version "+ responseObject.getString("version"));
            this.towerVersion = new TowerVersion(responseObject.getString("version"));
        }
    }

    public void testConnection() throws AnsibleTowerException {
        if(url == null) { throw new AnsibleTowerException("The URL is undefined"); }

        // We will run an unauthenticated test by the constructor calling the ping page so we can jump
        // straight into calling an authentication test

        // This will run an authentication test
        logger.logMessage("Testing authentication");
        HttpResponse response = makeRequest(GET, "jobs/");
        if(response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Failed to get authenticated connection ("+ response.getStatusLine().getStatusCode() +")");
        }
    }

    public String convertPotentialStringToID(String idToCheck, String api_endpoint) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        JSONObject foundItem = rawLookupByString(idToCheck, api_endpoint);
        logger.logMessage("Response from lookup: "+ foundItem.getString("id"));
        return foundItem.getString("id");
    }

    public JSONObject rawLookupByString(String idToCheck, String api_endpoint) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
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
            return responseObject;
        } catch(NumberFormatException nfe) {

            HttpResponse response = null;
            try {
                // We were probably given a name, lets try and resolve the name to an ID
                response = makeRequest(GET, api_endpoint + "?name=" + URLEncoder.encode(idToCheck, "UTF-8"));
            } catch(UnsupportedEncodingException e) {
                throw new AnsibleTowerException("Unable to encode item name for lookup");
            }

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

            // Loop over the results, if one of the items has the name copy its ID
            // If there are more than one job with the same name, fail
            if(responseObject.getInt("count") == 0) {
                throw new AnsibleTowerException("Unable to get any results when looking up "+ idToCheck);
            } else if(responseObject.getInt("count") > 1) {
                throw new AnsibleTowerException("The item "+ idToCheck +" is not unique");
            } else {
                JSONObject foundItem = (JSONObject) responseObject.getJSONArray("results").get(0);
                return foundItem;
            }
        }
    }

    public JSONObject getJobTemplate(String jobTemplate, String templateType) throws AnsibleTowerException {
        if(jobTemplate == null || jobTemplate.isEmpty()) {
            throw new AnsibleTowerException("Template can not be null");
        }

        checkTemplateType(templateType);
        String apiEndPoint = "/job_templates/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) {
            apiEndPoint = "/workflow_job_templates/";
        }

        try {
            jobTemplate = convertPotentialStringToID(jobTemplate, apiEndPoint);
        } catch(AnsibleTowerItemDoesNotExist atidne) {
            String ucTemplateType = templateType.replaceFirst(templateType.substring(0,1), templateType.substring(0,1).toUpperCase());
            throw new AnsibleTowerException(ucTemplateType +" template does not exist in tower");
        } catch(AnsibleTowerException ate) {
            throw new AnsibleTowerException("Unable to find "+ templateType +" template: "+ ate.getMessage());
        }

        // Now get the job template so we can check the options being passed in
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


    private void processCredentials(String credential, JSONObject postBody) throws AnsibleTowerException {
        // Get the machine or vault credential types
        HttpResponse response = makeRequest(GET,"/credential_types/?or__kind=ssh&or__kind=vault");
        if(response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unable to lookup the credential types");
        }
        JSONObject responseObject;
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
            responseObject = JSONObject.fromObject(json);
        } catch(IOException ioe) {
            throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
        }

        if(responseObject.getInt("count") != 2) {
            throw new AnsibleTowerException("Unable to find both machine and vault credentials type");
        }

        int machine_credential_type = -1;
        int vault_credential_type = -1;
        JSONArray credentialTypesArray = responseObject.getJSONArray("results");
        Iterator<JSONObject> listIterator = credentialTypesArray.iterator();
        while(listIterator.hasNext()) {
            JSONObject aCredentialType = listIterator.next();
            if(aCredentialType.getString("kind").equalsIgnoreCase("ssh")) {
                machine_credential_type = aCredentialType.getInt("id");
            } else if(aCredentialType.getString("kind").equalsIgnoreCase("vault")) {
                vault_credential_type = aCredentialType.getInt("id");
            }
        }

        if (vault_credential_type == -1) {
            logger.logMessage("[ERROR]: Unable to find vault credential type");
        }
        if (machine_credential_type == -1) {
            logger.logMessage("[ERROR]: Unable to find machine credential type");
        }
        /*
            Credential can be a comma delineated list and in 2.3.x can come in three types:
                Machine credentials
                Vaiult credentials
                Extra credentials
                We are going:
                    Make a hash of the different types
                    Split the string on , and loop over each item
                    Find it in Tower and sort it into its type
         */
        HashMap<String, Vector<Integer>> credentials = new HashMap<String, Vector<Integer>>();
        credentials.put("vault", new Vector<Integer>());
        credentials.put("machine", new Vector<Integer>());
        credentials.put("extra", new Vector<Integer>());
        for(String credentialString : credential.split(","))  {
            try {
                JSONObject jsonCredential = rawLookupByString(credentialString, "/credentials/");
                String myCredentialType = null;
                int credentialTypeId = jsonCredential.getInt("credential_type");
                if (credentialTypeId == machine_credential_type) {
                    myCredentialType = "machine";
                } else if (credentialTypeId == vault_credential_type) {
                    myCredentialType = "vault";
                } else {
                    myCredentialType = "extra";
                }
                credentials.get(myCredentialType).add(jsonCredential.getInt("id"));
            } catch(AnsibleTowerItemDoesNotExist ateide) {
                throw new AnsibleTowerException("Credential "+ credentialString +" does not exist in tower");
            } catch(AnsibleTowerException ate) {
                throw new AnsibleTowerException("Unable to find credential "+ credentialString +": "+ ate.getMessage());
            }
        }

        /*
            Now that we have processed everything we have to decide which way to pass it into the API.
            Pre 3.3 there were three possible parameters:
                extra_vars, vault_credential, machine_credential
            Starting in 3.3 you can take the seperate parameters or you can pass them all as a single credential param

            The decision point will be wheter or not there is more than one machine or vault credential.
            This is because the old method is not deprecated but it can't handle more than machine/vault credential
         */
        if(credentials.get("machine").size() > 1 || credentials.get("vault").size() > 1) {
            // We need to pass as a new field
            JSONArray allCredentials = new JSONArray();
            allCredentials.addAll(credentials.get("machine"));
            allCredentials.addAll(credentials.get("vault"));
            allCredentials.addAll(credentials.get("extra"));
            postBody.put("credentials", allCredentials);
        } else {
            // We need to pass individual fields
            if(credentials.get("machine").size() > 0) { postBody.put("credential", credentials.get("machine").get(0)); }
            if(credentials.get("vault").size() > 0) { postBody.put("vault_credential", credentials.get("vault").get(0)); }
            if(credentials.get("extra").size() > 0) {
                JSONArray extraCredentials = new JSONArray();
                extraCredentials.addAll(credentials.get("extra"));
                postBody.put("extra_credentials", extraCredentials);
            }
        }

    }


    public int submitTemplate(int jobTemplate, String extraVars, String limit, String jobTags, String skipJobTags, String jobType, String inventory, String credential, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        String apiEndPoint = "/job_templates/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) {
            apiEndPoint = "/workflow_job_templates/";
        }

        JSONObject postBody = new JSONObject();
        // I decided not to check if these were integers.
        // This way, Tower can throw an error if it needs to
        // And, in the future, if you can reference objects in tower via a tag/name we don't have to undo work here
        if(inventory != null && !inventory.isEmpty()) {
            try {
                inventory = convertPotentialStringToID(inventory, "/inventories/");
            } catch(AnsibleTowerItemDoesNotExist atidne) {
                throw new AnsibleTowerException("Inventory "+ inventory +" does not exist in tower");
            } catch(AnsibleTowerException ate) {
                throw new AnsibleTowerException("Unable to find inventory: "+ ate.getMessage());
            }
            postBody.put("inventory", inventory);
        }
        if(credential != null && !credential.isEmpty()) {
            processCredentials(credential, postBody);
        }
        if(limit != null && !limit.isEmpty()) {
            postBody.put("limit", limit);
        }
        if(jobTags != null && !jobTags.isEmpty()) {
            postBody.put("job_tags", jobTags);
        }
        if(skipJobTags != null && !skipJobTags.isEmpty()) {
            postBody.put("skip_tags", skipJobTags);
        }
        if(jobType != null &&  !jobType.isEmpty()){
            postBody.put("job_type", jobType);
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
                logger.logMessage("Unable to parse 400 response from json to get details: "+ e.getMessage());
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

    public boolean isJobCompleted(int jobID, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        String apiEndpoint = "/jobs/"+ jobID +"/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) { apiEndpoint = "/workflow_jobs/"+ jobID +"/"; }
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

    /**
     * @deprecated
     * Use isJobCompleted
     */
    @Deprecated
    public boolean isJobCommpleted(int jobID, String templateType) throws AnsibleTowerException {
        return isJobCompleted(jobID, templateType);
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
        if(!this.logIdForWorkflows.containsKey(jobID)) { this.logIdForWorkflows.put(jobID, 0); }
        HttpResponse response = makeRequest(GET, "/workflow_jobs/"+ jobID +"/workflow_nodes/?id__gt="+this.logIdForWorkflows.get(jobID));

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
                        // Here we want to return. Otherwise we might "loose" things.
                        // For example, say there are three nodes in the pipeline.
                        // Node 1 takes a long time, Node 2 which runs in parallel is quick
                        // If Node 2 executes second and completed we will use the ID of node 2 as the next ID.
                        // Node 1 results will be lost because node 2 has already finished.
                        // Returning will prevent this from happening.
                        return;
                    }

                    if(eventId > this.logIdForWorkflows.get(jobID)) { this.logIdForWorkflows.put(jobID, eventId); }
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
        // These are not normal logs, so we don't need to paginate
        String apiURL = "/inventory_updates/"+ syncID +"/";
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
        // These are not normal logs, so we don't need to paginate
        String apiURL = "/project_updates/"+ syncID +"/";
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
        if(!this.logIdForJobs.containsKey(jobID)) { this.logIdForJobs.put(jobID, 0); }
        boolean keepChecking = true;
        while(keepChecking) {
            String apiURL = "/jobs/" + jobID + "/job_events/?id__gt="+ this.logIdForJobs.get(jobID);
            HttpResponse response = makeRequest(GET, apiURL);

            if (response.getStatusLine().getStatusCode() == 200) {
                JSONObject responseObject;
                String json;
                try {
                    json = EntityUtils.toString(response.getEntity());
                    responseObject = JSONObject.fromObject(json);
                } catch (IOException ioe) {
                    throw new AnsibleTowerException("Unable to read response and convert it into json: " + ioe.getMessage());
                }

                logger.logMessage(json);

                if(responseObject.containsKey("next") && responseObject.getString("next") == null || responseObject.getString("next").equalsIgnoreCase("null")) {
                    keepChecking = false;
                }
                if (responseObject.containsKey("results")) {
                    for (Object anEvent : responseObject.getJSONArray("results")) {
                        Integer eventId = ((JSONObject) anEvent).getInt("id");
                        String stdOut = ((JSONObject) anEvent).getString("stdout");
                        logLine(stdOut);
                        if (eventId > this.logIdForJobs.get(jobID)) {
                            this.logIdForJobs.put(jobID, eventId);
                        }
                    }
                }
            } else {
                throw new AnsibleTowerException("Unexpected error code returned (" + response.getStatusLine().getStatusCode() + ")");
            }
        }
    }

    public boolean isJobFailed(int jobID, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        String apiEndPoint = "/jobs/"+ jobID +"/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) { apiEndPoint = "/workflow_jobs/"+ jobID +"/"; }
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

    private String getBasicAuthString() {
        String auth = this.username + ":" + this.password;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
        return "Basic " + new String(encodedAuth, Charset.forName("UTF-8"));
    }

    private String getAuthToken() throws AnsibleTowerException {
        logger.logMessage("Getting auth token for "+ this.username);

        String tokenURI = url + this.buildEndpoint("/authtoken/");
        HttpPost tokenRequest = new HttpPost(tokenURI);
        tokenRequest.setHeader(HttpHeaders.AUTHORIZATION, this.getBasicAuthString());
        JSONObject body = new JSONObject();
        body.put("username", this.username);
        body.put("password", this.password);
        try {
            StringEntity bodyEntity = new StringEntity(body.toString());
            tokenRequest.setEntity(bodyEntity);
        } catch(UnsupportedEncodingException uee) {
            throw new AnsibleTowerException("Unable to encode body as JSON: "+ uee.getMessage());
        }

        tokenRequest.setHeader("Content-Type", "application/json");

        DefaultHttpClient httpClient = getHttpClient();
        HttpResponse response;
        try {
            response = httpClient.execute(tokenRequest);
        } catch(Exception e) {
            throw new AnsibleTowerException("Unable to make tower request for aRequest completed withuthtoken: "+ e.getMessage());
        }

        if(response.getStatusLine().getStatusCode() == 400) {
            throw new AnsibleTowerException("Username/password invalid");
        } else if(response.getStatusLine().getStatusCode() == 404) {
            throw new AnsibleTowerDoesNotSupportAuthtoken("Server does not have endpoint: " + tokenURI);
        } else if(response.getStatusLine().getStatusCode() != 200 && response.getStatusLine().getStatusCode() != 201) {
            throw new AnsibleTowerException("Unable to get auth token, server responded with ("+ response.getStatusLine().getStatusCode() +")");
        }

        JSONObject responseObject;
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
            responseObject = JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleTowerException("Unable to read response and convert it into json: " + ioe.getMessage());
        }

        if (responseObject.containsKey("token")) {
            return responseObject.getString("token");
        }
        logger.logMessage(json);
        throw new AnsibleTowerException("Did not get a token from the request. Template response can be found in the jenkins.log");
    }
}
