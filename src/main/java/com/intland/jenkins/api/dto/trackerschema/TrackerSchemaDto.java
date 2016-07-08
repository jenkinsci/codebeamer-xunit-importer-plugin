/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.api.dto.trackerschema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include= JsonSerialize.Inclusion.NON_NULL)
public class TrackerSchemaDto {
    private TrackerPropertiesDto properties;

    public TrackerPropertiesDto getProperties() {
        return properties;
    }

    public void setProperties(TrackerPropertiesDto properties) {
        this.properties = properties;
    }

    public boolean doesTypeContain(String type) {
        boolean result = false;

        if (properties != null && properties.getType() != null && properties.getType().getSettings() != null) {
            TrackerTypeSettingDto[] settings = properties.getType().getSettings();
            for (TrackerTypeSettingDto setting : settings) {
                if (setting.getName().equals(type)) {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }
}
