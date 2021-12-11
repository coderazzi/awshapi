package net.coderazzi.awshapi.arguments;

import java.util.List;

public class Path {
    private final String uri;
    private String security;
    private List<String> scopes;

    public Path(String uri){
        this.uri = uri;
    }

    public void setSecurity(String security, List<String> scopes){
        this.security = security;
        this.scopes = scopes;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public String getSecurity() {
        return security;
    }

    public String getUri() {
        return uri;
    }
}
