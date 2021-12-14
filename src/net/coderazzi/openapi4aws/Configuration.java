package net.coderazzi.openapi4aws;

import java.util.List;
import java.util.Map;

public abstract class Configuration {
    public abstract Map<String, Authorizer> getAuthorizers();

    public abstract Integration getIntegration(String path, List<String> tags);

    public interface Authorizer {
        String getIdentitySource();

        String getIssuer();

        List<String> getAudiences();

        Map<Object, Object> getFlows();

        String getAuthorizationType();

        String getAuthorizerType();
    }

    public interface Integration {
        List<String> getScopes();

        String getAuthorizer();

        String getUri(String path);
    }

}
