/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.dto;

public class PluginConfiguration {
    private String uri;
    private String username;
    private String password;
    private Integer testSetTrackerId;
    private Integer testCaseTrackerId;
    private Integer testCaseParentId;
    private Integer testRunTrackerId;
    private Integer testConfigurationId;
    private Integer requirementTrackerId;
    private Integer requirementDepth;
    private Integer requirementParentId;
    private Integer bugTrackerId;
    private Integer numberOfBugsToReport;
    private String[] includedPackages;
    private String[] excludedPackages;
    private String[] truncatePackageTree;

    public PluginConfiguration() {
    }

    public PluginConfiguration(String uri, String username, String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getTestSetTrackerId() {
        return testSetTrackerId;
    }

    public void setTestSetTrackerId(Integer testSetTrackerId) {
        this.testSetTrackerId = testSetTrackerId;
    }

    public Integer getTestCaseTrackerId() {
        return testCaseTrackerId;
    }

    public void setTestCaseTrackerId(Integer testCaseTrackerId) {
        this.testCaseTrackerId = testCaseTrackerId;
    }

    public Integer getTestCaseParentId() {
        return testCaseParentId;
    }

    public void setTestCaseParentId(Integer testCaseParentId) {
        this.testCaseParentId = testCaseParentId;
    }

    public Integer getTestRunTrackerId() {
        return testRunTrackerId;
    }

    public void setTestRunTrackerId(Integer testRunTrackerId) {
        this.testRunTrackerId = testRunTrackerId;
    }

    public Integer getTestConfigurationId() {
        return testConfigurationId;
    }

    public void setTestConfigurationId(Integer testConfigurationId) {
        this.testConfigurationId = testConfigurationId;
    }

    public String[] getIncludedPackages() {
        return includedPackages;
    }

    public void setIncludedPackages(String[] includedPackages) {
        this.includedPackages = includedPackages;
    }

    public String[] getExcludedPackages() {
        return excludedPackages;
    }

    public void setExcludedPackages(String[] excludedPackages) {
        this.excludedPackages = excludedPackages;
    }

    public String[] getTruncatePackageTree() {
        return truncatePackageTree;
    }

    public void setTruncatePackageTree(String[] truncatePackageTree) {
        this.truncatePackageTree = truncatePackageTree;
    }

    public Integer getRequirementDepth() {
        return requirementDepth;
    }

    public void setRequirementDepth(Integer requirementDepth) {
        this.requirementDepth = requirementDepth;
    }

    public Integer getRequirementParentId() {
        return requirementParentId;
    }

    public void setRequirementParentId(Integer requirementParentId) {
        this.requirementParentId = requirementParentId;
    }

    public Integer getRequirementTrackerId() {
        return requirementTrackerId;
    }

    public void setRequirementTrackerId(Integer requirementTrackerId) {
        this.requirementTrackerId = requirementTrackerId;
    }

    public Integer getBugTrackerId() {
        return bugTrackerId;
    }

    public void setBugTrackerId(Integer bugTrackerId) {
        this.bugTrackerId = bugTrackerId;
    }

    public Integer getNumberOfBugsToReport() {
        return numberOfBugsToReport;
    }

    public void setNumberOfBugsToReport(Integer numberOfBugsToReport) {
        this.numberOfBugsToReport = numberOfBugsToReport;
    }
}
