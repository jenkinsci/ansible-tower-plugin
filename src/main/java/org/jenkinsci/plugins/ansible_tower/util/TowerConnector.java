package org.jenkinsci.plugins.ansible_tower.util;

/*
    This class handles all of the connections (api calls) to Tower itself
 */

import com.google.common.net.HttpHeaders;
import net.sf.json.JSONArray;
import org.apache.http.client.methods.*;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerDoesNotSupportAuthToken;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerRefusesToGiveToken;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerException;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerTransientException;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerRequestException;

import java.io.*;
import java.net.URI;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;

import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.ansible_tower.exceptions.AnsibleTowerItemDoesNotExist;

public class TowerConnector implements Serializable {
    // If adding a new method, make sure to update getMethodName()
    public static final int GET = 1;
    public static final int POST = 2;
    public static final int PATCH = 3;
    public static final String JOB_TEMPLATE_TYPE = "job";
    public static final String WORKFLOW_TEMPLATE_TYPE = "workflow";
    public static final String API_BASE_PATH_LEGACY = "/api/v2";
    public static final String API_BASE_PATH_AAP_CONTROLLER = "/api/controller/v2";
    public static final String API_GATEWAY_TOKEN_ENDPOINT = "/api/gateway/v1/tokens/";
    private static final String ARTIFACTS = "artifacts";
    private static final int MAX_TRANSIENT_GATEWAY_RETRIES = 5;
    private static final long TRANSIENT_GATEWAY_RETRY_DELAY_MS = 10000L;

    private transient String authorizationHeader = null;
    private transient String oauthToken = null;
    private transient String oAuthTokenID = null;
    private transient String oAuthTokenBaseEndpoint = null;
    private String url = null;
    private String apiBasePath = API_BASE_PATH_LEGACY;
    private transient String username = null;
    private transient String password = null;
    private TowerVersion towerVersion = null;
    private boolean trustAllCerts = true;
    private boolean importChildWorkflowLogs = false;
    private TowerLogger logger = new TowerLogger();
    private HashMap<Long, Set<Long>> processedWorkflowNodeIds = new HashMap<Long, Set<Long>>();
    private HashMap<Long, Long> logIdForJobs = new HashMap<Long, Long>();
    private HashMap<Long, Boolean> completedJobFailures = new HashMap<Long, Boolean>();

    private boolean removeColor = true;
    private boolean getFullLogs = false;
    private HashMap<String, String> jenkinsExports = new HashMap<String, String>();

    public TowerConnector(String url, String username, String password) { this(url, username, password, null, false, false); }

    public TowerConnector(String url, String username, String password, String oauthToken, Boolean trustAllCerts, Boolean debug) {
        this(url, username, password, oauthToken, trustAllCerts, debug, API_BASE_PATH_LEGACY);
    }

    public TowerConnector(String url, String username, String password, String oauthToken, Boolean trustAllCerts, Boolean debug, String apiBasePath) {
        this.url = normalizeBaseURL(url);
        this.apiBasePath = normalizeApiBasePath(apiBasePath);
        this.username = username;
        this.password = password;
        this.oauthToken = oauthToken;
        this.trustAllCerts = trustAllCerts;
        this.setDebug(debug);
        try {
            this.getVersion();
            logger.info("Connected to Tower: version=" + this.towerVersion.getVersion());
        } catch(AnsibleTowerException ate) {
            logger.warning("Unable to determine Tower version: " + ate.getMessage());
        }
        logger.debug("Created Tower connector: url=" + url);
    }

    public void setTrustAllCerts(boolean trustAllCerts) {
        this.trustAllCerts = trustAllCerts;
    }
    public void setDebug(boolean debug) {
        logger.setDebugging(debug);
    }
    public void setConsole(PrintStream console) { logger.setConsole(console); }
    public void setRemoveColor(boolean removeColor) { this.removeColor = removeColor;}
    public void setGetWorkflowChildLogs(boolean importChildWorkflowLogs) { this.importChildWorkflowLogs = importChildWorkflowLogs; }
    public void setGetFullLogs(boolean getFullLogs) { this.getFullLogs = getFullLogs; }
    public HashMap<String, String> getJenkinsExports() { return jenkinsExports; }

    static String normalizeBaseURL(String baseURL) {
        if(baseURL != null) {
            baseURL = baseURL.trim();
            if(baseURL.length() > 0 && baseURL.charAt(baseURL.length() - 1) == '/') {
                baseURL = baseURL.substring(0, (baseURL.length() - 1));
            }
        }
        return baseURL;
    }

    static String normalizeApiBasePath(String apiBasePath) {
        if(apiBasePath == null || apiBasePath.trim().isEmpty()) {
            return API_BASE_PATH_LEGACY;
        }
        apiBasePath = apiBasePath.trim();
        if(!apiBasePath.startsWith("/")) {
            apiBasePath = "/" + apiBasePath;
        }
        if(apiBasePath.endsWith("/")) {
            apiBasePath = apiBasePath.substring(0, apiBasePath.length() - 1);
        }
        return apiBasePath;
    }

    private DefaultHttpClient getHttpClient() throws AnsibleTowerException {
        URI myURI = null;
        try {
            myURI = new URI(url);
        } catch(URISyntaxException urise) {
            throw new AnsibleTowerException("Unable to prase base url: "+ urise);
        }

        HttpParams params = new BasicHttpParams();
        configureHttpTimeouts(params);
        if(trustAllCerts && myURI.getScheme().equalsIgnoreCase("https")) {
            logger.warning("Ignoring the insecure trust-all-certificates setting; standard TLS validation is enforced");
        }
        return new DefaultHttpClient(params);
    }

    static void configureHttpTimeouts(HttpParams params) {
        HttpConnectionParams.setConnectionTimeout(params, 10000);
        ConnManagerParams.setTimeout(params, 10000L);
        HttpConnectionParams.setSoTimeout(params, 30000);
    }

