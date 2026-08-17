package io.github.supermonster003.autojs6.plugin.nodejs;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight TypeScript erasure for plugin-owned pre-native dispatch.
 *
 * <p>This intentionally handles only erasable type syntax. TypeScript syntax
 * that requires JavaScript generation fails closed with the canonical runtime
 * error codes instead of being sent to Node as partially transformed source.</p>
 */
final class NodeTypeScriptStripper {

    static final String DIAGNOSTIC_SCOPE_MODULE_SOURCES = "module_sources";
    static final String DIAGNOSTIC_SCOPE_RUNTIME_MODULE_SOURCES = "runtime_module_sources";
    static final int SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT = 16;
    static final int DIAGNOSTIC_SOURCE_NAME_MAX_UTF16_CODE_UNITS = 256;
    private static final String DIAGNOSTIC_SOURCE_NAME_SEPARATOR = "...";

    static final String ERROR_UNSUPPORTED_EXTENSION =
            "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION";
    static final String ERROR_UNSUPPORTED_SYNTAX =
            "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX";

    private static final List<String> TYPESCRIPT_EXTENSIONS =
            Arrays.asList("ts", "mts", "cts");

    private static final List<UnsupportedPattern> UNSUPPORTED_PATTERNS = Arrays.asList(
            new UnsupportedPattern(
                    "enum",
                    "TypeScript enum declarations require code generation and are not supported",
                    Pattern.compile("(?m)(^|[;{}\\r\\n])\\s*(?:export\\s+)?(?:const\\s+)?enum\\s+[A-Za-z_$][0-9A-Za-z_$]*\\b")
            ),
            new UnsupportedPattern(
                    "namespace",
                    "TypeScript namespace/module declarations require code generation and are not supported",
                    Pattern.compile("(?m)(^|[;{}\\r\\n])\\s*(?:export\\s+)?(?:declare\\s+)?(?:namespace|module)\\s+[A-Za-z_$][0-9A-Za-z_$.]*\\s*\\{")
            ),
            new UnsupportedPattern(
                    "decorator",
                    "TypeScript decorators are not supported by AutoJs6 lightweight type stripping",
                    Pattern.compile("(?m)^\\s*@[A-Za-z_][0-9A-Za-z_$.]*")
            ),
            new UnsupportedPattern(
                    "parameter_property",
                    "TypeScript constructor parameter properties require code generation and are not supported",
                    Pattern.compile("\\bconstructor\\s*\\([^)]*\\b(?:public|private|protected|readonly)\\s+[A-Za-z_$][0-9A-Za-z_$]*\\s*:")
            ),
            new UnsupportedPattern(
                    "import_alias",
                    "TypeScript import aliases require code generation and are not supported",
                    Pattern.compile("(?m)(^|[;{}\\r\\n])\\s*import\\s+[A-Za-z_$][0-9A-Za-z_$]*\\s*=")
            )
    );

