package net.coderazzi.awshapi.arguments;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgumentsHandler {

    private static final Map<String, MainConsumer> argumentHandlers = new HashMap<>();
    private static final Map<String, SecurityConsumer> securityHandlers = new HashMap<>();
    private static final Map<String, Getter<Security>> securityCheckers = new HashMap<>();

    private static final String SECURITY="security";
    private static final String SECURITY_IDENTITY_SOURCE = "identity_source";
    private static final String SECURITY_ISSUER = "issuer";
    private static final String SECURITY_AUDIENCES = "audiences";
    private static final String SECURITY_TYPE = "type";
    private static final String SECURITY_AUTHORIZER_TYPE = "authorizerType";

    private Map<String, Security> securityInstances;

    public ArgumentsHandler(String args[]){;
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
        securityInstances.forEach((name, instance)->{
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

    private void handleSecurity(String definition, String value){
        if ("name".equals(definition)){
            if (securityInstances != null) {
                throw ArgumentException.alreadySpecified();
            }
            Security defaultSecurity = new Security(null);
            securityInstances = new HashMap<>();
            securityInstances.put("", defaultSecurity);
            convertToNonEmptyList(value).forEach( x-> securityInstances.put(x, new Security(defaultSecurity)));
        } else {
            handleGeneric(securityHandlers, securityInstances, definition, (i, h) -> {
                ((SecurityConsumer)h).securityHandle((Security)i, value);
            });
        }
    }


    private void handleGeneric(Map<String, ?> handlers,
                               Map<String, ?> instances,
                               String definition,
                               ObjectHandler objectHandler){
        String instance = "";
        Object consumer = handlers.get(definition);
        if (consumer == null) {
            int last = definition.lastIndexOf('.');
            if (last != -1) {
                instance = definition.substring(last + 1).trim();
                consumer = handlers.get(definition.substring(0, last).trim());
            }
        }
        Object instanceObject = instance==null? null : instances.get(instance);
        if (instanceObject==null || consumer==null) {
            throw ArgumentException.unexpected();
        }
        objectHandler.objectHandle(instanceObject, consumer);
    }


    interface ObjectHandler {
        void objectHandle(Object instance, Object handler);
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

    static {
        argumentHandlers.put(SECURITY, (self, k, v) -> self.handleSecurity(k, v));

        securityHandlers.put(SECURITY_IDENTITY_SOURCE, (s, a) -> s.setIdentitySource(a));
        securityHandlers.put(SECURITY_ISSUER, (s, a) -> s.setIssuer(a));
        securityHandlers.put(SECURITY_AUDIENCES, (s, a) -> s.setAudiences(convertToNonEmptyList(a)));
        securityHandlers.put(SECURITY_TYPE, (s, a) -> s.setType(a));
        securityHandlers.put(SECURITY_AUTHORIZER_TYPE, (s, a) -> s.setAuthorizerType(a));

        securityCheckers.put(SECURITY_IDENTITY_SOURCE, s -> s.getIdentitySource());
        securityCheckers.put(SECURITY_ISSUER, s -> s.getIssuer());
        securityCheckers.put(SECURITY_AUDIENCES, s -> s.getAudiences());
        securityCheckers.put(SECURITY_TYPE, s -> s.getType());
        securityCheckers.put(SECURITY_AUTHORIZER_TYPE, s -> s.getAuthorizerType());
    }

    private static List<String> convertToNonEmptyList(String arg){
        String split[] = arg.split(",");
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

    public static void main(String[] args) {
        new ArgumentsHandler(args);
    }

}
