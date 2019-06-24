package com.sap.cloud.lm.sl.cf.core.cf;

import java.util.ArrayList;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.ResilientCloudControllerClient;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.cloudfoundry.client.lib.oauth2.OAuthClient;
import org.cloudfoundry.client.lib.rest.CloudControllerRestClient;
import org.cloudfoundry.client.lib.rest.CloudControllerRestClientFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import com.sap.cloud.lm.sl.cf.client.CloudFoundryTokenProvider;
import com.sap.cloud.lm.sl.cf.client.TokenProvider;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.common.util.Pair;

@Named
public class CloudFoundryClientFactory extends ClientFactory {

    private ApplicationConfiguration configuration;
    private CloudControllerRestClientFactory clientFactory;
    private OAuthClientFactory oAuthClientFactory;

    @Inject
    public CloudFoundryClientFactory(ApplicationConfiguration configuration, OAuthClientFactory oAuthClientFactory) {
        this.clientFactory = new CloudControllerRestClientFactory(configuration.getControllerClientConnectionPoolSize(),
            configuration.getControllerClientThreadPoolSize(), configuration.shouldSkipSslValidation());
        this.configuration = configuration;
        this.oAuthClientFactory = oAuthClientFactory;
    }

    @Override
    protected Pair<CloudControllerClient, TokenProvider> createClient(CloudCredentials credentials) {
        OAuthClient oAuthClient = oAuthClientFactory.createOAuthClient();
        CloudControllerRestClient controllerClient = clientFactory.createClient(configuration.getControllerUrl(), credentials, null,
            oAuthClient);
        addTaggingInterceptor(controllerClient.getRestTemplate());
        return new Pair<>(new ResilientCloudControllerClient(controllerClient), new CloudFoundryTokenProvider(oAuthClient));
    }

    @Override
    protected Pair<CloudControllerClient, TokenProvider> createClient(CloudCredentials credentials, String org, String space) {
        OAuthClient oAuthClient = oAuthClientFactory.createOAuthClient();
        CloudControllerRestClient controllerClient = clientFactory.createClient(configuration.getControllerUrl(), credentials, org, space,
            oAuthClient);
        addTaggingInterceptor(controllerClient.getRestTemplate(), org, space);
        return new Pair<>(new ResilientCloudControllerClient(controllerClient), new CloudFoundryTokenProvider(oAuthClient));
    }

    protected Pair<CloudControllerClient, TokenProvider> createClient(CloudCredentials credentials, String spaceId) {
        CloudSpace target = computeTarget(credentials, spaceId);
        OAuthClient oAuthClient = oAuthClientFactory.createOAuthClient();
        CloudControllerRestClient controllerClient = clientFactory.createClient(configuration.getControllerUrl(), credentials, target,
            oAuthClient);
        addTaggingInterceptor(controllerClient.getRestTemplate(), target.getOrganization()
            .getName(), target.getName());
        return new Pair<>(new ResilientCloudControllerClient(controllerClient), new CloudFoundryTokenProvider(oAuthClient));
    }

    private void addTaggingInterceptor(RestTemplate template) {
        addTaggingInterceptor(template, null, null);
    }

    private void addTaggingInterceptor(RestTemplate template, String org, String space) {
        if (template.getInterceptors()
            .isEmpty()) {
            template.setInterceptors(new ArrayList<>());
        }
        ClientHttpRequestInterceptor requestInterceptor = new TaggingRequestInterceptor(configuration.getVersion(), org, space);
        template.getInterceptors()
            .add(requestInterceptor);
    }

    protected CloudSpace computeTarget(CloudCredentials credentials, String spaceId) {
        CloudControllerClient clientWithoutTarget = createClient(credentials)._1;
        return clientWithoutTarget.getSpace(UUID.fromString(spaceId));
    }

}
