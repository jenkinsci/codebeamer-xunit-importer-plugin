/*
 * Copyright (c) 2017 Intland Software (support@intland.com)
 *
 * Additional information can be found here: https://codebeamer.com/cb/project/1025
 * If you find any bugs please use the Tracker page to report them: https://codebeamer.com/cb/project/1025/tracker
 */
package com.intland.jenkins.api;

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

import java.io.IOException;
import java.util.*;

import static com.intland.jenkins.api.RestAdapter.PAGESIZE;

public class CodebeamerApiClient {
    public static final int HTTP_TIMEOUT_LONG = 45000; // ms
    public static final int HTTP_TIMEOUT_SHORT = 10000; // ms
    private static final int UPLOAD_BATCH_SIZE = 20;
    private final String DEFAULT_TESTSET_NAME = "Jenkins-xUnit";
    private final String TEST_CASE_TYPE_NAME = "Automated";
    private boolean isTestCaseTypeSupported = false;
    private PluginConfiguration pluginConfiguration;
    private BuildListener listener;

    private RestAdapter rest;

    public CodebeamerApiClient(PluginConfiguration pluginConfiguration, BuildListener listener, int timeout, RestAdapter rest) {
        this.pluginConfiguration = pluginConfiguration;
        this.listener = listener;

        this.rest = rest;
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

        TrackerItemDto parentTestRun = createParentTestRun(tests, buildIdentifier, build, pluginConfiguration.getTestConfigurationId(), testSetId, testCasesForCurrentTestRun.values());
        XUnitUtil.log(listener, String.format("Parent TestRun created with name: %s and id: %s ", parentTestRun.getName(), parentTestRun.getId()));

        int uploadCounter = 0;
        int numberOfReportedBugs = 0;
        List<TestResultItem> testsToUpload = tests.getTestResultItems();
        int toUploadSize = testsToUpload.size();
        while (uploadCounter < testsToUpload.size()) {
            int toIndex = uploadCounter + UPLOAD_BATCH_SIZE < toUploadSize ? uploadCounter + UPLOAD_BATCH_SIZE : toUploadSize;
            List<TestResultItem> testBatch = testsToUpload.subList(uploadCounter, toIndex);

            List<TestRunDto> testRuns = new ArrayList<>(testBatch.size());
            for (TestResultItem test : testBatch) {
                Integer testCaseId = testCasesForCurrentTestRun.get(test.getFullName());
                testRuns.add(createTestRunObject(pluginConfiguration.getTestConfigurationId(), testSetId, parentTestRun, test, testCaseId));
            }

            TrackerItemDto[] createdRuns = rest.postTrackerItems(testRuns);
            List<TestCaseDto> testCaseDtos = new ArrayList<>(createdRuns.length);

            for (int i = 0; i < createdRuns.length; i++) {
                XUnitUtil.log(listener, String.format("TestRun created with name: %s and id: %s ", createdRuns[i].getName(), createdRuns[i].getId()));

                long duration = (long) (testBatch.get(i).getDuration() * 1000);
                TrackerItemDto createdRun = createdRuns[i];
                TestCaseDto testCaseDto = new TestCaseDto(createdRun.getId(), "Finished"); // meaning: closed
                testCaseDto.setSpentMillis(duration);
                testCaseDtos.add(testCaseDto);

                if (isReportingBugNeeded(testBatch.get(i), numberOfReportedBugs)) {
                    createBug(testBatch.get(i), createdRun);
                    numberOfReportedBugs++;
                }
            }

            updateTrackerItems(testCaseDtos);

            uploadCounter += createdRuns.length;
            if (uploadCounter % 100 == 0) {
                XUnitUtil.log(listener, "uploaded: " + uploadCounter + " test runs");
            }
        }

        updateTestSetTestCases(testSetId, testCasesForCurrentTestRun.values());
        updateTrackerItemStatus(parentTestRun.getId(), "Finished");
        updateTrackerItemStatus(testSetId, "Completed"); // meaning: resolved
        XUnitUtil.log(listener, "Upload finished, uploaded: " + uploadCounter + " test runs");
    }

    private String getCodebeamerVersion() throws IOException {
        return rest.getVersion();
    }

    private boolean isTestCaseTypeSupported() throws IOException {
        TrackerSchemaDto trackerSchemaDto = rest.getTestCaseTrackerSchema();
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
        PagedTrackerItemsDto pagedTrackerItemsDto = rest.getPagedTrackerItemForName(name);
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
        return rest.postTrackerItem(bug);
    }

    private String generateNameOfBug(TestResultItem test) {
        return "Bug of " + test.getName();
    }

    private TestRunDto createTestRunObject(Integer testConfigurationId, Integer testSetId, TrackerItemDto parentItem, TestResultItem test, Integer testCaseId) throws IOException {
        TestRunDto testRunDto = new TestRunDto(test.getName(), parentItem.getId(), pluginConfiguration.getTestRunTrackerId(),
                Arrays.asList(new Integer[]{testCaseId}), testConfigurationId, test.getResult());
        if (test.getErrorDetail() != null) {
            testRunDto.setDescription(String.format("{{{%s}}}", test.getErrorDetail()));
            testRunDto.setDescFormat("Wiki");
        } else {
            testRunDto.setDescription("--");
        }

        testRunDto.setTestSet(testSetId);
        Integer releaseId = pluginConfiguration.getReleaseId();
        if (releaseId != null) {
            testRunDto.setRelease(releaseId);
        }

        testRunDto.setBuild(pluginConfiguration.getBuild());
        return testRunDto;
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
        TestRunDto parentRunDto = new TestRunDto(buildIdentifier, null, pluginConfiguration.getTestRunTrackerId(), testCaseIds, testConfigurationId, tests.getStatus());
        parentRunDto.setTestSet(testSetId);
        parentRunDto.setDescription(tests.getTestSummary().toWikiMarkup() + newMarkupContent);
        parentRunDto.setDescFormat("Wiki");
        return rest.postTrackerItem(parentRunDto);
    }

