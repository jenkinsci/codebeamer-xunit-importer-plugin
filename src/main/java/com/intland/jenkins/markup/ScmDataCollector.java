/*
 * Copyright (c) 2016 Intland Software (support@intland.com)
 */
package com.intland.jenkins.markup;

import com.intland.jenkins.api.CodebeamerApiClient;
import hudson.model.AbstractBuild;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.scm.ChangeLogSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScmDataCollector {
    private static final Pattern scmTaskIdPattern = Pattern.compile("(#([1-9][0-9]{3,9})((,|\\s+)[1-9][0-9]{3,9})*)(?:\\z|[\\s.,;:)/\\-]+)");

    public static ScmDto collectScmData(AbstractBuild<?, ?> build, CodebeamerApiClient apiClient) throws IOException {
        String repositoryLine = "Unsupported SCM";
        String changes = "";

        if (PluginUtil.isGitPluginInstalled() && build.getAction(BuildData.class) != null) {
            BuildData gitScm = build.getAction(BuildData.class);
            String repoUrl = (String)(gitScm.getRemoteUrls()).toArray()[0];
            Revision revision = gitScm.getLastBuiltRevision();
            if (revision != null) { //revision can be null for first shallow clone
                String repoRevision = gitScm.getLastBuiltRevision().getSha1String();
                String repoBranchName =  ((List<Branch>) gitScm.getLastBuiltRevision().getBranches()).get(0).getName();
                repositoryLine = String.format("[%s], %s, branch: %s", repoUrl, repoRevision, repoBranchName);
            } else {
                repositoryLine = String.format("[%s], revision information not available with shallow clone at first run", repoUrl);
            }
        } else if (PluginUtil.isMercurialPluginInstalled() && build.getAction(MercurialTagAction.class) != null) {
            MercurialTagAction hgScm = build.getAction(MercurialTagAction.class);
            repositoryLine = hgScm.getId();
        }

        for (ChangeLogSet.Entry entry : build.getChangeSet()) {
            String author = entry.getAuthor().toString();
            String userId = apiClient.getUserId(author);
            String commitMessage = getCommitMessage(entry);
            String commitMessageWithTaskLink = getCodebeamerTaskLink(commitMessage);
            String formattedUser = userId == null ? String.format("(%s)", author) : String.format("([USER:%s])", userId);

            changes += String.format("* %s %s\n", commitMessageWithTaskLink, formattedUser);
        }

        return new ScmDto(repositoryLine, changes);
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
