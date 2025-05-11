package eu.technest.geminichatplugin.settings;

import java.util.ArrayList;
import java.util.List;

public class GeminiSettingsState {
    public String geminiApiKey = "";
    public String selectedModelId = GeminiSettingsService.DEFAULT_MODEL_ID;
    public List<String> availableModelIds = new ArrayList<>(); // To cache fetched models
}
