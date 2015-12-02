package org.jenkinsci.plugins.bitbucket;

import hudson.Extension;
import hudson.Launcher;
import hudson.ProxyConfiguration;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.git.Revision;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.List;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

@Extension
public class BitbucketBuildStatusNotifier extends Notifier {

    private static final Logger LOG = Logger.getLogger(BitbucketBuildStatusNotifier.class);

    private static final String BITBUCKET_ORG = "bitbucket.org";

    private String credentialsId;

    public BitbucketBuildStatusNotifier() {
        this(StringUtils.EMPTY);
    }

    @DataBoundConstructor
    public BitbucketBuildStatusNotifier(String credentialsId) {
        super();
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        GitSCM scm = getGitSCM(build);
        if (scm == null) {
            listener.error("[Bitbucket Build Status Plugin] This plugin supports only Git SCM.");
            return true;
        }

        URIish uri = scm.getRepositories().get(0).getURIs().get(0);
        if (!StringUtils.equals(BITBUCKET_ORG, uri.getHost())) {
            listener.error("[Bitbucket Build Status Plugin] This plugin supports only Bitbucket repository.");
            return true;
        }

        if (StringUtils.isEmpty(getCredentialsId())) {
            listener.error("[Bitbucket Build Status Plugin] Set a credentials to access to Bitbucket API.");
            return true;
        }

        StandardUsernamePasswordCredentials credential = getCredentialById(getCredentialsId());
        if (credential == null) {
            listener.error("[Bitbucket Build Status Plugin] Invalid credential.");
            return true;
        }

        if (build.getResult().equals(Result.ABORTED) || build.getResult().equals(Result.NOT_BUILT)) {
            listener.error("[Bitbucket Build Status Plugin] Invalid credential.");
            return true;
        }

        String[] strings = StringUtils.split(uri.getPath(), '/');

        String owner = strings[0];
        String repoSlug = StringUtils.removeEnd(strings[1], ".git");
        Revision revision = scm.getBuildData(build).getLastBuiltRevision();

        String url = "https://api.bitbucket.org/2.0/repositories/" + owner + "/" + repoSlug + "/commit/" + revision.getSha1String() + "/statuses/build";

        HttpClient client = getHttpClient();
        PostMethod post = null;
        try {
            post = new PostMethod(url);
            post.addRequestHeader("Authorization", "Basic " + Base64.encodeBase64String((credential.getUsername() + ":" + credential.getPassword().getPlainText()).getBytes()));

            JSONObject field = new JSONObject();
            field.put("state", build.getResult().equals(Result.SUCCESS) ? "SUCCESSFUL" : "FAILED"); // INPROGRESS
            field.put("name", "Build #" + build.getNumber());
            field.put("url", Jenkins.getInstance().getRootUrl() + build.getUrl());
            field.put("key", "JENKINS-BUILD-" + build.getNumber());

            post.setRequestEntity(new StringRequestEntity(field.toString(), "application/json", "utf-8"));

            int response = client.executeMethod(post);
            if (response != HttpStatus.SC_CREATED) {
                listener.error(post.getResponseBodyAsString());
            }
        } catch (Exception e) {
            listener.error("[Bitbucket Build Status Plugin] " + e.getLocalizedMessage());
            LOG.error("Failed to update build status", e);
        } finally {
            if (post != null) {
                post.releaseConnection();
            }
        }
        return true;
    }

    protected HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        if (Jenkins.getInstance() != null) {
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (proxy != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    LOG.info("Using proxy authentication (user=" + username + ")");
                    // http://hc.apache.org/httpclient-3.x/authentication.html#Proxy_Authentication
                    // and
                    // http://svn.apache.org/viewvc/httpcomponents/oac.hc3x/trunk/src/examples/BasicAuthenticationExample.java?view=markup
                    client.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }

    protected GitSCM getGitSCM(AbstractBuild<?, ?> build) {
        if (build.getProject() != null) {
            SCM scm = build.getProject().getScm();
            if (scm != null && scm instanceof GitSCM) {
                return (GitSCM) scm;
            }
        }
        return null;
    }

    static StandardUsernamePasswordCredentials getCredentialById(String credentialId) {
        List<StandardUsernamePasswordCredentials> credentials = getCredentials(Jenkins.getInstance());
        for (StandardUsernamePasswordCredentials credential : credentials) {
            if (StringUtils.equals(credential.getId(), credentialId)) {
                return credential;
            }
        }
        return null;
    }

    static List<StandardUsernamePasswordCredentials> getCredentials(Jenkins context) {
        List<DomainRequirement> requirements = URIRequirementBuilder.create().withHostname(BITBUCKET_ORG).build();
        List<StandardUsernamePasswordCredentials> credencials = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM, requirements);
        return credencials;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Jenkins context, @QueryParameter String remoteBase) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            result.withMatching(GitClient.CREDENTIALS_MATCHER, getCredentials(context));
            return result;
        }

        @Override
        public String getHelpFile() {
            return "/plugin/bitbucket-build-status-plugin/BitbucketBuildStatusNotifier.html";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> project) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket Build Status";
        }
    }
}
