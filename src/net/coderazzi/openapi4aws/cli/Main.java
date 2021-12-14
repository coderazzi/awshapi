package net.coderazzi.openapi4aws.cli;

import net.coderazzi.openapi4aws.O4A_Exception;
import net.coderazzi.openapi4aws.Openapi4AWS;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        try {
            CliParser configuration = new CliParser(args);
            new Openapi4AWS(configuration).handle(configuration.getPaths(), configuration.getOutputFolder());
        } catch (O4A_Exception ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }

}
