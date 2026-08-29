package io.github.supermonster003.autojs6.plugin.nodejs;

import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fail-closed admission for source that reaches the Node runtime boundary.
 *
 * <p>The runtime executes JavaScript compiler output only. TypeScript source
 * must be compiled by the host-owned compiler integration before dispatch;
 * no request flag can enable an in-runtime fallback.</p>
 */
final class NodeTypeScriptSourcePolicy {

    static final String ERROR_COMPILER_REQUIRED =
            NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED;
    static final int DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 256;
    private static final String DIAGNOSTIC_SOURCE_NAME_SEPARATOR = "...";

    private NodeTypeScriptSourcePolicy() {
    }

    static String requireJavaScriptSource(String sourceName, String source) {
        requireCompilerOutput(sourceName);
        return source;
    }

    static Map<String, String> requireJavaScriptSources(Map<String, String> sources) {
        LinkedHashMap<String, String> admitted = new LinkedHashMap<>();
        if (sources != null) {
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                requireCompilerOutput(entry.getKey());
                admitted.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return Collections.unmodifiableMap(admitted);
    }

    static void requireCompilerOutput(String sourceName) {
        if (isAnyTypeScriptSourceName(sourceName)) {
            throw compilerRequired(sourceName);
        }
    }

    static boolean isTypeScriptSourceName(String sourceName) {
        if (isTypeScriptDeclarationSourceName(sourceName)) {
            return false;
        }
        String extension = extension(sourceName).toLowerCase(Locale.ROOT);
        return "ts".equals(extension) || "mts".equals(extension) || "cts".equals(extension);
    }

    static boolean isTypeScriptDeclarationSourceName(String sourceName) {
        String lowerCaseName = normalizedName(sourceName).toLowerCase(Locale.ROOT);
        return lowerCaseName.endsWith(".d.ts")
                || lowerCaseName.endsWith(".d.mts")
                || lowerCaseName.endsWith(".d.cts");
    }

    static boolean isAnyTypeScriptSourceName(String sourceName) {
        String extension = extension(sourceName).toLowerCase(Locale.ROOT);
        return isTypeScriptSourceName(sourceName)
                || isTypeScriptDeclarationSourceName(sourceName)
                || "tsx".equals(extension);
    }

    static DiagnosticSourceName diagnosticSourceName(String sourceName) {
        String normalized = normalizedName(sourceName);
        if (normalized.length() <= DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS) {
            return new DiagnosticSourceName(normalized, false);
        }
        int contentBudget = DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS
                - DIAGNOSTIC_SOURCE_NAME_SEPARATOR.length();
        int headBudget = contentBudget / 2;
        int tailBudget = contentBudget - headBudget;
        int headEnd = safeUtf16PrefixEnd(normalized, headBudget);
        int tailStart = Math.max(headEnd, normalized.length() - tailBudget);
        if (tailStart > 0
                && tailStart < normalized.length()
                && Character.isLowSurrogate(normalized.charAt(tailStart))
                && Character.isHighSurrogate(normalized.charAt(tailStart - 1))) {
            tailStart++;
        }
        return new DiagnosticSourceName(
                normalized.substring(0, headEnd)
                        + DIAGNOSTIC_SOURCE_NAME_SEPARATOR
                        + normalized.substring(tailStart),
                true
        );
    }

    private static CompilerRequiredException compilerRequired(String sourceName) {
        DiagnosticSourceName diagnosticSourceName = diagnosticSourceName(sourceName);
        return new CompilerRequiredException(
                sourceName,
                ERROR_COMPILER_REQUIRED
                        + ": Raw TypeScript must be compiled before Node.js Runtime execution: "
                        + diagnosticSourceName.value()
                        + ". Compile it through the AutoJs6 TypeScript Compiler plugin and dispatch the JavaScript output."
        );
    }

    private static int safeUtf16PrefixEnd(String value, int maximumEnd) {
        int end = Math.max(0, Math.min(maximumEnd, value.length()));
        if (end > 0
                && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return end;
    }

    private static String extension(String sourceName) {
        String normalized = normalizedName(sourceName);
        int index = normalized.lastIndexOf('.');
        return index < 0 || index == normalized.length() - 1
                ? ""
                : normalized.substring(index + 1);
    }

    private static String normalizedName(String sourceName) {
        return sourceName == null ? "" : sourceName;
    }

    static final class DiagnosticSourceName {
        private final String value;
        private final boolean truncated;

        DiagnosticSourceName(String value, boolean truncated) {
            this.value = value;
            this.truncated = truncated;
        }

        String value() {
            return value;
        }

        boolean truncated() {
            return truncated;
        }
    }

    static final class CompilerRequiredException extends IllegalArgumentException {
        private final String sourceName;

        CompilerRequiredException(String sourceName, String message) {
            super(message);
            this.sourceName = sourceName;
        }

        String errorCode() {
            return ERROR_COMPILER_REQUIRED;
        }

        String sourceName() {
            return sourceName;
        }

        String syntaxKind() {
            return "compiler_required";
        }

        int line() {
            return 1;
        }

        int column() {
            return 1;
        }

        Map<String, String> diagnostics() {
            DiagnosticSourceName diagnosticSourceName = diagnosticSourceName(sourceName);
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            values.put("embedded_script.error_code", errorCode());
            values.put("embedded_script.typescript.error", "true");
            values.put("embedded_script.typescript.source", diagnosticSourceName.value());
            values.put(
                    "embedded_script.typescript.source_truncated",
                    Boolean.toString(diagnosticSourceName.truncated())
            );
            values.put("embedded_script.typescript.syntax_kind", syntaxKind());
            values.put("embedded_script.typescript.line", Integer.toString(line()));
            values.put("embedded_script.typescript.column", Integer.toString(column()));
            return Collections.unmodifiableMap(values);
        }
    }
}
