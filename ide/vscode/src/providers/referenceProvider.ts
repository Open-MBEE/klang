import * as vscode from 'vscode';
import { KSymbolParser } from './symbolParser';

/**
 * Provides "Find All References" functionality for K language.
 * Shift+F12 on a symbol to find all usages.
 */
export class KReferenceProvider implements vscode.ReferenceProvider {
    async provideReferences(
        document: vscode.TextDocument,
        position: vscode.Position,
        context: vscode.ReferenceContext,
        token: vscode.CancellationToken
    ): Promise<vscode.Location[]> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return [];
        }

        const word = document.getText(wordRange);
        if (!word) {
            return [];
        }

        return await this.findAllReferences(word, context.includeDeclaration, token);
    }

    private async findAllReferences(
        symbolName: string,
        includeDeclaration: boolean,
        token: vscode.CancellationToken
    ): Promise<vscode.Location[]> {
        const locations: vscode.Location[] = [];
        const kFiles = await vscode.workspace.findFiles('**/*.k', '**/node_modules/**');

        // Regex to find word boundaries for the symbol
        const wordPattern = new RegExp(`\\b${this.escapeRegExp(symbolName)}\\b`, 'g');

        for (const fileUri of kFiles) {
            if (token.isCancellationRequested) {
                break;
            }

            const document = await vscode.workspace.openTextDocument(fileUri);
            const text = document.getText();

            let match: RegExpExecArray | null;
            while ((match = wordPattern.exec(text)) !== null) {
                const startPos = document.positionAt(match.index);
                const endPos = document.positionAt(match.index + symbolName.length);
                const range = new vscode.Range(startPos, endPos);

                // Check if this is inside a comment
                if (!this.isInComment(document, startPos)) {
                    locations.push(new vscode.Location(fileUri, range));
                }
            }
        }

        return locations;
    }

    private escapeRegExp(string: string): string {
        return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    private isInComment(document: vscode.TextDocument, position: vscode.Position): boolean {
        const line = document.lineAt(position.line).text;
        const beforeCursor = line.substring(0, position.character);

        // Check for line comments
        if (beforeCursor.includes('--') || beforeCursor.includes('//')) {
            const dashIndex = beforeCursor.indexOf('--');
            const slashIndex = beforeCursor.indexOf('//');
            const commentStart = Math.min(
                dashIndex >= 0 ? dashIndex : Infinity,
                slashIndex >= 0 ? slashIndex : Infinity
            );
            if (commentStart < position.character) {
                return true;
            }
        }

        // Simple check for block comments - could be enhanced
        // This is a basic heuristic
        const textBefore = document.getText(new vscode.Range(new vscode.Position(0, 0), position));
        const blockCommentStarts = (textBefore.match(/\/\*/g) || []).length;
        const blockCommentEnds = (textBefore.match(/\*\//g) || []).length;

        return blockCommentStarts > blockCommentEnds;
    }
}

