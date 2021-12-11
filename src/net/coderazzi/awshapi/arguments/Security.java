package net.coderazzi.awshapi.arguments;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Security {

    private final static Map<Object, Object> SECURITY_FLOWS = Collections.emptyMap();
    private final static String SECURITY_TYPE="oauth2";
    private final static String SECURITY_AUTHORIZER_TYPE="jwt";

    private Security defaultSecurity;
    private String identitySource;
    private String issuer;
    private List<String> audiences;
    private Map<Object, Object> flows = SECURITY_FLOWS;
    private String type;
    private String authorizerType;

    public Security(Security defaultSecurity){
        this.defaultSecurity = defaultSecurity;
    }

    public String getIdentitySource() {
        return identitySource==null && defaultSecurity!=null? defaultSecurity.getIdentitySource() : identitySource;
    }

    public void setIdentitySource(String identitySource) {
        checkUnspecified(this.identitySource);
        this.identitySource = identitySource;
    }

    public String getIssuer() {
        return issuer==null && defaultSecurity!=null? defaultSecurity.getIssuer() : issuer;
    }

    public void setIssuer(String issuer) {
        checkUnspecified(this.issuer);
        this.issuer = issuer;
    }

    public List<String> getAudiences() {
        return audiences==null && defaultSecurity!=null? defaultSecurity.getAudiences() : audiences;
    }

    public void setAudiences(List<String> audiences) {
        checkUnspecified(this.audiences);
        this.audiences = audiences;
    }

    public Map<Object, Object> getFlows() {
        return flows;
    }

    public String getType() {
        return SECURITY_TYPE;
    }

    public void setType(String type) {
        checkUnspecified(this.type);
        if (SECURITY_TYPE.equals(type)) {
            throw new ArgumentException(type +" : not a valid security type");
        }
        this.type = type;
    }

    public String getAuthorizerType() {
        return SECURITY_AUTHORIZER_TYPE;
    }

    public void setAuthorizerType(String authorizerType) {
        checkUnspecified(this.authorizerType);
        if (SECURITY_AUTHORIZER_TYPE.equals(type)) {
            throw new ArgumentException(type +" : not a valid security authorizer type");
        }
        this.authorizerType = authorizerType;
    }

    private void checkUnspecified(Object x) {
        if (x!=null) throw ArgumentException.alreadySpecified();
    }
}
