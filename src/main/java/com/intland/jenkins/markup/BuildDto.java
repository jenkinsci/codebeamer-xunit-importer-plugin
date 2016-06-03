/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.markup;

public class BuildDto {
    private String pluginVersion;
    private String projectUrl;
    private String buildUrl;
    private String formattedBuildDuration;
    private String formattedBuildTime;
    private String buildNumber;
    private String builtOn;
    private long currentTime;
    private long buildDuration;

    public BuildDto(String pluginVersion, String projectUrl, String buildUrl, String formattedBuildDuration,
                    String formattedBuildTime, String buildNumber, String builtOn, long currentTime, long buildDuration) {
        this.pluginVersion = pluginVersion;
        this.projectUrl = projectUrl;
        this.buildUrl = buildUrl;
        this.formattedBuildDuration = formattedBuildDuration;
        this.formattedBuildTime = formattedBuildTime;
        this.buildNumber = buildNumber;
        this.builtOn = builtOn;
        this.currentTime = currentTime;
        this.buildDuration = buildDuration;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String getProjectUrl() {
        return projectUrl;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public String getFormattedBuildDuration() {
        return formattedBuildDuration;
    }

    public String getFormattedBuildTime() {
        return formattedBuildTime;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getBuiltOn() {
        return builtOn;
    }

    public long getBuildDuration() {
        return buildDuration;
    }

    public long getCurrentTime() {
        return currentTime;
    }
}