package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    SocketSendingError("Error while sending message"),
    CannotOpenSocket("Error while opening socket"),
    BadClientId("Bad client id provided. Must be greater or equal to the number of replicas"),

    CannotParseMessage("Error while parsing message"),

    GeneratingKeyError("Error while generating key"),

    EncryptionError("Error while encrypting message"),

    SigningError("Error while signing message");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
