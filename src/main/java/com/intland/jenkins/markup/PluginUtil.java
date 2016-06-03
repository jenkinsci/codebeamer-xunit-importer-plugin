/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */
package com.intland.jenkins.markup;

import jenkins.model.Jenkins;

public class PluginUtil {

    public static boolean isGitPluginInstalled() {
        return isPluginInstalled("git");
    }

    public static boolean isMercurialPluginInstalled() {
        return isPluginInstalled("mercurial");
    }

    private static boolean isPluginInstalled(String pluginName) {
        return Jenkins.getInstance().getPlugin(pluginName) != null;
    }
}
