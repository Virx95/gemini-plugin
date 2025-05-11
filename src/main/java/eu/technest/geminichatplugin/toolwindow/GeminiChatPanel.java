package eu.technest.geminichatplugin.toolwindow; // YOUR BASE PACKAGE

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import eu.technest.geminichatplugin.css.Css;
import eu.technest.geminichatplugin.service.GeminiApiService; // YOUR BASE PACKAGE
import eu.technest.geminichatplugin.settings.GeminiSettingsService; // YOUR BASE PACKAGE
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static eu.technest.geminichatplugin.css.Css.toHex;

public class GeminiChatPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(GeminiChatPanel.class);

    private final Project project;
    private final JBTextArea inputField;
    private final JButton sendButton;
    private final GeminiSettingsService settingsService = GeminiSettingsService.getInstance();
    private final GeminiApiService geminiApiService;
    private final Css css;

    private final List<JsonObject> conversationHistory = new ArrayList<>();

    private final JEditorPane chatPane;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final StringBuilder chatHtmlContent = new StringBuilder("<html><body style='word-wrap: break-word;'>");

    private final JPanel loadingIndicatorPanel;
    private final JBLabel currentModelLabel;

    public GeminiChatPanel(Project project) {
        this.project = project;
        this.geminiApiService = new GeminiApiService();
        setLayout(new BorderLayout());

        MutableDataSet options = new MutableDataSet();
        markdownParser = Parser.builder(options).build();
        htmlRenderer = HtmlRenderer.builder(options).build();

        // --- Toolbar for Settings and Model Display ---
        JPanel topToolbarPanel = new JPanel(new BorderLayout(JBUI.scale(10), 0));
        topToolbarPanel.setBorder(JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(5), JBUI.scale(3), JBUI.scale(5))); // top, left, bottom, right padding

        // Settings Action with Callback
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        OpenGeminiSettingsActionWithCallback settingsAction = new OpenGeminiSettingsActionWithCallback(() -> {
            // This callback is executed after the settings dialog is closed with OK
            ApplicationManager.getApplication().invokeLater(this::updateCurrentModelLabel);
        });
        actionGroup.add(settingsAction);
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.TOOLWINDOW_TITLE, actionGroup, true); // true for horizontal
        actionToolbar.setTargetComponent(this); // Important for context

        // Current Model Label
        currentModelLabel = new JBLabel();
        currentModelLabel.setForeground(UIUtil.getLabelDisabledForeground());
        currentModelLabel.setBorder(JBUI.Borders.emptyLeft(JBUI.scale(5)));
        // Initial update will be called after UI is constructed or from welcome message

        topToolbarPanel.add(actionToolbar.getComponent(), BorderLayout.WEST);
        topToolbarPanel.add(currentModelLabel, BorderLayout.CENTER);

        add(topToolbarPanel, BorderLayout.NORTH);

        // --- Chat Display Area (JEditorPane) ---
        chatPane = new JEditorPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        chatPane.setEditorKit(kit);

        StyleSheet styleSheet = kit.getStyleSheet();
        this.css = new Css();
        css.setChatPanelCss(styleSheet);


        chatPane.setText(chatHtmlContent.toString() + "</body></html>");

        JBPopupMenu popupMenu = new JBPopupMenu();
        JBMenuItem clearChatItem = new JBMenuItem("Clear Chat");
        clearChatItem.addActionListener(e -> clearChat());
        popupMenu.add(clearChatItem);
        chatPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        // --- Loading Indicator ---
        loadingIndicatorPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        setUpLoadingPanel();

        // --- Input Area ---
        JPanel inputSectionPanel = new JPanel(new BorderLayout());
        inputField = new JBTextArea(3, 20); // 3 rows visible by default
        inputField.setLineWrap(true);
        inputField.setWrapStyleWord(true);
        sendButton = new JButton("Send");
        setUpInputPanel(inputSectionPanel);

        // --- JLayeredPane for Chat and Input ---
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(new BorderLayout());

        JBScrollPane scrollPane = new JBScrollPane(chatPane);
        layeredPane.add(scrollPane, BorderLayout.CENTER, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(inputSectionPanel, BorderLayout.SOUTH, JLayeredPane.PALETTE_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        // --- Event Listeners ---
        sendButton.addActionListener(this::sendMessage);
        
        // For multi-line text area, use Ctrl+Enter to send message
        inputField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    sendMessage(new ActionEvent(inputField, ActionEvent.ACTION_PERFORMED, null));
                    e.consume(); // Prevent newline from being added
                }
            }
        });

        // --- Initial Message and Model Label Update ---
        // Defer this until the panel is potentially visible or after a short delay
        // to ensure component sizes are calculated for truncation if needed.
        ApplicationManager.getApplication().invokeLater(() -> {
            updateCurrentModelLabel();
            appendMessage(SenderType.SYSTEM, "Welcome! Using model: " + settingsService.getSelectedModelId() + "\nTip: Press Ctrl+Enter to send message", false);
        });
    }

    private void setUpInputPanel(JPanel inputSectionPanel) {
        // Create a scrollable text area for multi-line input
        JBScrollPane scrollPane = new JBScrollPane(inputField);
        scrollPane.setPreferredSize(new Dimension(scrollPane.getPreferredSize().width, JBUI.scale(70)));
        scrollPane.setBorder(JBUI.Borders.empty());
        
        sendButton.setPreferredSize(new Dimension(JBUI.scale(70), JBUI.scale(28)));
    
        // Create a panel for the send button (align to top)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(sendButton);
    
        JPanel actualInputPanel = new JPanel(new BorderLayout());
        actualInputPanel.add(scrollPane, BorderLayout.CENTER);
        actualInputPanel.add(buttonPanel, BorderLayout.EAST);
        actualInputPanel.setBorder(JBUI.Borders.emptyTop(JBUI.scale(5)));

        inputSectionPanel.add(loadingIndicatorPanel, BorderLayout.NORTH);
        inputSectionPanel.add(actualInputPanel, BorderLayout.CENTER);
        inputSectionPanel.setBorder(JBUI.Borders.empty(0, 5, 5, 5));
    }

    private void setUpLoadingPanel() {
        JBLabel loadingLabel = new JBLabel("Processing...", new AnimatedIcon.Default(), SwingConstants.LEFT);
        loadingIndicatorPanel.add(loadingLabel);
        loadingIndicatorPanel.setVisible(false);
        loadingIndicatorPanel.setBorder(JBUI.Borders.emptyBottom(JBUI.scale(5)));
    }

    private void updateCurrentModelLabel() {
        // This method should always be called on the EDT
        String modelId = settingsService.getSelectedModelId();
        String displayText = modelId;
        int maxLength = 25; // Define a max length for display

        // Simple truncation logic, could be more sophisticated (e.g., based on actual component width)
        if (currentModelLabel.getGraphics() != null && currentModelLabel.getWidth() > 0) {
            FontMetrics fm = currentModelLabel.getFontMetrics(currentModelLabel.getFont());
            int maxWidthPixels = currentModelLabel.getWidth() - JBUI.scale(10); // Give some padding
            if (fm.stringWidth(modelId) > maxWidthPixels) {
                String temp = modelId;
                while (fm.stringWidth(temp + "...") > maxWidthPixels && temp.length() > 0) {
                    temp = temp.substring(0, temp.length() -1);
                }
                displayText = temp + "...";
            }
        } else if (modelId.length() > maxLength) { // Fallback if graphics context not available yet
            displayText = modelId.substring(0, maxLength - 3) + "...";
        }

        currentModelLabel.setText("Model: " + displayText);
        currentModelLabel.setToolTipText("Current Model: " + modelId);
        LOG.debug("Updated currentModelLabel. Display: '" + displayText + "', Full: '" + modelId + "'");
    }

    private void clearChat() {
        ApplicationManager.getApplication().invokeLater(() -> {
            chatHtmlContent.setLength(0);
            chatHtmlContent.append("<html><body style='word-wrap: break-word;'>");
            appendMessage(SenderType.SYSTEM, "Chat cleared.", false);
            conversationHistory.clear();
            chatPane.setText(chatHtmlContent.toString() + "</body></html>");
        });
    }

    private void setLoading(boolean isLoading) {
        ApplicationManager.getApplication().invokeLater(() -> {
            loadingIndicatorPanel.setVisible(isLoading);
            setInteractionEnabled(!isLoading);
        });
    }

    private void appendMessage(SenderType senderType, String messageText, boolean isUserMessageForHistoryIgnored) {
        // isUserMessageForHistoryIgnored is not actively used now as history is managed in sendMessage
        String htmlMessage = formatMessageToHtml(senderType, messageText);

        String senderColorHex = getSenderColorHex(senderType);
        String senderStyle = String.format("font-weight: bold; color: %s;", senderColorHex);

        String formattedEntry = String.format(
                "<div style='margin-bottom: %dpx;'>" +
                        "  <span style='%s'>%s:</span>" +
                        "  %s" +
                        "</div>",
                JBUI.scale(10),
                senderStyle,
                senderType.getDisplayName(),
                htmlMessage
        );

        ApplicationManager.getApplication().invokeLater(() -> {
            int insertPosition = chatHtmlContent.lastIndexOf("</body>");
            if (insertPosition != -1) {
                chatHtmlContent.insert(insertPosition, formattedEntry);
            } else {
                chatHtmlContent.append(formattedEntry);
            }
            chatPanesetTextPreserveScroll(chatHtmlContent.toString()); // Use helper
        });
    }

    private @NotNull String formatMessageToHtml(SenderType senderType, String messageText) {
        String htmlMessage;
        if (senderType == SenderType.GEMINI) {
            com.vladsch.flexmark.util.ast.Node document = markdownParser.parse(messageText);
            htmlMessage = htmlRenderer.render(document);
        } else {
            htmlMessage = messageText.replace("&", "&").replace("<", "<").replace(">", ">");
            htmlMessage = "<p>" + htmlMessage.replace("\n", "<br>") + "</p>"; // Also replace newlines for non-markdown
        }
        return htmlMessage;
    }

    private String getSenderColorHex(SenderType senderType) {
        String senderColorHex;
        switch (senderType) {
            case USER:
                senderColorHex = toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED);
                break;
            case SYSTEM:
            case ERROR:
                senderColorHex = toHex(JBUI.CurrentTheme.Label.disabledForeground());
                break;
            case GEMINI:
            default:
                senderColorHex = toHex(UIUtil.getLabelForeground());
                break;
        }
        return senderColorHex;
    }

    /**
     * Sets text on chatPane and tries to preserve scroll position or scroll to bottom.
     */
    private void chatPanesetTextPreserveScroll(String html) {
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatPane);
        JScrollBar verticalScrollBar = null;
        int previousValue = 0;
        boolean shouldScrollToBottom = true;

        if (scrollPane != null) {
            verticalScrollBar = scrollPane.getVerticalScrollBar();
            // If scrollbar is already at the bottom, we want to keep it at the bottom after update
            shouldScrollToBottom = (verticalScrollBar.getValue() + verticalScrollBar.getVisibleAmount()) >= (verticalScrollBar.getMaximum() - 20); // Give some tolerance
            if (!shouldScrollToBottom) {
                previousValue = verticalScrollBar.getValue();
            }
        }

        chatPane.setText(html); // This can reset scroll position

        JScrollBar finalVerticalScrollBar = verticalScrollBar; // For use in lambda
        int finalPreviousValue = previousValue;
        boolean finalShouldScrollToBottom = shouldScrollToBottom;

        // Restore scroll position after text update
        SwingUtilities.invokeLater(() -> {
            if (finalVerticalScrollBar != null) {
                if (finalShouldScrollToBottom || chatPane.getDocument().getLength() < 500) { // Heuristic: scroll to bottom for short content too
                    finalVerticalScrollBar.setValue(finalVerticalScrollBar.getMaximum());
                } else {
                    finalVerticalScrollBar.setValue(Math.min(finalPreviousValue, finalVerticalScrollBar.getMaximum()));
                }
            }
        });
    }


    private void sendMessage(ActionEvent e) {
        String userInput = inputField.getText().trim();
        if (userInput.isEmpty()) {
            return;
        }

        String apiKey = settingsService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Messages.showErrorDialog(project, "Gemini API Key is not set. Please configure it in Settings.", "API Key Missing");
            return;
        }

        String selectedModel = settingsService.getSelectedModelId();
        LOG.info("Sending message with model: " + selectedModel);

        appendMessage(SenderType.USER, userInput, true);
        inputField.setText("");
        setLoading(true);
        ApplicationManager.getApplication().invokeLater(this::updateCurrentModelLabel); // Ensure label is up-to-date

        JsonObject currentUserContentForHistory = new JsonObject();
        JsonObject userMessagePart = new JsonObject();
        userMessagePart.addProperty("text", userInput);
        JsonArray userPartsArray = new JsonArray();
        userPartsArray.add(userMessagePart);
        currentUserContentForHistory.addProperty("role", "user");
        currentUserContentForHistory.add("parts", userPartsArray);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<JsonObject> currentRequestHistory = new ArrayList<>(conversationHistory);
            geminiApiService.generateContent(apiKey, selectedModel, userInput, currentRequestHistory, new GeminiApiService.GeminiApiResponseCallback() {
                @Override
                public void onSuccess(String geminiResponse, JsonObject modelContent) {
                    setLoading(false);
                    appendMessage(SenderType.GEMINI, geminiResponse, false);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        conversationHistory.add(currentUserContentForHistory);
                        conversationHistory.add(modelContent);
                    });
                }

                @Override
                public void onFailure(String errorMessage, String detailedError) {
                    setLoading(false);
                    String fullErrorMessage = errorMessage;
                    if (detailedError != null && !detailedError.isEmpty() && !detailedError.equals(errorMessage)) {
                        fullErrorMessage += " Details: " + detailedError;
                    }
                    appendMessage(SenderType.ERROR, fullErrorMessage, false);
                    System.err.println("Gemini API Error: " + errorMessage + (detailedError != null ? "\nDetails: " + detailedError : ""));
                }
            });
        });
    }

    private void setInteractionEnabled(boolean enabled) {
        ApplicationManager.getApplication().invokeLater(() -> {
            inputField.setEnabled(enabled);
            sendButton.setEnabled(enabled);
        });
    }
}