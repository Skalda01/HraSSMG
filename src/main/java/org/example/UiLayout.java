package org.example;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

/** Jediný zdroj souřadnic pro kreslení i ovládání; jednotky respektují DPI monitoru. */
final class UiLayout {
    final int pixelWidth, pixelHeight, width, height;
    final double scale;
    final boolean compact, shortEditor;
    final Rectangle board, editor, orders, code, play, step, reset, resetCode, assignment, documentation, console, source, example, speed;
    final Rectangle boardTab, editorTab, fullscreen, levelButton, nextLevel, levelDialog, levelList, levelRobot, levelMaterials, levelBack, levelReload, levelClose, levelActivate;
    final int levelRowHeight = 52;
    final Rectangle documentationWindow, documentationContent, documentationBack, documentationClose;
    final Rectangle arrayPrevious, arrayNext;
    final int lineHeight = 24;

    UiLayout(int pixelWidth, int pixelHeight, double dpi) {
        this.pixelWidth = Math.max(1, pixelWidth);
        this.pixelHeight = Math.max(1, pixelHeight);
        double requested = Math.max(0.5, dpi);
        scale = Math.min(requested, Math.min(this.pixelWidth / 640.0, this.pixelHeight / 440.0));
        width = (int) Math.floor(this.pixelWidth / scale);
        height = (int) Math.floor(this.pixelHeight / scale);
        compact = width < 1080;
        int margin = width < 800 ? 16 : 24;
        int gap = 20;
        int bottom = height - 32;
        int leftWidth = compact ? width - 2 * margin
                : width - 2 * margin - gap - Math.clamp((int) (width * 0.38), 470, 760);
        int top = compact ? 126 : 86;
        int panelHeight = bottom - top;
        board = new Rectangle(margin, top, leftWidth, panelHeight);
        arrayPrevious = new Rectangle(board.x + board.width - 140, board.y + board.height - 38, 54, 26);
        arrayNext = new Rectangle(board.x + board.width - 78, board.y + board.height - 38, 54, 26);
        nextLevel = new Rectangle(board.x + board.width - 150, board.y + 4, 132, 26);
        orders = new Rectangle(margin, bottom, width - 2 * margin, 0);
        editor = new Rectangle(compact ? margin : margin + leftWidth + gap, top,
                compact ? leftWidth : width - 2 * margin - leftWidth - gap, panelHeight);
        shortEditor = editor.height < 540;
        int inset = 18, inner = editor.width - 2 * inset;
        int controlsY = editor.y + editor.height - 114;
        int controlsWidth = inner - 30;
        int playWidth = (int) (controlsWidth * 0.38);
        int stepWidth = (int) (controlsWidth * 0.16);
        int resetWidth = (int) (controlsWidth * 0.18);
        play = new Rectangle(editor.x + inset, controlsY, playWidth, 40);
        step = new Rectangle(play.x + play.width + 10, controlsY, stepWidth, 40);
        reset = new Rectangle(step.x + step.width + 10, controlsY, resetWidth, 40);
        resetCode = new Rectangle(reset.x + reset.width + 10, controlsY, controlsWidth - playWidth - stepWidth - resetWidth, 40);
        int tabsY = editor.y + (shortEditor ? 34 : 87);
        source = new Rectangle(editor.x + inset, tabsY, inner / 4, 30);
        assignment = new Rectangle(source.x + source.width, tabsY, inner / 4, 30);
        documentation = new Rectangle(assignment.x + assignment.width, tabsY, inner / 4, 30);
        console = new Rectangle(documentation.x + documentation.width, tabsY, inner - 3 * source.width, 30);
        example = new Rectangle(editor.x + inset, editor.y + 38, inner, 38);
        int codeTop = tabsY + 39;
        code = new Rectangle(editor.x + inset, codeTop, inner, Math.max(24, controlsY - 44 - codeTop));
        speed = new Rectangle(editor.x + inset + 8, editor.y + editor.height - 46, inner - 16, 28);
        boardTab = new Rectangle(margin, 78, 140, 34);
        editorTab = new Rectangle(margin + 148, 78, 170, 34);
        fullscreen = new Rectangle(width - margin - 68, 18, 68, 30);
        levelButton = new Rectangle(fullscreen.x - 132, 18, 122, 30);
        int dialogWidth = Math.min(860, width - 32), dialogHeight = Math.min(620, height - 32);
        levelDialog = new Rectangle((width - dialogWidth) / 2, (height - dialogHeight) / 2, dialogWidth, dialogHeight);
        int chapterWidth = (dialogWidth - 50) / 2;
        levelRobot = new Rectangle(levelDialog.x + 20, levelDialog.y + 79, chapterWidth, 34);
        levelMaterials = new Rectangle(levelRobot.x + chapterWidth + 10, levelRobot.y, dialogWidth - 50 - chapterWidth, 34);
        levelBack = new Rectangle(levelDialog.x + 20, levelDialog.y + 126, 150, 30);
        levelList = new Rectangle(levelDialog.x + 20, levelDialog.y + 170, dialogWidth - 40, dialogHeight - 264);
        int footerY = levelDialog.y + dialogHeight - 54;
        levelActivate = new Rectangle(levelDialog.x + 20, footerY, 180, 36);
        levelClose = new Rectangle(levelDialog.x + dialogWidth - 120, footerY, 100, 36);
        levelReload = new Rectangle(levelClose.x - 142, footerY, 132, 36);
        documentationWindow = new Rectangle(board);
        documentationBack = new Rectangle(documentationWindow.x + 20, documentationWindow.y + 16, 170, 34);
        documentationClose = new Rectangle(documentationWindow.x + documentationWindow.width - 140, documentationWindow.y + 16, 120, 34);
        int textWidth = Math.min(1000, documentationWindow.width - 48);
        documentationContent = new Rectangle(documentationWindow.x + (documentationWindow.width - textWidth) / 2,
                documentationWindow.y + 112, textWidth, documentationWindow.height - 138);
    }

