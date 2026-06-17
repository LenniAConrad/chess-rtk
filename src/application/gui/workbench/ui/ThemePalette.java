package application.gui.workbench.ui;

import java.awt.Color;

/**
 * Light/dark palette assignment for the public {@link Theme} color tokens.
 */
final class ThemePalette {

    /**
     * Prevents instantiation.
     */
    private ThemePalette() {
        // utility
    }

    static void applyLight() {
        Theme.BG = Theme.PASTEL_CHROME;
        Theme.TRANSPARENT = new Color(Theme.PASTEL_DOCUMENT.getRed(), Theme.PASTEL_DOCUMENT.getGreen(),
                Theme.PASTEL_DOCUMENT.getBlue(), 0);
        Theme.PANEL = Theme.PASTEL_DOCUMENT;
        Theme.PANEL_SOLID = Theme.blendOver(Theme.PANEL, Theme.BG);
        Theme.GLASS_HIGHLIGHT = new Color(255, 255, 255, 112);
        Theme.BACKDROP_TOP = new Color(0xF8F8FA);
        Theme.BACKDROP_BOTTOM = new Color(0xEFEFF3);
        Theme.ELEVATED = Theme.PASTEL_CHROME;
        Theme.ELEVATED_SOLID = Theme.blendOver(Theme.ELEVATED, Theme.BG);
        Theme.CARD = Theme.blendOver(Theme.PANEL, Theme.BG);
        Theme.CARD_BORDER = Theme.PASTEL_BORDER;
        Theme.LINE = Theme.PASTEL_BORDER;
        Theme.TEXT = Theme.PASTEL_INK;
        Theme.MUTED = Theme.PASTEL_MUTED;
        Theme.ACCENT = Theme.PASTEL_BLUE;
        Theme.SELECTION = new Color(0xDDEBFF);
        Theme.SELECTION_SOLID = Theme.blendOver(Theme.SELECTION, Theme.BG);
        Theme.ACCENT_HOVER = Theme.PASTEL_BLUE_HOVER;
        Theme.ACCENT_PRESSED = Theme.PASTEL_BLUE_PRESSED;
        Theme.SECONDARY_BUTTON = Theme.PASTEL_CHROME;
        Theme.SECONDARY_BUTTON_HOVER = Theme.PASTEL_SUBTLE;
        Theme.SECONDARY_BUTTON_PRESSED = new Color(0xDFDFE4);
        Theme.SECONDARY_BUTTON_TEXT = Theme.PASTEL_INK;
        Theme.GHOST_BUTTON = new Color(0, 0, 0, 0);
        Theme.GHOST_BUTTON_HOVER = Theme.PASTEL_SUBTLE;
        Theme.GHOST_BUTTON_PRESSED = new Color(0xDFDFE4);
        Theme.GHOST_BUTTON_TEXT = Theme.PASTEL_INK;
        Theme.DESTRUCTIVE_BUTTON = new Color(0xFFF1F0);
        Theme.DESTRUCTIVE_BUTTON_HOVER = new Color(0xFFE4E2);
        Theme.DESTRUCTIVE_BUTTON_PRESSED = new Color(0xFFD6D2);
        Theme.DESTRUCTIVE_BUTTON_TEXT = new Color(0xB3261E);
        Theme.BUTTON_DISABLED_BG = Theme.PASTEL_CHROME;
        Theme.BUTTON_DISABLED_BORDER = Theme.PASTEL_BORDER;
        Theme.BUTTON_DISABLED_TEXT = Theme.PASTEL_MUTED;
        Theme.INPUT_BORDER = new Color(0xC6C6CC);
        Theme.INPUT_FOCUS = Theme.PASTEL_BLUE;
        Theme.FOCUS_RING = Theme.withAlpha(Theme.INPUT_FOCUS, 95);
        Theme.INPUT_DISABLED = Theme.PASTEL_CHROME;
        Theme.TOGGLE_BG = Theme.PASTEL_SUBTLE;
        Theme.TOGGLE_BORDER = Theme.INPUT_BORDER;
        Theme.TOGGLE_TRACK = Theme.PASTEL_MUTED;
        Theme.TOGGLE_ON_BG = new Color(0xD5EBFF);
        Theme.TOGGLE_ON_TRACK = Theme.PASTEL_BLUE;
        Theme.TOGGLE_THUMB = Theme.PASTEL_DOCUMENT;
        Theme.INPUT = Theme.PASTEL_DOCUMENT;
        Theme.TEXT_AREA = Theme.PASTEL_DOCUMENT;
        Theme.TERMINAL = Theme.PASTEL_DOCUMENT;
        Theme.TERMINAL_TEXT = Theme.PASTEL_INK;
        Theme.TEXT_SELECTION = new Color(0xADD6FF);
        Theme.PRIMARY_BUTTON_TEXT = Color.WHITE;
        setFixedBoardAndEvalColors();
        Theme.TAB_ACCENT_UNDERLINE = Theme.withAlpha(Theme.ACCENT, 255);
        Theme.TAB_HOVER = new Color(Theme.PASTEL_SUBTLE.getRed(), Theme.PASTEL_SUBTLE.getGreen(),
                Theme.PASTEL_SUBTLE.getBlue(), 255);
        Theme.TAB_IDLE = new Color(Theme.BG.getRed(), Theme.BG.getGreen(), Theme.BG.getBlue(), 255);
        Theme.SCROLLBAR_TRACK = new Color(Theme.PASTEL_CHROME.getRed(), Theme.PASTEL_CHROME.getGreen(),
                Theme.PASTEL_CHROME.getBlue(), 0);
        Theme.SCROLLBAR_THUMB = new Color(Theme.PASTEL_MUTED.getRed(), Theme.PASTEL_MUTED.getGreen(),
                Theme.PASTEL_MUTED.getBlue(), 80);
        Theme.SCROLLBAR_THUMB_HOVER = new Color(Theme.PASTEL_MUTED.getRed(), Theme.PASTEL_MUTED.getGreen(),
                Theme.PASTEL_MUTED.getBlue(), 130);
        Theme.TOOLTIP_BG = Theme.PASTEL_CHROME;
        Theme.TOOLTIP_TEXT = Theme.PASTEL_INK;
        Theme.TOOLTIP_BORDER = Theme.PASTEL_BORDER;
        Theme.STATUS_SUCCESS_BG = new Color(239, 250, 243);
        Theme.STATUS_SUCCESS_BORDER = Theme.PASTEL_GREEN;
        Theme.STATUS_SUCCESS_TEXT = Theme.PASTEL_GREEN_TEXT;
        Theme.STATUS_WARNING_BG = new Color(255, 246, 216);
        Theme.STATUS_WARNING_BORDER = Theme.PASTEL_AMBER;
        Theme.STATUS_WARNING_TEXT = Theme.PASTEL_AMBER_TEXT;
        Theme.STATUS_ERROR_BG = new Color(255, 241, 240);
        Theme.STATUS_ERROR_BORDER = Theme.PASTEL_CORAL;
        Theme.STATUS_ERROR_TEXT = Theme.PASTEL_CORAL_TEXT;
        Theme.STATUS_INFO_BG = new Color(0xEAF4FF);
        Theme.STATUS_INFO_BORDER = Theme.PASTEL_BLUE;
        Theme.STATUS_INFO_TEXT = Theme.PASTEL_BLUE_TEXT;
        Theme.STATUS_READY_BG = Theme.STATUS_INFO_BG;
        Theme.STATUS_READY_BORDER = Theme.STATUS_INFO_BORDER;
        Theme.STATUS_READY_TEXT = Theme.STATUS_INFO_TEXT;
        Theme.STATUS_RUNNING_BG = Theme.STATUS_INFO_BG;
        Theme.STATUS_RUNNING_BORDER = Theme.STATUS_INFO_BORDER;
        Theme.STATUS_RUNNING_TEXT = Theme.STATUS_INFO_TEXT;
        Theme.STATUS_COMPLETE_BG = Theme.STATUS_SUCCESS_BG;
        Theme.STATUS_COMPLETE_BORDER = Theme.STATUS_SUCCESS_BORDER;
        Theme.STATUS_COMPLETE_TEXT = Theme.STATUS_SUCCESS_TEXT;
        Theme.STATUS_MISSING_BG = Theme.STATUS_WARNING_BG;
        Theme.STATUS_MISSING_BORDER = Theme.STATUS_WARNING_BORDER;
        Theme.STATUS_MISSING_TEXT = Theme.STATUS_WARNING_TEXT;
        Theme.STATUS_NOT_RUN_BG = Theme.PASTEL_CHROME;
        Theme.STATUS_NOT_RUN_BORDER = Theme.PASTEL_BORDER;
        Theme.STATUS_NOT_RUN_TEXT = Theme.PASTEL_MUTED;
        Theme.STATUS_PAUSED_BG = Theme.STATUS_WARNING_BG;
        Theme.STATUS_PAUSED_BORDER = Theme.STATUS_WARNING_BORDER;
        Theme.STATUS_PAUSED_TEXT = Theme.STATUS_WARNING_TEXT;
        Theme.STATUS_STALE_BG = Theme.STATUS_WARNING_BG;
        Theme.STATUS_STALE_BORDER = Theme.STATUS_WARNING_BORDER;
        Theme.STATUS_STALE_TEXT = Theme.STATUS_WARNING_TEXT;
        Theme.CODE_BLOCK_BG = Theme.PASTEL_CHROME;
        Theme.CODE_BLOCK_BORDER = Theme.PASTEL_BORDER;
        Theme.CODE_BLOCK_TEXT = Theme.PASTEL_INK;
        Theme.LOGO_BACKGROUND = new Color(Theme.PASTEL_PURPLE.getRed(), Theme.PASTEL_PURPLE.getGreen(),
                Theme.PASTEL_PURPLE.getBlue(), 230);
        Theme.LOGO_MARK = Theme.PASTEL_CORAL;
        Theme.TOGGLE_FOCUS = Theme.withAlpha(Theme.INPUT_FOCUS, 95);
        Theme.NN_POSITIVE = Theme.PASTEL_GREEN;
        Theme.NN_NEGATIVE = Theme.PASTEL_CORAL;
        Theme.NN_TRUNK = Theme.PASTEL_BLUE;
        Theme.NN_FOCUS = Theme.PASTEL_BLUE;
        Theme.NN_POLICY = Theme.PASTEL_PURPLE;
        Theme.NN_VALUE = Theme.PASTEL_GREEN;
        Theme.NN_NEUTRAL = Theme.PASTEL_SUBTLE;
        Theme.NN_HEAT_ZERO = Theme.PASTEL_SUBTLE;
    }

