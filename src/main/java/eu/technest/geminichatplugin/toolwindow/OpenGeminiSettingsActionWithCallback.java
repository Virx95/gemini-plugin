package eu.technest.geminichatplugin.toolwindow; // YOUR BASE PACKAGE

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper; // Needed for exit code
import eu.technest.geminichatplugin.settings.GeminiSettingsDialog; // YOUR BASE PACKAGE
import org.jetbrains.annotations.NotNull;

public class OpenGeminiSettingsActionWithCallback extends AnAction implements DumbAware {

    private final Runnable onOkCallback;

    public OpenGeminiSettingsActionWithCallback(Runnable onOkCallback) {
        super("Settings", "Configure Gemini API Key and Model", AllIcons.General.Settings);
        this.onOkCallback = onOkCallback;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        GeminiSettingsDialog dialog = new GeminiSettingsDialog(e.getProject());
        if (dialog.showAndGet()) { // showAndGet() returns true if OK was pressed
            if (onOkCallback != null) {
                onOkCallback.run();
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }
}