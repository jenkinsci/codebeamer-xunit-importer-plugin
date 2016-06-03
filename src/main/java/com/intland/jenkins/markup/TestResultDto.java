/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.markup;

public class TestResultDto {
    private String formattedTestDuration;
    private int totalCount;
    private int failCount;
    private String failedDifference;
    private long testDuration;

    public TestResultDto(String formattedTestDuration, int totalCount, int failCount, String failedDifference, long testDuration) {
        this.formattedTestDuration = formattedTestDuration;
        this.totalCount = totalCount;
        this.failCount = failCount;
        this.failedDifference = failedDifference;
        this.testDuration = testDuration;
    }

    public String getFormattedTestDuration() {
        return formattedTestDuration;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public String getFailedDifference() {
        return failedDifference;
    }

    public long getTestDuration() {
        return testDuration;
    }
}
