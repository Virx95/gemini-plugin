package eu.technest.geminichatplugin.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import eu.technest.geminichatplugin.settings.GeminiSettingsDialog;
import org.jetbrains.annotations.NotNull;

public class OpenGeminiSettingsAction extends AnAction implements DumbAware {

    public OpenGeminiSettingsAction() {
        super("Settings", "Configure Gemini API Key", AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        GeminiSettingsDialog dialog = new GeminiSettingsDialog(e.getProject());
        dialog.show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

}