    static void applyDark() {
        Theme.BG = Theme.DARK_CHROME;
        Theme.TRANSPARENT = new Color(Theme.DARK_DOCUMENT.getRed(), Theme.DARK_DOCUMENT.getGreen(),
                Theme.DARK_DOCUMENT.getBlue(), 0);
        Theme.PANEL = Theme.DARK_DOCUMENT;
        Theme.PANEL_SOLID = Theme.blendOver(Theme.PANEL, Theme.BG);
        Theme.GLASS_HIGHLIGHT = new Color(255, 255, 255, 24);
        Theme.BACKDROP_TOP = new Color(0x303030);
        Theme.BACKDROP_BOTTOM = new Color(0x242424);
        Theme.ELEVATED = Theme.DARK_ELEVATED;
        Theme.ELEVATED_SOLID = Theme.blendOver(Theme.ELEVATED, Theme.BG);
        Theme.CARD = new Color(0x232323);
        Theme.CARD_BORDER = new Color(0x3E3E3E);
        Theme.LINE = Theme.DARK_SUBTLE;
        Theme.TEXT = Theme.DARK_INK;
        Theme.MUTED = Theme.DARK_MUTED;
        Theme.ACCENT = Theme.DARK_ACCENT;
        Theme.SELECTION = new Color(0x264F78);
        Theme.SELECTION_SOLID = Theme.blendOver(Theme.SELECTION, Theme.BG);
        Theme.ACCENT_HOVER = Theme.DARK_ACCENT_HOVER;
        Theme.ACCENT_PRESSED = Theme.DARK_ACCENT_PRESSED;
        Theme.SECONDARY_BUTTON = new Color(0x363636);
        Theme.SECONDARY_BUTTON_HOVER = new Color(0x424242);
        Theme.SECONDARY_BUTTON_PRESSED = new Color(0x4B4B4B);
        Theme.SECONDARY_BUTTON_TEXT = Theme.DARK_INK;
        Theme.GHOST_BUTTON = new Color(0, 0, 0, 0);
        Theme.GHOST_BUTTON_HOVER = new Color(0x373737);
        Theme.GHOST_BUTTON_PRESSED = new Color(0x424242);
        Theme.GHOST_BUTTON_TEXT = Theme.DARK_INK;
        Theme.DESTRUCTIVE_BUTTON = Theme.withAlpha(new Color(0xF07167), 18);
        Theme.DESTRUCTIVE_BUTTON_HOVER = Theme.withAlpha(new Color(0xF07167), 34);
        Theme.DESTRUCTIVE_BUTTON_PRESSED = Theme.withAlpha(new Color(0xF07167), 52);
        Theme.DESTRUCTIVE_BUTTON_TEXT = new Color(0xF07167);
        Theme.BUTTON_DISABLED_BG = Theme.DARK_DOCUMENT;
        Theme.BUTTON_DISABLED_BORDER = Theme.DARK_SUBTLE;
        Theme.BUTTON_DISABLED_TEXT = new Color(0x868686);
        Theme.INPUT_BORDER = Theme.DARK_BORDER;
        Theme.INPUT_FOCUS = Theme.DARK_ACCENT;
        Theme.FOCUS_RING = Theme.withAlpha(Theme.INPUT_FOCUS, 120);
        Theme.INPUT_DISABLED = new Color(0x323232);
        Theme.TOGGLE_BG = Theme.DARK_ELEVATED;
        Theme.TOGGLE_BORDER = Theme.DARK_BORDER;
        Theme.TOGGLE_TRACK = Theme.DARK_MUTED;
        Theme.TOGGLE_ON_BG = Theme.withAlpha(new Color(0x2489DB), 130);
        Theme.TOGGLE_ON_TRACK = Theme.DARK_ACCENT;
        Theme.TOGGLE_THUMB = Theme.DARK_INK;
        Theme.INPUT = Theme.DARK_ELEVATED;
        Theme.TEXT_AREA = Theme.DARK_DOCUMENT;
        Theme.TERMINAL = Theme.DARK_DOCUMENT;
        Theme.TERMINAL_TEXT = Theme.DARK_INK;
        Theme.TEXT_SELECTION = new Color(0x264F78);
        Theme.PRIMARY_BUTTON_TEXT = Color.WHITE;
        setFixedBoardAndEvalColors();
        Theme.TAB_ACCENT_UNDERLINE = Theme.withAlpha(Theme.ACCENT, 255);
        Theme.TAB_HOVER = new Color(0x373737);
        Theme.TAB_IDLE = new Color(Theme.BG.getRed(), Theme.BG.getGreen(), Theme.BG.getBlue(), 255);
        Theme.SCROLLBAR_TRACK = new Color(Theme.DARK_CHROME.getRed(), Theme.DARK_CHROME.getGreen(),
                Theme.DARK_CHROME.getBlue(), 0);
        Theme.SCROLLBAR_THUMB = new Color(Theme.DARK_MUTED.getRed(), Theme.DARK_MUTED.getGreen(),
                Theme.DARK_MUTED.getBlue(), 82);
        Theme.SCROLLBAR_THUMB_HOVER = new Color(Theme.DARK_MUTED.getRed(), Theme.DARK_MUTED.getGreen(),
                Theme.DARK_MUTED.getBlue(), 150);
        Theme.TOOLTIP_BG = Theme.DARK_ELEVATED;
        Theme.TOOLTIP_TEXT = Theme.DARK_INK;
        Theme.TOOLTIP_BORDER = Theme.DARK_BORDER;
        Theme.STATUS_SUCCESS_BG = Theme.DARK_ELEVATED;
        Theme.STATUS_SUCCESS_BORDER = Theme.DARK_GREEN;
        Theme.STATUS_SUCCESS_TEXT = Theme.DARK_SUCCESS_TEXT;
        Theme.STATUS_WARNING_BG = Theme.DARK_ELEVATED;
        Theme.STATUS_WARNING_BORDER = Theme.DARK_AMBER;
        Theme.STATUS_WARNING_TEXT = Theme.DARK_WARNING_TEXT;
        Theme.STATUS_ERROR_BG = Theme.DARK_ELEVATED;
        Theme.STATUS_ERROR_BORDER = Theme.DARK_CORAL;
        Theme.STATUS_ERROR_TEXT = Theme.DARK_ERROR_TEXT;
        Theme.STATUS_INFO_BG = Theme.DARK_ELEVATED;
        Theme.STATUS_INFO_BORDER = Theme.DARK_ACCENT;
        Theme.STATUS_INFO_TEXT = Theme.DARK_INFO_TEXT;
        Theme.STATUS_READY_BG = Theme.STATUS_INFO_BG;
        Theme.STATUS_READY_BORDER = Theme.STATUS_INFO_BORDER;
        Theme.STATUS_READY_TEXT = Theme.STATUS_INFO_TEXT;
        Theme.STATUS_RUNNING_BG = Theme.STATUS_INFO_BG;
        Theme.STATUS_RUNNING_BORDER = Theme.STATUS_INFO_BORDER;
        Theme.STATUS_RUNNING_TEXT = Theme.STATUS_INFO_TEXT;
        Theme.STATUS_COMPLETE_BG = Theme.STATUS_SUCCESS_BG;
        Theme.STATUS_COMPLETE_BORDER = Theme.STATUS_SUCCESS_BORDER;
        Theme.STATUS_COMPLETE_TEXT = Theme.STATUS_SUCCESS_TEXT;
        Theme.STATUS_MISSING_BG = Theme.STATUS_WARNING_BG;
        Theme.STATUS_MISSING_BORDER = Theme.STATUS_WARNING_BORDER;
        Theme.STATUS_MISSING_TEXT = Theme.STATUS_WARNING_TEXT;
        Theme.STATUS_NOT_RUN_BG = Theme.DARK_ELEVATED;
        Theme.STATUS_NOT_RUN_BORDER = Theme.DARK_BORDER;
        Theme.STATUS_NOT_RUN_TEXT = Theme.DARK_MUTED;
        Theme.STATUS_PAUSED_BG = Theme.STATUS_WARNING_BG;
        Theme.STATUS_PAUSED_BORDER = Theme.STATUS_WARNING_BORDER;
        Theme.STATUS_PAUSED_TEXT = Theme.STATUS_WARNING_TEXT;
        Theme.STATUS_STALE_BG = Theme.STATUS_WARNING_BG;
        Theme.STATUS_STALE_BORDER = Theme.STATUS_WARNING_BORDER;
        Theme.STATUS_STALE_TEXT = Theme.STATUS_WARNING_TEXT;
        Theme.CODE_BLOCK_BG = Theme.DARK_DOCUMENT;
        Theme.CODE_BLOCK_BORDER = Theme.DARK_BORDER;
        Theme.CODE_BLOCK_TEXT = Theme.DARK_INK;
        Theme.LOGO_BACKGROUND = new Color(Theme.DARK_PURPLE.getRed(), Theme.DARK_PURPLE.getGreen(),
                Theme.DARK_PURPLE.getBlue(), 230);
        Theme.LOGO_MARK = Theme.DARK_CORAL;
        Theme.TOGGLE_FOCUS = Theme.withAlpha(Theme.INPUT_FOCUS, 120);
        Theme.NN_POSITIVE = Theme.DARK_GREEN;
        Theme.NN_NEGATIVE = Theme.DARK_CORAL;
        Theme.NN_TRUNK = Theme.DARK_ACCENT;
        Theme.NN_FOCUS = Theme.DARK_ACCENT;
        Theme.NN_POLICY = Theme.DARK_PURPLE;
        Theme.NN_VALUE = Theme.DARK_GREEN;
        Theme.NN_NEUTRAL = Theme.DARK_SUBTLE;
        Theme.NN_HEAT_ZERO = Theme.DARK_SUBTLE;
    }

