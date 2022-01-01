package net.coderazzi.openapi4aws.cli;

import net.coderazzi.openapi4aws.Configuration;
import net.coderazzi.openapi4aws.O4A_Exception;
import net.coderazzi.openapi4aws.Openapi4AWS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class Argument implements Comparable<Argument>{
    final String argument;
    final String area;
    final String type;
    final String value;
    private final int priority;

    Argument(String argument, String area, String type, String value, int argPosition){
        this.argument = argument;
        this.area= area;
        this.type = type;
        this.value= value;
        if (area.equals(CliParser.CONFIGURATION)) {
            this.priority = -1;
        } else if (area.equals(CliParser.AUTHORIZER) && type.equals(CliParser.AUTHORIZER_DEFINITION)){
            this.priority = -2;
        } else {
            this.priority = argPosition;
        }
    }

    @Override
    public int compareTo(Argument argument) {
        return this.priority - argument.priority;
    }
}

/**
 * Open4AWS configuration using command line arguments, prepended or not with prefix --
 */
public class CliParser extends Configuration {

    private static final Map<String, ArgumentConsumer> argumentHandlers = new HashMap<>();
    private static final Map<String, AuthorizerConsumer> authorizerConsumers = new HashMap<>();
    private static final Map<String, Getter<AuthorizerParameter>> authorizerCheckers = new HashMap<>();

    public static final String CONFIGURATION = "configuration";
    public static final String AUTHORIZER = "authorizer.";
    public static final String AUTHORIZER_DEFINITION = "name";
    private static final String AUTHORIZER_IDENTITY_SOURCE = "identity-source";
    private static final String AUTHORIZER_ISSUER = "issuer";
    private static final String AUTHORIZER_AUDIENCES = "audience";
    private static final String AUTHORIZER_AUTHORIZATION_TYPE = "authorization-type";
    private static final String AUTHORIZER_TYPE = "authorizer-type";
    private static final String TAG = "tag.";
    private static final String PATH = "path.";
    private static final String FILENAME = "filename";
    private static final String GLOB = "glob";
    private static final String OUTPUT = "output-folder";

    static {
        argumentHandlers.put(AUTHORIZER, CliParser::handleAuthorizer);
        argumentHandlers.put(TAG, CliParser::handleTag);
        argumentHandlers.put(PATH, CliParser::handlePath);
        argumentHandlers.put(FILENAME, CliParser::handleFilename);
        argumentHandlers.put(GLOB, CliParser::handleGlob);
        argumentHandlers.put(OUTPUT, CliParser::handleOutput);
        argumentHandlers.put(CONFIGURATION, CliParser::handleConfiguration);

        authorizerConsumers.put(AUTHORIZER_IDENTITY_SOURCE, AuthorizerParameter::setIdentitySource);
        authorizerConsumers.put(AUTHORIZER_ISSUER, AuthorizerParameter::setIssuer);
        authorizerConsumers.put(AUTHORIZER_AUDIENCES, (s, a) -> s.setAudiences(convertToNonEmptyList(a)));
        authorizerConsumers.put(AUTHORIZER_AUTHORIZATION_TYPE, AuthorizerParameter::setAuthorizationType);
        authorizerConsumers.put(AUTHORIZER_TYPE, AuthorizerParameter::setType);

        authorizerCheckers.put(AUTHORIZER_IDENTITY_SOURCE, AuthorizerParameter::getIdentitySource);
        authorizerCheckers.put(AUTHORIZER_ISSUER, AuthorizerParameter::getIssuer);
        authorizerCheckers.put(AUTHORIZER_AUDIENCES, AuthorizerParameter::getAudience);
        authorizerCheckers.put(AUTHORIZER_AUTHORIZATION_TYPE, AuthorizerParameter::getAuthorizationType);
        authorizerCheckers.put(AUTHORIZER_TYPE, AuthorizerParameter::getType);
    }

    private final Map<String, AuthorizerParameter> authorizers = new LinkedHashMap<>();
    private final Map<String, IntegrationParameter> tags = new HashMap<>();
    private final Map<String, IntegrationParameter> paths = new HashMap<>();
    private final Set<String> filenames = new HashSet<>();
    private final Set<String> globs = new HashSet<>();
    private final Pattern argPattern = Pattern.compile(String.format("^(--)?(%s)([^=]*)=(.+)$",
            String.join("|", argumentHandlers.keySet())));
    private Path outputFolder;

    /**
     * Constructor reading the configuration parameters from a file
     * @param filename file to read
     */
    public CliParser(String filename) {
        this(readFile(filename));
    }

    /**
     * Constructor using parameters array, normally those in the command line
     *
     * @param args command line arguments
     */
    CliParser(String[] args) {
        handleArguments(args, false);
        authorizers.forEach((name, instance) -> {
            if (!name.isEmpty()) {
                authorizerCheckers.forEach((prop, checker) -> {
                    if (null == checker.get(instance)) {
                        String missing = AUTHORIZER + prop;
                        throw new CliException("Missing " + missing + " or " + missing + "." + name);
                    }
                });
            }
        });
    }

    /**
     * Splits a comma-separated string into a list of trimmed Strings, all non-blank.
     * An ArgumentException is raised if the resulting list were empty.
     */
    private static List<String> convertToNonEmptyList(String arg) {
        String[] split = arg.split(",");
        List<String> ret = new ArrayList<>(split.length);
        for (String each : split) {
            String trimmed = each.trim();
            if (!trimmed.isEmpty()) {
                ret.add(trimmed);
            }
        }
        if (ret.isEmpty()) {
            throw new CliException("invalid value: " + arg);
        }
        return ret;
    }

