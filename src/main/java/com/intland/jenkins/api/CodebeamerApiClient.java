/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */
package com.intland.jenkins.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intland.jenkins.XUnitUtil;
import com.intland.jenkins.api.dto.*;
import com.intland.jenkins.api.dto.trackerschema.TrackerSchemaDto;
import com.intland.jenkins.dto.NodeMapping;
import com.intland.jenkins.dto.PluginConfiguration;
import com.intland.jenkins.dto.TestResultItem;
import com.intland.jenkins.dto.TestResults;
import com.intland.jenkins.markup.*;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;

public class CodebeamerApiClient {
    private final String DEFAULT_TESTSET_NAME = "Jenkins-TestSet";
    private final String TEST_CASE_TYPE_NAME = "Automated";
    private final int HTTP_TIMEOUT = 10000;
    private boolean isTestCaseTypeSupported = false;
    private HttpClient client;
    private RequestConfig requestConfig;
    private PluginConfiguration pluginConfiguration;
    private String baseUrl;
    private ObjectMapper objectMapper;
    private BuildListener listener;

    public CodebeamerApiClient(PluginConfiguration pluginConfiguration, BuildListener listener) {
        this.pluginConfiguration = pluginConfiguration;
        this.baseUrl = pluginConfiguration.getUri();
        this.listener = listener;

        objectMapper = new ObjectMapper();
        CredentialsProvider provider = getCredentialsProvider(pluginConfiguration.getUsername(), pluginConfiguration.getPassword());
        client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        requestConfig = RequestConfig.custom().setConnectionRequestTimeout(HTTP_TIMEOUT)
                                                .setConnectTimeout(HTTP_TIMEOUT)
                                                .setSocketTimeout(HTTP_TIMEOUT)
                                                .build();
    }

    public void postTestRuns(TestResults tests, AbstractBuild<?, ?> build) throws IOException {
        String buildIdentifier = getBuildIdentifier(build);
        XUnitUtil.log(listener, "Starting xUnit tests upload");
        XUnitUtil.log(listener, "Checking supported Test Case types");
        isTestCaseTypeSupported = isTestCaseTypeSupported();
        XUnitUtil.log(listener, String.format("Test Case type: %s, supported: %s", TEST_CASE_TYPE_NAME, isTestCaseTypeSupported));

        XUnitUtil.log(listener, "Fetching Test Cases");
        TrackerItemDto[] testCases = getTrackerItems(pluginConfiguration.getTestCaseTrackerId());
        NodeMapping testCasesMap = XUnitUtil.getNodeMapping(testCases);

        TrackerItemDto[] requirements = null;
        NodeMapping requirementsNodeMapping = null;
        Map<Integer, TrackerItemDto[]> verifiesMap = new HashMap<>();
        if (pluginConfiguration.getRequirementTrackerId() != null) {
            XUnitUtil.log(listener, "Fetching Requirements");
            requirements = getTrackerItems(pluginConfiguration.getRequirementTrackerId());
            verifiesMap = XUnitUtil.getVerifiesMap(testCases);
            requirementsNodeMapping = XUnitUtil.getNodeMapping(requirements);
        }

        String testSetName = DEFAULT_TESTSET_NAME + "-" + buildIdentifier;
        XUnitUtil.log(listener, "Creating Test Set: " + testSetName);
        Integer testSetId = findOrCreateTrackerItem(pluginConfiguration.getTestSetTrackerId(), testSetName, "--");
        XUnitUtil.log(listener, "Test Set created with id: " + testSetId);
        Map<String, Integer> testCasesForCurrentTestRun = new HashMap<>();
        for (TestResultItem test : tests.getTestResultItems()) {
            Integer testCaseId = findOrCreateTrackerItemInTree(test.getFullName(), pluginConfiguration.getTestCaseTrackerId(), testCasesMap,
                    pluginConfiguration.getTestCaseParentId(), null, "Accepted");
            testCasesForCurrentTestRun.put(test.getFullName(), testCaseId);

            // create requirement if needed
            if (requirements != null && verifiesMap.get(testCaseId) == null) {
                createRequirementInTree(verifiesMap, requirementsNodeMapping, test, testCaseId);
            }
        }

        TrackerItemDto parentItem = createParentTestRun(tests, buildIdentifier, build, pluginConfiguration.getTestConfigurationId(), testSetId, testCasesForCurrentTestRun.values());
        XUnitUtil.log(listener, String.format("Parent TestRun created with name: %s and id: %s ", parentItem.getName(), parentItem.getId()));

        int uploadCounter = 0;
        int numberOfReportedBugs = 0;
        for (TestResultItem test : tests.getTestResultItems()) {
            Integer testCaseId = testCasesForCurrentTestRun.get(test.getFullName());
            TrackerItemDto createdRun = createTestRun(pluginConfiguration.getTestConfigurationId(), testSetId, parentItem, test, testCaseId);
            XUnitUtil.log(listener, String.format("TestRun created with name: %s and id: %s ", createdRun.getName(), createdRun.getId()));
            updateTestRunStatusAndSpentTime(createdRun.getId(), "Completed", (long)(test.getDuration() * 1000));

            if (isReportingBugNeeded(test, numberOfReportedBugs)) {
                createBug(test, createdRun);
                numberOfReportedBugs++;
            }

            uploadCounter++;
            if (uploadCounter % 100 == 0) {
                XUnitUtil.log(listener, "uploaded: " + uploadCounter + " test runs");
            }
        }

        updateTestSetTestCases(testSetId, testCasesForCurrentTestRun.values());
        updateTrackerItemStatus(testSetId, "Completed");
        XUnitUtil.log(listener, "Upload finished, uploaded: " + uploadCounter + " test runs");
    }

