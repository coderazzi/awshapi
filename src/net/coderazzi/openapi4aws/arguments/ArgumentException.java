package net.coderazzi.awshapi.arguments;

public class ArgumentException extends RuntimeException{
    public ArgumentException(String msg) {
        super(msg);
    }

    public static ArgumentException alreadySpecified()  {
        return new ArgumentException("already specified");
    }

    public static ArgumentException invalidValue(String value){
        return new ArgumentException("invalid value: " + value);
    }

    public static ArgumentException unexpected(){
        return new ArgumentException("unexpected");
    }

}
