package eu.technest.geminichatplugin.settings; // YOUR BASE PACKAGE

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import eu.technest.geminichatplugin.service.GeminiApiService; // YOUR BASE PACKAGE
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import static eu.technest.geminichatplugin.settings.GeminiSettingsService.DEFAULT_MODEL_ID;

public class GeminiSettingsDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(GeminiSettingsDialog.class);

    private final Project project;
    private final JBTextField apiKeyField = new JBTextField();
    private final ComboBox<String> modelComboBox = new ComboBox<>();
    private final JButton refreshModelsButton = new JButton("Refresh Models");
    private final JBLabel loadingModelsLabel = new JBLabel("Fetching models...", new AnimatedIcon.Default(), SwingConstants.LEFT);

    private final GeminiSettingsService settingsService = GeminiSettingsService.getInstance();
    private final GeminiApiService apiService;

    private boolean isLoadingModels = false; // Explicit flag for loading state

    public GeminiSettingsDialog(@Nullable Project project) {
        super(project, true);
        this.project = project;
        this.apiService = new GeminiApiService();
        setTitle("Gemini AI Settings");
        init();
        setupListeners();
        initializeUIStateAndFetchIfNeeded();
    }

    private void setupListeners() {
        apiKeyField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                handleApiKeyChange();
            }
        });
        refreshModelsButton.addActionListener(e -> fetchModels(true, apiKeyField.getText().trim()));
    }

    private void handleApiKeyChange() {
        String currentApiKey = apiKeyField.getText().trim();
        updateRefreshAndModelControlsEnabledState(); // Centralized UI update

        if (currentApiKey.isEmpty()) {
            // API key cleared, reset model list
            ApplicationManager.getApplication().invokeLater(() -> {
                populateModelComboBoxInternal(Collections.singletonList(getDefaultModelId()), getDefaultModelId());
            });
        } else {
            // API key entered. If no models loaded yet and not currently loading, try to fetch.
            if (modelComboBox.getItemCount() <= 1 && // <=1 because it might just have the default placeholder
                    (settingsService.getAvailableModelIds() == null || settingsService.getAvailableModelIds().isEmpty()) &&
                    !isLoadingModels) {
                LOG.info("API key entered, and no models cached/displayed. Triggering fetch.");
                fetchModels(false, currentApiKey);
            }
        }
    }

    private String getDefaultModelId() {
        String selected = settingsService.getSelectedModelId();
        return (selected == null || selected.trim().isEmpty()) ? DEFAULT_MODEL_ID : selected;
    }

    private void initializeUIStateAndFetchIfNeeded() {
        LOG.debug("initializeUIStateAndFetchIfNeeded called.");
        String apiKey = settingsService.getGeminiApiKey();
        apiKeyField.setText(apiKey);
        loadingModelsLabel.setVisible(false); // Ensure hidden initially

        List<String> cachedModels = settingsService.getAvailableModelIds();
        String selectedModelInSettings = getDefaultModelId(); // Use getter for robust default

        LOG.debug("Initial state - APIKey: '" + apiKey + "', CachedModels: " +
                (cachedModels == null ? "null" : cachedModels.size()) +
                ", SelectedModelInSettings: '" + selectedModelInSettings + "'");

        // Populate with cached or default, then update enabled states
        if (cachedModels != null && !cachedModels.isEmpty()) {
            populateModelComboBoxInternal(cachedModels, selectedModelInSettings);
        } else {
            populateModelComboBoxInternal(Collections.singletonList(selectedModelInSettings), selectedModelInSettings);
        }
        // updateRefreshAndModelControlsEnabledState will be called inside populateModelComboBoxInternal's invokeLater

        // Decide if initial fetch is needed
        if (!apiKey.isEmpty()) {
            boolean fetchNeeded = (cachedModels == null || cachedModels.isEmpty() || !cachedModels.contains(selectedModelInSettings));
            if (fetchNeeded && !isLoadingModels) {
                LOG.info("API key present, cache insufficient. Triggering initial silent model fetch.");
                fetchModels(false, apiKey);
            } else if (!fetchNeeded) {
                LOG.info("API key present, cache sufficient. No initial fetch needed.");
                // Ensure UI is enabled if not fetching
                ApplicationManager.getApplication().invokeLater(this::updateRefreshAndModelControlsEnabledState);
            }
        } else {
            LOG.info("No API key. Controls will be disabled.");
            ApplicationManager.getApplication().invokeLater(this::updateRefreshAndModelControlsEnabledState);
        }
    }

    /**
     * Internal method to populate the combo box. Assumed to be called within an invokeLater block
     * or if already on EDT. It will also call updateRefreshAndModelControlsEnabledState.
     */
    private void populateModelComboBoxInternal(List<String> modelIdsToDisplay, String modelToSelect) {
        // This method itself does not need invokeLater if its callers ensure they are on EDT
        // or if this method is only called from within an invokeLater block.
        // For safety, if there's any doubt, wrap its body in invokeLater.
        // However, since its callers (fetchModels callbacks, initializeUIState) mostly do,
        // we can often skip it here to avoid nested invokeLater calls.
        // For this refactor, let's assume callers handle EDT.

        Vector<String> modelsVector = new Vector<>();
        if (modelIdsToDisplay != null && !modelIdsToDisplay.isEmpty()) {
            modelsVector.addAll(modelIdsToDisplay);
        } else {
            LOG.warn("populateModelComboBoxInternal called with empty/null modelIdsToDisplay. Using default.");
            modelsVector.add(getDefaultModelId());
            if (!getDefaultModelId().equals(DEFAULT_MODEL_ID)) modelsVector.add(DEFAULT_MODEL_ID);
        }
        modelsVector = new Vector<>(modelsVector.stream().distinct().sorted().collect(Collectors.toList()));

        String currentDialogSelection = (String) modelComboBox.getSelectedItem();
        modelComboBox.setModel(new DefaultComboBoxModel<>(modelsVector));

        if (modelToSelect != null && modelsVector.contains(modelToSelect)) {
            modelComboBox.setSelectedItem(modelToSelect);
        } else if (currentDialogSelection != null && modelsVector.contains(currentDialogSelection)) {
            modelComboBox.setSelectedItem(currentDialogSelection);
        } else if (!modelsVector.isEmpty()) {
            modelComboBox.setSelectedIndex(0);
        }
        LOG.debug("Model combo box populated. Items: " + modelsVector.size() + ", Selected: " + modelComboBox.getSelectedItem());
        updateRefreshAndModelControlsEnabledState(); // Update UI after populating
    }


    private void fetchModels(boolean showUserMessages, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (showUserMessages) {
                Messages.showWarningDialog(project, "Please enter an API Key first.", "API Key Required");
            }
            LOG.warn("fetchModels called without API key.");
            setLoadingFlagAndUI(false); // Turn off loading visuals and update UI
            return;
        }
        if (isLoadingModels) {
            LOG.info("Model fetch already in progress. Ignoring new request.");
            return; // Prevent concurrent fetches
        }

        LOG.info("Fetching models. ShowUserMessages: " + showUserMessages);
        setLoadingFlagAndUI(true);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fetching Gemini Models", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                apiService.listModels(apiKey, new GeminiApiService.ListModelsCallback() {
                    @Override
                    public void onSuccess(List<String> fetchedModelIds) {
                        LOG.info("Successfully fetched " + fetchedModelIds.size() + " models: " + fetchedModelIds);
                        settingsService.setAvailableModelIds(fetchedModelIds);

                        ApplicationManager.getApplication().invokeLater(() -> { // Ensure all following UI updates on EDT
                            String modelToSelect = settingsService.getSelectedModelId(); // User's preferred model
                            List<String> modelsForComboBox = new ArrayList<>(fetchedModelIds);

                            if (modelsForComboBox.isEmpty()) {
                                LOG.warn("API returned 0 models. Using default.");
                                modelsForComboBox.add(getDefaultModelId());
                                modelToSelect = getDefaultModelId();
                            } else if (!modelsForComboBox.contains(modelToSelect)) {
                                LOG.info("Previously selected model '" + modelToSelect + "' not in fetched list. Selecting first from new list: " + modelsForComboBox.get(0));
                                modelToSelect = modelsForComboBox.get(0);
                            }
                            populateModelComboBoxInternal(modelsForComboBox, modelToSelect);

                            if (showUserMessages) {
                                Messages.showInfoMessage(project, "Models refreshed (" + fetchedModelIds.size() + " found).", "Models Refreshed");
                            }
                            setLoadingFlagAndUI(false);
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        LOG.warn("Failed to fetch models: " + errorMessage);
                        ApplicationManager.getApplication().invokeLater(() -> { // Ensure all following UI updates on EDT
                            // On failure, populate with CACHED models if available, else just the default.
                            List<String> currentCache = settingsService.getAvailableModelIds();
                            String modelToSelect = settingsService.getSelectedModelId();

                            if (currentCache == null || currentCache.isEmpty()) {
                                LOG.info("Fetch failed, no models in cache. Using default model.");
                                currentCache = Collections.singletonList(getDefaultModelId());
                                modelToSelect = getDefaultModelId();
                            } else if (!currentCache.contains(modelToSelect)){
                                modelToSelect = currentCache.get(0);
                            }
                            populateModelComboBoxInternal(currentCache, modelToSelect);

                            if (showUserMessages) {
                                Messages.showErrorDialog(project, "Failed to fetch models: " + errorMessage, "Fetch Error");
                            }
                            setLoadingFlagAndUI(false);
                        });
                    }
                });
            }
            @Override
            public void onCancel() {
                LOG.info("Model fetching task cancelled.");
                ApplicationManager.getApplication().invokeLater(() -> setLoadingFlagAndUI(false));
            }
            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.error("Error during model fetching task: ", error);
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showErrorDialog(project, "Unexpected error fetching models: " + error.getMessage(), "Task Error");
                    populateModelComboBoxInternal(Collections.singletonList(getDefaultModelId()), getDefaultModelId());
                    setLoadingFlagAndUI(false);
                });
            }
        });
    }

    /**
     * Sets the isLoadingModels flag and updates the UI to reflect the loading state.
     * Must be called on the EDT.
     */
    private void setLoadingFlagAndUI(boolean isLoading) {
        // This method must be called on the EDT.
        // If not, wrap calls to it in ApplicationManager.getApplication().invokeLater()
        isLoadingModels = isLoading;
        loadingModelsLabel.setVisible(isLoadingModels);
        apiKeyField.setEnabled(!isLoadingModels);
        updateRefreshAndModelControlsEnabledState();
        LOG.debug("setLoadingFlagAndUI: isLoadingModels = " + isLoadingModels);
    }

    /**
     * Updates the enabled state of refresh button and model combo box.
     * Must be called on the EDT.
     */
    private void updateRefreshAndModelControlsEnabledState() {
        // This method must be called on the EDT.
        boolean apiKeyPresent = !apiKeyField.getText().trim().isEmpty();
        refreshModelsButton.setEnabled(apiKeyPresent && !isLoadingModels);
        modelComboBox.setEnabled(apiKeyPresent && !isLoadingModels && modelComboBox.getItemCount() > 0);
        LOG.debug("updateRefreshAndModelControlsEnabledState - Refresh: " + refreshModelsButton.isEnabled() +
                ", ComboBox: " + modelComboBox.isEnabled() +
                ", APIKeyPresent: " + apiKeyPresent + ", IsLoading: " + isLoadingModels);

    }


    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        // ... (same as before)
        JPanel apiKeyPanel = new JPanel(new BorderLayout(JBUI.scale(5), 0));
        apiKeyPanel.add(apiKeyField, BorderLayout.CENTER);
        apiKeyPanel.add(refreshModelsButton, BorderLayout.EAST);

        JPanel modelPanel = new JPanel(new BorderLayout(JBUI.scale(5), 0));
        modelPanel.add(modelComboBox, BorderLayout.CENTER);
        modelPanel.add(loadingModelsLabel, BorderLayout.EAST);
        loadingModelsLabel.setBorder(JBUI.Borders.emptyLeft(JBUI.scale(3)));

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Gemini API Key:"), apiKeyPanel, 1, false)
                .addLabeledComponent(new JBLabel("Chat Model:"), modelPanel, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    protected void doOKAction() {
        // ... (same as before)
        settingsService.setGeminiApiKey(apiKeyField.getText().trim());
        String selectedModel = (String) modelComboBox.getSelectedItem();
        if (selectedModel != null && !selectedModel.trim().isEmpty()) {
            settingsService.setSelectedModelId(selectedModel);
        } else {
            settingsService.setSelectedModelId(DEFAULT_MODEL_ID);
        }
        super.doOKAction();
    }
}