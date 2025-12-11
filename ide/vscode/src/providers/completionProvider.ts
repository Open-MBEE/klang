import * as vscode from 'vscode';
import { KSymbolParser, KSymbol } from './symbolParser';

/**
 * Provides code completion for K language files
 */
export class KCompletionProvider implements vscode.CompletionItemProvider {
    // K language keywords
    private static readonly KEYWORDS = [
        'package', 'import', 'class', 'assoc', 'extends', 'fun', 'req', 'val', 'var',
        'type', 'annotation', 'trait', 'if', 'then', 'else', 'forall', 'exists',
        'prev', 'this', 'result', 'true', 'false', 'null', 'new', 'in', 'assert'
    ];

    // K built-in types
    private static readonly BUILTIN_TYPES = [
        'Int', 'Real', 'Bool', 'String', 'Unit', 'Char', 'Any', 'Nothing',
        'Set', 'Bag', 'Seq', 'Map', 'Option', 'List', 'Tuple'
    ];

    // K built-in functions/operators
    private static readonly BUILTIN_FUNCTIONS = [
        'abs', 'min', 'max', 'sum', 'count', 'size', 'isEmpty', 'nonEmpty',
        'head', 'tail', 'last', 'init', 'take', 'drop', 'filter', 'map',
        'concat', 'flatten', 'distinct', 'contains', 'indexOf', 'union',
        'intersect', 'diff', 'subset', 'superset', 'toInt', 'toReal', 'toString'
    ];

    provideCompletionItems(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken,
        context: vscode.CompletionContext
    ): vscode.CompletionItem[] | vscode.CompletionList {
        const completions: vscode.CompletionItem[] = [];
        const linePrefix = document.lineAt(position).text.substring(0, position.character);

        // Get context from the line
        const inTypePosition = this.isTypePosition(linePrefix);
        const afterDot = linePrefix.trimEnd().endsWith('.');
        const inClassBody = this.isInClassBody(document, position);

        // Parse document for user-defined symbols
        const symbols = KSymbolParser.parseDocument(document);

        // After a dot - member completions
        if (afterDot) {
            return this.getMemberCompletions(document, position, symbols);
        }

        // In type position (after : or extends)
        if (inTypePosition) {
            return this.getTypeCompletions(symbols);
        }

        // In class body - suggest member-level keywords
        if (inClassBody) {
            completions.push(...this.getClassBodyKeywords());
        } else {
            // Top level - suggest top-level keywords
            completions.push(...this.getTopLevelKeywords());
        }

        // Add user-defined symbols
        completions.push(...this.getSymbolCompletions(symbols, position));

        // Add built-in functions
        completions.push(...this.getBuiltinFunctionCompletions());

        return completions;
    }

    private isTypePosition(linePrefix: string): boolean {
        // After : for type annotation, or after extends
        return /:\s*[A-Z]?[a-zA-Z0-9_]*$/.test(linePrefix) ||
               /extends\s+[A-Z]?[a-zA-Z0-9_,\s]*$/.test(linePrefix);
    }

    private isInClassBody(document: vscode.TextDocument, position: vscode.Position): boolean {
        // Simple heuristic: check if we're inside braces after a class declaration
        let braceDepth = 0;
        let inClass = false;

        for (let i = 0; i <= position.line; i++) {
            const line = document.lineAt(i).text;
            const endCol = i === position.line ? position.character : line.length;

            for (let j = 0; j < endCol; j++) {
                if (line[j] === '{') {
                    braceDepth++;
                } else if (line[j] === '}') {
                    braceDepth--;
                }
            }

            if (/^\s*(class|assoc)\s+[A-Z]/.test(line)) {
                inClass = true;
            }
        }

        return inClass && braceDepth > 0;
    }

    private getTypeCompletions(symbols: KSymbol[]): vscode.CompletionItem[] {
        const completions: vscode.CompletionItem[] = [];

        // Built-in types
        for (const type of KCompletionProvider.BUILTIN_TYPES) {
            const item = new vscode.CompletionItem(type, vscode.CompletionItemKind.Class);
            item.detail = 'Built-in type';
            item.sortText = '0' + type; // Sort built-ins first
            completions.push(item);
        }

        // User-defined classes
        for (const symbol of symbols) {
            if (symbol.kind === 'class') {
                const item = new vscode.CompletionItem(symbol.name, vscode.CompletionItemKind.Class);
                item.detail = symbol.extends ? `extends ${symbol.extends}` : 'Class';
                item.documentation = symbol.documentation;
                item.sortText = '1' + symbol.name;
                completions.push(item);
            }
        }

        return completions;
    }

