package eu.technest.geminichatplugin.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "eu.technest.geminichatplugin.settings.GeminiSettingsState",
        storages = @Storage("GeminiChatPluginSettings.xml")
)
public class GeminiSettingsService implements PersistentStateComponent<GeminiSettingsState> {

    public static final String DEFAULT_MODEL_ID = "gemini-1.5-flash-latest";

    private GeminiSettingsState myState = new GeminiSettingsState();

    public static GeminiSettingsService getInstance() {
        return ApplicationManager.getApplication().getService(GeminiSettingsService.class);
    }

    @Nullable
    @Override
    public GeminiSettingsState getState() {
        return myState;
    }

    public void loadState(@NotNull GeminiSettingsState state) {
        XmlSerializerUtil.copyBean(state, myState);
        if (myState.availableModelIds == null) {
            myState.availableModelIds = new ArrayList<>();
        }
        // Ensure a default model is set if selectedModelId is missing or empty after loading
        if (myState.selectedModelId == null || myState.selectedModelId.trim().isEmpty()) {
            myState.selectedModelId = DEFAULT_MODEL_ID;
        }
    }

    public String getGeminiApiKey() {
        return myState.geminiApiKey;
    }

    public void setGeminiApiKey(String apiKey) {
        myState.geminiApiKey = apiKey;
    }

    public String getSelectedModelId() {
        if (myState.selectedModelId == null || myState.selectedModelId.trim().isEmpty()) {
            return DEFAULT_MODEL_ID; // Use the constant for fallback
        }
        return myState.selectedModelId;
    }

    public void setSelectedModelId(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            myState.selectedModelId = DEFAULT_MODEL_ID; // Ensure saving a valid default
        } else {
            myState.selectedModelId = modelId;
        }
    }

    public List<String> getAvailableModelIds() {
        if (myState.availableModelIds == null) {
            myState.availableModelIds = new ArrayList<>();
        }
        return new ArrayList<>(myState.availableModelIds); // Return a copy
    }

    public void setAvailableModelIds(List<String> modelIds) {
        myState.availableModelIds = new ArrayList<>(modelIds); // Store a copy
    }
}
