import * as vscode from 'vscode';

/**
 * Provides code formatting for K language files
 */
export class KFormattingProvider implements vscode.DocumentFormattingEditProvider, vscode.DocumentRangeFormattingEditProvider {

    provideDocumentFormattingEdits(
        document: vscode.TextDocument,
        options: vscode.FormattingOptions,
        token: vscode.CancellationToken
    ): vscode.TextEdit[] {
        return this.formatDocument(document, undefined, options);
    }

    provideDocumentRangeFormattingEdits(
        document: vscode.TextDocument,
        range: vscode.Range,
        options: vscode.FormattingOptions,
        token: vscode.CancellationToken
    ): vscode.TextEdit[] {
        return this.formatDocument(document, range, options);
    }

    private formatDocument(
        document: vscode.TextDocument,
        range: vscode.Range | undefined,
        options: vscode.FormattingOptions
    ): vscode.TextEdit[] {
        const edits: vscode.TextEdit[] = [];
        const indent = options.insertSpaces ? ' '.repeat(options.tabSize) : '\t';

        const startLine = range?.start.line ?? 0;
        const endLine = range?.end.line ?? document.lineCount - 1;

        let indentLevel = 0;
        let inBlockComment = false;
        let inEqualsComment = false;

        // First pass: determine initial indent level at startLine
        for (let i = 0; i < startLine; i++) {
            const line = document.lineAt(i).text;
            const trimmed = line.trim();

            if (/^={2,}$/.test(trimmed)) {
                inEqualsComment = !inEqualsComment;
                continue;
            }
            if (inEqualsComment) continue;

            if (trimmed.startsWith('/*')) inBlockComment = true;
            if (trimmed.endsWith('*/')) {
                inBlockComment = false;
                continue;
            }
            if (inBlockComment) continue;

            // Count braces on this line
            const openBraces = (line.match(/{/g) || []).length;
            const closeBraces = (line.match(/}/g) || []).length;
            indentLevel += openBraces - closeBraces;
        }

        // Second pass: format each line
        for (let i = startLine; i <= endLine; i++) {
            const line = document.lineAt(i);
            const originalText = line.text;
            const trimmedText = originalText.trim();

            // Skip empty lines
            if (!trimmedText) {
                continue;
            }

            // Handle equals comment blocks
            if (/^={2,}$/.test(trimmedText)) {
                inEqualsComment = !inEqualsComment;
                const formatted = indent.repeat(indentLevel) + trimmedText;
                if (formatted !== originalText) {
                    edits.push(vscode.TextEdit.replace(line.range, formatted));
                }
                continue;
            }

            if (inEqualsComment) {
                // Format content inside equals comments
                const formatted = indent.repeat(indentLevel) + trimmedText;
                if (formatted !== originalText) {
                    edits.push(vscode.TextEdit.replace(line.range, formatted));
                }
                continue;
            }

            // Handle block comments
            if (trimmedText.startsWith('/*')) {
                inBlockComment = true;
                const formatted = indent.repeat(indentLevel) + trimmedText;
                if (formatted !== originalText) {
                    edits.push(vscode.TextEdit.replace(line.range, formatted));
                }
                if (trimmedText.endsWith('*/')) {
                    inBlockComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                // Align comment continuation
                let formatted: string;
                if (trimmedText.startsWith('*')) {
                    formatted = indent.repeat(indentLevel) + ' ' + trimmedText;
                } else {
                    formatted = indent.repeat(indentLevel) + ' * ' + trimmedText;
                }
                if (trimmedText.endsWith('*/')) {
                    inBlockComment = false;
                }
                if (formatted !== originalText) {
                    edits.push(vscode.TextEdit.replace(line.range, formatted));
                }
                continue;
            }

            // Handle line comments
            if (trimmedText.startsWith('--') || trimmedText.startsWith('//')) {
                const formatted = indent.repeat(indentLevel) + trimmedText;
                if (formatted !== originalText) {
                    edits.push(vscode.TextEdit.replace(line.range, formatted));
                }
                continue;
            }

            // Calculate indent for this line
            // Decrease indent if line starts with }
            let currentIndent = indentLevel;
            if (trimmedText.startsWith('}')) {
                currentIndent = Math.max(0, indentLevel - 1);
            }

            // Format the line content
            let formattedContent = this.formatLineContent(trimmedText);
            const formatted = indent.repeat(currentIndent) + formattedContent;

            if (formatted !== originalText) {
                edits.push(vscode.TextEdit.replace(line.range, formatted));
            }

            // Update indent level for next line
            const openBraces = (trimmedText.match(/{/g) || []).length;
            const closeBraces = (trimmedText.match(/}/g) || []).length;
            indentLevel += openBraces - closeBraces;
            indentLevel = Math.max(0, indentLevel);
        }

        return edits;
    }

    /**
     * Format the content of a single line (spacing around operators, etc.)
     */
    private formatLineContent(text: string): string {
        let result = text;

        // Preserve string literals
        const strings: string[] = [];
        result = result.replace(/"[^"]*"/g, (match) => {
            strings.push(match);
            return `__STRING_${strings.length - 1}__`;
        });

