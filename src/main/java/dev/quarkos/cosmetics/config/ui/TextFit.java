package dev.quarkos.cosmetics.config.ui;

import java.util.function.ToIntFunction;

public final class TextFit {
    private static final String ELLIPSIS = "…";

    private TextFit() {
    }

    /** Fits one line at grapheme boundaries supplied by Java's character break iterator. */
    public static String ellipsis(String text, int width, ToIntFunction<String> measure) {
        if (text == null || width <= 0) {
            return "";
        }
        String line = text.replace('\n', ' ').replace('\r', ' ');
        if (measure.applyAsInt(line) <= width) {
            return line;
        }
        if (measure.applyAsInt(ELLIPSIS) > width) {
            return "";
        }
        var breaks = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
        breaks.setText(line);
        String fitted = ELLIPSIS;
        for (int end = breaks.first(); (end = breaks.next()) != java.text.BreakIterator.DONE;) {
            String result = line.substring(0, end).stripTrailing() + ELLIPSIS;
            if (measure.applyAsInt(result) > width) {
                break;
            }
            fitted = result;
        }
        return fitted;
    }
}
