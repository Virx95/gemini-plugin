package eu.technest.geminichatplugin.css;

import com.intellij.openapi.components.Service;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.text.html.StyleSheet;
import java.awt.*;

@Service
public class Css {

    public void setChatPanelCss(StyleSheet styleSheet) {
        Font defaultFont = UIUtil.getLabelFont();
        int defaultFontSize = defaultFont.getSize();
        int codeFontSize = defaultFontSize - 1;
        Font monoFontRaw = new Font(Font.MONOSPACED, Font.PLAIN, codeFontSize);
        Font monospacedFont = JBFont.create(monoFontRaw);
        // --- CSS Styles ---
        styleSheet.addRule(String.format(
                "body { font-family: '%s', sans-serif; font-size: %dpt; margin: %dpx; color: %s; background-color: %s; }",
                defaultFont.getFamily(), defaultFontSize, JBUI.scale(5),
                toHex(UIUtil.getLabelForeground()), toHex(UIUtil.getEditorPaneBackground())
        ));
        styleSheet.addRule("p { margin-top: 0.5em; margin-bottom: 0.5em; }");
        styleSheet.addRule("ul { margin-left: %dpx; padding-left: 0; list-style-type: disc; }".formatted(JBUI.scale(20)));
        styleSheet.addRule("ol { margin-left: %dpx; padding-left: 0; list-style-type: decimal; }".formatted(JBUI.scale(20)));
        styleSheet.addRule("li { margin-bottom: 0.2em; }");
        styleSheet.addRule("strong { font-weight: bold; }");
        styleSheet.addRule("em { font-style: italic; }");
        styleSheet.addRule(String.format("h1 { font-size: %dpt; font-weight: bold; margin-top: 1em; margin-bottom: 0.5em; }", (int)(defaultFontSize * 1.5)));
        styleSheet.addRule(String.format("h2 { font-size: %dpt; font-weight: bold; margin-top: 0.8em; margin-bottom: 0.4em; }", (int)(defaultFontSize * 1.3)));
        styleSheet.addRule(String.format("h3 { font-size: %dpt; font-weight: bold; margin-top: 0.7em; margin-bottom: 0.3em; }", (int)(defaultFontSize * 1.1)));
        styleSheet.addRule(String.format(
                "pre { background-color: %s; padding: %dpx %dpx; border: 1px solid %s; border-radius: %dpx; overflow-x: auto; font-family: '%s', monospace; font-size: %dpt; margin-top: 0.5em; margin-bottom: 0.5em; }",
                toHex(UIUtil.getEditorPaneBackground().brighter()), JBUI.scale(5), JBUI.scale(8),
                toHex(UIUtil.getBoundsColor()), JBUI.scale(3), monospacedFont.getFamily(), codeFontSize
        ));
        styleSheet.addRule(String.format(
                "code { font-family: '%s', monospace; background-color: %s; color: %s; padding: %dpx %dpx; border-radius: %dpx; font-size: %dpt; }",
                monospacedFont.getFamily(), toHex(UIManager.getColor("TextField.background")),
                toHex(UIUtil.getLabelForeground()), JBUI.scale(1), JBUI.scale(3), JBUI.scale(3), codeFontSize
        ));
        styleSheet.addRule(String.format(
                "blockquote { border-left: %dpx solid %s; margin-left: 0; padding-left: %dpx; color: %s; }",
                JBUI.scale(3), toHex(UIUtil.getLabelDisabledForeground()), JBUI.scale(10),
                toHex(UIUtil.getLabelDisabledForeground().darker())
        ));
        styleSheet.addRule(String.format(
                "hr { border: 0; height: %dpx; background-color: %s; margin-top: 1em; margin-bottom: 1em; }",
                JBUI.scale(1), toHex(UIUtil.getBoundsColor())
        ));
        // --- End CSS Styles ---
    }

    public static String toHex(Color color) {
        if (color == null) return "#000000";
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