    private getMemberCompletions(
        document: vscode.TextDocument,
        position: vscode.Position,
        symbols: KSymbol[]
    ): vscode.CompletionItem[] {
        const completions: vscode.CompletionItem[] = [];

        // Get the identifier before the dot
        const linePrefix = document.lineAt(position).text.substring(0, position.character);
        const match = linePrefix.match(/([a-zA-Z_][a-zA-Z0-9_]*)\s*\.$/);

        if (match) {
            const varName = match[1];

            // Find the type of this variable
            const varSymbol = symbols.find(s => s.name === varName && s.kind === 'member');
            if (varSymbol && varSymbol.type) {
                // Find members of the type
                const typeSymbol = symbols.find(s => s.name === varSymbol.type && s.kind === 'class');
                if (typeSymbol) {
                    // Add members of that class
                    for (const member of symbols) {
                        if (member.containerName === typeSymbol.name) {
                            const kind = member.kind === 'function'
                                ? vscode.CompletionItemKind.Method
                                : member.kind === 'member'
                                    ? vscode.CompletionItemKind.Field
                                    : vscode.CompletionItemKind.Property;

                            const item = new vscode.CompletionItem(member.name, kind);
                            item.detail = member.type || member.returnType || '';
                            item.documentation = member.documentation;
                            completions.push(item);
                        }
                    }
                }
            }
        }

        // Also add common member access completions
        completions.push(...this.getBuiltinFunctionCompletions());

        return completions;
    }

    private getTopLevelKeywords(): vscode.CompletionItem[] {
        const topLevelKeywords = ['package', 'import', 'class', 'assoc', 'type', 'annotation', 'trait'];
        return topLevelKeywords.map(kw => {
            const item = new vscode.CompletionItem(kw, vscode.CompletionItemKind.Keyword);
            item.detail = 'K keyword';
            item.insertText = this.getKeywordSnippet(kw);
            return item;
        });
    }

    private getClassBodyKeywords(): vscode.CompletionItem[] {
        const bodyKeywords = ['fun', 'req', 'val', 'var', 'extends'];
        const completions = bodyKeywords.map(kw => {
            const item = new vscode.CompletionItem(kw, vscode.CompletionItemKind.Keyword);
            item.detail = 'K keyword';
            item.insertText = this.getKeywordSnippet(kw);
            return item;
        });

        // Also suggest types for property declarations
        completions.push(...this.getTypeCompletions([]));

        return completions;
    }

    private getKeywordSnippet(keyword: string): vscode.SnippetString | string {
        switch (keyword) {
            case 'package':
                return new vscode.SnippetString('package ${1:name}');
            case 'import':
                return new vscode.SnippetString('import ${1:package}');
            case 'class':
                return new vscode.SnippetString('class ${1:Name} {\n\t$0\n}');
            case 'assoc':
                return new vscode.SnippetString('assoc ${1:Name} {\n\t$0\n}');
            case 'fun':
                return new vscode.SnippetString('fun ${1:name}(${2:params}): ${3:Type} {\n\t$0\n}');
            case 'req':
                return new vscode.SnippetString('req ${1:constraint}');
            case 'val':
                return new vscode.SnippetString('val ${1:name}: ${2:Type} = ${0}');
            case 'var':
                return new vscode.SnippetString('var ${1:name}: ${2:Type}');
            default:
                return keyword;
        }
    }

    private getSymbolCompletions(symbols: KSymbol[], position: vscode.Position): vscode.CompletionItem[] {
        const completions: vscode.CompletionItem[] = [];

        for (const symbol of symbols) {
            // Don't suggest the symbol we're currently defining
            if (symbol.range.contains(position)) {
                continue;
            }

            let kind: vscode.CompletionItemKind;
            switch (symbol.kind) {
                case 'class':
                    kind = vscode.CompletionItemKind.Class;
                    break;
                case 'function':
                    kind = vscode.CompletionItemKind.Function;
                    break;
                case 'member':
                    kind = vscode.CompletionItemKind.Field;
                    break;
                case 'constraint':
                    kind = vscode.CompletionItemKind.Constant;
                    break;
                case 'package':
                    kind = vscode.CompletionItemKind.Module;
                    break;
                default:
                    kind = vscode.CompletionItemKind.Variable;
            }

            const item = new vscode.CompletionItem(symbol.name, kind);
            item.detail = symbol.type || symbol.returnType || symbol.extends || symbol.kind;
            item.documentation = symbol.documentation;
            completions.push(item);
        }

        return completions;
    }

    private getBuiltinFunctionCompletions(): vscode.CompletionItem[] {
        return KCompletionProvider.BUILTIN_FUNCTIONS.map(fn => {
            const item = new vscode.CompletionItem(fn, vscode.CompletionItemKind.Function);
            item.detail = 'Built-in function';
            return item;
        });
    }
}