    static private String[] readFile(String filename) {
        try {
            return Files.readAllLines(Paths.get(filename)).stream()
                    .map(String::trim).filter(x -> !x.isEmpty() && !x.startsWith("#")).toArray(String[]::new);
        } catch (IOException ex) {
            throw new O4A_Exception("Cannot read " + CONFIGURATION + " file " + filename + " : " + ex);
        }
    }

    /**
     * Parses the given arguments. If strict is True, arguments cannot be preceded with dashes
     */
    private void handleArguments(String[] args, boolean strict) {
        List<Argument> ret = new ArrayList<>();
        Boolean usingDashes = strict? false : null;
        for (String arg : args) {
            Matcher m = argPattern.matcher(arg);
            if (m.matches()) {
                boolean dashes = m.group(1) != null;
                // be coherent on the use of -- when preceding them in the command line
                // (and they are not supported in configuration files, where strict is true)
                if (usingDashes == null || usingDashes == dashes) {
                    usingDashes = dashes;
                    String area = m.group(2);
                    String type = m.group(3).trim();
                    String value = m.group(4).trim();
                    // if area ends with '.', type cannot be empty
                    if (!value.isEmpty() && (area.endsWith(".") != type.isEmpty())) {
                        ret.add(new Argument(arg, area, type, value, ret.size()));
                        continue;
                    }
                }
            }
            throw CliException.unexpectedArgument();
        }
        Collections.sort(ret);
        for (Argument arg : ret) {
            try {
                argumentHandlers.get(arg.area).consume(this, arg.type, arg.value);
            } catch (CliException ex) {
                throw new CliException(arg.argument + " : " + ex.getMessage());
            }
        }
    }

    @Override
    public Map<String, Authorizer> getAuthorizers() {
        return authorizers.entrySet().stream().filter(x -> !x.getKey().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, Integration> getPathIntegrations() {
        return paths.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, Integration> getTagIntegrations() {
        return tags.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Integration getIntegration(String path, List<String> pathTags) {
        return getIntegration(path, pathTags, this.paths, this.tags);
    }

    public Collection<Path> getPaths() {
        return getPaths(filenames, globs);
    }

    public Path getOutputFolder() {
        return outputFolder;
    }

    private void handleOutput(String empty, String definition) {
        this.outputFolder = Paths.get(definition);
    }

    private void handleConfiguration(String empty, String definition) {
        handleArguments(readFile(definition), true);
    }

    private void handleFilename(String empty, String definition) {
        filenames.add(definition);
    }

    private void handleGlob(String empty, String definition) {
        globs.add(definition);
    }

    /**
     * Handles a tag definition: tag.NAME=value, converting the NAME to lowercase
     */
    private void handleTag(String definition, String value) {
        handleTagOrPath(tags, value, false, definition.toLowerCase(Locale.ROOT));
    }

    /**
     * Handles a method definition: method.path1...pathN=value, replacing the dots in the paths with '/'
     */
    private void handlePath(String definition, String value) {
        handleTagOrPath(paths, value, true, "/" + definition.replace(".", "/"));
    }

    private void handleTagOrPath(Map<String, IntegrationParameter> map, String value, boolean isPath, String definition) {
        List<String> parts = convertToNonEmptyList(value);
        IntegrationParameter integration = new IntegrationParameter(parts.get(0), isPath); //uri
        if (parts.size() > 1) {
            String authorizerName = parts.get(1); //note that it cannot be empty / blank, and is already trimmed
            if (authorizers.get(authorizerName) == null) {
                throw new CliException(authorizerName + " is not a provided authorizer name");
            }
            integration.setAuthorization(parts.get(1), parts.subList(2, parts.size()));
        }
        map.put(definition, integration);
    }

    private AuthorizerParameter getDefaultAuthorizer(){
        AuthorizerParameter defaultAuthorizer = authorizers.get("");
        if (defaultAuthorizer == null) {
            authorizers.put("", defaultAuthorizer = new AuthorizerParameter(null));
        }
        return defaultAuthorizer;
    }

    private void handleAuthorizer(String definition, String value) {
        if (AUTHORIZER_DEFINITION.equals(definition)) {
            final AuthorizerParameter defaultAuthorizer = getDefaultAuthorizer();
            convertToNonEmptyList(value).forEach(x -> authorizers.put(x, new AuthorizerParameter(defaultAuthorizer)));
        } else {
            String name = "";
            AuthorizerConsumer authorizerConsumer = authorizerConsumers.get(definition);
            if (authorizerConsumer == null) {
                int last = definition.lastIndexOf('.');
                if (last != -1) {
                    name = definition.substring(last + 1).trim();
                    authorizerConsumer = authorizerConsumers.get(definition.substring(0, last).trim());
                }
            }
            AuthorizerParameter authorizer = authorizers.get(name);
            if (authorizer == null || authorizerConsumer == null) {
                throw CliException.unexpectedArgument();
            }
            authorizerConsumer.handle(authorizer, value);
        }
    }

    private interface ArgumentConsumer {
        void consume(CliParser self, String key, String value);
    }

    private interface AuthorizerConsumer {
        void handle(AuthorizerParameter s, String arg);
    }

    private interface Getter<T> {
        Object get(T t);
    }

    public static void main(String[] args) {
        try {
            CliParser configuration = new CliParser(args);
            new Openapi4AWS(configuration).handle(configuration.getPaths(), configuration.getOutputFolder());
        } catch (O4A_Exception ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }

}
