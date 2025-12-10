import * as vscode from 'vscode';
import { KSymbolParser, KSymbol, SymbolKind } from './symbolParser';

/**
 * Provides "Go to Definition" functionality for K language.
 * Ctrl+Click or F12 on a symbol to jump to its definition.
 */
export class KDefinitionProvider implements vscode.DefinitionProvider {
    async provideDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.Definition | undefined> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);
        if (!word) {
            return undefined;
        }

        // Search for the definition in all K files
        const locations = await this.findDefinitions(word, token);
        return locations;
    }

    private async findDefinitions(
        symbolName: string,
        token: vscode.CancellationToken
    ): Promise<vscode.Location[]> {
        const locations: vscode.Location[] = [];
        const kFiles = await vscode.workspace.findFiles('**/*.k', '**/node_modules/**');

        for (const fileUri of kFiles) {
            if (token.isCancellationRequested) {
                break;
            }

            const document = await vscode.workspace.openTextDocument(fileUri);
            const symbols = KSymbolParser.parseDocument(document);

            for (const symbol of symbols) {
                if (symbol.name === symbolName) {
                    locations.push(new vscode.Location(fileUri, symbol.range));
                }
            }
        }

        return locations;
    }
}

