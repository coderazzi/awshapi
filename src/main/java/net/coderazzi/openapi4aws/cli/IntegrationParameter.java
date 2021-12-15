package net.coderazzi.openapi4aws.cli;

import net.coderazzi.openapi4aws.Configuration;

import java.util.List;

class IntegrationParameter implements Configuration.Integration {
    private final String uri;
    private final boolean finalUri;
    private String authorizer;
    private List<String> scopes;

    /**
     * @param uri      The defined URI
     * @param finalUri Set to false for TAG specifications, where the final uri will be composed of this URI plus the
     *                 used path.
     */
    public IntegrationParameter(String uri, boolean finalUri) {
        this.uri = !finalUri && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        this.finalUri = finalUri;
    }

    public void setAuthorization(String authorizer, List<String> scopes) {
        this.authorizer = authorizer;
        this.scopes = scopes;
    }

    /**
     * @return the defined scopes. This can be null if getAuthorizer is null, otherwise it is a valid list,
     * which could be empty, but cannot contain any blank strings
     */
    @Override
    public List<String> getScopes() {
        return scopes;
    }

    /**
     * @return the defined authorizer. It can be null, but not blank
     */
    @Override
    public String getAuthorizer() {
        return authorizer;
    }

    /**
     * @param path: the route used, that can be part of the returned URI.
     * @return the defined URI, which cannot be null or blank.
     */
    @Override
    public String getUri(String path) {
        return finalUri ? uri : uri + path;
    }
}
