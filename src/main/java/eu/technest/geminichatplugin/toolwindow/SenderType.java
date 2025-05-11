package eu.technest.geminichatplugin.toolwindow;

public enum SenderType {
    USER("You"),
    GEMINI("Gemini"),
    SYSTEM("System"),
    ERROR("Error"); // For general errors or API errors

    private final String displayName;

    SenderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