        // Space after keywords
        result = result.replace(/\b(package|import|class|assoc|extends|fun|req|val|var|type|if|then|else|forall|exists)\b(?!\s)/g, '$1 ');

        // Space around : in type annotations (but not ::)
        result = result.replace(/([a-zA-Z0-9_)])\s*:\s*(?!:)([A-Z])/g, '$1: $2');

        // Space around = in assignments (but not == or !=)
        result = result.replace(/([^=!<>])\s*=\s*(?!=)/g, '$1 = ');

        // Space around comparison operators
        result = result.replace(/\s*(==|!=|<=|>=|<|>)\s*/g, ' $1 ');

        // Space around logical operators
        result = result.replace(/\s*(&&|\|\|)\s*/g, ' $1 ');

        // Space around arithmetic operators (but be careful with negative numbers)
        result = result.replace(/([a-zA-Z0-9_)])\s*([+\-*/])\s*([a-zA-Z0-9_(])/g, '$1 $2 $3');

        // Space after comma
        result = result.replace(/,\s*/g, ', ');

        // No space before (
        result = result.replace(/\s+\(/g, '(');

        // Space after { and before }
        result = result.replace(/{\s*/g, '{ ');
        result = result.replace(/\s*}/g, ' }');

        // But allow { on its own line
        result = result.replace(/{\s*$/g, '{');

        // And } on its own line
        if (result.trim() === '}') {
            result = '}';
        }

        // Remove multiple spaces
        result = result.replace(/  +/g, ' ');

        // Restore string literals
        for (let i = 0; i < strings.length; i++) {
            result = result.replace(`__STRING_${i}__`, strings[i]);
        }

        return result.trim();
    }
}

/**
 * Provides on-type formatting (format as you type)
 */
export class KOnTypeFormattingProvider implements vscode.OnTypeFormattingEditProvider {

    provideOnTypeFormattingEdits(
        document: vscode.TextDocument,
        position: vscode.Position,
        ch: string,
        options: vscode.FormattingOptions,
        token: vscode.CancellationToken
    ): vscode.TextEdit[] {
        const edits: vscode.TextEdit[] = [];
        const line = document.lineAt(position.line);
        const indent = options.insertSpaces ? ' '.repeat(options.tabSize) : '\t';

        // When typing }, auto-adjust indent
        if (ch === '}') {
            const trimmed = line.text.trim();
            if (trimmed === '}') {
                // Find matching opening brace to determine indent
                let braceCount = 1;
                let targetIndent = 0;

                for (let i = position.line - 1; i >= 0 && braceCount > 0; i--) {
                    const prevLine = document.lineAt(i).text;
                    const openBraces = (prevLine.match(/{/g) || []).length;
                    const closeBraces = (prevLine.match(/}/g) || []).length;
                    braceCount += closeBraces - openBraces;

                    if (braceCount === 0) {
                        // Found the matching line
                        const leadingSpace = prevLine.match(/^(\s*)/)?.[1] || '';
                        targetIndent = leadingSpace.length;
                        break;
                    }
                }

                const currentIndent = line.text.match(/^(\s*)/)?.[1] || '';
                const newIndent = options.insertSpaces
                    ? ' '.repeat(targetIndent)
                    : '\t'.repeat(Math.floor(targetIndent / options.tabSize));

                if (currentIndent !== newIndent) {
                    edits.push(vscode.TextEdit.replace(
                        new vscode.Range(position.line, 0, position.line, currentIndent.length),
                        newIndent
                    ));
                }
            }
        }

        // When pressing Enter after {, add indent
        if (ch === '\n') {
            const prevLine = document.lineAt(Math.max(0, position.line - 1));
            if (prevLine.text.trim().endsWith('{')) {
                const prevIndent = prevLine.text.match(/^(\s*)/)?.[1] || '';
                const newIndent = prevIndent + indent;

                // Check if the current line is empty and needs indentation
                if (line.text.trim() === '') {
                    edits.push(vscode.TextEdit.insert(new vscode.Position(position.line, 0), newIndent));
                }
            }
        }

        return edits;
    }
}