    private static final Pattern IMPORT_TYPE_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+type\\b[^\\r\\n;]*(?:;)?\\s*");
    private static final Pattern EXPORT_TYPE_PATTERN =
            Pattern.compile("(?m)^\\s*export\\s+type\\b[^\\r\\n;]*(?:;)?\\s*");
    private static final Pattern EXPORT_INTERFACE_INLINE_PATTERN =
            Pattern.compile("(?m)^\\s*export\\s+interface\\s+[A-Za-z_$][0-9A-Za-z_$]*(?:\\s+extends\\s+[^{]+)?\\s*\\{\\s*\\}\\s*");
    private static final Pattern INTERFACE_DECLARATION_PATTERN =
            Pattern.compile("(?m)(^|[\\r\\n])\\s*(?:export\\s+)?interface\\s+[A-Za-z_$][0-9A-Za-z_$]*(?:\\s+extends\\s+[^{]+)?\\s*");
    private static final Pattern TYPE_ALIAS_PATTERN =
            Pattern.compile("(?ms)(^|[\\r\\n])\\s*(?:export\\s+)?type\\s+[A-Za-z_$][0-9A-Za-z_$]*(?:\\s*<[^;\\r\\n=]+>)?\\s*=\\s*.*?;\\s*");
    private static final Pattern FUNCTION_TYPE_PARAMETERS_PATTERN =
            Pattern.compile("\\bfunction\\s+([A-Za-z_$][0-9A-Za-z_$]*)\\s*<[^>\\r\\n]+>\\s*\\(");
    private static final Pattern VARIABLE_TYPE_PATTERN =
            Pattern.compile("\\b(const|let|var)\\s+([A-Za-z_$][0-9A-Za-z_$]*)\\s*:\\s*[^=;\\r\\n]+(?=\\s*=)");
    private static final Pattern PARAMETER_TYPE_PATTERN =
            Pattern.compile("([,(]\\s*[A-Za-z_$][0-9A-Za-z_$]*)\\??\\s*:\\s*[^,)=\\r\\n]+(?=\\s*[,)=])");
    private static final Pattern RETURN_TYPE_PATTERN =
            Pattern.compile("(\\))\\s*:\\s*[^={;\\r\\n]+(?=\\s*(?:=>|\\{|;))");
    private static final Pattern PROPERTY_TYPE_PATTERN =
            Pattern.compile("(?m)^(\\s*(?:(?:public|private|protected|readonly|static)\\s+)*[A-Za-z_$][0-9A-Za-z_$]*)(\\??)\\s*:\\s*[^=;\\r\\n]+(?=\\s*[=;])");
    /**
     * Replaces string/template contents (and their delimiters) in the lexical
     * mask. Must be non-whitespace: expression-position anchors such as the
     * as-assertion prefix (S)s+as must treat a masked literal as a value,
     * or ('cjs' as string) collapses the entire literal into the stripped
     * span. Kept outside every rewriter type character class so masked
     * literals are never mistaken for erasable type syntax.
     */
    private static final char STRING_MASK_CHAR = '\u0001';
    private static final Pattern AS_ASSERTION_PATTERN =
            Pattern.compile("(\\S)\\s+as\\s+[A-Za-z_$][0-9A-Za-z_$<>,.? \\t\\[\\]|&{}:'\"-]*(?=\\s*[,);+\\-*/\\]\\r\\n])");
    private static final Pattern SATISFIES_ASSERTION_PATTERN =
            Pattern.compile("(\\S)\\s+satisfies\\s+[A-Za-z_$][0-9A-Za-z_$<>,.? \\t\\[\\]|&{}:'\"-]*(?=\\s*[,);+\\-*/\\]\\r\\n])");

    private NodeTypeScriptStripper() {
    }

    static Result stripIfTypeScript(String sourceName, String source) {
        if (isTypeScriptDeclarationSourceName(sourceName)) {
            return new Result(source, false, Collections.emptyMap());
        }
        String extension = extension(sourceName).toLowerCase(Locale.ROOT);
        if ("tsx".equals(extension)) {
            throw unsupportedExtension(sourceName);
        }
        if (!TYPESCRIPT_EXTENSIONS.contains(extension)) {
            return new Result(source, false, Collections.emptyMap());
        }

        detectUnsupportedSyntax(sourceName, source);
        String output = source;
        output = removeTypeOnlyStatements(output);
        output = removeInterfaceDeclarations(output);
        output = removeTypeAliasDeclarations(output);
        output = stripFunctionTypeParameters(output);
        output = stripTypeAnnotations(output);
        output = stripTypeAssertions(output);

        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        DiagnosticSourceName diagnosticSourceName = diagnosticSourceName(sourceName);
        diagnostics.put("embedded_script.typescript.stripped", Boolean.toString(!output.equals(source)));
        diagnostics.put("embedded_script.typescript.source", diagnosticSourceName.value());
        diagnostics.put(
                "embedded_script.typescript.source_truncated",
                Boolean.toString(diagnosticSourceName.truncated())
        );
        diagnostics.put("embedded_script.typescript.extension", extension);
        return new Result(output, !output.equals(source), diagnostics);
    }

