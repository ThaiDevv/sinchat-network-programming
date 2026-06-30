package com.client.util;

/**
 * Centralized style constants used across all UI views.
 * Extracted from ChatView and LoginView to avoid duplication.
 */
public final class StyleConstants {
    private StyleConstants() {}

    // Background colors
    public static final String BG_BLACK   = "#000000";
    public static final String PANEL_DARK = "#111111";
    public static final String BORDER_COLOR = "#333333";

    // Text colors
    public static final String TEXT_WHITE  = "#ffffff";
    public static final String TEXT_MUTED  = "#888888";
    public static final String TEXT_DIM    = "#555555";

    // Accent
    public static final String INPUT_BORDER = "#444444";
    public static final String ACCENT       = "#7c5cfc";
    public static final String ACCENT_BLUE  = "#1877f2";

    // Pin
    public static final String PIN_COLOR = "#ffdd00";

    // --- LoginView styles ---

    public static final String STYLE_INPUT_BASE =
        "-fx-background-color: black;" +
        "-fx-border-width: 2px;" +
        "-fx-border-radius: 24px;" +
        "-fx-background-radius: 24px;" +
        "-fx-text-fill: white;" +
        "-fx-prompt-text-fill: #666;" +
        "-fx-font-size: 18px;" +
        "-fx-padding: 18px;";

    public static final String STYLE_INPUT_NORMAL  = STYLE_INPUT_BASE + "-fx-border-color: #444;";
    public static final String STYLE_INPUT_FOCUSED = STYLE_INPUT_BASE + "-fx-border-color: #e96161;";

    public static final String STYLE_BTN_NORMAL =
        "-fx-background-color: white; -fx-text-fill: black;" +
        "-fx-font-size: 20px; -fx-font-weight: bold;" +
        "-fx-background-radius: 24px; -fx-border-radius: 24px;" +
        "-fx-border-color: transparent; -fx-border-width: 3px; -fx-cursor: hand;";

    public static final String STYLE_BTN_FOCUSED =
        "-fx-background-color: white; -fx-text-fill: black;" +
        "-fx-font-size: 20px; -fx-font-weight: bold;" +
        "-fx-background-radius: 24px; -fx-border-radius: 24px;" +
        "-fx-border-color: #e96161; -fx-border-width: 3px; -fx-cursor: hand;";

    public static final String STYLE_EYE_NORMAL =
        "-fx-background-color: transparent; -fx-border-color: transparent;" +
        "-fx-border-width: 2px; -fx-border-radius: 8px;" +
        "-fx-text-fill: #aaa; -fx-font-size: 13px; -fx-cursor: hand;";

    public static final String STYLE_EYE_FOCUSED =
        "-fx-background-color: transparent; -fx-border-color: #e96161;" +
        "-fx-border-width: 2px; -fx-border-radius: 8px;" +
        "-fx-text-fill: #aaa; -fx-font-size: 13px; -fx-cursor: hand;";

    public static final String STYLE_LINK_NORMAL =
        "-fx-text-fill: #aaa; -fx-border-color: transparent;" +
        "-fx-border-width: 1px; -fx-border-radius: 6px;";

    public static final String STYLE_LINK_FOCUSED =
        "-fx-text-fill: #e96161; -fx-border-color: #e96161;" +
        "-fx-border-width: 1px; -fx-border-radius: 6px;";

    // --- ChatView common styles ---

    public static String contactItemStyle(String bg, String radius) {
        return "-fx-background-color: " + bg + "; -fx-background-radius: " + radius + "; -fx-cursor: hand;";
    }

    public static String contactItemHoverStyle(String radius) {
        return "-fx-background-color: #1e1e1e; -fx-background-radius: " + radius + "; -fx-cursor: hand;";
    }

    public static String contactItemNormalStyle(String radius) {
        return "-fx-background-color: transparent; -fx-background-radius: " + radius + "; -fx-cursor: hand;";
    }
}
