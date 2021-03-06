package com.redhat.engineering.plugins.services;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.redhat.engineering.plugins.domain.Session;
import com.redhat.engineering.plugins.domain.Vote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vdedik@redhat.com
 */
@SuppressWarnings("unchecked")
public class VoteService extends AbstractPokerService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final PluginSettings pluginSettings;
    private final IssueService issueService;
    private final JiraAuthenticationContext authContext;
    private final UserManager userManager;

    public VoteService(PluginSettingsFactory pluginSettingsFactory, IssueService issueService,
                          JiraAuthenticationContext authContext, UserManager userManager) {
        this.pluginSettings = pluginSettingsFactory.createGlobalSettings();
        this.issueService = issueService;
        this.authContext = authContext;
        this.userManager = userManager;
    }

    public void save(Vote vote) {
        String issueStoreKey = getIssueStoreKey(vote.getSession().getIssue());

        List<String> voters = getList(issueStoreKey + ".voters");
        if (!voters.contains(vote.getVoter().getKey())) {
            voters.add(vote.getVoter().getKey());
            pluginSettings.put(issueStoreKey + ".voters", voters);
        }

        String valueKey = issueStoreKey + "." + vote.getVoter().getKey();
        String originalValue = (String) pluginSettings.get(valueKey);
        pluginSettings.put(valueKey, vote.getValue());

        List<String> votes = getList(issueStoreKey + ".votes");
        if (originalValue != null) {
            votes.remove(originalValue);
        }
        votes.add(vote.getValue());
        pluginSettings.put(issueStoreKey + ".votes", votes);

        String commentKey = issueStoreKey + "." + vote.getVoter().getKey() + ".comment";
        pluginSettings.put(commentKey, vote.getComment());
    }

    public List<String> getVoteValsBySession(Session session) {
        String storeKey = getIssueStoreKey(session.getIssue()) + ".votes";
        return getList(storeKey);
    }

    public List<Vote> getVotesBySession(Session session) {
        String issueStoreKey = getIssueStoreKey(session.getIssue());
        String votersKey = issueStoreKey + ".voters";
        List<String> voters = getList(votersKey);

        List<Vote> votes = new ArrayList<Vote>();
        for (String voterKey : voters) {
            Vote vote = new Vote();
            vote.setSession(session);
            vote.setVoter(userManager.getUserByKey(voterKey));
            String voteVal = (String) pluginSettings.get(issueStoreKey + "." + voterKey);
            vote.setValue(voteVal);
            String comment = (String) pluginSettings.get(issueStoreKey + "." + voterKey + ".comment");
            vote.setComment(comment);
            votes.add(vote);
        }

        return votes;
    }

    public List<ApplicationUser> getVotersBySession(Session session) {
        String issueStoreKey = getIssueStoreKey(session.getIssue());
        List<String> votersRaw = getList(issueStoreKey + ".voters");
        List<ApplicationUser> voters = new ArrayList<ApplicationUser>();

        for (String voterRaw : votersRaw) {
            ApplicationUser voter = userManager.getUserByKey(voterRaw);
            voters.add(voter);
        }
        return voters;
    }

    public boolean isVoter(Session session, ApplicationUser user) {
        return getVoteVal(session, user) != null;
    }

    public String getVoteVal(Session session, ApplicationUser user) {
        return (String) pluginSettings.get(getIssueStoreKey(session.getIssue()) + "." + user.getKey());
    }

    public String getVoteComment(Session session, ApplicationUser user) {
        return (String) pluginSettings.get(getIssueStoreKey(session.getIssue()) + "." + user.getKey() + ".comment");
    }

    public void removeAllVotes(Session session) {
        String issueStoreKey = getIssueStoreKey(session.getIssue());
        pluginSettings.remove(issueStoreKey + ".votes");
        List<String> voters = getList(issueStoreKey + ".voters");
        for (String voter : voters) {
            pluginSettings.remove(issueStoreKey + "." + voter);
            pluginSettings.remove(issueStoreKey + "." + voter + ".comment");
        }
        pluginSettings.remove(issueStoreKey + ".voters");
    }

    private <T> List<T> getList(String storeKey) {
        List<T> result = (List<T>) pluginSettings.get(storeKey);
        if (result == null) {
            result = new ArrayList<T>();
        }
        return result;
    }
}
