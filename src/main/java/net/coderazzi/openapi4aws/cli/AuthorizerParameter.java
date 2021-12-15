package net.coderazzi.openapi4aws.cli;

import net.coderazzi.openapi4aws.Configuration;
import net.coderazzi.openapi4aws.O4A_Exception;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class AuthorizerParameter implements Configuration.Authorizer {

    private final static Map<Object, Object> AUTHORIZER_FLOWS = Collections.emptyMap();
    private final static String AUTHORIZATION_TYPE = "oauth2";
    private final static String AUTHORIZER_TYPE = "jwt";

    private final AuthorizerParameter defaultAuthorizer;
    private final Map<Object, Object> flows = AUTHORIZER_FLOWS;
    private String identitySource;
    private String issuer;
    private List<String> audiences;
    private String authorizationType;
    private String type;

    public AuthorizerParameter(AuthorizerParameter defaultAuthorizer) {
        this.defaultAuthorizer = defaultAuthorizer;
    }

    @Override
    public String getIdentitySource() {
        return identitySource == null && defaultAuthorizer != null ? defaultAuthorizer.getIdentitySource() : identitySource;
    }

    public void setIdentitySource(String identitySource) {
        checkUnspecified(this.identitySource);
        this.identitySource = identitySource;
    }

    @Override
    public String getIssuer() {
        return issuer == null && defaultAuthorizer != null ? defaultAuthorizer.getIssuer() : issuer;
    }

    public void setIssuer(String issuer) {
        checkUnspecified(this.issuer);
        this.issuer = issuer;
    }

    @Override
    public List<String> getAudience() {
        return audiences == null && defaultAuthorizer != null ? defaultAuthorizer.getAudience() : audiences;
    }

    public void setAudiences(List<String> audiences) {
        checkUnspecified(this.audiences);
        this.audiences = audiences;
    }

    @Override
    public Map<Object, Object> getFlows() {
        return flows;
    }

    @Override
    public String getAuthorizationType() {
        return AUTHORIZATION_TYPE;
    }

    public void setAuthorizationType(String authorizationType) {
        checkUnspecified(this.authorizationType);
        if (AUTHORIZATION_TYPE.equals(authorizationType)) {
            throw new O4A_Exception(authorizationType + " : not a valid authorization type");
        }
        this.authorizationType = authorizationType;
    }

    @Override
    public String getType() {
        return AUTHORIZER_TYPE;
    }

    public void setType(String authorizerType) {
        checkUnspecified(this.type);
        if (AUTHORIZER_TYPE.equals(authorizationType)) {
            throw new O4A_Exception(authorizationType + " : not a valid authorizer type");
        }
        this.type = authorizerType;
    }

    private void checkUnspecified(Object x) {
        if (x != null) throw CliException.duplicatedArgument();
    }
}
