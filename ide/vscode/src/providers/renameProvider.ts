import * as vscode from 'vscode';
import { KSymbolParser, KSymbol } from './symbolParser';

/**
 * Provides rename symbol functionality for K language files
 */
export class KRenameProvider implements vscode.RenameProvider {

    async provideRenameEdits(
        document: vscode.TextDocument,
        position: vscode.Position,
        newName: string,
        token: vscode.CancellationToken
    ): Promise<vscode.WorkspaceEdit | undefined> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const oldName = document.getText(wordRange);

        // Validate the new name
        if (!this.isValidIdentifier(newName)) {
            throw new Error(`Invalid identifier: ${newName}`);
        }

        // Find all occurrences across the workspace
        const edit = new vscode.WorkspaceEdit();
        const locations = await this.findAllReferences(oldName, document);

        for (const location of locations) {
            edit.replace(location.uri, location.range, newName);
        }

        return edit;
    }

    prepareRename(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): vscode.Range | { range: vscode.Range; placeholder: string } | undefined {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);

        // Don't allow renaming keywords or built-in types
        if (this.isKeyword(word) || this.isBuiltinType(word)) {
            throw new Error(`Cannot rename ${word}: it is a keyword or built-in type`);
        }

        return {
            range: wordRange,
            placeholder: word
        };
    }

    private isValidIdentifier(name: string): boolean {
        // K identifiers: start with letter or underscore, followed by letters, digits, underscores
        return /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name);
    }

    private isKeyword(word: string): boolean {
        const keywords = [
            'package', 'import', 'class', 'assoc', 'extends', 'fun', 'req', 'val', 'var',
            'type', 'annotation', 'trait', 'if', 'then', 'else', 'forall', 'exists',
            'prev', 'this', 'result', 'true', 'false', 'null', 'new', 'in', 'assert'
        ];
        return keywords.includes(word);
    }

    private isBuiltinType(word: string): boolean {
        const builtinTypes = [
            'Int', 'Real', 'Bool', 'String', 'Unit', 'Char', 'Any', 'Nothing',
            'Set', 'Bag', 'Seq', 'Map', 'Option', 'List', 'Tuple'
        ];
        return builtinTypes.includes(word);
    }

    private async findAllReferences(
        name: string,
        originDocument: vscode.TextDocument
    ): Promise<vscode.Location[]> {
        const locations: vscode.Location[] = [];

        // Find in current document first
        const currentDocLocations = this.findInDocument(name, originDocument);
        locations.push(...currentDocLocations);

        // Find in all other K files in workspace
        const kFiles = await vscode.workspace.findFiles('**/*.k', '**/node_modules/**');

        for (const fileUri of kFiles) {
            if (fileUri.fsPath === originDocument.uri.fsPath) {
                continue; // Already processed
            }

            try {
                const document = await vscode.workspace.openTextDocument(fileUri);
                const docLocations = this.findInDocument(name, document);
                locations.push(...docLocations);
            } catch (error) {
                console.warn(`Could not process ${fileUri.fsPath}: ${error}`);
            }
        }

        return locations;
    }

    private findInDocument(name: string, document: vscode.TextDocument): vscode.Location[] {
        const locations: vscode.Location[] = [];
        const text = document.getText();

        // Use word boundary regex to find exact matches
        const regex = new RegExp(`\\b${this.escapeRegex(name)}\\b`, 'g');
        let match;

        while ((match = regex.exec(text)) !== null) {
            const startPos = document.positionAt(match.index);
            const endPos = document.positionAt(match.index + name.length);

            // Check if this is within a comment or string
            if (!this.isInCommentOrString(document, startPos)) {
                locations.push(new vscode.Location(
                    document.uri,
                    new vscode.Range(startPos, endPos)
                ));
            }
        }

        return locations;
    }

    private isInCommentOrString(document: vscode.TextDocument, position: vscode.Position): boolean {
        const lineText = document.lineAt(position.line).text;
        const charIndex = position.character;

        // Check for line comment before this position
        const lineCommentMatch = lineText.match(/^(.*?)(--|\/\/)/);
        if (lineCommentMatch && lineCommentMatch[1].length <= charIndex) {
            return true;
        }

        // Simple string check (doesn't handle escaped quotes perfectly)
        let inString = false;
        for (let i = 0; i < charIndex; i++) {
            if (lineText[i] === '"' && (i === 0 || lineText[i - 1] !== '\\')) {
                inString = !inString;
            }
        }
        if (inString) {
            return true;
        }

        // Check for block comment
        const textBeforePosition = document.getText(new vscode.Range(0, 0, position.line, charIndex));
        const blockCommentStarts = (textBeforePosition.match(/\/\*/g) || []).length;
        const blockCommentEnds = (textBeforePosition.match(/\*\//g) || []).length;
        if (blockCommentStarts > blockCommentEnds) {
            return true;
        }

        // Check for equals comment
        const lines = textBeforePosition.split('\n');
        let inEqualsComment = false;
        for (const line of lines) {
            if (/^={2,}$/.test(line.trim())) {
                inEqualsComment = !inEqualsComment;
            }
        }
        if (inEqualsComment) {
            return true;
        }

        return false;
    }

    private escapeRegex(str: string): string {
        return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }
}

