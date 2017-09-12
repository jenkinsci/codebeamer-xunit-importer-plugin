/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */
package com.intland.jenkins.markup;

import com.intland.jenkins.api.CodebeamerApiClient;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.scm.ChangeLogSet;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.SubversionTagAction;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScmDataCollector {
    private static final Pattern scmTaskIdPattern = Pattern.compile("(#([1-9][0-9]{3,9})((,|\\s+)[1-9][0-9]{3,9})*)(?:\\z|[\\s.,;:)/\\-]+)");

    public static ScmDto collectScmData(Run<?, ?> build, CodebeamerApiClient apiClient) throws IOException {
        String repositoryLine = "Unsupported SCM";
        String changes = "";

        if (PluginUtil.isGitPluginInstalled() && build.getAction(BuildData.class) != null) {
            BuildData gitScm = build.getAction(BuildData.class);
            String repoUrl = (String)(gitScm.getRemoteUrls()).toArray()[0];

            String cbRepoUrl = apiClient.getCodeBeamerRepoUrlForGit(repoUrl);

            Revision revision = gitScm.getLastBuiltRevision();
            if (revision != null) { //revision can be null for first shallow clone
                String repoRevision = gitScm.getLastBuiltRevision().getSha1String();
                String repoBranchName =  ((List<Branch>) gitScm.getLastBuiltRevision().getBranches()).get(0).getName();
                repositoryLine = String.format("%s, %s, branch: %s", cbRepoUrl, repoRevision, repoBranchName);
            } else {
                repositoryLine = String.format("%s, revision information not available with shallow clone at first run", cbRepoUrl);
            }
        } else if (PluginUtil.isMercurialPluginInstalled() && build.getAction(MercurialTagAction.class) != null) {
            MercurialTagAction hgScm = build.getAction(MercurialTagAction.class);
            repositoryLine = hgScm.getId();
        } else if (PluginUtil.isSubversionPluginInstalled() && build.getAction(SubversionTagAction.class) != null) {
            // SVN
            SubversionTagAction svnScm = build.getAction(SubversionTagAction.class);
            AbstractBuild mybuild = svnScm.getBuild();
            SubversionSCM scm = (SubversionSCM) mybuild.getProject().getScm();
            ModuleLocation[] locs = scm.getLocations();
            String remote = locs[0].remote;

            String cbRepoUrl = apiClient.getCodeBeamerRepoUrlForSVN(remote);
            repositoryLine = String.format("%s", cbRepoUrl);
        }

        // This is only called when there has been a commit since the last run
        ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet = null;
        if (build instanceof AbstractBuild<?, ?>) {
            changeLogSet = ((AbstractBuild) build).getChangeSet();
        } else {
            try {
                List<ChangeLogSet<? extends  ChangeLogSet.Entry>> list = (List<ChangeLogSet<? extends ChangeLogSet.Entry>>) build.getClass().getMethod("getChangeSets").invoke(build);
                if (!list.isEmpty()) {
                    changeLogSet = list.get(0);
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {

            }
        }
        if (changeLogSet != null) {
            for (ChangeLogSet.Entry entry: changeLogSet) {
                String author = getAuthorString(entry);
                String commitMessage = getCommitMessage(entry);
                String commitMessageWithTaskLink = getCodebeamerTaskLink(commitMessage);
                String formattedUser = String.format("%s", author);

                changes += String.format("* %s %s\n", commitMessageWithTaskLink, formattedUser);
            }
        }
        return new ScmDto(repositoryLine, changes);
    }

    private static String getAuthorString(ChangeLogSet.Entry entry) {
        if (entry instanceof GitChangeSet) {
            return String.format("%s (%s)", ((GitChangeSet) entry).getAuthorName(), ((GitChangeSet) entry).getAuthorEmail());
        }
        return entry.getAuthor().toString();
    }

    //Special treatment for git, entry.getMsg() truncates multiline git comments
    private static String getCommitMessage(ChangeLogSet.Entry entry) {
        String resultUnescaped = entry.getMsg();
        if (entry instanceof GitChangeSet) {
            resultUnescaped = ((GitChangeSet) entry).getComment();
        }

        String result = resultUnescaped.trim()
                .replaceAll("\\n", " ")
                .replaceAll("\\t", " ")
                .replaceAll("\\*", " \\\\\\\\*");
        return result;
    }

    private static String getCodebeamerTaskLink(String gitCommitMessage) {
        Matcher commitMessageMatcher = scmTaskIdPattern.matcher(gitCommitMessage);
        String result = gitCommitMessage;

        List<String> issues = new ArrayList<String>();
        while (commitMessageMatcher.find()) {
            issues.add(commitMessageMatcher.group(1));
        }

        if (issues.size() > 0) {
            for (String issue : issues) {
                String link = String.format("[%s|ISSUE:%s]", issue, issue.replace("#",""));
                result = result.replace(issue, link);
            }
        }

        return result;
    }
}
