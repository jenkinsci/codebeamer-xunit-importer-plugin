/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.dto;

import java.util.List;

public class TestResults {
    private List<TestResultItem> testResultItems;
    private TestSummary testSummary;
    private String status;

    public TestResults(List<TestResultItem> testResultItems, TestSummary testSummary, String status) {
        this.testResultItems = testResultItems;
        this.testSummary = testSummary;
        this.status = status;
    }

    public List<TestResultItem> getTestResultItems() {
        return testResultItems;
    }

    public TestSummary getTestSummary() {
        return testSummary;
    }

    public String getStatus() {
        return status;
    }
}