    private void createRequirementInTree(Map<Integer, TrackerItemDto[]> verifiesMap, NodeMapping requirementsNodeMapping, TestResultItem test, Integer testCaseId) throws IOException {
        Integer requirementId = findOrCreateTrackerItemInTree(test.getFullName(), pluginConfiguration.getRequirementTrackerId(),
                requirementsNodeMapping, pluginConfiguration.getRequirementParentId(), pluginConfiguration.getRequirementDepth(), null);
        updateTestCaseVerifies(testCaseId, requirementId);
        verifiesMap.put(testCaseId, new TrackerItemDto[]{new TrackerItemDto()});
    }

    private Integer findOrCreateTrackerItem(Integer trackerId, String name, String description) throws IOException {
        PagedTrackerItemsDto pagedTrackerItemsDto = rest.getPagedTrackerItemsForName(trackerId, name);
        if (pagedTrackerItemsDto.getTotal() > 0) {
            return pagedTrackerItemsDto.getItems()[0].getId();
        } else {
            // no entry found => create one
            TestRunDto testConfig = new TestRunDto();
            testConfig.setName(name);
            testConfig.setTracker(String.format("/tracker/%s", trackerId));
            testConfig.setDescription(description);
            return rest.postTrackerItem(testConfig).getId();
        }
    }

    private String getBuildIdentifier(AbstractBuild<?, ?> build) {
        return build.getProject().getName() + " #" + build.getNumber();
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

        return rest.postTrackerItem(testCaseDto).getId();
    }

    public TrackerItemDto[] getTrackerItems(Integer trackerId) throws IOException {
        PagedTrackerItemsDto pagedTrackerItemsDto = rest.getTrackerItems(trackerId, 1);

        final int totalPages = (pagedTrackerItemsDto.getTotal() / PAGESIZE) + 1;
        List<TrackerItemDto> items = new ArrayList(Arrays.asList(pagedTrackerItemsDto.getItems()));
        for (int page = 2; page < totalPages; page++) {
            pagedTrackerItemsDto = rest.getTrackerItems(trackerId, page);
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
        return rest.updateTrackerItem(trackerItemDto);
    }

    private TrackerItemDto updateTestCaseVerifies(Integer testCaseId, Integer requirementId) throws IOException {
        TrackerItemDto[] verifies = new TrackerItemDto[]{ new TrackerItemDto("/item/" + requirementId) };
        TrackerItemDto trackerItemDto = new TrackerItemDto("/item/" + testCaseId, verifies);
        return rest.updateTrackerItem(trackerItemDto);
    }

    private TrackerItemDto updateTestRunStatusAndSpentTime(Integer id, String status, Long duration) throws IOException {
        TestCaseDto testCaseDto = new TestCaseDto(id, status);
        testCaseDto.setSpentMillis(duration);
        return rest.updateTestCaseItem(testCaseDto);
    }

    private TrackerItemDto updateTrackerItemStatus(Integer id, String status) throws IOException {
        TestCaseDto testCaseDto = new TestCaseDto(id, status);
        return rest.updateTestCaseItem(testCaseDto);
    }

    private TrackerItemDto updateTrackerItems(List<TestCaseDto> testCaseDtos) throws IOException {
        return rest.updateTestCaseItems(testCaseDtos);
    }

    public TrackerItemDto getTrackerItem(Integer itemId) throws IOException {
        return rest.getTrackerItem(itemId);
    }

    public TrackerDto getTrackerType(Integer trackerId) throws IOException {
        return rest.getTrackerType(trackerId);
    }

    public String getUserId(String author)  throws IOException {
        UserDto userDto = rest.getUserId(author);
        if (userDto != null) {
            String uri = userDto.getUri();
            return uri.substring(uri.lastIndexOf("/") + 1);
        }
        return null;
    }

    public String getCodeBeamerRepoUrlForGit(String repoUrl) throws IOException {
        String[] segments = repoUrl.split("/");
        String name = segments[segments.length - 1];
        RepositoryDto repositoryDto = rest.getRepositoryUrl(name, "git");

        return pluginConfiguration.getUri() + repositoryDto.getUri();
    }

    public String getCodeBeamerRepoUrlForSVN(String remote) {
        String[] segments = remote.split("/");
        // 0 = 'svn:' or 'http(s):', 1 = '', 2 = hostname
        for (int i = 3; i < segments.length; ++i) {
            String segment = segments[i];
            try {
                RepositoryDto repositoryDto = rest.getRepositoryUrl(segment, "svn");
                return pluginConfiguration.getUri() + repositoryDto.getUri();
            } catch (IOException ex) {
                continue;
            }
        }
        return "";
    }
}