    private String buildEndpoint(String endpoint) {
        return buildEndpoint(endpoint, this.apiBasePath);
    }

    static String buildEndpoint(String endpoint, String apiBasePath) {
        if(endpoint.startsWith("/api/")) { return endpoint; }

        String full_endpoint = normalizeApiBasePath(apiBasePath);
        if(!endpoint.startsWith("/")) { full_endpoint += "/"; }
        full_endpoint += endpoint;
        return full_endpoint;
    }

    static String buildOAuthTokenEndpoint(String apiBasePath) {
        if(API_BASE_PATH_AAP_CONTROLLER.equals(normalizeApiBasePath(apiBasePath))) {
            return API_GATEWAY_TOKEN_ENDPOINT;
        }
        return buildEndpoint("/tokens/", apiBasePath);
    }

    static StringEntity createJsonEntity(JSONObject body) {
        return new StringEntity(body.toString(), StandardCharsets.UTF_8);
    }

    static boolean isAAPControllerMode(String apiBasePath) {
        return API_BASE_PATH_AAP_CONTROLLER.equals(normalizeApiBasePath(apiBasePath));
    }

    static boolean shouldProbeOAuthSupport(String apiBasePath) {
        return !isAAPControllerMode(apiBasePath);
    }

    static String buildJobURL(String uiBaseURL, String apiBasePath, long jobID, String jobType) {
        uiBaseURL = normalizeBaseURL(uiBaseURL);
        jobType = normalizeJobType(jobType);

        if(isAAPControllerMode(apiBasePath)) {
            if("workflow_job".equals(jobType)) {
                return uiBaseURL + "/execution/jobs/workflow/" + jobID + "/output";
            } else if("project_update".equals(jobType)) {
                return uiBaseURL + "/execution/jobs/project_update/" + jobID + "/output";
            } else if("inventory_update".equals(jobType)) {
                return uiBaseURL + "/execution/jobs/inventory_update/" + jobID + "/output";
            }
            return uiBaseURL + "/execution/jobs/playbook/" + jobID + "/output";
        }

        if("workflow_job".equals(jobType)) {
            return uiBaseURL + "/#/jobs/workflow/" + jobID;
        } else if("project_update".equals(jobType)) {
            return uiBaseURL + "/#/jobs/project/" + jobID;
        } else if("inventory_update".equals(jobType)) {
            return uiBaseURL + "/#/jobs/inventory/" + jobID;
        }
        return uiBaseURL + "/#/jobs/" + jobID;
    }

    private static String normalizeJobType(String jobType) {
        if(jobType == null || jobType.trim().isEmpty()) {
            return JOB_TEMPLATE_TYPE;
        }
        jobType = jobType.trim().toLowerCase();
        if(WORKFLOW_TEMPLATE_TYPE.equals(jobType)) {
            return "workflow_job";
        }
        return jobType;
    }

    private HttpResponse makeRequest(int requestType, String endpoint) throws AnsibleTowerException {
        return makeRequest(requestType, endpoint, null, false);
    }

