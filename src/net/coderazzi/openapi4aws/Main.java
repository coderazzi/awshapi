package net.coderazzi.openapi4aws;

import net.coderazzi.openapi4aws.arguments.ArgumentsHandler;
import net.coderazzi.openapi4aws.arguments.Security;
import net.coderazzi.openapi4aws.arguments.Specification;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main {

    public static final String DEFAULT_INTEGRATION_TYPE = "http_proxy";
    public static final String DEFAULT_INTEGRATION_CONNECTION_TYPE = "INTERNET";
    private static final String PATHS = "paths";
    private static final String DEFAULT_INTEGRATION_PAYLOAD_FORMAT_VERSION = "1.0";
    private final ArgumentsHandler arguments;

    Main(ArgumentsHandler handler) {
        this.arguments = handler;
    }

    public static void main(String[] args) throws IOException {
        try {
            new Main(new ArgumentsHandler(args)).handle();
        } catch (O4A_Exception ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }

    public void handle() throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        Map<String, Object> specification;
        for (Path path : arguments.getInput()) {
            try (final InputStream is = Files.newInputStream(path)) {
                specification = yaml.load(is);
            } catch (ClassCastException cex) {
                specification = null;
            }
            if (specification == null) {
                throw new O4A_Exception(path + ": invalid openapi content");
            }
            try {
                augment(specification);
            } catch (O4A_Exception ex) {
                throw new O4A_Exception(path + ex.getMessage());
            }
            try (final OutputStream os = Files.newOutputStream(path)) {
                yaml.dump(specification, new OutputStreamWriter(os));
            }
        }
    }

    private void augment(Map<String, Object> specification) {
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

    private Map<String, Object> createSecuritySchema(Security security) {
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
    private Map<String, Object> castToMap(Object obj, String location) {
        if (obj != null) {
            try {
                Map<?, ?> ret = (Map<?, ?>) obj;
                checkOnlyStrings(ret.keySet(), location);
                return (Map<String, Object>) ret;
            } catch (ClassCastException cex) {
                throw O4A_Exception.invalidType(location);
            }
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> castToList(Object obj, String location) {
        if (obj != null) {
            try {
                List<?> ret = (List<?>) obj;
                checkOnlyStrings(ret, location);
                return (List<String>) ret;
            } catch (ClassCastException cex) {
                throw O4A_Exception.invalidType(location);
            }
        }
        return new ArrayList<>();
    }

    private void checkOnlyStrings(Collection<?> x, String location) {
        x.forEach(k -> {
            if (!(k instanceof String)) {
                throw new O4A_Exception("unexpected openapi content on " + location + ":" + k);
            }
        });
    }

    private Map<String, Object> getMap(Map<String, Object> specification, String path) {
        return castToMap(specification.computeIfAbsent(path, x -> new LinkedHashMap<>()), path + "path");
    }
}
