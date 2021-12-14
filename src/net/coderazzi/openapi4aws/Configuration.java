package net.coderazzi.openapi4aws;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public abstract class Configuration {
    public abstract Map<String, Authorizer> getAuthorizers();

    public abstract Integration getIntegration(String path, List<String> tags);

    protected Collection<Path> getPaths(Collection<String> filenames, Collection<String> globs) throws IOException {
        Set<Path> ret = new HashSet<>();
        if (filenames != null) {
            filenames.forEach(x -> ret.add(Paths.get(x)));
        }
        if (globs != null) {
            for (String each : globs) {
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
            }
        }
        return ret;
    }

    public interface Authorizer {
        String getIdentitySource();

        String getIssuer();

        List<String> getAudiences();

        Map<Object, Object> getFlows();

        String getAuthorizationType();

        String getAuthorizerType();
    }

    public interface Integration {
        List<String> getScopes();

        String getAuthorizer();

        String getUri(String path);
    }

}
