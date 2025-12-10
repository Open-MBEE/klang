import * as vscode from 'vscode';
import { KSymbolParser, KSymbol, SymbolKind } from './symbolParser';

/**
 * Provides workspace-wide symbol search (Ctrl+T).
 * Allows searching for classes, functions, etc. across all K files.
 */
export class KWorkspaceSymbolProvider implements vscode.WorkspaceSymbolProvider {
    async provideWorkspaceSymbols(
        query: string,
        token: vscode.CancellationToken
    ): Promise<vscode.SymbolInformation[]> {
        const results: vscode.SymbolInformation[] = [];
        const kFiles = await vscode.workspace.findFiles('**/*.k', '**/node_modules/**');
        const lowerQuery = query.toLowerCase();

        for (const fileUri of kFiles) {
            if (token.isCancellationRequested) {
                break;
            }

            const document = await vscode.workspace.openTextDocument(fileUri);
            const symbols = KSymbolParser.parseDocument(document);

            for (const symbol of symbols) {
                // Filter by query (fuzzy match)
                if (query === '' || this.fuzzyMatch(symbol.name, lowerQuery)) {
                    results.push(new vscode.SymbolInformation(
                        symbol.name,
                        this.mapKind(symbol.kind),
                        symbol.containerName || '',
                        new vscode.Location(fileUri, symbol.range)
                    ));
                }
            }
        }

        return results;
    }

    private fuzzyMatch(name: string, query: string): boolean {
        const lowerName = name.toLowerCase();

        // Simple contains match
        if (lowerName.includes(query)) {
            return true;
        }

        // Fuzzy match: check if all query chars appear in order
        let queryIndex = 0;
        for (let i = 0; i < lowerName.length && queryIndex < query.length; i++) {
            if (lowerName[i] === query[queryIndex]) {
                queryIndex++;
            }
        }
        return queryIndex === query.length;
    }

    private mapKind(kind: SymbolKind): vscode.SymbolKind {
        switch (kind) {
            case 'class':
                return vscode.SymbolKind.Class;
            case 'function':
                return vscode.SymbolKind.Function;
            case 'member':
                return vscode.SymbolKind.Field;
            case 'constraint':
                return vscode.SymbolKind.Property;
            case 'package':
                return vscode.SymbolKind.Package;
            default:
                return vscode.SymbolKind.Variable;
        }
    }
}

