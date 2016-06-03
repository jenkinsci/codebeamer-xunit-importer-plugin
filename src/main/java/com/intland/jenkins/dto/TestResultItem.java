/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.dto;

public class TestResultItem {
    //public enum RESULT {SUCCESS, FALIURE, SKIPPED}
    private String fullName;
    private String name;
    private float duration;
    private boolean successful;
    private String result;
    private String errorDetail;

    public TestResultItem(String fullName, float duration, boolean successful, String result) {
        this.fullName = fullName;
        this.name = fullName.substring(fullName.lastIndexOf(".") + 1);
        this.duration = duration;
        this.successful = successful;
        this.result = result;
    }

    public String getFullName() {
        return fullName;
    }

    public String getName() {
        return name;
    }

    public float getDuration() {
        return duration;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getResult() {
        return result;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public String getErrorDetail() {
        return errorDetail;
    }
}
