/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */
package com.intland.jenkins;

import com.intland.jenkins.api.dto.TrackerItemDto;
import com.intland.jenkins.dto.*;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;

import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

public class XUnitUtil {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final String SUCCESS_STATUS = "Passed";
    private static final String FAILED_STATUS = "Failed";

    public static TestResults getTestResultItems(AbstractTestResultAction action, PluginConfiguration pluginConfiguration) {
        List<TestResultItem> cbCases = new ArrayList<>();
        int testCount = 0;
        int successCount = 0;
        int failCount = 0;
        for (SuiteResult suiteResult : getSuiteResultsFromAction(action)) {
            Collection<CaseResult> cases = suiteResult.getCases();
            for (CaseResult caseResult : cases) {
                String name = caseResult.getFullName();
                if (isAllowedByPackageFilters(name, pluginConfiguration)) {
                    for (String truncatePackage : pluginConfiguration.getTruncatePackageTree()) {
                        if (name.startsWith(truncatePackage.trim())) {
                            name = name.substring(truncatePackage.length() + 1);
                            break;
                        }
                    }

                    boolean passed = caseResult.getFailCount() == 0;
                    TestResultItem testResultItem = new TestResultItem(name, caseResult.getDuration(),
                                                                passed, passed ? SUCCESS_STATUS : FAILED_STATUS);

                    if (passed) {
                        successCount++;
                    } else {
                        testResultItem.setErrorDetail(String.format("%s\n%s", caseResult.getErrorDetails(), caseResult.getErrorStackTrace()));
                        failCount++;
                    }

                    cbCases.add(testResultItem);
                    testCount++;
                }
            }
        }

        String overallStatus = failCount == 0 ? SUCCESS_STATUS : FAILED_STATUS;
        return new TestResults(cbCases, new TestSummary(testCount, successCount, failCount), overallStatus);
    }

    public static NodeMapping getNodeMapping(TrackerItemDto[] trackerItems) {
        Arrays.sort(trackerItems, new Comparator<TrackerItemDto>() {
            @Override
            public int compare(TrackerItemDto o1, TrackerItemDto o2) {
                return o1.getId().intValue() - o2.getId().intValue();
            }
        });

        Map<Integer, String> idNodeMapping = new HashMap<>();
        Map<String, Integer> nodeIdMapping = new HashMap<>();
        for (TrackerItemDto trackerItem : trackerItems) {
            Integer key = trackerItem.getId();
            String value;

            if (trackerItem.getParent() == null) {
                value = trackerItem.getName();
            } else {
                value = idNodeMapping.get(trackerItem.getParent().getId()) + "." + trackerItem.getName();
            }

            idNodeMapping.put(key, value);
            if (nodeIdMapping.get(value) == null) {
                nodeIdMapping.put(value, key);
            }

        }

        return new NodeMapping(idNodeMapping, nodeIdMapping);
    }

    public static Map<Integer, TrackerItemDto[]> getVerifiesMap(TrackerItemDto[] testCases) {
        Map<Integer, TrackerItemDto[]> result = new HashMap<>();

        for (TrackerItemDto testCase : testCases) {
            if (testCase.getVerifies() != null && testCase.getVerifies().length > 0) {
                result.put(testCase.getId(), testCase.getVerifies());
            }
        }

        return result;
    }

    public static String limitName(String name, Integer limit, String separator) {
        String result = name;
        if (limit != null && name.indexOf(separator) > -1) {
            StringTokenizer tokenizer = new StringTokenizer(name, separator);
            int limitCounter = 1;
            result = null;

            while (tokenizer.hasMoreElements() && limitCounter <= limit.intValue()) {
                String token = tokenizer.nextToken();
                result = result == null ? token : result + separator + token;
                limitCounter++;
            }
        }

        return result;
    }

    public static void log(TaskListener listener, String message) {
        String log = String.format("%s %s", DATE_FORMAT.format(new Date()), message);
        listener.getLogger().println(log);
    }

    public static String getStringFromInputStream(InputStream is) throws IOException {
        BufferedReader bufferedReader = null;
        StringBuilder stringBuilder = new StringBuilder();

        String line;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(is));
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }

        return stringBuilder.toString();
    }

    public static String encodeParam(String param) {
        try {
            String result = URLEncoder.encode(param, "UTF-8");
            return result.replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            return param;
        }
    }

    private static List<SuiteResult> getSuiteResultsFromAction(AbstractTestResultAction action) {
        List<SuiteResult> suiteResults = new ArrayList<>();
        if (action.getResult() instanceof List) { // aggregate/ maven Result
            List<AggregatedTestResultAction.ChildReport> childReports = (List<AggregatedTestResultAction.ChildReport>) action.getResult();
            for (AggregatedTestResultAction.ChildReport childReport : childReports) {
                TestResult testResult = (TestResult) childReport.result;
                for (SuiteResult suiteResult : testResult.getSuites()) {
                    suiteResults.add(suiteResult);
                }
            }

        } else { // plain junit result
            TestResult testResult = (TestResult) action.getResult();
            for (SuiteResult suiteResult : testResult.getSuites()) {
                suiteResults.add(suiteResult);
            }
        }
        return suiteResults;
    }

    private static boolean isAllowedByPackageFilters(String name, PluginConfiguration pluginConfiguration) {
        boolean include = pluginConfiguration.getIncludedPackages().length == 0;
        boolean exclude = false;

        for (String includedPackage : pluginConfiguration.getIncludedPackages()) {
            if (name.startsWith(includedPackage.trim())) {
                include = true;
                break;
            }
        }

        for (String excludedPackage : pluginConfiguration.getExcludedPackages()) {
            if (name.startsWith(excludedPackage.trim())) {
                exclude = true;
                break;
            }
        }

        return include && !exclude;
    }

    // http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
    public static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }
}