    Point2D.Double pointer(double x, double y, int windowWidth, int windowHeight) {
        return new Point2D.Double(x * pixelWidth / Math.max(1, windowWidth) / scale,
                y * pixelHeight / Math.max(1, windowHeight) / scale);
    }

    int visibleRows() { return Math.max(1, code.height / lineHeight); }
    int codeBaseline() { return code.y + 18; }
    int codeX() { return code.x + 44; }
    int codeRow(double y) { return (int) Math.floor((y - code.y) / lineHeight); }

    int speedAt(double x) {
        return Math.clamp(1 + (int) Math.round(9 * (x - speed.x) / speed.width), 1, 10);
    }
    int levelRows() { return Math.max(1, levelList.height / levelRowHeight); }

    record BoardLayout(Rectangle sidebar, int gridX, int gridY, double cell) {
        private int panelGap() { return sidebar.height < 280 ? 8 : 12; }
        private int panelHeight() { return (sidebar.height - panelGap()) / 2; }
        Rectangle orderPanel() { return new Rectangle(sidebar.x, sidebar.y, sidebar.width, panelHeight()); }
        Rectangle inventoryPanel() { return new Rectangle(sidebar.x, sidebar.y + panelHeight() + panelGap(), sidebar.width, panelHeight()); }
    }

    BoardLayout warehouse(Level level) {
        int inset = 12, minSidebarWidth = 164, gap = 32;
        double cell = Math.min((board.width - 2 * inset - minSidebarWidth - gap - 8) / (double) level.width(),
                (board.height - 76) / (double) level.height());
        int startX = board.x + inset;
        int gridX = board.x + board.width - inset - 8 - (int) (cell * level.width());
        int sidebarWidth = gridX - gap - startX;
        int gridY = (int) Math.round(board.y + 56 + (board.height - 76 - cell * level.height()) / 2);
        Rectangle sidebar = new Rectangle(startX, gridY - 8, sidebarWidth, (int) (cell * level.height()) + 16);
        return new BoardLayout(sidebar, gridX, gridY, cell);
    }
}
