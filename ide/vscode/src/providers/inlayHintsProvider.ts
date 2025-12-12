Iimport * as vscode from 'vscode';
import { KSymbolParser } from './symbolParser';

/**
 * Provides inlay hints for K language:
 * - Parameter names at call sites
 * - Inferred types for properties without explicit types
 */
export class KInlayHintsProvider implements vscode.InlayHintsProvider {

    async provideInlayHints(
        document: vscode.TextDocument,
        range: vscode.Range,
        token: vscode.CancellationToken
    ): Promise<vscode.InlayHint[]> {
        const hints: vscode.InlayHint[] = [];
        const text = document.getText();
        const symbols = KSymbolParser.parseDocument(document);

        // Build a map of function definitions and their parameters
        const functionParams = new Map<string, string[]>();
        for (const symbol of symbols) {
            if (symbol.kind === 'function') {
                // Extract parameter names from the function definition
                const funcLine = document.lineAt(symbol.range.start.line).text;
                const paramsMatch = funcLine.match(/fun\s+\w+\s*\(([^)]*)\)/);
                if (paramsMatch && paramsMatch[1]) {
                    const params = paramsMatch[1].split(',').map(p => {
                        const colonIdx = p.indexOf(':');
                        return colonIdx > 0 ? p.substring(0, colonIdx).trim() : p.trim();
                    }).filter(p => p.length > 0);
                    functionParams.set(symbol.name, params);
                }
            }
        }

        // Find function calls and add parameter name hints
        const callRegex = /\b([a-zA-Z_][a-zA-Z0-9_]*)\s*\(/g;
        let match;
        while ((match = callRegex.exec(text)) !== null) {
            const funcName = match[1];
            const params = functionParams.get(funcName);
            if (params && params.length > 0) {
                // Find the arguments in the call
                const callStart = match.index + match[0].length;
                const argsResult = this.parseArguments(text, callStart);

                if (argsResult) {
                    for (let i = 0; i < Math.min(params.length, argsResult.args.length); i++) {
                        const arg = argsResult.args[i];
                        const argPos = document.positionAt(arg.start);

                        // Only show hint if arg is not already named (name = value)
                        if (!arg.text.includes('=')) {
                            const hint = new vscode.InlayHint(
                                argPos,
                                `${params[i]}: `,
                                vscode.InlayHintKind.Parameter
                            );
                            hint.paddingRight = true;
                            hints.push(hint);
                        }
                    }
                }
            }
        }

        // Add type hints for properties with inferred types (showing the declared type)
        // This helps when the type is far from the property name
        for (const symbol of symbols) {
            if (symbol.kind === 'member' && symbol.type) {
                const line = document.lineAt(symbol.range.start.line);
                const lineText = line.text;

                // Check if there's a complex expression (=) after the type
                const colonIndex = lineText.indexOf(':');
                const eqIndex = lineText.indexOf('=');

                if (eqIndex > colonIndex && colonIndex >= 0) {
                    // Property has an initializer - show type hint at end of line
                    const typeMatch = lineText.match(/:\s*([A-Z][a-zA-Z0-9_]*)/);
                    if (typeMatch && lineText.length > 60) {
                        // Long line - add a type hint at the end
                        const hint = new vscode.InlayHint(
                            new vscode.Position(symbol.range.start.line, lineText.trimEnd().length),
                            ` → ${typeMatch[1]}`,
                            vscode.InlayHintKind.Type
                        );
                        hint.paddingLeft = true;
                        hints.push(hint);
                    }
                }
            }
        }

        return hints;
    }

    private parseArguments(text: string, start: number): { args: Array<{start: number, text: string}> } | null {
        const args: Array<{start: number, text: string}> = [];
        let depth = 1;
        let argStart = start;
        let current = start;

        while (current < text.length && depth > 0) {
            const char = text[current];

            if (char === '(') {
                depth++;
            } else if (char === ')') {
                depth--;
                if (depth === 0) {
                    // End of arguments
                    const argText = text.substring(argStart, current).trim();
                    if (argText) {
                        args.push({ start: argStart, text: argText });
                    }
                }
            } else if (char === ',' && depth === 1) {
                // Argument separator at top level
                const argText = text.substring(argStart, current).trim();
                if (argText) {
                    args.push({ start: argStart, text: argText });
                }
                argStart = current + 1;
            }

            current++;
        }

        return depth === 0 ? { args } : null;
    }
}

