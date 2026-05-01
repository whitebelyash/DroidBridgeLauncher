package ca.dnamobile.javalauncher.controls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numeric expression resolver for imported Pojav/Zalith/Mojo/Amethyst dynamicX/dynamicY values.
 *
 * Important for Zalith profiles:
 * - width/height passed here are the final Android view size in physical pixels.
 * - px(...) and dp(...) in Zalith formulas also need to resolve to physical pixels.
 *   If px(160) is treated as 160 instead of 160 * density, controls overlap.
 */
final class ExpressionResolver {
    private static final Pattern PX_FUNCTION = Pattern.compile("\\bpx\\s*\\(\\s*([-+]?[0-9]*\\.?[0-9]+)\\s*\\)");
    private static final Pattern DP_FUNCTION = Pattern.compile("\\bdp\\s*\\(\\s*([-+]?[0-9]*\\.?[0-9]+)\\s*\\)");

    private final String text;
    private int position;

    private ExpressionResolver(@NonNull String text) {
        this.text = text;
    }

    static float resolve(
            @Nullable String input,
            float fallback,
            int screenWidth,
            int screenHeight,
            float controlWidth,
            float controlHeight
    ) {
        return resolve(input, fallback, screenWidth, screenHeight, controlWidth, controlHeight, 1f, 100f);
    }

    static float resolve(
            @Nullable String input,
            float fallback,
            int screenWidth,
            int screenHeight,
            float controlWidth,
            float controlHeight,
            float density,
            float preferredScale
    ) {
        if (input == null || input.trim().isEmpty()) return fallback;

        float safeDensity = density > 0f ? density : 1f;
        float safePreferredScale = preferredScale > 0f ? preferredScale : 100f;
        float margin = 2f * safeDensity;

        String prepared = input.trim()
                .replace("${screen_width}", String.valueOf(screenWidth))
                .replace("${screen_height}", String.valueOf(screenHeight))
                .replace("${width}", String.valueOf(controlWidth))
                .replace("${height}", String.valueOf(controlHeight))
                .replace("${preferred_scale}", String.valueOf(safePreferredScale))
                .replace("${scale}", String.valueOf(safePreferredScale))
                .replace("${margin}", String.valueOf(margin))
                .replace("${right}", String.valueOf(Math.max(0f, screenWidth - controlWidth)))
                .replace("${bottom}", String.valueOf(Math.max(0f, screenHeight - controlHeight)));

        prepared = replacePixelFunction(prepared, PX_FUNCTION, safeDensity);
        prepared = replacePixelFunction(prepared, DP_FUNCTION, safeDensity);

        try {
            ExpressionResolver parser = new ExpressionResolver(prepared);
            double value = parser.parseExpression();
            parser.skipSpaces();
            if (parser.position != prepared.length()) {
                throw new IllegalArgumentException("Unexpected token at " + parser.position + " in " + prepared);
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
            return (float) value;
        } catch (Throwable ignored) {
            try {
                return Float.parseFloat(prepared);
            } catch (Throwable ignoredAgain) {
                return fallback;
            }
        }
    }

    @NonNull
    private static String replacePixelFunction(@NonNull String input, @NonNull Pattern pattern, float density) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            float value = Float.parseFloat(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(value * density)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipSpaces();
            if (match('+')) value += parseTerm();
            else if (match('-')) value -= parseTerm();
            else return value;
        }
    }

    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            skipSpaces();
            if (match('*')) value *= parseFactor();
            else if (match('/')) value /= parseFactor();
            else return value;
        }
    }

    private double parseFactor() {
        skipSpaces();
        if (match('+')) return parseFactor();
        if (match('-')) return -parseFactor();
        if (match('(')) {
            double value = parseExpression();
            if (!match(')')) throw new IllegalArgumentException("Missing )");
            return value;
        }

        int start = position;
        boolean seenDot = false;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c >= '0' && c <= '9') {
                position++;
            } else if (c == '.' && !seenDot) {
                seenDot = true;
                position++;
            } else {
                break;
            }
        }
        if (start == position) {
            throw new IllegalArgumentException("Expected number at " + position + " in " + text);
        }
        return Double.parseDouble(text.substring(start, position));
    }

    private boolean match(char expected) {
        skipSpaces();
        if (position < text.length() && text.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void skipSpaces() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }
}
