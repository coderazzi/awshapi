package net.coderazzi.awshapi.arguments;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgumentsHandler {

    private static final Map<String, MainConsumer> argumentHandlers = new HashMap<>();
    private static final Map<String, SecurityConsumer> securityConsumers = new HashMap<>();
    private static final Map<String, Getter<Security>> securityCheckers = new HashMap<>();

    private static final String SECURITY="security";
    private static final String SECURITY_IDENTITY_SOURCE = "identity_source";
    private static final String SECURITY_ISSUER = "issuer";
    private static final String SECURITY_AUDIENCES = "audiences";
    private static final String SECURITY_TYPE = "type";
    private static final String SECURITY_AUTHORIZER_TYPE = "authorizerType";
    private static final String TAG="tag";
    private static final String METHOD="method";

    private final Map<String, Security> securities = new HashMap<>();
    private final Map<String, Path> tags = new HashMap<>();
    private final Map<String, Path> methods = new HashMap<>();

    public ArgumentsHandler(String []args){
        Pattern p = Pattern.compile(String.format("^(?:--)?(%s)\\.([^=]+)=(.+)$",
                String.join("|", argumentHandlers.keySet())));
        for (String arg: args) {
            try {
                Matcher m = p.matcher(arg);
                if (m.matches()){
                    String area = m.group(1);
                    String type = m.group(2).trim();
                    String value = m.group(3).trim();
                    if (!type.isEmpty() && !value.isEmpty()) {
                        argumentHandlers.get(area).consume(this, type, value);
                        continue;
                    }
                }
                throw ArgumentException.unexpected();
            } catch(ArgumentException ex){
                throw new ArgumentException(arg + " : " + ex.getMessage());
            }
        }
        securities.forEach((name, instance)->{
            if (!name.isEmpty()) {
                securityCheckers.forEach((prop, checker) -> {
                    if (null==checker.get(instance)) {
                        String missing = SECURITY + "." + prop;
                        throw new ArgumentException("Missing " + missing + " or " + missing + "." + name);
                    }
                });
            }
        });
    }

    /**
     * Handles a tag definition: tag.NAME=value, converting the NAME to lowercase
     */
    private void handleTag(String definition, String value){
        handleTagOrMethod(tags, value, "/" + definition.toLowerCase(Locale.ROOT));
    }


    /**
     * Handles a method definition: method.path1...pathN=value, replacing the dots in the paths with '/'
     */
    private void handleMethod(String definition, String value){
        handleTagOrMethod(methods, value, "/" + definition.replace(".", "/"));
    }


    private void handleTagOrMethod(Map<String, Path> map, String value, String definition){
        if (map.containsKey(definition)) {
            throw ArgumentException.alreadySpecified();
        }
        List<String> parts = convertToNonEmptyList(value);
        Path path = new Path(parts.get(0));
        if (parts.size() > 1) {
            String securityName = parts.get(1);
            if (securities.get(securityName) == null) {
                throw new ArgumentException(securityName + " is not a provided security name");
            }
            path.setSecurity(parts.get(1), parts.subList(Math.min(2, parts.size()-1), parts.size()-1));
        }
        map.put(definition, path);
    }


    private void handleSecurity(String definition, String value){
        if ("name".equals(definition)){
            if (!securities.isEmpty()) {
                throw ArgumentException.alreadySpecified();
            }
            Security defaultSecurity = new Security(null);
            securities.put("", defaultSecurity);
            convertToNonEmptyList(value).forEach( x-> securities.put(x, new Security(defaultSecurity)));
        } else {
            String name = "";
            SecurityConsumer securityConsumer = securityConsumers.get(definition);
            if (securityConsumer == null) {
                int last = definition.lastIndexOf('.');
                if (last != -1) {
                    name = definition.substring(last + 1).trim();
                    securityConsumer = securityConsumers.get(definition.substring(0, last).trim());
                }
            }
            Security security = securities.get(name);
            if (security==null || securityConsumer==null) {
                throw ArgumentException.unexpected();
            }
            securityConsumer.securityHandle(security, value);
        }
    }



    /**
     * Splits a comma-separated string into a list of trimmed Strings, all non-blank.
     * An ArgumentException is raised if the resulting list were empty.
     */
    private static List<String> convertToNonEmptyList(String arg){
        String []split = arg.split(",");
        List<String> ret = new ArrayList<>(split.length);
        for (String each : split) {
            String trimmed = each.trim();
            if (!trimmed.isEmpty()) {
                ret.add(trimmed);
            }
        }
        if (ret.isEmpty()) {
            throw ArgumentException.invalidValue(arg);
        }
        return ret;
    }

    static {
        argumentHandlers.put(SECURITY, ArgumentsHandler::handleSecurity);
        argumentHandlers.put(TAG, ArgumentsHandler::handleTag);
        argumentHandlers.put(METHOD, ArgumentsHandler::handleMethod);

        securityConsumers.put(SECURITY_IDENTITY_SOURCE, Security::setIdentitySource);
        securityConsumers.put(SECURITY_ISSUER, Security::setIssuer);
        securityConsumers.put(SECURITY_AUDIENCES, (s, a) -> s.setAudiences(convertToNonEmptyList(a)));
        securityConsumers.put(SECURITY_TYPE, Security::setType);
        securityConsumers.put(SECURITY_AUTHORIZER_TYPE, Security::setAuthorizerType);

        securityCheckers.put(SECURITY_IDENTITY_SOURCE, Security::getIdentitySource);
        securityCheckers.put(SECURITY_ISSUER, Security::getIssuer);
        securityCheckers.put(SECURITY_AUDIENCES, Security::getAudiences);
        securityCheckers.put(SECURITY_TYPE, Security::getType);
        securityCheckers.put(SECURITY_AUTHORIZER_TYPE, Security::getAuthorizerType);
    }

    interface MainConsumer {
        void consume(ArgumentsHandler self, String key, String value);
    }

    interface SecurityConsumer {
        void securityHandle(Security s, String arg);
    }

    interface Getter<T> {
        Object get(T t);
    }

}