    private static void setFixedBoardAndEvalColors() {
        Theme.BOARD_LIGHT = new Color(240, 217, 181);
        Theme.BOARD_DARK = new Color(181, 136, 99);
        Theme.BOARD_SHADOW = new Color(64, 64, 64);
        Theme.BOARD_EDGE = new Color(64, 64, 64);
        Theme.COORD_ON_LIGHT = Theme.BOARD_DARK;
        Theme.COORD_ON_DARK = Theme.BOARD_LIGHT;
        Theme.BOARD_HIGHLIGHT = new Color(246, 222, 93, 86);
        Theme.LAST_MOVE_EDGE = Theme.BOARD_HIGHLIGHT;
        Theme.SELECTED_EDGE = new Color(101, 143, 74, 112);
        Theme.LEGAL_TARGET = new Color(77, 103, 50, 158);
        Theme.LEGAL_CAPTURE_FILL = new Color(77, 103, 50, 58);
        Theme.LEGAL_CAPTURE_EDGE = new Color(77, 103, 50, 132);
        Theme.BOARD_ARROW = new Color(143, 189, 232);
        Color check = Theme.isDark() ? Theme.DARK_CORAL : Theme.PASTEL_CORAL;
        Theme.CHECK_CORE = new Color(check.getRed(), check.getGreen(), check.getBlue(), 245);
        Theme.CHECK_GLOW = Theme.withAlpha(check, 209);
        Theme.CHECK_FILL = new Color(check.getRed(), check.getGreen(), check.getBlue(), 190);
        Theme.CHECK_EDGE = Theme.withAlpha(check, 105);
        Theme.EVAL_BLACK = Color.BLACK;
        Theme.EVAL_WHITE = Color.WHITE;
        Theme.EVAL_FRAME = Theme.isDark() ? new Color(238, 238, 238) : new Color(17, 17, 17);
        Theme.EVAL_DIVIDER = new Color(128, 128, 128, 176);
    }
}