    /**
     * Strips a bounded, preloaded source map without changing its names or
     * insertion order. Sources without a TypeScript extension are copied
     * byte-for-byte, including plugin-generated runtime modules. A generated
     * module is transformed only when its source name explicitly ends in a
     * supported TypeScript extension.
     */
    static SourceMapResult stripSourceMap(
            String diagnosticScope,
            Map<String, String> sources
    ) {
        LinkedHashMap<String, String> preparedSources = new LinkedHashMap<>();
        LinkedHashMap<String, String> sourceDiagnostics = new LinkedHashMap<>();
        int inputCount = 0;
        int typeScriptCount = 0;
        int strippedCount = 0;

        if (sources != null) {
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                String sourceName = entry.getKey();
                String source = entry.getValue() == null ? "" : entry.getValue();
                Result result = stripIfTypeScript(sourceName, source);
                preparedSources.put(sourceName, result.source());
                inputCount++;
                if (!result.diagnostics().isEmpty()) {
                    if (typeScriptCount < SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT) {
                        String prefix = "embedded_script.typescript."
                                + diagnosticScope
                                + ".source."
                                + typeScriptCount
                                + ".";
                        sourceDiagnostics.put(
                                prefix + "name",
                                result.diagnostics().get("embedded_script.typescript.source")
                        );
                        sourceDiagnostics.put(
                                prefix + "name_truncated",
                                result.diagnostics().get("embedded_script.typescript.source_truncated")
                        );
                        sourceDiagnostics.put(
                                prefix + "extension",
                                result.diagnostics().get("embedded_script.typescript.extension")
                        );
                        sourceDiagnostics.put(prefix + "stripped", Boolean.toString(result.stripped()));
                    }
                    typeScriptCount++;
                    if (result.stripped()) {
                        strippedCount++;
                    }
                }
            }
        }

