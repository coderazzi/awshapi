package net.coderazzi.openapi4aws.arguments;

import java.util.List;

public class Specification {
    private final String uri;
    private String security;
    private List<String> scopes;

    public Specification(String uri) {
        this.uri = uri;
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
     * @return the defined URI, which cannot be null or blank.
     */
    public String getUri() {
        return uri;
    }
}