    private HttpResponse makeRequest(int requestType, String endpoint, JSONObject body) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        return makeRequest(requestType, endpoint, body, false);
    }

    public HttpResponse makeRequest(int requestType, String endpoint, JSONObject body, boolean noAuth) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        // Parse the URL
        URI myURI;
        try {
            myURI = new URI(url+buildEndpoint(endpoint));
        } catch(Exception e) {
            throw new AnsibleTowerException("URL issue: "+ e.getMessage());
        }

        logger.debug("Building "+ getMethodName(requestType) +" request to "+ myURI.toString());

        HttpUriRequest request;
        if(requestType == GET) {
            request = new HttpGet(myURI);
        } else if(requestType ==  POST || requestType == PATCH) {
            HttpEntityEnclosingRequestBase myRequest;
            if(requestType == POST) {
                myRequest = new HttpPost(myURI);
            } else {
                myRequest = new HttpPatch(myURI);
            }
            if (body != null && !body.isEmpty()) {
                myRequest.setEntity(createJsonEntity(body));
            }
            request = myRequest;
            request.setHeader("Content-Type", "application/json");
        } else {
            throw new AnsibleTowerException("The requested method is unknown");
        }

        // If we haven't determined auth yet we need to go get it
        if(!noAuth) {
            if(this.authorizationHeader == null) {
                // We dont' have an authorization header yet so we need to construct one
                logger.debug("Determining authorization headers");

                if(this.oauthToken != null) {
                    // First if we have an oauthToken we can just use it
                    logger.debug("Adding oauth bearer token from Jenkins");
                    this.authorizationHeader = "Bearer "+ this.oauthToken;
                } else if(this.username != null && this.password != null) {
                    // Second, if we have a username and a password we can try to go get a token

                    // AAP controller mode uses the gateway token endpoint directly. Legacy Tower/AWX still probes /api/o/.
                    if (!shouldProbeOAuthSupport(this.apiBasePath) || this.towerSupports("/api/o/")) {
                        logger.debug("Requesting an OAuth token");
                        try {
                            this.authorizationHeader = "Bearer " + this.getOAuthToken();
                        } catch(AnsibleTowerException ate) {
                            logger.warning("Unable to get OAuth token: " + ate.getMessage());
                        }
                    }

                    // Second, we will try to get a legacy authtoken if Tower supports if
                    if(this.authorizationHeader == null && this.towerSupports(this.buildEndpoint("/authtoken/"))) {
                        logger.debug("Requesting a legacy auth token");
                        try {
                            this.authorizationHeader = "Token " + this.getAuthToken();
                        } catch (AnsibleTowerException ate) {
                            logger.warning("Unable to get legacy auth token: " + ate.getMessage());
                        }
                    }

                    // Finally, we will revert to basic auth.
                    // There could be a case where someone allows basic auth to the API and
                    // Refuses oAuth token creation for LDAO based users.
                    // This would allow for that conditio
                    /* To test this scenario I created an AWX devel install and added this line:
                        ----------------------------------------------------------------
                        diff --git a/awx/main/models/oauth.py b/awx/main/models/oauth.py
                        index 51bb9be0e..b2b9d80aa 100644
                                --- a/awx/main/models/oauth.py
                                +++ b/awx/main/models/oauth.py
                        @@ -135,6 +135,7 @@ class OAuth2AccessToken(AbstractAccessToken):
                        return valid

                        def validate_external_users(self):
                        +        raise oauth2.AccessDeniedError('OAuth2 Tokens cannot be created')
                        if self.user and settings.ALLOW_OAUTH2_FOR_EXTERNAL_USERS is False:
                        external_account = get_external_account(self.user)
                        if external_account is not None:
                        ----------------------------------------------------------------
                        This made it impossible for any user to get an oAuth toekn
                        simulating what would happen to a user if they were an LDAP source and the option to
                        disable tokens for LDAP users were turned on.
                    */

                    if (this.authorizationHeader == null) {
                        logger.debug("Tower does not support authtoken or oauth, reverting to basic auth");
                        this.authorizationHeader = this.getBasicAuthString();
                    }
                } else {
                    throw new AnsibleTowerException("Auth is required for this call but no auth info exists");
                }

            }

            if(this.authorizationHeader == null) {
                throw new AnsibleTowerException("We should have gotten an authorization header but did not");
            }
            request.setHeader(HttpHeaders.AUTHORIZATION, this.authorizationHeader);
        }

        DefaultHttpClient httpClient = getHttpClient();
        HttpResponse response;
        long requestStarted = System.nanoTime();
        try {
            response = httpClient.execute(request);
        } catch(Exception e) {
            long durationMs = (System.nanoTime() - requestStarted) / 1_000_000L;
            String failure = "HTTP request failed: method=" + getMethodName(requestType)
                + ", url=" + TowerLogger.sanitizeUrl(myURI.toString())
                + ", exception=" + e.getClass().getSimpleName() + ", durationMs=" + durationMs;
            logger.consoleError(failure);
            if(requestType == POST && isOutcomeUncertainEndpoint(endpoint)) {
                logger.consoleWarning(unknownOutcomeMessage(endpoint,
                    "Jenkins did not receive a response"));
            }
            if(isTransientTransportFailure(e)) {
                throw new AnsibleTowerTransientException("Transient Tower transport failure: method="
                    + getMethodName(requestType) + ", url=" + TowerLogger.sanitizeUrl(myURI.toString())
                    + ", cause=" + e.getClass().getSimpleName(), e);
            }
            throw new AnsibleTowerException("Unable to make tower request: "+ e.getMessage());
        }

        int statusCode = response.getStatusLine().getStatusCode();
        long durationMs = (System.nanoTime() - requestStarted) / 1_000_000L;
        String responseMetadata = "HTTP request " + (statusCode >= 400 ? "failed" : "completed")
            + ": method=" + getMethodName(requestType)
            + ", url=" + TowerLogger.sanitizeUrl(myURI.toString())
            + ", httpStatus=" + statusCode + ", durationMs=" + durationMs;
        if(statusCode >= 400) {
            logger.consoleError(responseMetadata);
            if(requestType == POST && isOutcomeUncertainEndpoint(endpoint) && isTransientGatewayStatus(statusCode)) {
                logger.consoleWarning(unknownOutcomeMessage(endpoint,
                    "Jenkins received HTTP " + statusCode));
            }
        } else {
            logger.debug(responseMetadata);
        }
        if(statusCode == 404) {
            throw new AnsibleTowerItemDoesNotExist("The item does not exist");
        } else if(statusCode == 401) {
            throw new AnsibleTowerException("Username/password invalid");
        } else if(statusCode == 403) {
            String exceptionText = "Request was forbidden";
            JSONObject responseObject = null;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
                if(responseObject.containsKey("detail")) {
                    exceptionText+= ": "+ responseObject.getString("detail");
                }
            } catch (IOException | RuntimeException ignored) {
                // Keep the generic forbidden message when the error body is not valid JSON.
            }

            throw new AnsibleTowerException(exceptionText);
        }

        return response;
    }

    static boolean isOutcomeUncertainEndpoint(String endpoint) {
        return endpoint != null && (endpoint.contains("/launch/") || endpoint.endsWith("/update/"));
    }

    static String unknownOutcomeMessage(String endpoint, String responseFailure) {
        boolean launch = endpoint != null && endpoint.contains("/launch/");
        String operation = launch ? "launch" : "project sync";
        String resource = launch ? "job" : "sync";
        return "Tower " + operation + " outcome is unknown; the controller may have created the "
            + resource + ", but " + responseFailure + ". Automatic retry was not performed.";
    }

    static boolean isTransientTransportFailure(Throwable failure) {
        Throwable current = failure;
        while(current != null) {
            if(current instanceof SocketTimeoutException || current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof java.io.InterruptedIOException
                    || "NoHttpResponseException".equals(current.getClass().getSimpleName())
                    || "ConnectionPoolTimeoutException".equals(current.getClass().getSimpleName())
                    || "HttpHostConnectException".equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean towerSupports(String end_point) throws AnsibleTowerException {
        // To determine if we support oAuth we will be making a HEAD call to /api/o to see what happens

        URI myURI;
        try {
            myURI = new URI(url+end_point);
        } catch(Exception e) {
            throw new AnsibleTowerException("Unable to construct URL for "+ end_point +": "+ e.getMessage());
        }

        logger.debug("Checking if Tower can: "+ myURI.toString());

        DefaultHttpClient httpClient = getHttpClient();
        HttpResponse response;
        try {
            response = httpClient.execute(new HttpHead(myURI));
        } catch(Exception e) {
            throw new AnsibleTowerException("Unable to make Tower HEAD request for "+ end_point +": "+ e.getMessage());
        }

        logger.debug("Can Tower request completed with ("+ response.getStatusLine().getStatusCode() +")");
        if(response.getStatusLine().getStatusCode() == 404) {
            logger.debug("Tower does not supoort "+ end_point);
            return false;
        } else {
            logger.debug("Tower supoorts "+ end_point);
            return true;
        }
    }

    public String getURL() { return url; }
    public void getVersion() throws AnsibleTowerException {
        // The version is housed on the poing page which is openly accessable
        HttpResponse response = makeRequest(GET, "ping/", null, true);
        if(response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unexpected error code returned from ping connection ("+ response.getStatusLine().getStatusCode() +")");
        }
        logger.debug("Ping page loaded");

        JSONObject responseObject;
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
            responseObject = JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleTowerException("Unable to read ping response and convert it into json: " + ioe.getMessage());
        }

        if (responseObject.containsKey("version")) {
            logger.debug("Successfully got version "+ responseObject.getString("version"));
            this.towerVersion = new TowerVersion(responseObject.getString("version"));
        }
    }

    public void testConnection() throws AnsibleTowerException {
        if(url == null) { throw new AnsibleTowerException("The URL is undefined"); }

        // We will run an unauthenticated test by the constructor calling the ping page so we can jump
        // straight into calling an authentication test

        // This will run an authentication test
        logger.debug("Testing authentication");
        HttpResponse response = makeRequest(GET, "jobs/");
        if(response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Failed to get authenticated connection ("+ response.getStatusLine().getStatusCode() +")");
        }
        releaseToken();
    }

    public String convertPotentialStringToID(String idToCheck, String api_endpoint) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        JSONObject foundItem = rawLookupByString(idToCheck, api_endpoint);
        logger.debug("Response from lookup: "+ foundItem.getString("id"));
        return foundItem.getString("id");
    }

    public JSONObject rawLookupByString(String idToCheck, String api_endpoint) throws AnsibleTowerException, AnsibleTowerItemDoesNotExist {
        try {
            Integer.parseInt(idToCheck);
            // We got an ID so lets see if we can load that item
            HttpResponse response = makeRequest(GET, api_endpoint + idToCheck +"/");
            requireSuccessfulLookup(response, api_endpoint + idToCheck + "/");
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
            requireSuccessfulLookup(response, api_endpoint + "?name=<redacted>");

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

    private void requireSuccessfulLookup(HttpResponse response, String endpoint) throws AnsibleTowerException {
        int statusCode = response.getStatusLine().getStatusCode();
        if(statusCode != 200) {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new AnsibleTowerRequestException("GET " + TowerLogger.sanitizeUrl(url + buildEndpoint(endpoint))
                + " returned HTTP " + statusCode);
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
            throw new AnsibleTowerException("Unable to find "+ templateType +" template: "+ ate.getMessage(), ate);
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

        long machine_credential_type = -1L;
        long vault_credential_type = -1L;
        JSONArray credentialTypesArray = responseObject.getJSONArray("results");
        Iterator<JSONObject> listIterator = credentialTypesArray.iterator();
        while(listIterator.hasNext()) {
            JSONObject aCredentialType = listIterator.next();
            if(aCredentialType.getString("kind").equalsIgnoreCase("ssh")) {
                machine_credential_type = aCredentialType.getLong("id");
            } else if(aCredentialType.getString("kind").equalsIgnoreCase("vault")) {
                vault_credential_type = aCredentialType.getLong("id");
            }
        }

        if (vault_credential_type == -1L) {
            logger.warning("Unable to find vault credential type");
        }
        if (machine_credential_type == -1L) {
            logger.warning("Unable to find machine credential type");
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
        HashMap<String, Vector<Long>> credentials = new HashMap<String, Vector<Long>>();
        credentials.put("vault", new Vector<Long>());
        credentials.put("machine", new Vector<Long>());
        credentials.put("extra", new Vector<Long>());
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
                credentials.get(myCredentialType).add(jsonCredential.getLong("id"));
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
            Starting in 3.3 you can take the separate parameters or you can pass them all as a single credential param

            Previously the decision point was whether or not we had multiple machine or vault creds.
            This was because both formats were accepted at one point.

            That behaviour has since been deprecated.
            We will now check if the version of tower is > 3.5.0 or we have multiple credential types
         */
        if(
                this.towerVersion.is_greater_or_equal("3.5.0") ||
                (credentials.get("machine").size() > 1 || credentials.get("vault").size() > 1)
        ) {
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


    public long submitTemplate(long jobTemplate, String extraVars, String limit, String jobTags, String skipJobTags, String jobType, String inventory, String credential, String scmBranch, String templateType) throws AnsibleTowerException {
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
        if(scmBranch != null && !scmBranch.isEmpty()) {
            postBody.put("scm_branch", scmBranch);
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
                return responseObject.getLong("id");
            }
            logger.severe("Tower launch response did not contain a job ID");
            throw new AnsibleTowerException("Did not get an ID from the Tower launch response");
        } else if(response.getStatusLine().getStatusCode() == 400) {
            String json = null;
            JSONObject responseObject = null;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(Exception e) {
                logger.warning("Unable to parse Tower 400 response: " + e.getMessage());
            }

            /*
                Types of things that might come back:
                {"extra_vars":["Must be valid JSON or YAML."],"variables_needed_to_start":["'my_var' value missing"]}
                {"credential":["Invalid pk \"999999\" - object does not exist."]}
                {"inventory":["Invalid pk \"99999999\" - object does not exist."]}

                Note: we are only testing for extra_vars as the other items should be checked during convertPotentialStringToID
            */

            if(responseObject != null && responseObject.containsKey("extra_vars")) {
                throw new AnsibleTowerException("Tower rejected the supplied extra vars");
            } else {
                throw new AnsibleTowerException("Tower received a bad request (400 response code)");
            }
        } else {
            throw new AnsibleTowerRequestException("Unexpected error code returned ("
                + response.getStatusLine().getStatusCode() + ")");
        }
    }

    public void checkTemplateType(String templateType) throws AnsibleTowerException {
        if(templateType.equalsIgnoreCase(JOB_TEMPLATE_TYPE)) { return; }
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) { return; }
        throw new AnsibleTowerException("Template type can only be '"+ JOB_TEMPLATE_TYPE +"' or '"+ WORKFLOW_TEMPLATE_TYPE+"'");
    }

    public boolean isJobCompleted(long jobID, String templateType) throws AnsibleTowerException {
        int retryCount = 0;
        while(true) {
            try {
                return pollJobStatusOnce(jobID, templateType).isCompleted();
            } catch(AnsibleTowerTransientException transientFailure) {
                if(retryCount >= MAX_TRANSIENT_GATEWAY_RETRIES) {
                    logger.severe("job_status_poll exhausted: jobId=" + jobID
                        + ", templateType=" + templateType + ", attempts=" + (retryCount + 1));
                    throw transientFailure;
                }
                retryCount++;
                logger.warning(buildRetryLogMessage("job_status_poll", jobID, templateType,
                    buildEndpoint(statusEndpoint(jobID, templateType)), transientFailure.getStatusCode(),
                    retryCount, MAX_TRANSIENT_GATEWAY_RETRIES, TRANSIENT_GATEWAY_RETRY_DELAY_MS));
                try {
                    Thread.sleep(TRANSIENT_GATEWAY_RETRY_DELAY_MS);
                } catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AnsibleTowerException("Interrupted while retrying job status request", interrupted);
                }
            }
        }
    }

    TowerJobStatus pollJobStatusOnce(long jobID, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);
        String apiEndpoint = statusEndpoint(jobID, templateType);
        HttpResponse response = makeRequest(GET, apiEndpoint);
        int statusCode = response.getStatusLine().getStatusCode();
        if(statusCode == 200) {
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
                    return new TowerJobStatus(false, false);
                } else {
                    Map<String, String> statusArtifacts = new HashMap<String, String>();
                    // Since we were finished we will now also check for stats
                    if(responseObject.containsKey(ARTIFACTS)) {
                        logger.debug("Processing artifacts");
                        JSONObject artifacts = responseObject.getJSONObject(ARTIFACTS);
                        if(artifacts.containsKey("JENKINS_EXPORT")) {
                            JSONArray exportVariables = artifacts.getJSONArray("JENKINS_EXPORT");
                            Iterator<JSONObject> listIterator = exportVariables.iterator();
                            while(listIterator.hasNext()) {
                                JSONObject entry = listIterator.next();
                                Iterator<String> keyIterator = entry.keys();
                                while(keyIterator.hasNext()) {
                                    String key = keyIterator.next();
                                    String value = entry.getString(key);
                                    jenkinsExports.put(key, value);
                                    statusArtifacts.put(key, value);
                                }
                            }
                        }
                    }
                    if(!responseObject.containsKey("failed")) {
                        throw new AnsibleTowerException("Tower job response did not contain a failed status");
                    }
                    boolean failed = responseObject.getBoolean("failed");
                    completedJobFailures.put(jobID, failed);
                    return new TowerJobStatus(true, failed, statusArtifacts);
                }
            }
            logger.severe("Tower job status response did not contain finished: jobId=" + jobID
                + ", templateType=" + templateType);
            throw new AnsibleTowerException("Tower job response did not contain a finished status");
        } else if(isTransientGatewayStatus(statusCode)) {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new AnsibleTowerTransientException("jobId=" + jobID + ", templateType="
                + templateType + ", endpoint=" + buildEndpoint(apiEndpoint), statusCode);
        } else {
            throw new AnsibleTowerException("Unexpected error code returned (" + statusCode + ")");
        }
    }

    private String statusEndpoint(long jobID, String templateType) {
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) {
            return "/workflow_jobs/" + jobID + "/";
        }
        return "/jobs/" + jobID + "/";
    }

    static boolean isTransientGatewayStatus(int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    static String buildRetryLogMessage(String operation, long jobID, String templateType, String endpoint,
            int statusCode, int attempt, int maxAttempts, long retryDelayMs) {
        return operation + " failed: jobId=" + jobID
            + ", templateType=" + templateType
            + ", endpoint=" + endpoint
            + ", httpStatus=" + statusCode
            + ", attempt=" + attempt + "/" + maxAttempts
            + ", retryDelayMs=" + retryDelayMs;
    }

    static String buildRetryExhaustedLogMessage(String operation, long jobID, String templateType,
            String endpoint, int statusCode, int attempts) {
        return operation + " exhausted retries: jobId=" + jobID
            + ", templateType=" + templateType
            + ", endpoint=" + endpoint
            + ", httpStatus=" + statusCode
            + ", attempts=" + attempts;
    }

    public void cancelJob(long jobID, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        String apiEndpoint = "/jobs/"+ jobID +"/";
        if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)) { apiEndpoint = "/workflow_jobs/"+ jobID +"/"; }
        apiEndpoint = apiEndpoint + "cancel/";
        HttpResponse response = makeRequest(GET, apiEndpoint);

        if(response.getStatusLine().getStatusCode() != 200) {
            throw new AnsibleTowerException("Unexpected error code returned (" + response.getStatusLine().getStatusCode() + ")");
        }

        JSONObject responseObject;
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
            responseObject = JSONObject.fromObject(json);
        } catch(IOException ioe) {
            throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
        }

        if(responseObject.containsKey("can_cancel")) {
            boolean canCancel = responseObject.getBoolean("can_cancel");
            // If we can't cancel this job raise an error
            if(!canCancel) { throw new AnsibleTowerException("The job can not be canceled at this time"); }
        }

        // Reuqest for Tower to cancel the job
        response = makeRequest(POST, apiEndpoint);
        if(response.getStatusLine().getStatusCode() != 202) {
            throw new AnsibleTowerException("Unexpected error code returned (" + response.getStatusLine().getStatusCode());
        }

        // We will now try for up to 10 seconds to cancel the job.
        int counter = 10;
        while(counter > 0) {
            response = makeRequest(GET, apiEndpoint);
            if(response.getStatusLine().getStatusCode() != 200) {
                throw new AnsibleTowerException("Unexpected error code returned (" + response.getStatusLine().getStatusCode() + ")");
            }
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            if (responseObject.containsKey("can_cancel")) {
                boolean canCancel = responseObject.getBoolean("can_cancel");
                if(!canCancel) { return; }
            }
            counter--;
            try {
                Thread.sleep(1000);
            } catch(InterruptedException ie) {
                throw new AnsibleTowerException("Interrupted while attempting to cancel job");
            }
        }

        throw new AnsibleTowerException("Failed to cancel the job within the specified time limit");
    }

    /**
     * @deprecated
     * Use isJobCompleted
     */
    @Deprecated
    public boolean isJobCommpleted(long jobID, String templateType) throws AnsibleTowerException {
        return isJobCompleted(jobID, templateType);
    }

    public Vector<String> getLogEvents(long jobID, String templateType) throws AnsibleTowerException {
        int retryCount = 0;
        while(true) {
            try {
                return getLogEventsOnce(jobID, templateType);
            } catch(AnsibleTowerTransientException transientFailure) {
                if(retryCount >= MAX_TRANSIENT_GATEWAY_RETRIES) {
                    throw transientFailure;
                }
                retryCount++;
                logger.warning("job_events_poll failed: jobId=" + jobID + ", templateType="
                    + templateType + ", retry=" + retryCount + "/" + MAX_TRANSIENT_GATEWAY_RETRIES
                    + ", httpStatus=" + transientFailure.getStatusCode()
                    + ", retryDelayMs=" + TRANSIENT_GATEWAY_RETRY_DELAY_MS);
                try {
                    Thread.sleep(TRANSIENT_GATEWAY_RETRY_DELAY_MS);
                } catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AnsibleTowerException("Interrupted while retrying job events request", interrupted);
                }
            }
        }
    }

    Vector<String> getLogEventsOnce(long jobID, String templateType) throws AnsibleTowerException {
        Vector<String> events = new Vector<String>();
        checkTemplateType(templateType);
        if(templateType.equalsIgnoreCase(JOB_TEMPLATE_TYPE)) {
            events.addAll(logJobEvents(jobID));
        } else if(templateType.equalsIgnoreCase(WORKFLOW_TEMPLATE_TYPE)){
            events.addAll(logWorkflowEvents(jobID, this.importChildWorkflowLogs));
        } else {
            throw new AnsibleTowerException("Tower Connector does not know how to log events for a "+ templateType);
        }
        return events;
    }

    private static String UNIFIED_JOB_TYPE = "unified_job_type";
    private static String UNIFIED_JOB_TEMPLATE = "unified_job_template";

    private Vector<String> logWorkflowEvents(long jobID, boolean importWorkflowChildLogs) throws AnsibleTowerException {
        Vector<String> events = new Vector<String>();
        Set<Long> processed = this.processedWorkflowNodeIds.get(jobID);
        if(processed == null) {
            processed = new HashSet<Long>();
            this.processedWorkflowNodeIds.put(jobID, processed);
        }
        String apiEndpoint = "/workflow_jobs/" + jobID + "/workflow_nodes/?id__gt=0";
        while(apiEndpoint != null) {
            HttpResponse response = makeRequest(GET, apiEndpoint);
            int statusCode = response.getStatusLine().getStatusCode();
            if(isTransientGatewayStatus(statusCode)) {
                EntityUtils.consumeQuietly(response.getEntity());
                throw new AnsibleTowerTransientException("jobId=" + jobID + ", templateType=workflow"
                    + ", endpoint=" + buildEndpoint(apiEndpoint), statusCode);
            }
            if(statusCode != 200) {
                throw new AnsibleTowerException("Unexpected error code returned (" + statusCode + ")");
            }
            JSONObject responseObject;
            String json;
            try {
                json = EntityUtils.toString(response.getEntity());
                responseObject = JSONObject.fromObject(json);
            } catch(IOException ioe) {
                throw new AnsibleTowerException("Unable to read response and convert it into json: "+ ioe.getMessage());
            }

            if(responseObject.containsKey("results")) {
                logger.debug("Workflow events received: jobId=" + jobID
                    + ", resultCount=" + responseObject.getJSONArray("results").size());
                for(Object anEventObject : responseObject.getJSONArray("results")) {
                    JSONObject anEvent = (JSONObject) anEventObject;
                    long eventId = anEvent.getLong("id");
                    if(processed.contains(eventId)) { continue; }

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

                    Vector<String> nodeEvents = new Vector<String>();
                    nodeEvents.addAll(logLine(job.getString("name") +" => "+ job.getString("status") +" "+ this.getJobURL(job.getLong("id"), templateType.getString(UNIFIED_JOB_TYPE))));

                    if(importWorkflowChildLogs) {
                        if(templateType.getString(UNIFIED_JOB_TYPE).equalsIgnoreCase("job")) {
                            nodeEvents.addAll(logJobEvents(job.getLong("id")));
                        } else if(templateType.getString(UNIFIED_JOB_TYPE).equalsIgnoreCase("project_update")) {
                            nodeEvents.addAll(logProjectSync(job.getLong("id")));
                        } else if(templateType.getString(UNIFIED_JOB_TYPE).equalsIgnoreCase("inventory_update")) {
                            nodeEvents.addAll(logInventorySync(job.getLong("id")));
                        } else {
                            nodeEvents.addAll(logLine("Unknown job type in workflow: "+ templateType.getString(UNIFIED_JOB_TYPE)));
                        }
                    }
                    nodeEvents.addAll(logLine(""));
                    nodeEvents.addAll(logLine(""));
                    processed.add(eventId);
                    events.addAll(nodeEvents);
                }
            }
            apiEndpoint = nextPage(responseObject);
        }
        return events;
    }

    private String nextPage(JSONObject responseObject) {
        if(!responseObject.containsKey("next") || responseObject.get("next") == null) {
            return null;
        }
        String next = responseObject.getString("next");
        if("null".equalsIgnoreCase(next) || next.isEmpty()) {
            return null;
        }
        if(next.startsWith(this.url)) {
            return next.substring(this.url.length());
        }
        return next;
    }

    public Vector<String> logLine(String output) throws AnsibleTowerException {
        Vector<String> return_lines = new Vector<String>();
        String[] lines = output.split("\\r\\n");
        for(String line : lines) {
            // Even if we don't log, we are going to see if this line contains the string JENKINS_EXPORT VAR=value
            if(line.matches("^.*JENKINS_EXPORT.*$")) {
                // The value might have some ansi color on it so we need to force the removal  of it
                String[] entities = removeColor(line).split("=", 2);
                if(entities.length == 2) {
                    entities[0] = entities[0].replaceAll(".*JENKINS_EXPORT ", "");
                    entities[1] = entities[1].replaceAll("\"$", "");
                    jenkinsExports.put(entities[0], entities[1]);
                }
            }
            if(removeColor) {
                // This regex was found on https://stackoverflow.com/questions/14652538/remove-ascii-color-codes
                line = removeColor(line);
            }
            return_lines.add(line);
        }
        return return_lines;
    }

    private String removeColor(String coloredLine) {
        return coloredLine.replaceAll("\u001B\\[[;\\d]*m", "");
    }


    private Vector<String> logInventorySync(long syncID) throws AnsibleTowerException {
        Vector<String> events = new Vector<String>();
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

            logger.debug("Inventory sync output received: syncId=" + syncID
                + ", hasStdout=" + responseObject.containsKey("result_stdout"));

            if(responseObject.containsKey("result_stdout")) {
                events.addAll(logLine(responseObject.getString("result_stdout")));
            }
        } else {
            throw logImportFailure("inventory update: syncId=" + syncID
                + ", endpoint=" + buildEndpoint(apiURL), response);
        }
        return events;
    }


    private Vector<String> logProjectSync(long syncID) throws AnsibleTowerException {
        Vector<String> events = new Vector<String>();
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

            logger.debug("Project sync output received: syncId=" + syncID
                + ", hasStdout=" + responseObject.containsKey("result_stdout"));

            if(responseObject.containsKey("result_stdout")) {
                events.addAll(logLine(responseObject.getString("result_stdout")));
            }
        } else {
            throw logImportFailure("project update: syncId=" + syncID
                + ", endpoint=" + buildEndpoint(apiURL), response);
        }
        return events;
    }

    private Vector<String> logJobEvents(long jobID) throws AnsibleTowerException {
        Vector<String> events = new Vector<String>();
        if(!this.logIdForJobs.containsKey(jobID)) { this.logIdForJobs.put(jobID, 0L); }
        long highestEventId = this.logIdForJobs.get(jobID);
        String apiURL = "/jobs/" + jobID + "/job_events/?id__gt=" + highestEventId;
        while(apiURL != null) {
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

                if (responseObject.containsKey("results")) {
                    logger.debug("Job events received: jobId=" + jobID
                        + ", resultCount=" + responseObject.getJSONArray("results").size());
                    for (Object anEvent : responseObject.getJSONArray("results")) {
                        JSONObject eventObject = (JSONObject) anEvent;
                        long eventId = eventObject.getLong("id");
                        String stdOut = eventObject.getString("stdout");
                        if(this.getFullLogs) {
                            try {
                                stdOut = eventObject.getJSONObject("event_data").getJSONObject("res").getString("msg");
                                if ("".equals(stdOut)){
                                    stdOut = eventObject.getJSONObject("event_data").getJSONObject("res").getString("stdout");
                                }
                            } catch (Exception e) {
                                // If we don't have this its ok, not all messages will have the res
                            }
                        }
                        events.addAll(logLine(stdOut));
                        if (eventId > highestEventId) {
                            highestEventId = eventId;
                        }
                    }
                }
                apiURL = nextPage(responseObject);
            } else {
                throw logImportFailure("job events: jobId=" + jobID
                    + ", endpoint=" + buildEndpoint(apiURL), response);
            }
        }
        this.logIdForJobs.put(jobID, highestEventId);
        return events;
    }

    private AnsibleTowerException logImportFailure(String operation, HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        EntityUtils.consumeQuietly(response.getEntity());
        if(isTransientGatewayStatus(statusCode)) {
            return new AnsibleTowerTransientException(operation, statusCode);
        }
        return new AnsibleTowerException("Unexpected error code returned (" + statusCode + ")");
    }

    public boolean isJobFailed(long jobID, String templateType) throws AnsibleTowerException {
        checkTemplateType(templateType);

        if(completedJobFailures.containsKey(jobID)) {
            return completedJobFailures.get(jobID);
        }

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
            logger.severe("Tower job response did not contain failed: jobId=" + jobID
                + ", templateType=" + templateType);
            throw new AnsibleTowerException("Tower job response did not contain a failed status");
        } else {
            throw new AnsibleTowerException("Unexpected error code returned (" + response.getStatusLine().getStatusCode() + ")");
        }
    }

    public String getJobURL(long myJobID, String templateType) {
        return buildJobURL(this.url, this.apiBasePath, myJobID, templateType);
    }

    public String getProjectSyncURL(long syncID) {
        return buildJobURL(this.url, this.apiBasePath, syncID, "project_update");
    }

    private String getBasicAuthString() {
        String auth = this.username + ":" + this.password;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
        return "Basic " + new String(encodedAuth, Charset.forName("UTF-8"));
    }

    private String getOAuthToken() throws AnsibleTowerException {
        String tokenEndpoint = buildOAuthTokenEndpoint(this.apiBasePath);
        String tokenURI = url + tokenEndpoint;
        HttpPost oauthTokenRequest = new HttpPost(tokenURI);
        oauthTokenRequest.setHeader(HttpHeaders.AUTHORIZATION, this.getBasicAuthString());
        JSONObject body = new JSONObject();
        body.put("description", "Jenkins Token");
        body.put("application", null);
        body.put("scope", "write");
        oauthTokenRequest.setEntity(createJsonEntity(body));

        oauthTokenRequest.setHeader("Content-Type", "application/json");

        DefaultHttpClient httpClient = getHttpClient();
        HttpResponse response;
        try {
            logger.debug("Calling for oauth token at "+ tokenURI);
            response = httpClient.execute(oauthTokenRequest);
        } catch(Exception e) {
            throw new AnsibleTowerException("Unable to make request for an oauth token: "+ e.getMessage());
        }

        if(response.getStatusLine().getStatusCode() == 400 || response.getStatusLine().getStatusCode() == 401) {
            throw new AnsibleTowerException("Username/password invalid");
        } else if(response.getStatusLine().getStatusCode() == 404) {
            throw new AnsibleTowerDoesNotSupportAuthToken("Server does not have tokens endpoint: " + tokenURI);
        } else if(response.getStatusLine().getStatusCode() == 403) {
            throw new AnsibleTowerRefusesToGiveToken("Server refuses to give tokens");
        } else if(response.getStatusLine().getStatusCode() != 200 && response.getStatusLine().getStatusCode() != 201) {
            throw new AnsibleTowerException("Unable to get oauth token, server responded with ("+ response.getStatusLine().getStatusCode() +")");
        }

        JSONObject responseObject;
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
            responseObject = JSONObject.fromObject(json);
        } catch (IOException ioe) {
            throw new AnsibleTowerException("Unable to read oatuh response and convert it into json: " + ioe.getMessage());
        }

        if (responseObject.containsKey("id")) {
            this.oAuthTokenID = responseObject.getString("id");
            this.oAuthTokenBaseEndpoint = tokenEndpoint;
        }

        if (responseObject.containsKey("token")) {
            logger.debug("OAuth token acquired");
            return responseObject.getString("token");
        }
        logger.severe("OAuth token response did not contain a token");
        throw new AnsibleTowerException("OAuth token response did not contain a token");
    }

    private String getAuthToken() throws AnsibleTowerException {
        logger.debug("Requesting auth token");

        String tokenURI = url + this.buildEndpoint("/authtoken/");
        HttpPost tokenRequest = new HttpPost(tokenURI);
        tokenRequest.setHeader(HttpHeaders.AUTHORIZATION, this.getBasicAuthString());
        JSONObject body = new JSONObject();
        body.put("username", this.username);
        body.put("password", this.password);
        tokenRequest.setEntity(createJsonEntity(body));

        tokenRequest.setHeader("Content-Type", "application/json");

        DefaultHttpClient httpClient = getHttpClient();
        HttpResponse response;
        try {
            logger.debug("Calling for token at "+ tokenURI);
            response = httpClient.execute(tokenRequest);
        } catch(Exception e) {
            throw new AnsibleTowerException("Unable to make request for an authtoken: "+ e.getMessage());
        }

        if(response.getStatusLine().getStatusCode() == 400) {
            throw new AnsibleTowerException("Username/password invalid");
        } else if(response.getStatusLine().getStatusCode() == 404) {
            throw new AnsibleTowerDoesNotSupportAuthToken("Server does not have endpoint: " + tokenURI);
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
            logger.debug("AuthToken acquired");
            return responseObject.getString("token");
        }
        logger.severe("Auth token response did not contain a token");
        throw new AnsibleTowerException("Auth token response did not contain a token");
    }

    public void releaseToken() {
        if(this.oAuthTokenID != null) {
            logger.debug("Deleting OAuth token");
            try {
                String tokenEndpoint = this.oAuthTokenBaseEndpoint;
                if(tokenEndpoint == null) {
                    tokenEndpoint = this.buildEndpoint("/tokens/");
                }
                String tokenURI = url + tokenEndpoint + this.oAuthTokenID + "/";
                HttpDelete tokenRequest = new HttpDelete(tokenURI);
                tokenRequest.setHeader(HttpHeaders.AUTHORIZATION, this.getBasicAuthString());

                DefaultHttpClient httpClient = getHttpClient();
                logger.debug("Calling OAuth token delete: endpoint=" + tokenEndpoint);
                HttpResponse response = httpClient.execute(tokenRequest);
                if(response.getStatusLine().getStatusCode() == 400) {
                    logger.warning("Unable to delete OAuth token: invalid authorization");
                } else if(response.getStatusLine().getStatusCode() != 204) {
                    logger.warning("Unable to delete OAuth token: httpStatus="
                        + response.getStatusLine().getStatusCode());
                }
                logger.debug("oAuth Token deleted");

                this.oAuthTokenID = null;
                this.oAuthTokenBaseEndpoint = null;
                this.authorizationHeader = null;
            } catch(Exception e) {
                logger.warning("Failed to delete OAuth token: " + e.getMessage());
            }

        }
    }

    public String getMethodName(int methodId) {
        if(methodId == 1) { return "GET"; }
        else if(methodId == 2) { return "POST"; }
        else if(methodId == 3) { return "PATCH"; }
        else { return "UNKNOWN"; }
    }
}
