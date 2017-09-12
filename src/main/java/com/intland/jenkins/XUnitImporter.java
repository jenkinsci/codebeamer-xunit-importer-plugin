/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */
package com.intland.jenkins;

import com.intland.jenkins.api.CodebeamerApiClient;
import com.intland.jenkins.api.RestAdapter;
import com.intland.jenkins.api.dto.TrackerDto;
import com.intland.jenkins.api.dto.TrackerItemDto;
import com.intland.jenkins.dto.PluginConfiguration;
import com.intland.jenkins.dto.TestResults;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;


public class XUnitImporter extends Notifier implements SimpleBuildStep {
    public static final String PLUGIN_SHORTNAME = "codebeamer-xunit-importer";
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
    private Integer releaseId;
    private String build;
    private String includedPackages;
    private String excludedPackages;
    private String truncatePackageTree;

    @DataBoundConstructor
    public XUnitImporter(String uri, String username, String password, Integer testSetTrackerId, Integer testCaseTrackerId,
                         Integer testRunTrackerId, Integer testConfigurationId) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.testSetTrackerId = testSetTrackerId;
        this.testCaseTrackerId = testCaseTrackerId;
        this.testCaseParentId = null;
        this.testRunTrackerId = testRunTrackerId;
        this.testConfigurationId = testConfigurationId;
        this.requirementTrackerId = null;
        this.requirementDepth = null;
        this.requirementParentId = null;
        this.bugTrackerId = null;
        this.numberOfBugsToReport = null;
        this.releaseId = null;
        this.build = null;
        this.includedPackages = null;
        this.excludedPackages = null;
        this.truncatePackageTree = null;
    }

    @Deprecated
    public XUnitImporter(String uri, String username, String password, Integer testSetTrackerId, Integer testCaseTrackerId, Integer testCaseParentId,
                         Integer testRunTrackerId, Integer testConfigurationId, Integer requirementTrackerId, Integer requirementDepth,
                         Integer requirementParentId, Integer bugTrackerId, Integer numberOfBugsToReport, Integer releaseId, String build,
                         String includedPackages, String excludedPackages, String truncatePackageTree) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.testSetTrackerId = testSetTrackerId;
        this.testCaseTrackerId = testCaseTrackerId;
        this.testCaseParentId = testCaseParentId;
        this.testRunTrackerId = testRunTrackerId;
        this.testConfigurationId = testConfigurationId;
        this.requirementTrackerId = requirementTrackerId;
        this.requirementDepth = requirementDepth;
        this.requirementParentId = requirementParentId;
        this.bugTrackerId = bugTrackerId;
        this.numberOfBugsToReport = numberOfBugsToReport;
        this.releaseId = releaseId;
        this.build = build;
        this.includedPackages = includedPackages;
        this.excludedPackages = excludedPackages;
        this.truncatePackageTree = truncatePackageTree;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PluginConfiguration pluginConfiguration = getPluginConfiguration();

        RestAdapter restAdapter = new RestAdapter(pluginConfiguration, CodebeamerApiClient.HTTP_TIMEOUT_LONG, listener);
        CodebeamerApiClient apiClient = new CodebeamerApiClient(pluginConfiguration, listener, CodebeamerApiClient.HTTP_TIMEOUT_LONG, restAdapter);

        if (testCaseParentId != null ) {
            TrackerItemDto trackerItemDto = apiClient.getTrackerItem(testCaseParentId);
            if (trackerItemDto == null) {
                XUnitUtil.log(listener, "Test Case Top Node ID item does not exist");
                return;
            }

            pluginConfiguration.setTestCaseTrackerId(trackerItemDto.getTracker().getId());
        }

        if (requirementParentId != null ) {
            TrackerItemDto trackerItemDto = apiClient.getTrackerItem(requirementParentId);
            if (trackerItemDto == null) {
                XUnitUtil.log(listener, "Requirement Top Node ID item does not exist");
                return;
            }
            pluginConfiguration.setRequirementTrackerId(trackerItemDto.getTracker().getId());
        }


        AbstractTestResultAction action = build.getAction(AbstractTestResultAction.class);
        if (action == null) {
            // previous step failed to execute, e.g. no test report files found
            XUnitUtil.log(listener, "Previous action failed, aborting...");
            return;
        }

        TestResults testResults = XUnitUtil.getTestResultItems(action, pluginConfiguration);

        apiClient.postTestRuns(testResults, build, filePath);

    }

    // Getter for jenkins UI
    public Integer getTestCaseTrackerId() {
        return testCaseTrackerId;
    }

    public Integer getTestCaseParentId() {
        return testCaseParentId;
    }

    @DataBoundSetter
    public void setTestCaseParentId(Integer testCaseParentId) {
        this.testCaseParentId = testCaseParentId;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    public String getUri() {
        return uri;
    }

    public Integer getTestSetTrackerId() {
        return testSetTrackerId;
    }

    public Integer getTestRunTrackerId() {
        return testRunTrackerId;
    }

    public String getTruncatePackageTree() {
        return truncatePackageTree;
    }

    @DataBoundSetter
    public void setTruncatePackageTree(String truncatePackageTree) {
        this.truncatePackageTree = truncatePackageTree;
    }

    public String getIncludedPackages() {
        return includedPackages;
    }

    @DataBoundSetter
    public void setIncludedPackages(String includedPackages) {
        this.includedPackages = includedPackages;
    }

    public String getExcludedPackages() {
        return excludedPackages;
    }

    @DataBoundSetter
    public void setExcludedPackages(String excludedPackages) {
        this.excludedPackages = excludedPackages;
    }

    public Integer getRequirementDepth() {
        return requirementDepth;
    }

    @DataBoundSetter
    public void setRequirementDepth(Integer requirementDepth) {
        this.requirementDepth = requirementDepth;
    }

    public Integer getRequirementParentId() {
        return requirementParentId;
    }

    @DataBoundSetter
    public void setRequirementParentId(Integer requirementParentId) {
        this.requirementParentId = requirementParentId;
    }

    public Integer getTestConfigurationId() {
        return testConfigurationId;
    }

    public Integer getBugTrackerId() {
        return bugTrackerId;
    }

    @DataBoundSetter
    public void setBugTrackerId(Integer bugTrackerId) {
        this.bugTrackerId = bugTrackerId;
    }

    public Integer getNumberOfBugsToReport() {
        return numberOfBugsToReport;
    }

    @DataBoundSetter
    public void setNumberOfBugsToReport(Integer numberOfBugsToReport) {
        this.numberOfBugsToReport = numberOfBugsToReport;
    }

    public Integer getRequirementTrackerId() {
        return requirementTrackerId;
    }

    @DataBoundSetter
    public void setRequirementTrackerId(Integer requirementTrackerId) {
        this.requirementTrackerId = requirementTrackerId;
    }

    public Integer getReleaseId() {
        return releaseId;
    }

    @DataBoundSetter
    public void setReleaseId(Integer releaseId) {
        this.releaseId = releaseId;
    }

    public String getBuild() {
        return build;
    }

    @DataBoundSetter
    public void setBuild(String build) {
        this.build = build;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public PluginConfiguration getPluginConfiguration() {
        PluginConfiguration pluginConfiguration = new PluginConfiguration();
        pluginConfiguration.setUri(uri);
        pluginConfiguration.setUsername(username);
        pluginConfiguration.setPassword(password);
        pluginConfiguration.setTestCaseTrackerId(testCaseTrackerId);
        pluginConfiguration.setTestCaseParentId(testCaseParentId);
        pluginConfiguration.setTestSetTrackerId(testSetTrackerId);
        pluginConfiguration.setTestRunTrackerId(testRunTrackerId);
        pluginConfiguration.setTestConfigurationId(testConfigurationId);
        pluginConfiguration.setRequirementTrackerId(requirementTrackerId);
        pluginConfiguration.setRequirementDepth(requirementDepth);
        pluginConfiguration.setRequirementParentId(requirementParentId);
        pluginConfiguration.setBugTrackerId(bugTrackerId);
        pluginConfiguration.setNumberOfBugsToReport(numberOfBugsToReport == null ? 10 : numberOfBugsToReport);
        pluginConfiguration.setReleaseId(releaseId);
        pluginConfiguration.setBuild(build);
        pluginConfiguration.setIncludedPackages(includedPackages == null || includedPackages.trim().equals("") ? new String[]{} : includedPackages.split(";"));
        pluginConfiguration.setExcludedPackages(excludedPackages == null || excludedPackages.trim().equals("") ? new String[]{} : excludedPackages.split(";"));
        pluginConfiguration.setTruncatePackageTree(truncatePackageTree == null || truncatePackageTree.trim().equals("") ? new String[]{} : truncatePackageTree.split(";"));
        return pluginConfiguration;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Codebeamer xUnit Importer";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/" + PLUGIN_SHORTNAME + "/help/help.html";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckTestSetTrackerId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password) throws IOException {
            return validateTrackerType(value, new PluginConfiguration(uri, username, password), true, 108);
        }

        public FormValidation doCheckTestCaseTrackerId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password, @QueryParameter Integer testCaseParentId) throws IOException {
            if (testCaseParentId == null) {
                return validateTrackerType(value, new PluginConfiguration(uri, username, password), true, 102);
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckReleaseId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password) throws IOException {
            return validateTrackerItemWithTracker(value, new PluginConfiguration(uri, username, password), false, 103);
        }

        public FormValidation doCheckTestCaseParentId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password) throws IOException {
            return validateTrackerItemWithTracker(value, new PluginConfiguration(uri, username, password), false, 102);
        }

        public FormValidation doCheckRequirementTrackerId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password, @QueryParameter Integer requirementParentId) throws IOException {
            if (requirementParentId == null) {
                return validateTrackerType(value, new PluginConfiguration(uri, username, password), false, 5, 10);
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckRequirementParentId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password) throws IOException {
            return validateTrackerItemWithTracker(value, new PluginConfiguration(uri, username, password), false, 5, 10);
        }

        public FormValidation doCheckTestRunTrackerId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password) throws IOException {
            return validateTrackerType(value, new PluginConfiguration(uri, username, password), true, 9);
        }

        public FormValidation doCheckTestConfigurationId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password) throws IOException {
            return validateTrackerItemWithTracker(value, new PluginConfiguration(uri, username, password), true, 109);
        }

        public FormValidation doCheckBugTrackerId(@QueryParameter Integer value, @QueryParameter String uri,  @QueryParameter String username, @QueryParameter String password) throws IOException {
            return validateTrackerType(value, new PluginConfiguration(uri, username, password), false, 2);
        }

        private FormValidation validateTrackerItemWithTracker(Integer value, PluginConfiguration pluginConfiguration, boolean required, Integer... validTrackerTypeIds) {
            FormValidation result = FormValidation.ok();
            if (value != null) {
                try {
                    RestAdapter restAdapter = new RestAdapter(pluginConfiguration, CodebeamerApiClient.HTTP_TIMEOUT_SHORT, null);
                    CodebeamerApiClient apiClient = new CodebeamerApiClient(pluginConfiguration, null, CodebeamerApiClient.HTTP_TIMEOUT_SHORT, restAdapter);
                    TrackerItemDto trackerItem = apiClient.getTrackerItem(value);
                    if (trackerItem != null) {
                        Integer trackerId = trackerItem.getTracker().getId();
                        result = validateTrackerType(trackerId, pluginConfiguration, false, validTrackerTypeIds);
                    } else {
                        result = FormValidation.error("Tracker Item can not be found");
                    }
                } catch (IOException e) {
                    result = FormValidation.error("codeBeamer could not be reached with the provided uri/credentials; IOException: " + e.getMessage());
                }
            } else if (required) {
                result = FormValidation.error("This field is required");
            }
            return result;
        }

        private FormValidation validateTrackerType(Integer value, PluginConfiguration pluginConfiguration, boolean required, Integer... validTrackerTypeIds) {
            FormValidation result = FormValidation.ok();

            if (value != null) {
                try {
                    boolean valid = checkTrackerType(pluginConfiguration, value, validTrackerTypeIds);
                    if (valid) {
                        result = FormValidation.ok();
                    } else {
                        result = FormValidation.error("Tracker Type does not match the required Type");
                    }
                } catch (IOException e) {
                    result = FormValidation.error("codeBeamer could not be reached with the provided uri/credentials; IOException: " + e.getMessage());
                }
            } else if (required) {
                result = FormValidation.error("This field is required");
            }

            return result;
        }

        private boolean checkTrackerType(PluginConfiguration pluginConfiguration, Integer trackerId, Integer... validTrackerTypeIds) throws IOException {
            RestAdapter restAdapter = new RestAdapter(pluginConfiguration, CodebeamerApiClient.HTTP_TIMEOUT_SHORT, null);
            CodebeamerApiClient apiClient = new CodebeamerApiClient(pluginConfiguration, null, CodebeamerApiClient.HTTP_TIMEOUT_SHORT, restAdapter);
            TrackerDto trackerDto = apiClient.getTrackerType(trackerId);

            if (trackerDto != null) {
                Integer typeId = trackerDto.getType().getTypeId();
                if (typeId == null) {
                    return false;
                }

                for (Integer validTrackerTypeId : validTrackerTypeIds) {
                    if (typeId.intValue() == validTrackerTypeId.intValue()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }
}

