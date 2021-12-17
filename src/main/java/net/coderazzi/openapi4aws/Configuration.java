package net.coderazzi.openapi4aws;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public abstract class Configuration {
    public abstract Map<String, Authorizer> getAuthorizers();

    public abstract Integration getIntegration(String path, List<String> tags);

    /**
     * Utility method to find all the paths associated to a single configuration (filenames + globs)
     *
     * @param filenames collection of specific filenames
     * @param globs     collection of glob specifications, which return no matching paths
     * @return the existing matching paths
     */
    protected final Collection<Path> getPaths(Collection<String> filenames, Collection<String> globs) {
        Set<Path> ret = new HashSet<>();
        if (filenames != null) {
            filenames.forEach(x -> ret.add(Paths.get(x)));
        }
        if (globs != null) {
            for (String each : globs) {
                try {
                    final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + each);
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
                } catch (IOException iex) {
                    throw new O4A_Exception("IOError while looking for glob " + each + ": " + iex);
                }
            }
        }
        return ret;
    }

    /**
     * Utility to find a suitable integration for a route path with given tags.
     *
     * @param path          the route path to match
     * @param pathTags      any tags associated to that path
     * @param paths         integration paths to use, as a map from path to the defined integration
     * @param lowerCaseTags integration tags to use, as a map from the tag names in lower case to the defined
     *                      integrations
     * @return the most suitable integration
     */
    protected final Integration getIntegration(String path, List<String> pathTags,
                                               Map<String, ? extends Integration> paths,
                                               Map<String, ? extends Integration> lowerCaseTags) {
        Integration ret = paths.get(path);
        if (ret == null) {
            for (String tag : pathTags) {
                ret = lowerCaseTags.get(tag.toLowerCase(Locale.ROOT));
                if (ret != null) {
                    break;
                }
            }
        }
        return ret;
    }


    public interface Authorizer {
        String getIdentitySource();

        String getIssuer();

        List<String> getAudience();

        Map<Object, Object> getFlows();

        String getAuthorizationType();

        String getType();
    }

    public interface Integration {
        List<String> getScopes();

        String getAuthorizer();

        String getUri(String path);
    }

}
