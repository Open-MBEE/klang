import * as vscode from 'vscode';
import { KSymbolParser, KSymbol } from './symbolParser';

/**
 * Provides hover information for K language symbols.
 * Shows documentation comments when hovering over symbols.
 */
export class KHoverProvider implements vscode.HoverProvider {
    async provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.Hover | undefined> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);
        if (!word) {
            return undefined;
        }

        // Find the symbol definition and its documentation
        const symbolInfo = await this.findSymbolWithDoc(word, token);
        if (!symbolInfo) {
            return undefined;
        }

        const markdown = new vscode.MarkdownString();

        // Add kind and name
        markdown.appendCodeblock(symbolInfo.signature, 'k');

        // Add documentation if available
        if (symbolInfo.documentation) {
            markdown.appendMarkdown('\n---\n');
            markdown.appendMarkdown(symbolInfo.documentation);
        }

        // Add location info
        if (symbolInfo.location) {
            markdown.appendMarkdown(`\n\n*Defined in ${symbolInfo.location}*`);
        }

        return new vscode.Hover(markdown, wordRange);
    }

    private async findSymbolWithDoc(
        symbolName: string,
        token: vscode.CancellationToken
    ): Promise<{ signature: string; documentation?: string; location?: string } | undefined> {
        const kFiles = await vscode.workspace.findFiles('**/*.k', '**/node_modules/**');

        for (const fileUri of kFiles) {
            if (token.isCancellationRequested) {
                break;
            }

            const document = await vscode.workspace.openTextDocument(fileUri);
            const symbols = KSymbolParser.parseDocument(document);

            for (const symbol of symbols) {
                if (symbol.name === symbolName) {
                    const relativePath = vscode.workspace.asRelativePath(fileUri);
                    return {
                        signature: this.getSignature(symbol, document),
                        documentation: symbol.documentation,
                        location: `${relativePath}:${symbol.range.start.line + 1}`
                    };
                }
            }
        }

        return undefined;
    }

    private getSignature(symbol: KSymbol, document: vscode.TextDocument): string {
        switch (symbol.kind) {
            case 'class':
                return symbol.extends
                    ? `class ${symbol.name} extends ${symbol.extends}`
                    : `class ${symbol.name}`;
            case 'function':
                return symbol.returnType
                    ? `fun ${symbol.name} : ${symbol.returnType}`
                    : `fun ${symbol.name}`;
            case 'member':
                return symbol.type
                    ? `${symbol.name} : ${symbol.type}`
                    : symbol.name;
            case 'constraint':
                return `req ${symbol.name}`;
            default:
                return symbol.name;
        }
    }
}