    private boolean isTestCaseTypeSupported() throws IOException {
        String url = String.format("%s/rest/tracker/%s/schema", baseUrl, pluginConfiguration.getTestCaseTrackerId());
        String json = get(url);
        TrackerSchemaDto trackerSchemaDto = objectMapper.readValue(json, TrackerSchemaDto.class);
        return trackerSchemaDto.doesTypeContain(TEST_CASE_TYPE_NAME);
    }

    private boolean isReportingBugNeeded(TestResultItem test, int numberOfReportedBugs) throws IOException  {
        boolean isBugTrackerEnabled = pluginConfiguration.getBugTrackerId() != null;
        boolean needToReportMoreBugs = pluginConfiguration.getNumberOfBugsToReport() > 0 && pluginConfiguration.getNumberOfBugsToReport() > numberOfReportedBugs;

        return  isBugTrackerEnabled &&
                !test.isSuccessful() &&
                needToReportMoreBugs &&
                !isTrackerItemWithNameAndStatusExist(test);
    }

    private boolean isTrackerItemWithNameAndStatusExist(TestResultItem test) throws IOException {
        String name = generateNameOfBug(test);
        String cbQl = XUnitUtil.encodeParam(String.format("tracker.id IN ('%s') AND workItemStatus IN ('Unset','InProgress') " +
                "AND summary LIKE '%s'", pluginConfiguration.getBugTrackerId(), name));

        String url = String.format("%s/rest/query/page/1?queryString=%s&pagesize=1", baseUrl, cbQl);
        String content = get(url);
        PagedTrackerItemsDto pagedTrackerItemsDto = objectMapper.readValue(content, PagedTrackerItemsDto.class);
        boolean itemExists = pagedTrackerItemsDto.getTotal() > 0;
        if (itemExists) {
            XUnitUtil.log(listener, String.format("Unresolved bug with name: %s already exists, skipping Bug report creation", name));
        }

        return itemExists;
    }

