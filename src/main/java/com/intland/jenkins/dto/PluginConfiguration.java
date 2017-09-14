/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.dto;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

public class PluginConfiguration {
    private String uri;
    private StandardUsernamePasswordCredentials credentials;
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
    private Integer releaseId;
    private String build;
    private String[] includedPackages;
    private String[] excludedPackages;
    private String[] truncatePackageTree;

    public PluginConfiguration() {
    }

    public PluginConfiguration(String uri, StandardUsernamePasswordCredentials credentials) {
        this.uri = uri;
        this.credentials = credentials;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setCredentials(StandardUsernamePasswordCredentials credentials) {
        this.credentials = credentials;
    }

    public String getUsername() {
        return credentials != null ? credentials.getUsername() : "";
    }

    public String getPassword() {
        return credentials != null ? credentials.getPassword().getPlainText() : "";
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

    public Integer getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Integer releaseId) {
        this.releaseId = releaseId;
    }

    public String getBuild() {
        return build;
    }

    public void setBuild(String build) {
        this.build = build;
    }
}