        LinkedHashMap<String, String> diagnostics = new LinkedHashMap<>();
        String prefix = "embedded_script.typescript." + diagnosticScope + ".";
        diagnostics.put(prefix + "input_count", Integer.toString(inputCount));
        diagnostics.put(prefix + "typescript_count", Integer.toString(typeScriptCount));
        diagnostics.put(prefix + "stripped_count", Integer.toString(strippedCount));
        int detailCount = Math.min(typeScriptCount, SOURCE_MAP_DIAGNOSTIC_DETAIL_LIMIT);
        diagnostics.put(prefix + "detail_count", Integer.toString(detailCount));
        diagnostics.put(
                prefix + "detail_truncated_count",
                Integer.toString(typeScriptCount - detailCount)
        );
        diagnostics.putAll(sourceDiagnostics);
        return new SourceMapResult(preparedSources, diagnostics);
    }

    static boolean isTypeScriptSourceName(String sourceName) {
        return !isTypeScriptDeclarationSourceName(sourceName)
                && TYPESCRIPT_EXTENSIONS.contains(extension(sourceName).toLowerCase(Locale.ROOT));
    }

    static boolean isUnsupportedTypeScriptSourceName(String sourceName) {
        return !isTypeScriptDeclarationSourceName(sourceName)
                && "tsx".equalsIgnoreCase(extension(sourceName));
    }

    static boolean isTypeScriptDeclarationSourceName(String sourceName) {
        String lowerCaseName = sourceName.toLowerCase(Locale.ROOT);
        return lowerCaseName.endsWith(".d.ts")
                || lowerCaseName.endsWith(".d.mts")
                || lowerCaseName.endsWith(".d.cts");
    }

    private static void detectUnsupportedSyntax(String sourceName, String source) {
        String masked = maskNonCode(source);
        for (UnsupportedPattern unsupportedPattern : UNSUPPORTED_PATTERNS) {
            Matcher matcher = unsupportedPattern.pattern.matcher(masked);
            if (matcher.find()) {
                throw unsupportedSyntax(
                        sourceName,
                        source,
                        unsupportedPattern.kind,
                        unsupportedPattern.description,
                        matcher.start()
                );
            }
        }
    }

    private static UnsupportedTypeScriptException unsupportedExtension(String sourceName) {
        DiagnosticSourceName diagnosticSourceName = diagnosticSourceName(sourceName);
        return new UnsupportedTypeScriptException(
                ERROR_UNSUPPORTED_EXTENSION,
                sourceName,
                "tsx",
                1,
                1,
                "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION: TypeScript TSX source is not supported by AutoJs6 lightweight type stripping: "
                        + diagnosticSourceName.value()
                        + ". Compile TSX to JavaScript before packaging or running it."
        );
    }

    private static UnsupportedTypeScriptException unsupportedSyntax(
            String sourceName,
            String source,
            String kind,
            String description,
            int index
    ) {
        int[] position = lineColumnAt(source, index);
        DiagnosticSourceName diagnosticSourceName = diagnosticSourceName(sourceName);
        return new UnsupportedTypeScriptException(
                ERROR_UNSUPPORTED_SYNTAX,
                sourceName,
                kind,
                position[0],
                position[1],
                "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX: "
                        + description
                        + " in "
                        + diagnosticSourceName.value()
                        + ":"
                        + position[0]
                        + ":"
                        + position[1]
                        + ". AutoJs6 lightweight TypeScript support only strips erasable type syntax; compile this project before running it."
        );
    }

    /**
     * Produces a Binder-safe diagnostic representation while retaining both
     * the start and file-name suffix. The original source-map key is never
     * changed. Boundaries are adjusted so a UTF-16 surrogate pair is not split.
     */
    static DiagnosticSourceName diagnosticSourceName(String sourceName) {
        String normalized = sourceName == null ? "" : sourceName;
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
        String value = normalized.substring(0, headEnd)
                + DIAGNOSTIC_SOURCE_NAME_SEPARATOR
                + normalized.substring(tailStart);
        return new DiagnosticSourceName(value, true);
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

    private static String removeTypeOnlyStatements(String source) {
        String output = replaceWithNewlinePadding(IMPORT_TYPE_PATTERN, source);
        output = replaceWithNewlinePadding(EXPORT_TYPE_PATTERN, output);
        return replaceWithNewlinePadding(EXPORT_INTERFACE_INLINE_PATTERN, output);
    }

    private static String removeInterfaceDeclarations(String source) {
        return removeBraceDeclarations(source, INTERFACE_DECLARATION_PATTERN);
    }

    private static String removeTypeAliasDeclarations(String source) {
        return replaceWithNewlinePadding(TYPE_ALIAS_PATTERN, source);
    }

    private static String replaceWithNewlinePadding(Pattern pattern, String source) {
        Matcher matcher = matcherAgainstCode(pattern, source);
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            result.append(source, cursor, matcher.start());
            result.append(newlinePadding(source.substring(matcher.start(), matcher.end())));
            cursor = matcher.end();
        }
        result.append(source, cursor, source.length());
        return result.toString();
    }

    private static String removeBraceDeclarations(String source, Pattern pattern) {
        String masked = maskNonCodeSameLength(source);
        Matcher matcher = pattern.matcher(masked);
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < source.length()) {
            if (!matcher.find(cursor)) {
                break;
            }
            int braceStart = masked.indexOf('{', matcher.end());
            if (braceStart < 0) {
                break;
            }
            int end = matchingBraceEnd(source, braceStart);
            if (end < 0) {
                break;
            }
            result.append(source, cursor, matcher.start());
            result.append(newlinePadding(source.substring(matcher.start(), end + 1)));
            cursor = end + 1;
        }
        result.append(source, cursor, source.length());
        return result.toString();
    }

    private static String stripFunctionTypeParameters(String source) {
        Matcher matcher = matcherAgainstCode(FUNCTION_TYPE_PARAMETERS_PATTERN, source);
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            result.append(source, cursor, matcher.start());
            result.append("function ");
            appendOriginalGroup(result, source, matcher, 1);
            result.append('(');
            cursor = matcher.end();
        }
        result.append(source, cursor, source.length());
        return result.toString();
    }

    private static String stripTypeAnnotations(String source) {
        String output = replaceGroups(VARIABLE_TYPE_PATTERN, source, 1, " ", 2);
        output = replaceGroups(PARAMETER_TYPE_PATTERN, output, 1);
        output = replaceGroups(RETURN_TYPE_PATTERN, output, 1);
        return replaceGroups(PROPERTY_TYPE_PATTERN, output, 1, "", 2);
    }

    private static String stripTypeAssertions(String source) {
        String output = replaceGroups(AS_ASSERTION_PATTERN, source, 1);
        return replaceGroups(SATISFIES_ASSERTION_PATTERN, output, 1);
    }

    private static String replaceGroups(Pattern pattern, String source, Object... parts) {
        Matcher matcher = matcherAgainstCode(pattern, source);
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            result.append(source, cursor, matcher.start());
            for (Object part : parts) {
                if (part instanceof Integer) {
                    appendOriginalGroup(result, source, matcher, (Integer) part);
                } else {
                    result.append(part);
                }
            }
            cursor = matcher.end();
        }
        result.append(source, cursor, source.length());
        return result.toString();
    }

    /**
     * Matches only code while preserving the source's exact UTF-16 offsets.
     * Rewriters must use matcher indices to copy from {@code source}; masked
     * text is never appended to the output.
     */
    private static Matcher matcherAgainstCode(Pattern pattern, String source) {
        return pattern.matcher(maskNonCodeSameLength(source));
    }

    private static String maskNonCodeSameLength(String source) {
        String masked = maskNonCode(source);
        if (masked.length() != source.length()) {
            throw new IllegalStateException("TypeScript lexical mask changed source length.");
        }
        return masked;
    }

    private static void appendOriginalGroup(
            StringBuilder output,
            String source,
            Matcher matcher,
            int group
    ) {
        int start = matcher.start(group);
        int end = matcher.end(group);
        if (start >= 0 && end >= start) {
            output.append(source, start, end);
        }
    }

    private static int matchingBraceEnd(String source, int braceStart) {
        int depth = 0;
        ScanState state = ScanState.CODE;
        boolean escaped = false;
        for (int index = braceStart; index < source.length(); index++) {
            char current = source.charAt(index);
            Character next = index + 1 < source.length() ? source.charAt(index + 1) : null;
            switch (state) {
                case LINE_COMMENT:
                    if (current == '\n' || current == '\r') {
                        state = ScanState.CODE;
                    }
                    break;
                case BLOCK_COMMENT:
                    if (current == '*' && next != null && next == '/') {
                        index++;
                        state = ScanState.CODE;
                    }
                    break;
                case SINGLE_QUOTE:
                case DOUBLE_QUOTE:
                case TEMPLATE:
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if ((state == ScanState.SINGLE_QUOTE && current == '\'')
                            || (state == ScanState.DOUBLE_QUOTE && current == '"')
                            || (state == ScanState.TEMPLATE && current == '`')) {
                        state = ScanState.CODE;
                    }
                    break;
                case CODE:
                    if (current == '/' && next != null && next == '/') {
                        index++;
                        state = ScanState.LINE_COMMENT;
                    } else if (current == '/' && next != null && next == '*') {
                        index++;
                        state = ScanState.BLOCK_COMMENT;
                    } else if (current == '\'') {
                        state = ScanState.SINGLE_QUOTE;
                    } else if (current == '"') {
                        state = ScanState.DOUBLE_QUOTE;
                    } else if (current == '`') {
                        state = ScanState.TEMPLATE;
                    } else if (current == '{') {
                        depth++;
                    } else if (current == '}') {
                        depth--;
                        if (depth == 0) {
                            return index;
                        }
                    }
                    break;
            }
        }
        return -1;
    }

    private static String maskNonCode(String source) {
        StringBuilder output = new StringBuilder(source.length());
        ScanState state = ScanState.CODE;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            Character next = index + 1 < source.length() ? source.charAt(index + 1) : null;
            switch (state) {
                case LINE_COMMENT:
                    if (current == '\n' || current == '\r') {
                        output.append(current);
                        state = ScanState.CODE;
                    } else {
                        output.append(' ');
                    }
                    break;
                case BLOCK_COMMENT:
                    if (current == '*' && next != null && next == '/') {
                        output.append("  ");
                        index++;
                        state = ScanState.CODE;
                    } else {
                        output.append(current == '\n' || current == '\r' ? current : ' ');
                    }
                    break;
                case SINGLE_QUOTE:
                case DOUBLE_QUOTE:
                case TEMPLATE:
                    // Mask string contents with a non-whitespace placeholder so
                    // expression-position rewriters (e.g. the `as`-assertion
                    // stripper's `(\S)\s+as` anchor) still see the literal as a
                    // value instead of skippable whitespace.
                    output.append(current == '\n' || current == '\r' ? current : STRING_MASK_CHAR);
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if ((state == ScanState.SINGLE_QUOTE && current == '\'')
                            || (state == ScanState.DOUBLE_QUOTE && current == '"')
                            || (state == ScanState.TEMPLATE && current == '`')) {
                        state = ScanState.CODE;
                    }
                    break;
                case CODE:
                    if (current == '/' && next != null && next == '/') {
                        output.append("  ");
                        index++;
                        state = ScanState.LINE_COMMENT;
                    } else if (current == '/' && next != null && next == '*') {
                        output.append("  ");
                        index++;
                        state = ScanState.BLOCK_COMMENT;
                    } else if (current == '\'') {
                        output.append(STRING_MASK_CHAR);
                        state = ScanState.SINGLE_QUOTE;
                    } else if (current == '"') {
                        output.append(STRING_MASK_CHAR);
                        state = ScanState.DOUBLE_QUOTE;
                    } else if (current == '`') {
                        output.append(STRING_MASK_CHAR);
                        state = ScanState.TEMPLATE;
                    } else {
                        output.append(current);
                    }
                    break;
            }
        }
        return output.toString();
    }

    private static String extension(String sourceName) {
        int slash = Math.max(sourceName.lastIndexOf('/'), sourceName.lastIndexOf('\\'));
        int dot = sourceName.lastIndexOf('.');
        return dot > slash && dot < sourceName.length() - 1 ? sourceName.substring(dot + 1) : "";
    }

    private static int[] lineColumnAt(String source, int index) {
        int line = 1;
        int column = 1;
        int end = Math.max(0, Math.min(index, source.length()));
        for (int cursor = 0; cursor < end; cursor++) {
            if (source.charAt(cursor) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new int[]{line, column};
    }

    private static String newlinePadding(String source) {
        StringBuilder padding = new StringBuilder();
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') {
                padding.append('\n');
            }
        }
        return padding.toString();
    }

    static final class Result {
        private final String source;
        private final boolean stripped;
        private final Map<String, String> diagnostics;

        Result(String source, boolean stripped, Map<String, String> diagnostics) {
            this.source = source;
            this.stripped = stripped;
            this.diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
        }

        String source() {
            return source;
        }

        boolean stripped() {
            return stripped;
        }

        Map<String, String> diagnostics() {
            return diagnostics;
        }
    }

    static final class SourceMapResult {
        private final Map<String, String> sources;
        private final Map<String, String> diagnostics;

        SourceMapResult(Map<String, String> sources, Map<String, String> diagnostics) {
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
            this.diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
        }

        Map<String, String> sources() {
            return sources;
        }

        Map<String, String> diagnostics() {
            return diagnostics;
        }
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

    static final class UnsupportedTypeScriptException extends IllegalArgumentException {
        private final String errorCode;
        private final String sourceName;
        private final String syntaxKind;
        private final int line;
        private final int column;

        UnsupportedTypeScriptException(
                String errorCode,
                String sourceName,
                String syntaxKind,
                int line,
                int column,
                String message
        ) {
            super(message);
            this.errorCode = errorCode;
            this.sourceName = sourceName;
            this.syntaxKind = syntaxKind;
            this.line = line;
            this.column = column;
        }

        String errorCode() {
            return errorCode;
        }

        String sourceName() {
            return sourceName;
        }

        String syntaxKind() {
            return syntaxKind;
        }

        int line() {
            return line;
        }

        int column() {
            return column;
        }

        Map<String, String> diagnostics() {
            DiagnosticSourceName diagnosticSourceName = diagnosticSourceName(sourceName);
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            values.put("embedded_script.error_code", errorCode);
            values.put("embedded_script.typescript.error", "true");
            values.put("embedded_script.typescript.source", diagnosticSourceName.value());
            values.put(
                    "embedded_script.typescript.source_truncated",
                    Boolean.toString(diagnosticSourceName.truncated())
            );
            values.put("embedded_script.typescript.syntax_kind", syntaxKind);
            values.put("embedded_script.typescript.line", Integer.toString(line));
            values.put("embedded_script.typescript.column", Integer.toString(column));
            return Collections.unmodifiableMap(values);
        }
    }

    private enum ScanState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        TEMPLATE
    }

    private static final class UnsupportedPattern {
        private final String kind;
        private final String description;
        private final Pattern pattern;

        private UnsupportedPattern(String kind, String description, Pattern pattern) {
            this.kind = kind;
            this.description = description;
            this.pattern = pattern;
        }
    }
}
