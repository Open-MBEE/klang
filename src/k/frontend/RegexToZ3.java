package k.frontend;

/**
 * Converts standard regex patterns to Z3's regex algebra (SMT-LIB2 format).
 *
 * Supported patterns:
 * - [a-z], [A-Z], [0-9] - character ranges
 * - [abc] - character sets
 * - [^abc] - negated character sets (limited support)
 * - . - any character
 * - * - zero or more
 * - + - one or more
 * - ? - optional
 * - | - alternation
 * - () - grouping
 * - \d, \w, \s - common character classes
 * - {n}, {n,m} - repetition (converted to expanded form)
 *
 * Z3 regex algebra:
 * - (re.range "a" "z") - character range
 * - (re.union r1 r2) - alternation
 * - (re.++ r1 r2) - concatenation
 * - (re.* r) - zero or more (Kleene star)
 * - (re.+ r) - one or more
 * - (re.opt r) - optional
 * - (re.allchar) - any character
 * - (str.to_re "literal") - literal string
 */
public class RegexToZ3 {

    private String pattern;
    private int pos;

    public RegexToZ3(String pattern) {
        this.pattern = pattern;
        this.pos = 0;
    }

    /**
     * Convert a regex pattern to Z3 SMT-LIB2 format.
     * @param pattern The regex pattern
     * @return Z3 regex expression in SMT-LIB2 format
     */
    public static String convert(String pattern) {
        // Handle empty pattern
        if (pattern == null || pattern.isEmpty()) {
            return "(str.to_re \"\")";
        }

        // Check if it's just a literal string (no special chars)
        if (isLiteralPattern(pattern)) {
            return "(str.to_re \"" + escapeForSMT(pattern) + "\")";
        }

        RegexToZ3 converter = new RegexToZ3(pattern);
        return converter.parseRegex();
    }