    private TrackerItemDto createBug(TestResultItem test, TrackerItemDto createdRun) throws IOException {
        TestRunDto bug = new TestRunDto();
        bug.setTracker(String.format("/tracker/" + pluginConfiguration.getBugTrackerId()));
        bug.setName(generateNameOfBug(test));
        bug.setDescription(String.format("{{{%s}}} \\\\ [ISSUE:%s]", test.getErrorDetail(), createdRun.getId()));
        bug.setDescFormat("Wiki");
        return postTrackerItem(bug);
    }

    private String generateNameOfBug(TestResultItem test) {
        return "Bug of " + test.getName();
    }

    private TrackerItemDto createTestRun(Integer testConfigurationId, Integer testSetId, TrackerItemDto parentItem, TestResultItem test, Integer testCaseId) throws IOException {
        TestRunDto testRunDto = new TestRunDto(test.getName(), parentItem.getId(), pluginConfiguration.getTestRunTrackerId(),
                Arrays.asList(new Integer[]{testCaseId}), testConfigurationId, test.getResult());
        if (test.getErrorDetail() != null) {
            testRunDto.setDescription(String.format("{{{%s}}}", test.getErrorDetail()));
            testRunDto.setDescFormat("Wiki");
        } else {
            testRunDto.setDescription("--");
        }
        testRunDto.setTestSet(testSetId);
        return postTrackerItem(testRunDto);
    }

    private String createParentMarkup(AbstractBuild<?, ?> build) throws IOException {
        long currentTime = System.currentTimeMillis();
        BuildDto buildDto = BuildDataCollector.collectBuildData(build, currentTime);
        ScmDto scmDto = ScmDataCollector.collectScmData(build, this);
        TestResultDto testResultDto = TestResultCollector.collectTestResultData(build, listener);

        return new WikiMarkupBuilder()
                .initWithTestReportTemplate()
                .withBuildInfo(buildDto)
                .withTestReportInfo(testResultDto)
                .withScmInfo(scmDto)
                .build();
    }

    private TrackerItemDto createParentTestRun(TestResults tests, String buildIdentifier, AbstractBuild<?, ?> build, Integer testConfigurationId, Integer testSetId, Collection<Integer> testCaseIds) throws IOException {
        String newMarkupContent = createParentMarkup(build);
        TrackerItemDto parentItem;
        TestRunDto parentRunDto = new TestRunDto(buildIdentifier, null, pluginConfiguration.getTestRunTrackerId(), testCaseIds, testConfigurationId, tests.getStatus());
        parentRunDto.setTestSet(testSetId);
        parentRunDto.setDescription(tests.getTestSummary().toWikiMarkup() + newMarkupContent);
        parentRunDto.setDescFormat("Wiki");
        parentItem = postTrackerItem(parentRunDto);
        return parentItem;
    }

    private void createRequirementInTree(Map<Integer, TrackerItemDto[]> verifiesMap, NodeMapping requirementsNodeMapping, TestResultItem test, Integer testCaseId) throws IOException {
        Integer requirementId = findOrCreateTrackerItemInTree(test.getFullName(), pluginConfiguration.getRequirementTrackerId(),
                requirementsNodeMapping, pluginConfiguration.getRequirementParentId(), pluginConfiguration.getRequirementDepth(), null);
        updateTestCaseVerifies(testCaseId, requirementId);
        verifiesMap.put(testCaseId, new TrackerItemDto[]{new TrackerItemDto()});
    }

    private Integer findOrCreateTrackerItem(Integer trackerId, String name, String description) throws IOException {
        String urlParamName = XUnitUtil.encodeParam(name);
        String content = get(String.format(baseUrl + "/rest/tracker/%s/items/or/name=%s/page/1", trackerId, urlParamName));
        PagedTrackerItemsDto pagedTrackerItemsDto = objectMapper.readValue(content, PagedTrackerItemsDto.class);

        Integer result;
        if (pagedTrackerItemsDto.getTotal() > 0) {
            result = pagedTrackerItemsDto.getItems()[0].getId();
        } else {
            TestRunDto testConfig = new TestRunDto();
            testConfig.setName(name);
            testConfig.setTracker("/tracker/" + trackerId);
            testConfig.setDescription(description);
            TrackerItemDto trackerItemDto = postTrackerItem(testConfig);
            result = trackerItemDto.getId();
        }

        return result;
    }

