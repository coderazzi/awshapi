package net.coderazzi.openapi4aws.cli;

import net.coderazzi.openapi4aws.O4A_Exception;

class CliException extends O4A_Exception {
    public CliException(String msg) {
        super(msg);
    }

    public static CliException unexpectedArgument() {
        return new CliException("unexpected argument");
    }

}
