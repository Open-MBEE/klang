import * as vscode from 'vscode';
import { KSymbolParser, KSymbol, SymbolKind } from './symbolParser';

/**
 * Provides document symbols for the Outline view and breadcrumbs.
 * Also enables Ctrl+Shift+O to navigate within a file.
 */
export class KDocumentSymbolProvider implements vscode.DocumentSymbolProvider {
    provideDocumentSymbols(
        document: vscode.TextDocument,
        token: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.DocumentSymbol[]> {
        const symbols = KSymbolParser.parseDocument(document);
        return this.convertToDocumentSymbols(symbols);
    }

    private convertToDocumentSymbols(symbols: KSymbol[]): vscode.DocumentSymbol[] {
        const result: vscode.DocumentSymbol[] = [];
        let currentClass: vscode.DocumentSymbol | undefined;

        for (const symbol of symbols) {
            const vscodeKind = this.mapKind(symbol.kind);
            const detail = this.getDetail(symbol);

            const docSymbol = new vscode.DocumentSymbol(
                symbol.name,
                detail,
                vscodeKind,
                symbol.range,
                symbol.range
            );

            if (symbol.kind === 'class') {
                // Start a new class scope
                if (currentClass) {
                    result.push(currentClass);
                }
                currentClass = docSymbol;
            } else if (currentClass && symbol.kind !== 'package') {
                // Add as child of current class
                currentClass.children.push(docSymbol);
            } else {
                // Top-level symbol
                if (currentClass) {
                    result.push(currentClass);
                    currentClass = undefined;
                }
                result.push(docSymbol);
            }
        }

        // Don't forget the last class
        if (currentClass) {
            result.push(currentClass);
        }

        return result;
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

    private getDetail(symbol: KSymbol): string {
        switch (symbol.kind) {
            case 'class':
                return symbol.extends ? `extends ${symbol.extends}` : '';
            case 'function':
                return symbol.returnType ? `: ${symbol.returnType}` : '';
            case 'member':
                return symbol.type ? `: ${symbol.type}` : '';
            case 'constraint':
                return 'constraint';
            default:
                return '';
        }
    }
}

