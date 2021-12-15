package net.coderazzi.openapi4aws.cli;

import net.coderazzi.openapi4aws.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class CliParser extends Configuration {

    private static final Map<String, MainConsumer> argumentHandlers = new HashMap<>();
    private static final Map<String, AuthorizerConsumer> authorizerConsumers = new HashMap<>();
    private static final Map<String, Getter<AuthorizerParameter>> authorizerCheckers = new HashMap<>();

    private static final String AUTHORIZER = "authorizer.";
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
    private Path outputFolder;

    public CliParser(String[] args) {
        Pattern p = Pattern.compile(String.format("^(?:--)?(%s)([^=]*)=(.+)$",
                String.join("|", argumentHandlers.keySet())));
        for (String arg : args) {
            try {
                Matcher m = p.matcher(arg);
                if (m.matches()) {
                    String area = m.group(1);
                    String type = m.group(2).trim();
                    String value = m.group(3).trim();
                    if (!value.isEmpty()) {
                        argumentHandlers.get(area).consume(this, type, value);
                        continue;
                    }
                }
                throw CliException.unexpectedArgument();
            } catch (CliException ex) {
                throw new CliException(arg + " : " + ex.getMessage());
            }
        }
        if (filenames.isEmpty() && globs.isEmpty()) {
            throw new CliException("Missing " + FILENAME + " or " + GLOB);
        }
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

    @Override
    public Map<String, Authorizer> getAuthorizers() {
        return authorizers.entrySet().stream().filter(x -> !x.getKey().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    //    @Override
//    public IntegrationParameter getIntegration(String path, List<String> tags) {
//        IntegrationParameter ret = paths.get(path);
//        if (ret == null) {
//            for (String tag : tags) {
//                ret = this.tags.get(tag.toLowerCase(Locale.ROOT));
//                if (ret != null) {
//                    break;
//                }
//            }
//        }
//        return ret;
//    }
//
    @Override
    public Integration getIntegration(String path, List<String> tags) {
        return getIntegration(path, tags, this.paths, this.tags);
    }

    public Collection<Path> getPaths() {
        return getPaths(filenames, globs);
    }

    public Path getOutputFolder() {
        return outputFolder;
    }

    private void handleOutput(String value, String definition) {
        if (!value.isEmpty()) {
            throw CliException.unexpectedArgument();
        }
        if (outputFolder != null) {
            throw CliException.duplicatedArgument();
        }
        this.outputFolder = Paths.get(definition);
    }

    private void handleFilename(String value, String definition) {
        handleInputList(value, definition, filenames);
    }

    private void handleGlob(String value, String definition) {
        handleInputList(value, definition, globs);
    }

    private void handleInputList(String value, String definition, Collection<String> target) {
        if (!value.isEmpty()) {
            throw CliException.unexpectedArgument();
        }
        target.add(definition);
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
        if (map.containsKey(definition)) {
            throw CliException.duplicatedArgument();
        }
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

    private void handleAuthorizer(String definition, String value) {
        if ("name".equals(definition)) {
            if (!authorizers.isEmpty()) {
                throw CliException.duplicatedArgument();
            }
            AuthorizerParameter defaultAuthorizer = new AuthorizerParameter(null);
            authorizers.put("", defaultAuthorizer);
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

    private interface MainConsumer {
        void consume(CliParser self, String key, String value);
    }

    private interface AuthorizerConsumer {
        void handle(AuthorizerParameter s, String arg);
    }

    private interface Getter<T> {
        Object get(T t);
    }

}
