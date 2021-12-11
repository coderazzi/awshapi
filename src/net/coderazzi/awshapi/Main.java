package net.coderazzi.awshapi;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Main {

    private static final String YAML_PATHS = "paths";
    private static final String YAML_COMPONENTS = "components";
    private static final String YAML_SECURITY_SCHEMAS = "securitySchemes";

    private static final String ISSUER_FORMAT = "https://cognito-idp.%s.amazonaws.com/%s";
    private static final String URI_FORMAT = "http://{ip_port}{method}";

    private static final String INTEGRATION_PAYLOAD_FORMAT_VERSION = "payloadFormatVersion";
    private static final String DEFAULT_INTEGRATION_PAYLOAD_FORMAT_VERSION = "1.0";
    public static final String INTEGRATION_TYPE = "type";
    public static final String DEFAULT_INTEGRATION_TYPE = "http_proxy";
    public static final String INTEGRATION_CONNECTION_TYPE = "connectionType";
    public static final String DEFAULT_INTEGRATION_CONNECTION_TYPE = "INTERNET";
    public static final String YAML_TAGS = "tags";
    public static final String INTEGRATION_HTTP_METHOD = "httpMethod";
    public static final String INTEGRATION_URI = "uri";
    public static final String SECURITY = "security";
    public static final String X_AMAZON_APIGATEWAY_INTEGRATION = "x-amazon-apigateway-integration";


    public void handle(String []args) throws IOException{
        if (args.length != 8) {
            errorAndExit("Invalid parameter list");
        }
        String securitySchemaName=args[6],
                identitySource=args[1],
                audiences=args[2],
                awsRegion=args[3],
                cognitoId=args[4],
                scopes=args[5];
        Map<String, String> tagUriMap = new LinkedHashMap<>();
        for (String each :  args[7].split(",")) {
            String parts[] = each.split("=", 2);
            if (parts.length != 2) {
                errorAndExit("Invalid argument: " + each);
            }
            tagUriMap.put(parts[0], parts[1]);
        }
        for (Path path : getFiles(args[0])){
            augment(path, securitySchemaName, identitySource, audiences, awsRegion, cognitoId, scopes, tagUriMap);
        }
    }

    private void augment(Path filePath, String securitySchemaName, String identitySource, String audiences,
                         String awsRegion, String cognitoId, String scopes, Map<String, String> tagUriMap)
            throws IOException
    {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        Map<String, Object> specification;
        try( final InputStream is = Files.newInputStream(filePath) ){
            specification = yaml.load(is);
            getMap(getMap(specification, YAML_COMPONENTS), YAML_SECURITY_SCHEMAS)
                    .put(securitySchemaName,createSecuritySchema(identitySource, audiences, awsRegion, cognitoId));

            Map<String, Object> tmp = new LinkedHashMap<>();
            tmp.put(securitySchemaName, convertToList(scopes));
            final List<Object> securityScope = new ArrayList();
            securityScope.add(tmp);

            final Map<String, String> integration = new LinkedHashMap<>();
            integration.put(INTEGRATION_PAYLOAD_FORMAT_VERSION, DEFAULT_INTEGRATION_PAYLOAD_FORMAT_VERSION);
            integration.put(INTEGRATION_TYPE, DEFAULT_INTEGRATION_TYPE);
            integration.put(INTEGRATION_CONNECTION_TYPE, DEFAULT_INTEGRATION_CONNECTION_TYPE);

            getMap(specification, YAML_PATHS).forEach((path, pathSpec) -> {
                String location = YAML_PATHS + "." + path;
                castToMap(pathSpec, location).forEach((method, v) -> {
                    String subLocation = location + "." + method;

                    Map<String, Object> methodSpec = castToMap(v, subLocation);
                    List<String> tags = castToList(methodSpec.get(YAML_TAGS), subLocation);

                    integration.put(INTEGRATION_HTTP_METHOD, method.toUpperCase(Locale.ROOT));
                    integration.put(INTEGRATION_URI, getUri(path, tags, tagUriMap));
                    methodSpec.put(SECURITY, securityScope);
                    methodSpec.put(X_AMAZON_APIGATEWAY_INTEGRATION, integration);
                });
            });
        }
        try(final OutputStream os = Files.newOutputStream(filePath) ){
            yaml.dump(specification, new OutputStreamWriter(os));
        }
    }

    private String getUri(String method, List<String> tags, Map<String, String> tagUriMap){
        if (tags.size() != 1){
            errorAndExit(String.format("Method %s has %d tags, 1 expected", method, tags.size()));
        }
        String tag = tags.get(0);
        String use = tagUriMap.get(tag);
        if (use == null) {
            errorAndExit(String.format("Method %s has unexpected tag %s", method, tag));
        }
        return String.format(URI_FORMAT, use, method);
    }

    private Map<String, Object> castToMap(Object obj, String location){
        if (obj==null) {
            return new LinkedHashMap<>();
        }
        if (!(obj instanceof Map)) {
            errorAndExit("Yaml defines an unexpected type for " + location);
        }
        ((Map) obj).keySet().forEach( k -> {
            if (! (k instanceof String)) {
                errorAndExit("Yaml defines an unexpected content on " + location + " : " + k);
            }
        });
        return (Map<String, Object>) obj;
    }

    private List<String> castToList(Object obj, String location){
        if (obj==null) {
            return new ArrayList<>();
        }
        if (!(obj instanceof List)) {
            errorAndExit("Yaml defines an unexpected type for " + location);
        }
        ((List) obj).forEach( k -> {
            if (! (k instanceof String)) {
                errorAndExit("Yaml defines an unexpected content for " + location + " : " + k);
            }
        });
        return (List<String>) obj;
    }

    private Map<String, Object> createSecuritySchema(String identitySource, String audiences, String awsRegion,
                                                     String cognitoId){
        Map<String, Object> ret = new LinkedHashMap<>();
        Map<String, Object> authorizer = new LinkedHashMap<>();
        Map<String, Object> configuration = new LinkedHashMap<>();
        ret.put("type", "oauth2");
        ret.put("flows", new LinkedHashMap<>());
        ret.put("x-amazon-apigateway-authorizer", authorizer);
        authorizer.put("identitySource", identitySource);
        authorizer.put("type", "jwt");
        authorizer.put("jwtConfiguration", configuration);
        configuration.put("audience", convertToList(audiences));
        configuration.put("issuer", String.format(ISSUER_FORMAT, awsRegion, cognitoId));
        return ret;
    }

    private List<String> convertToList(String argument){
        String split[] = argument.split(",");
        List<String> ret = new ArrayList<>(split.length);
        for (String each : split) {
            ret.add(each.trim());
        }
        return ret;
    }

//    private String[] convertToList(String argument){
//        String split[] = argument.split(",");
//        for (int i = split.length -1 ; i>=0 ; i--){
//            split[i] = split[i].trim();
//        }
//        return split;
//    }


    private Map<String, Object> getMap(Map<String, Object> specification, String path){
        Object obj = specification.get(path);
        if (obj == null) {
            obj = new LinkedHashMap<>();
            specification.put(path, obj);
        }
        return castToMap(obj, path + "path");
    }

    private Set<Path> getFiles(String specification) throws IOException {
        final Set<Path> ret = new HashSet<>();
        for (String each : specification.split(",")) {
            final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:"+each.trim());
            Files.walkFileTree(FileSystems.getDefault().getPath(""), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (pathMatcher.matches(path)) {
                        ret.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return ret;
    }

    private void errorAndExit(String message){
        System.err.println(message);
        System.exit(1);
    }

    public static void main(String[] args) throws IOException{
        new Main().handle(args);
    }
}
