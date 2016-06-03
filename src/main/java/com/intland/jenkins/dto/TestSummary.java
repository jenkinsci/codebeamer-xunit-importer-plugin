/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.dto;

public class TestSummary {
    private int testCount;
    private int successCount;
    private int failCount;

    public TestSummary(int testCount, int successCount, int failCount) {
        this.testCount = testCount;
        this.successCount = successCount;
        this.failCount = failCount;
    }

    public String toWikiMarkup() {
        return String.format("[{ PieChart title='Test Result' threed='true' seriespaint='GREEN,RED' \n\n" +
                "Successful, %s\n" +
                "Failure, %s\n" +
         "}]", successCount, failCount);
    }

    public int getTestCount() {
        return testCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailCount() {
        return failCount;
    }
}