    /**
     * Check if pattern contains only literal characters (no regex metacharacters)
     */
    private static boolean isLiteralPattern(String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '[' || c == ']' || c == '(' || c == ')' ||
                c == '*' || c == '+' || c == '?' || c == '|' ||
                c == '.' || c == '^' || c == '$' || c == '\\' ||
                c == '{' || c == '}') {
                return false;
            }
        }
        return true;
    }

    /**
     * Escape special characters for SMT-LIB2 string literals
     */
    private static String escapeForSMT(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append("\"\"");  // Double quote escaping in SMT-LIB2
            } else if (c == '\\') {
                sb.append("\\\\");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ========== Parser Methods ==========

    private char peek() {
        if (pos < pattern.length()) {
            return pattern.charAt(pos);
        }
        return '\0';
    }

    private char consume() {
        return pattern.charAt(pos++);
    }

    private boolean hasMore() {
        return pos < pattern.length();
    }

    /**
     * Parse a full regex (handles alternation at top level)
     * regex ::= concat ('|' concat)*
     */
    private String parseRegex() {
        String left = parseConcat();

        while (hasMore() && peek() == '|') {
            consume(); // eat '|'
            String right = parseConcat();
            left = "(re.union " + left + " " + right + ")";
        }

        return left;
    }

    /**
     * Parse concatenation of terms
     * concat ::= term*
     */
    private String parseConcat() {
        StringBuilder terms = new StringBuilder();
        String first = null;
        int count = 0;

        while (hasMore() && peek() != '|' && peek() != ')') {
            String term = parseTerm();
            if (term != null) {
                if (first == null) {
                    first = term;
                } else if (count == 1) {
                    terms.append("(re.++ ").append(first).append(" ").append(term).append(")");
                } else {
                    terms.insert(0, "(re.++ ");
                    terms.append(" ").append(term).append(")");
                }
                count++;
            }
        }

        if (count == 0) {
            return "(str.to_re \"\")";  // Empty regex
        } else if (count == 1) {
            return first;
        } else {
            return terms.toString();
        }
    }

    /**
     * Parse a single term with optional quantifier
     * term ::= atom ('*' | '+' | '?' | '{n}' | '{n,m}')?
     */
    private String parseTerm() {
        String atom = parseAtom();
        if (atom == null) return null;

        if (hasMore()) {
            char c = peek();
            if (c == '*') {
                consume();
                return "(re.* " + atom + ")";
            } else if (c == '+') {
                consume();
                return "(re.+ " + atom + ")";
            } else if (c == '?') {
                consume();
                return "(re.opt " + atom + ")";
            } else if (c == '{') {
                return parseRepetition(atom);
            }
        }

        return atom;
    }

    /**
     * Parse repetition {n} or {n,m}
     */
    private String parseRepetition(String atom) {
        consume(); // eat '{'

        StringBuilder numStr = new StringBuilder();
        while (hasMore() && Character.isDigit(peek())) {
            numStr.append(consume());
        }
        int min = numStr.length() > 0 ? Integer.parseInt(numStr.toString()) : 0;
        int max = min;

        if (hasMore() && peek() == ',') {
            consume(); // eat ','
            numStr = new StringBuilder();
            while (hasMore() && Character.isDigit(peek())) {
                numStr.append(consume());
            }
            max = numStr.length() > 0 ? Integer.parseInt(numStr.toString()) : Integer.MAX_VALUE;
        }

        if (hasMore() && peek() == '}') {
            consume(); // eat '}'
        }

        // Convert {n,m} to repeated concatenation with optional parts
        // {3} = atom ++ atom ++ atom
        // {2,4} = atom ++ atom ++ (opt atom) ++ (opt atom)

        if (min == 0 && max == Integer.MAX_VALUE) {
            return "(re.* " + atom + ")";
        } else if (min == 1 && max == Integer.MAX_VALUE) {
            return "(re.+ " + atom + ")";
        } else if (min == 0 && max == 1) {
            return "(re.opt " + atom + ")";
        }

        // Build the repetition manually
        StringBuilder result = new StringBuilder();

        // Required part (min copies)
        for (int i = 0; i < min; i++) {
            if (result.length() == 0) {
                result.append(atom);
            } else {
                result.insert(0, "(re.++ ");
                result.append(" ").append(atom).append(")");
            }
        }

        // Optional part (max - min copies)
        int optCount = Math.min(max - min, 10); // Limit to avoid explosion
        for (int i = 0; i < optCount; i++) {
            String opt = "(re.opt " + atom + ")";
            if (result.length() == 0) {
                result.append(opt);
            } else {
                result.insert(0, "(re.++ ");
                result.append(" ").append(opt).append(")");
            }
        }

        return result.length() > 0 ? result.toString() : "(str.to_re \"\")";
    }

    /**
     * Parse an atom (single unit)
     * atom ::= '(' regex ')' | '[' charclass ']' | '.' | '\' escape | literal
     */
    private String parseAtom() {
        if (!hasMore()) return null;

        char c = peek();

        if (c == '(') {
            consume(); // eat '('
            String inner = parseRegex();
            if (hasMore() && peek() == ')') {
                consume(); // eat ')'
            }
            return inner;
        }

        if (c == '[') {
            return parseCharClass();
        }

        if (c == '.') {
            consume();
            return "(re.allchar)";
        }

        if (c == '\\') {
            return parseEscape();
        }

        if (c == '*' || c == '+' || c == '?' || c == '|' || c == ')' || c == ']' || c == '}') {
            return null; // These are handled elsewhere
        }

        if (c == '^' || c == '$') {
            consume(); // Anchors - ignore for now (Z3 doesn't have direct support)
            return null;
        }

        // Literal character
        consume();
        return "(str.to_re \"" + escapeForSMT(String.valueOf(c)) + "\")";
    }

    /**
     * Parse character class [...]
     */
    private String parseCharClass() {
        consume(); // eat '['

        boolean negated = false;
        if (hasMore() && peek() == '^') {
            consume();
            negated = true;
        }

        StringBuilder parts = new StringBuilder();
        int partCount = 0;

        while (hasMore() && peek() != ']') {
            String part = parseCharClassPart();
            if (part != null) {
                if (partCount == 0) {
                    parts.append(part);
                } else {
                    parts.insert(0, "(re.union ");
                    parts.append(" ").append(part).append(")");
                }
                partCount++;
            }
        }

        if (hasMore() && peek() == ']') {
            consume(); // eat ']'
        }

        String result = parts.toString();

        if (negated) {
            // Negation: intersection with allchar complement
            // (re.inter (re.* re.allchar) (re.comp result))
            // Simplified: just use re.allchar for now (imprecise but workable)
            // TODO: Proper negation support
            result = "(re.allchar)";
        }

        return result;
    }

    /**
     * Parse a single part of a character class (range or single char)
     */
    private String parseCharClassPart() {
        if (!hasMore() || peek() == ']') return null;

        char c = consume();

        // Handle escape in char class
        if (c == '\\' && hasMore()) {
            c = consume();
            // Handle \d, \w, \s inside char class
            if (c == 'd') return "(re.range \"0\" \"9\")";
            if (c == 'w') return "(re.union (re.range \"a\" \"z\") (re.union (re.range \"A\" \"Z\") (re.union (re.range \"0\" \"9\") (str.to_re \"_\"))))";
            if (c == 's') return "(re.union (str.to_re \" \") (re.union (str.to_re \"\\t\") (str.to_re \"\\n\")))";
            // Other escapes - treat as literal
        }

        // Check for range a-z
        if (hasMore() && peek() == '-') {
            int savedPos = pos;
            consume(); // eat '-'
            if (hasMore() && peek() != ']') {
                char end = consume();
                if (end == '\\' && hasMore()) {
                    end = consume();
                }
                return "(re.range \"" + escapeForSMT(String.valueOf(c)) + "\" \"" + escapeForSMT(String.valueOf(end)) + "\")";
            } else {
                pos = savedPos; // Backtrack, '-' is literal at end
            }
        }

        // Single character
        return "(str.to_re \"" + escapeForSMT(String.valueOf(c)) + "\")";
    }

    /**
     * Parse escape sequence
     */
    private String parseEscape() {
        consume(); // eat '\'
        if (!hasMore()) return "(str.to_re \"\\\\\")";

        char c = consume();

        switch (c) {
            case 'd': // Digit [0-9]
                return "(re.range \"0\" \"9\")";
            case 'D': // Non-digit
                return "(re.allchar)"; // Approximation
            case 'w': // Word char [a-zA-Z0-9_]
                return "(re.union (re.range \"a\" \"z\") (re.union (re.range \"A\" \"Z\") (re.union (re.range \"0\" \"9\") (str.to_re \"_\"))))";
            case 'W': // Non-word
                return "(re.allchar)"; // Approximation
            case 's': // Whitespace
                return "(re.union (str.to_re \" \") (re.union (str.to_re \"\\t\") (str.to_re \"\\n\")))";
            case 'S': // Non-whitespace
                return "(re.allchar)"; // Approximation
            case 'n':
                return "(str.to_re \"\\n\")";
            case 't':
                return "(str.to_re \"\\t\")";
            case 'r':
                return "(str.to_re \"\\r\")";
            default:
                // Literal escaped character
                return "(str.to_re \"" + escapeForSMT(String.valueOf(c)) + "\")";
        }
    }

    // ========== Test Main ==========

    public static void main(String[] args) {
        // Test cases
        String[] patterns = {
            "hello",           // Literal
            "[a-z]+",          // One or more lowercase letters
            "[0-9]*",          // Zero or more digits
            "[A-Z][a-z]*",     // Uppercase followed by lowercase
            "\\d+",            // Digits
            "\\w+",            // Word characters
            "[a-zA-Z0-9]+",    // Alphanumeric
            "a|b|c",           // Alternation
            "(ab)+",           // Grouped repetition
            "colou?r",         // Optional
            "[A-Z]{2}[0-9]{2}" // Fixed length
        };

        for (String p : patterns) {
            System.out.println("Pattern: " + p);
            System.out.println("Z3:      " + convert(p));
            System.out.println();
        }
    }
}