    private String getBuildIdentifier(AbstractBuild<?, ?> build) {
        return build.getProject().getName() + "#" + build.getNumber();
    }

    private Integer findOrCreateTrackerItemInTree(String fullName, Integer trackerId, NodeMapping nodeMapping, Integer folder, Integer limit, String status) throws IOException {
        fullName = XUnitUtil.limitName(fullName, limit, ".");

        if (folder != null && nodeMapping.getIdNodeMapping().get(folder) != null) {
            fullName = nodeMapping.getIdNodeMapping().get(folder) + "." + fullName;
        }

        Integer result = nodeMapping.getNodeIdMapping().get(fullName);
        if (result == null) {
            StringTokenizer tokenizer = new StringTokenizer(fullName, ".");
            String segment = null;
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken();
                segment = segment == null ? token : segment + "." + token;

                if (nodeMapping.getNodeIdMapping().get(segment) == null) {
                    result = postTrackerItemWithParent(segment, trackerId, result);
                    nodeMapping.getNodeIdMapping().put(segment, result);

                    if (status != null) {
                        updateTrackerItemStatus(result, status);
                    }
                } else {
                    result = nodeMapping.getNodeIdMapping().get(segment);
                }
            }
        }

        return result;
    }

    private Integer postTrackerItemWithParent(String path, Integer trackerId, Integer parentId) throws IOException {
        String name = path.substring(path.lastIndexOf(".") + 1);
        String tracker = String.format("/tracker/%s", trackerId);
        TestRunDto testCaseDto = new TestRunDto(name, tracker, parentId);
        testCaseDto.setDescription("--");

        // Tracker is a test case tracker and type is supported
        if (trackerId.equals(pluginConfiguration.getTestCaseTrackerId()) && isTestCaseTypeSupported) {
            testCaseDto.setType(TEST_CASE_TYPE_NAME);
        }

        String content = objectMapper.writeValueAsString(testCaseDto);
        return post(content).getId();
    }

    public TrackerItemDto postTrackerItem(TestRunDto testRunDto) throws IOException {
        String content = objectMapper.writeValueAsString(testRunDto);
        return post(content);
    }

    public TrackerItemDto[] getTrackerItems(Integer trackerId) throws IOException {
        String url = String.format("%s/rest/tracker/%s/items/page/1?pagesize=500", baseUrl, trackerId);
        String json = get(url);
        PagedTrackerItemsDto pagedTrackerItemsDto = objectMapper.readValue(json, PagedTrackerItemsDto.class);

        int numberOfRequests = (pagedTrackerItemsDto.getTotal() / 500) + 1;
        List<TrackerItemDto> items = new ArrayList(Arrays.asList(pagedTrackerItemsDto.getItems()));
        for (int i = 2; i < numberOfRequests; i++) {
            url = String.format("%s/rest/tracker/%s/items/page/%s?pagesize=500", baseUrl, trackerId, numberOfRequests);
            json = get(url);
            pagedTrackerItemsDto = objectMapper.readValue(json, PagedTrackerItemsDto.class);
            items.addAll(Arrays.asList(pagedTrackerItemsDto.getItems()));
        }

        return items.toArray(new TrackerItemDto[items.size()]);
    }

    private TrackerItemDto updateTestSetTestCases(Integer testSetId, Collection<Integer> testCases) throws IOException {
        List<Object[]> testCasesList = new ArrayList<>();
        for (Integer testCaseId : testCases) {
            testCasesList.add(new Object[] {new ReferenceDto("/item/" + testCaseId), Boolean.TRUE, Boolean.TRUE});
        }

        TrackerItemDto trackerItemDto = new TrackerItemDto();
        trackerItemDto.setUri("/item/" + testSetId);
        trackerItemDto.setTestCases(testCasesList);
        String content = objectMapper.writeValueAsString(trackerItemDto);
        return put(content);
    }

    private TrackerItemDto updateTestCaseVerifies(Integer testCaseId, Integer requirementId) throws IOException {
        TrackerItemDto[] verifies = new TrackerItemDto[]{ new TrackerItemDto("/item/" + requirementId) };
        TrackerItemDto trackerItemDto = new TrackerItemDto("/item/" + testCaseId, verifies);
        String content = objectMapper.writeValueAsString(trackerItemDto);
        return put(content);
    }

    private TrackerItemDto updateTestRunStatusAndSpentTime(Integer id, String status, Long duration) throws IOException {
        TestCaseDto testCaseDto = new TestCaseDto(id, status);
        testCaseDto.setSpentMillis(duration);
        String content = objectMapper.writeValueAsString(testCaseDto);
        return put(content);
    }

    private TrackerItemDto updateTrackerItemStatus(Integer id, String status) throws IOException {
        TestCaseDto testCaseDto = new TestCaseDto(id, status);
        String content = objectMapper.writeValueAsString(testCaseDto);
        return put(content);
    }

    public TrackerItemDto getTrackerItem(Integer itemId) throws IOException {
        String value = get(baseUrl + "/rest/item/" + itemId);
        return value != null ? objectMapper.readValue(value, TrackerItemDto.class) : null;
    }

    public TrackerDto getTrackerType(Integer trackerId) throws IOException {
        String value = get(baseUrl + "/rest/tracker/" + trackerId);
        return value != null ? objectMapper.readValue(value, TrackerDto.class) : null;
    }

    public String getUserId(String author)  throws IOException {
        String authorNoSpace = author.replaceAll(" ", "");
        String tmpUrl = String.format("%s/rest/user/%s", baseUrl, authorNoSpace);

        String httpResult = get(tmpUrl);
        String result = null;

        if (httpResult != null) { //20X success
            UserDto userDto = objectMapper.readValue(httpResult, UserDto.class);
            String uri = userDto.getUri();
            result = uri.substring(uri.lastIndexOf("/") + 1);
        }

        return result;
    }

    private TrackerItemDto post(String content) throws IOException {
        HttpPost post = new HttpPost(String.format("%s/rest/item", baseUrl));
        post.setConfig(requestConfig);

        StringEntity stringEntity = new StringEntity(content, "UTF-8");
        stringEntity.setContentType("application/json");
        post.setEntity(stringEntity);

        HttpResponse response = client.execute(post);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 201) {
            String json = new BasicResponseHandler().handleResponse(response);
            post.releaseConnection();
            return objectMapper.readValue(json, TrackerItemDto.class);
        } else {
            InputStream responseStream = response.getEntity().getContent();
            String error = XUnitUtil.getStringFromInputStream(responseStream);
            XUnitUtil.log(listener, "ERROR: " + error);
            post.releaseConnection();
            throw new IOException("post returned with status code: " + statusCode);
        }
    }

    private TrackerItemDto put(String content) throws IOException {
        HttpPut put = new HttpPut(String.format("%s/rest/item", baseUrl));
        put.setConfig(requestConfig);

        StringEntity stringEntity = new StringEntity(content, "UTF-8");
        stringEntity.setContentType("application/json");
        put.setEntity(stringEntity);

        HttpResponse response = client.execute(put);
        String json  = new BasicResponseHandler().handleResponse(response);
        put.releaseConnection();

        return objectMapper.readValue(json, TrackerItemDto.class);
    }

    private String get(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setConfig(requestConfig);
        HttpResponse response = client.execute(get);
        int statusCode = response.getStatusLine().getStatusCode();

        String result = null;
        if (statusCode == 200) {
            result = new BasicResponseHandler().handleResponse(response);
        }

        get.releaseConnection();

        return result;
    }

    private CredentialsProvider getCredentialsProvider(String username, String password) {
        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
        provider.setCredentials(AuthScope.ANY, credentials);
        return provider;
    }
}
