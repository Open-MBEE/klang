import * as vscode from 'vscode';

export type SymbolKind = 'class' | 'function' | 'member' | 'constraint' | 'package';

export interface KSymbol {
    name: string;
    kind: SymbolKind;
    range: vscode.Range;
    documentation?: string;
    extends?: string;
    type?: string;
    returnType?: string;
    containerName?: string;
}

/**
 * Parses K language files to extract symbols (classes, functions, members, constraints).
 */
export class KSymbolParser {
    // Patterns for different K language constructs
    private static readonly PATTERNS = {
        // Package declaration
        package: /^\s*package\s+([a-zA-Z_][a-zA-Z0-9_.]*)/,

        // Class/assoc declaration with optional extends
        class: /^\s*(class|assoc)\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+([A-Za-z0-9_,\s]+))?/,

        // Member declaration (name : Type)
        member: /^\s*([a-z][a-zA-Z0-9_]*)\s*:\s*([A-Z][a-zA-Z0-9_<>,\s]*)/,

        // Function declaration
        function: /^\s*fun\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*(?:\([^)]*\))?\s*(?::\s*([A-Z][a-zA-Z0-9_<>,\s]*))?/,

        // Named constraint (req Name: ...)
        namedConstraint: /^\s*req\s+([A-Z][a-zA-Z0-9_]*)\s*:/,

        // Anonymous constraint (req ...)
        constraint: /^\s*req\s+(?![A-Z][a-zA-Z0-9_]*\s*:)/,

        // Documentation comments
        blockCommentStart: /^\s*\/\*\*?/,
        blockCommentEnd: /\*\/\s*$/,
        lineComment: /^\s*(?:--|\/\/)\s*(.*)/,
        equalsComment: /^={2,}\s*$/
    };

    /**
     * Parse a K document and extract all symbols.
     */
    static parseDocument(document: vscode.TextDocument): KSymbol[] {
        const symbols: KSymbol[] = [];
        const text = document.getText();
        const lines = text.split('\n');

        let currentPackage: string | undefined;
        let currentClass: string | undefined;
        let pendingDoc: string | undefined;
        let inBlockComment = false;
        let inEqualsComment = false;
        let blockCommentLines: string[] = [];

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const trimmedLine = line.trim();

            // Track documentation comments
            if (this.PATTERNS.equalsComment.test(trimmedLine)) {
                if (inEqualsComment) {
                    // End of equals comment block
                    pendingDoc = blockCommentLines.join('\n').trim();
                    blockCommentLines = [];
                    inEqualsComment = false;
                } else {
                    // Start of equals comment block
                    inEqualsComment = true;
                    blockCommentLines = [];
                }
                continue;
            }

            if (inEqualsComment) {
                blockCommentLines.push(trimmedLine);
                continue;
            }

            // Block comment handling
            if (this.PATTERNS.blockCommentStart.test(trimmedLine)) {
                inBlockComment = true;
                blockCommentLines = [];
                // Extract content after /** or /*
                const content = trimmedLine.replace(/^\s*\/\*\*?\s*/, '');
                if (content && !this.PATTERNS.blockCommentEnd.test(content)) {
                    blockCommentLines.push(content);
                }
                if (this.PATTERNS.blockCommentEnd.test(trimmedLine)) {
                    inBlockComment = false;
                    pendingDoc = blockCommentLines.join('\n').trim();
                    blockCommentLines = [];
                }
                continue;
            }

            if (inBlockComment) {
                if (this.PATTERNS.blockCommentEnd.test(trimmedLine)) {
                    inBlockComment = false;
                    // Extract content before */
                    const content = trimmedLine.replace(/\*\/\s*$/, '').replace(/^\s*\*\s?/, '');
                    if (content) {
                        blockCommentLines.push(content);
                    }
                    pendingDoc = blockCommentLines.join('\n').trim();
                    blockCommentLines = [];
                } else {
                    // Remove leading * from comment lines
                    const content = trimmedLine.replace(/^\s*\*\s?/, '');
                    blockCommentLines.push(content);
                }
                continue;
            }

            // Skip empty lines and single-line comments for symbol detection
            if (!trimmedLine || this.PATTERNS.lineComment.test(trimmedLine)) {
                continue;
            }

            // Package declaration
            const packageMatch = line.match(this.PATTERNS.package);
            if (packageMatch) {
                currentPackage = packageMatch[1];
                symbols.push({
                    name: packageMatch[1],
                    kind: 'package',
                    range: new vscode.Range(i, 0, i, line.length)
                });
                pendingDoc = undefined;
                continue;
            }

            // Class/assoc declaration
            const classMatch = line.match(this.PATTERNS.class);
            if (classMatch) {
                currentClass = classMatch[2];
                const extendsClause = classMatch[3]?.trim();
                symbols.push({
                    name: classMatch[2],
                    kind: 'class',
                    range: new vscode.Range(i, line.indexOf(classMatch[2]), i, line.indexOf(classMatch[2]) + classMatch[2].length),
                    documentation: pendingDoc,
                    extends: extendsClause,
                    containerName: currentPackage
                });
                pendingDoc = undefined;
                continue;
            }

            // Function declaration
            const functionMatch = line.match(this.PATTERNS.function);
            if (functionMatch) {
                symbols.push({
                    name: functionMatch[1],
                    kind: 'function',
                    range: new vscode.Range(i, line.indexOf(functionMatch[1]), i, line.indexOf(functionMatch[1]) + functionMatch[1].length),
                    documentation: pendingDoc,
                    returnType: functionMatch[2]?.trim(),
                    containerName: currentClass
                });
                pendingDoc = undefined;
                continue;
            }

            // Named constraint
            const namedConstraintMatch = line.match(this.PATTERNS.namedConstraint);
            if (namedConstraintMatch) {
                symbols.push({
                    name: namedConstraintMatch[1],
                    kind: 'constraint',
                    range: new vscode.Range(i, line.indexOf(namedConstraintMatch[1]), i, line.indexOf(namedConstraintMatch[1]) + namedConstraintMatch[1].length),
                    documentation: pendingDoc,
                    containerName: currentClass
                });
                pendingDoc = undefined;
                continue;
            }

            // Member declaration (must come after function to avoid conflicts)
            const memberMatch = line.match(this.PATTERNS.member);
            if (memberMatch && currentClass) {
                symbols.push({
                    name: memberMatch[1],
                    kind: 'member',
                    range: new vscode.Range(i, line.indexOf(memberMatch[1]), i, line.indexOf(memberMatch[1]) + memberMatch[1].length),
                    documentation: pendingDoc,
                    type: memberMatch[2]?.trim(),
                    containerName: currentClass
                });
                pendingDoc = undefined;
                continue;
            }

            // Clear pending doc if we hit a non-documented line
            if (!trimmedLine.startsWith('req') && !trimmedLine.startsWith('}')) {
                pendingDoc = undefined;
            }
        }

        return symbols;
    }
}

