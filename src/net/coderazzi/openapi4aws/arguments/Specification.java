package net.coderazzi.openapi4aws.arguments;

import java.util.List;

public class Specification {
    private final String uri;
    private final boolean finalUri;
    private String security;
    private List<String> scopes;

    /**
     * @param uri The defined URI
     * @param finalUri Set to false for TAG specifications, where the final uri will be composed of this URI plus the
     *                 used path.
     */
    public Specification(String uri, boolean finalUri) {
        this.uri = !finalUri && uri.endsWith("/") ? uri.substring(0, uri.length()-1) : uri;
        this.finalUri = finalUri;
    }

    public void setSecurity(String security, List<String> scopes) {
        this.security = security;
        this.scopes = scopes;
    }

    /**
     * @return the defined scopes. This can be null if getSecurity is null, otherwise it is a valid list,
     * which could be empty, but cannot contain any blank strings
     */
    public List<String> getScopes() {
        return scopes;
    }

    /**
     * @return the defined security. It can be null, but not blank
     */
    public String getSecurity() {
        return security;
    }

    /**
     * @param path: the route used, that can be part of the returned URI.
     * @return the defined URI, which cannot be null or blank.
     */
    public String getUri(String path) {
        return finalUri? uri : uri + path;
    }
}
