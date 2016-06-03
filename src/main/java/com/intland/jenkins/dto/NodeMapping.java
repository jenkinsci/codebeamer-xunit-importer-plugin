/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */

package com.intland.jenkins.dto;

import java.util.Map;

public class NodeMapping {
    private Map<Integer, String> idNodeMapping;
    private Map<String, Integer> nodeIdMapping;

    public NodeMapping(Map<Integer, String> idNodeMapping, Map<String, Integer> nodeIdMapping) {
        this.idNodeMapping = idNodeMapping;
        this.nodeIdMapping = nodeIdMapping;
    }

    public void setNodeIdMapping(Map<String, Integer> nodeIdMapping) {
        this.nodeIdMapping = nodeIdMapping;
    }

    public Map<Integer, String> getIdNodeMapping() {
        return idNodeMapping;
    }

    public Map<String, Integer> getNodeIdMapping() {
        return nodeIdMapping;
    }
}
