package net.coderazzi.openapi4aws;

import net.coderazzi.openapi4aws.arguments.ArgumentException;
import net.coderazzi.openapi4aws.arguments.ArgumentsHandler;
import net.coderazzi.openapi4aws.arguments.Security;
import net.coderazzi.openapi4aws.arguments.Specification;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.*;
import java.util.*;

public class Main {

    private static final String PATHS = "paths";

    private static final String DEFAULT_INTEGRATION_PAYLOAD_FORMAT_VERSION = "1.0";
    public static final String DEFAULT_INTEGRATION_TYPE = "http_proxy";
    public static final String DEFAULT_INTEGRATION_CONNECTION_TYPE = "INTERNET";

    private final ArgumentsHandler arguments;

    Main(ArgumentsHandler handler) {
        this.arguments = handler;
    }

    public void handle() throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        Map<String, Object> specification;
        for (Path path : arguments.getInput()) {
            try( final InputStream is = Files.newInputStream(path) ){
                specification = yaml.load(is);
            } catch(ClassCastException cex) {
                specification = null;
            }
            if (specification==null) {
                errorAndExit("Invalid content in file " + path);
            }
            augment(specification);
            try(final OutputStream os = Files.newOutputStream(path) ){
                yaml.dump(specification, new OutputStreamWriter(os));
            }
        }
    }

    private void augment(Map<String, Object> specification)
    {
        Map<String, Security> securities = arguments.getSecurities();
        if (!securities.isEmpty()) {
            Map<String, Object> securitySchemas = getMap(getMap(specification, "components"), "securitySchemes");
            securities.forEach((name, security) -> securitySchemas.put(name, createSecuritySchema(security)));
        }

        getMap(specification, PATHS).forEach((path, pathSpec) -> {
            String location = PATHS + ":" + path;
            castToMap(pathSpec, location).forEach((method, v) -> {
                String subLocation = location + ":" + method;

                Map<String, Object> methodSpec = castToMap(v, subLocation);
                List<String> tags = castToList(methodSpec.get("tags"), subLocation);
                Specification spec = arguments.getSpecification(path, tags);

                if (spec != null) {
                    final Map<String, String> integration = new LinkedHashMap<>();
                    integration.put("payloadFormatVersion", DEFAULT_INTEGRATION_PAYLOAD_FORMAT_VERSION);
                    integration.put("type", DEFAULT_INTEGRATION_TYPE);
                    integration.put("connectionType", DEFAULT_INTEGRATION_CONNECTION_TYPE);
                    integration.put("httpMethod", method.toUpperCase(Locale.ROOT));
                    integration.put("uri", spec.getUri());
                    methodSpec.put("x-amazon-apigateway-integration", integration);

                    String securitySchema = spec.getSecurity();
                    if (securitySchema != null) {
                        Map<String, Object> scopes = new LinkedHashMap<>();
                        scopes.put(securitySchema, new ArrayList<>(spec.getScopes()));
                        final List<Object> securityScope = new ArrayList<>();
                        securityScope.add(scopes);
                        methodSpec.put("security", securityScope);
                    }
                }
            });
        });
    }

    private Map<String, Object> createSecuritySchema(Security security){
        Map<String, Object> ret = new LinkedHashMap<>();
        Map<String, Object> authorizer = new LinkedHashMap<>();
        Map<String, Object> configuration = new LinkedHashMap<>();
        ret.put("type", security.getType());
        ret.put("flows", new HashMap<>(security.getFlows()));
        ret.put("x-amazon-apigateway-authorizer", authorizer);
        authorizer.put("identitySource", security.getIdentitySource());
        authorizer.put("type", security.getAuthorizerType());
        authorizer.put("jwtConfiguration", configuration);
        configuration.put("audience", new ArrayList<>(security.getAudiences()));
        configuration.put("issuer", security.getIssuer());
        return ret;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object obj, String location){
        if (obj!=null) {
            try {
                Map<?,?> ret = (Map<?,?>) obj;
                ret.keySet().forEach( k -> {
                    if (! (k instanceof String)) {
                        errorAndExit("Yaml defines an unexpected content on " + location + " : " + k);
                    }
                });
                return (Map<String, Object>) ret;
            } catch (ClassCastException cex) {
                errorAndExit("Yaml defines an unexpected type for " + location);
            }
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> castToList(Object obj, String location) {
        if (obj!=null) {
            try {
                List<?> ret = (List<?>) obj;
                ret.forEach(k -> {
                    if (!(k instanceof String)) {
                        errorAndExit("Yaml defines an unexpected content for " + location + " : " + k);
                    }
                });
                return (List<String>) ret;
            } catch (ClassCastException cex) {
                errorAndExit("Yaml defines an unexpected type for " + location);
            }
        }
        return new ArrayList<>();
    }

    private Map<String, Object> getMap(Map<String, Object> specification, String path){
        return castToMap(specification.computeIfAbsent(path, x -> new LinkedHashMap<>()), path + "path");
    }

    private static void errorAndExit(String message){
        System.err.println(message);
        System.exit(1);
    }

    public static void main(String[] args) throws IOException{
        try {
            new Main(new ArgumentsHandler(args)).handle();
        } catch(ArgumentException ex){
            errorAndExit(ex.getMessage());
        }
    }
}
