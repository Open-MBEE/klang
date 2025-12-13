import * as vscode from 'vscode';
import { KSolution, KObject } from './autoSolve';

/**
 * Variable bounds information for inline display
 */
export interface VariableBoundsDisplay {
    variableName: string;
    minValue?: string;
    maxValue?: string;
    exactValue?: string;
    feasible: boolean;
    varType: string;
}

/**
 * Provides inline decorations showing variable values and constraint status
 * directly in the editor, similar to how Java debuggers show values.
 */
export class KInlineDecorations implements vscode.Disposable {

    // Decoration types
    private valueDecorationType: vscode.TextEditorDecorationType;
    private satisfiedDecorationType: vscode.TextEditorDecorationType;
    private unsatisfiedDecorationType: vscode.TextEditorDecorationType;
    private rangeDecorationType: vscode.TextEditorDecorationType;

    private disposables: vscode.Disposable[] = [];
    private currentSolution: KSolution | undefined;

    constructor() {
        // Value decoration (green, shown after property declarations)
        this.valueDecorationType = vscode.window.createTextEditorDecorationType({
            after: {
                margin: '0 0 0 1em',
                color: new vscode.ThemeColor('debugTokenExpression.value'),
                fontStyle: 'italic'
            }
        });

        // Satisfied constraint decoration (green checkmark)
        this.satisfiedDecorationType = vscode.window.createTextEditorDecorationType({
            after: {
                margin: '0 0 0 1em',
                contentText: '✓',
                color: new vscode.ThemeColor('testing.iconPassed')
            },
            gutterIconPath: undefined, // Could add gutter icon
            gutterIconSize: 'contain'
        });

        // Unsatisfied/conflicting constraint decoration (red X)
        this.unsatisfiedDecorationType = vscode.window.createTextEditorDecorationType({
            after: {
                margin: '0 0 0 1em',
                contentText: '✗',
                color: new vscode.ThemeColor('testing.iconFailed')
            },
            backgroundColor: new vscode.ThemeColor('diffEditor.removedTextBackground'),
            isWholeLine: true
        });

        // Range decoration (shows feasible range for variables)
        this.rangeDecorationType = vscode.window.createTextEditorDecorationType({
            after: {
                margin: '0 0 0 1em',
                color: new vscode.ThemeColor('debugTokenExpression.number'),
                fontStyle: 'italic'
            }
        });

        // Update decorations when editor changes
        this.disposables.push(
            vscode.window.onDidChangeActiveTextEditor(editor => {
                if (editor && editor.document.languageId === 'k') {
                    this.updateDecorations(editor);
                }
            })
        );
    }

    /**
     * Update decorations with a new solution
     */
    public showSolution(solution: KSolution): void {
        this.currentSolution = solution;

        const editor = vscode.window.activeTextEditor;
        if (editor && editor.document.languageId === 'k') {
            this.updateDecorations(editor);
        }
    }

    /**
     * Clear all decorations
     */
    public clearDecorations(): void {
        this.currentSolution = undefined;
        const editor = vscode.window.activeTextEditor;
        if (editor) {
            editor.setDecorations(this.valueDecorationType, []);
            editor.setDecorations(this.satisfiedDecorationType, []);
            editor.setDecorations(this.unsatisfiedDecorationType, []);
            editor.setDecorations(this.rangeDecorationType, []);
        }
    }

    private updateDecorations(editor: vscode.TextEditor): void {
        if (!this.currentSolution) {
            this.clearDecorations();
            return;
        }

        const document = editor.document;
        const text = document.getText();

        if (this.currentSolution.status === 'sat') {
            this.showSatDecorations(editor, document, text);
        } else if (this.currentSolution.status === 'unsat') {
            this.showUnsatDecorations(editor, document, text);
        }
    }

    private showSatDecorations(
        editor: vscode.TextEditor,
        document: vscode.TextDocument,
        text: string
    ): void {
        const valueDecorations: vscode.DecorationOptions[] = [];
        const satisfiedDecorations: vscode.DecorationOptions[] = [];

        // Build a map of object values by class and property
        const valueMap = this.buildValueMap(this.currentSolution!.objects);

        // Find property declarations and show their values
        const propPattern = /^\s*([a-z][a-zA-Z0-9_]*)\s*:\s*([A-Z][a-zA-Z0-9_]*)/gm;
        let match;

        // Track current class context
        let currentClass = '';
        const classPattern = /^\s*(?:class|assoc)\s+([A-Z][a-zA-Z0-9_]*)/gm;
        const classMatches: Array<{name: string, start: number, end: number}> = [];

        while ((match = classPattern.exec(text)) !== null) {
            const classStart = match.index;
            classMatches.push({
                name: match[1],
                start: classStart,
                end: text.length // Will be refined
            });
        }

        // Refine class end positions
        for (let i = 0; i < classMatches.length - 1; i++) {
            classMatches[i].end = classMatches[i + 1].start;
        }

        // Find property values
        while ((match = propPattern.exec(text)) !== null) {
            const propName = match[1];
            const propType = match[2];
            const position = match.index;

            // Find which class this property belongs to
            currentClass = '';
            for (const cm of classMatches) {
                if (position >= cm.start && position < cm.end) {
                    currentClass = cm.name;
                    break;
                }
            }

            if (currentClass) {
                // Look up value in solution
                const value = this.findPropertyValue(valueMap, currentClass, propName);

                if (value !== undefined) {
                    const line = document.positionAt(position).line;
                    const lineEnd = document.lineAt(line).range.end;

                    valueDecorations.push({
                        range: new vscode.Range(lineEnd, lineEnd),
                        renderOptions: {
                            after: {
                                contentText: ` = ${value}`,
                                color: new vscode.ThemeColor('debugTokenExpression.value'),
                                fontStyle: 'italic'
                            }
                        }
                    });
                }
            }
        }

        // Find constraints and mark them as satisfied
        const constraintPattern = /^\s*req\s+/gm;
        while ((match = constraintPattern.exec(text)) !== null) {
            const line = document.positionAt(match.index).line;
            const lineEnd = document.lineAt(line).range.end;

            satisfiedDecorations.push({
                range: new vscode.Range(lineEnd, lineEnd)
            });
        }

        editor.setDecorations(this.valueDecorationType, valueDecorations);
        editor.setDecorations(this.satisfiedDecorationType, satisfiedDecorations);
        editor.setDecorations(this.unsatisfiedDecorationType, []);
    }

    private showUnsatDecorations(
        editor: vscode.TextEditor,
        document: vscode.TextDocument,
        text: string
    ): void {
        const unsatisfiedDecorations: vscode.DecorationOptions[] = [];

        // If we have unsat core, highlight those specific constraints
        if (this.currentSolution!.unsatCore && this.currentSolution!.unsatCore.length > 0) {
            for (const coreItem of this.currentSolution!.unsatCore) {
                // Try to find this constraint in the document
                // The core might contain constraint names or line numbers
                const lineMatch = coreItem.match(/line\s*(\d+)/i);
                if (lineMatch) {
                    const lineNum = parseInt(lineMatch[1]) - 1; // Convert to 0-indexed
                    if (lineNum >= 0 && lineNum < document.lineCount) {
                        const line = document.lineAt(lineNum);
                        unsatisfiedDecorations.push({
                            range: line.range,
                            hoverMessage: `Part of unsatisfiable core: ${coreItem}`
                        });
                    }
                }
            }
        } else {
            // No specific core info - mark all constraints as potentially conflicting
            const constraintPattern = /^\s*req\s+/gm;
            let match;
            while ((match = constraintPattern.exec(text)) !== null) {
                const line = document.positionAt(match.index).line;
                unsatisfiedDecorations.push({
                    range: document.lineAt(line).range,
                    hoverMessage: 'Model is unsatisfiable - this constraint may be part of the conflict'
                });
            }
        }

        editor.setDecorations(this.unsatisfiedDecorationType, unsatisfiedDecorations);
        editor.setDecorations(this.valueDecorationType, []);
        editor.setDecorations(this.satisfiedDecorationType, []);
    }

    /**
     * Show feasible ranges for variables (during debugging/stepping)
     */
    public showRanges(ranges: Map<string, [number | string, number | string]>): void {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.languageId !== 'k') {
            return;
        }

        const document = editor.document;
        const text = document.getText();
        const rangeDecorations: vscode.DecorationOptions[] = [];

        // Find property declarations and show their ranges
        const propPattern = /^\s*([a-z][a-zA-Z0-9_]*)\s*:\s*([A-Z][a-zA-Z0-9_]*)/gm;
        let match;

        while ((match = propPattern.exec(text)) !== null) {
            const propName = match[1];
            const range = ranges.get(propName);

            if (range) {
                const line = document.positionAt(match.index).line;
                const lineEnd = document.lineAt(line).range.end;

                rangeDecorations.push({
                    range: new vscode.Range(lineEnd, lineEnd),
                    renderOptions: {
                        after: {
                            contentText: ` ∈ [${range[0]}, ${range[1]}]`,
                            color: new vscode.ThemeColor('debugTokenExpression.number'),
                            fontStyle: 'italic'
                        }
                    }
                });
            }
        }

        editor.setDecorations(this.rangeDecorationType, rangeDecorations);
    }

    /**
     * Show variable bounds from solver progress data
     * This is the enhanced version that handles VariableBounds objects
     */
    public showVariableBounds(bounds: Map<string, VariableBoundsDisplay>): void {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.languageId !== 'k') {
            return;
        }

        const document = editor.document;
        const text = document.getText();
        const rangeDecorations: vscode.DecorationOptions[] = [];

        // Track current class context for qualified lookups
        const classRanges: Array<{name: string, start: number, end: number}> = [];
        const classPattern = /^\s*(?:class|assoc)\s+([A-Z][a-zA-Z0-9_]*)/gm;
        let classMatch;
        while ((classMatch = classPattern.exec(text)) !== null) {
            classRanges.push({
                name: classMatch[1],
                start: classMatch.index,
                end: text.length
            });
        }
        for (let i = 0; i < classRanges.length - 1; i++) {
            classRanges[i].end = classRanges[i + 1].start;
        }

        // Find property declarations and show their bounds
        const propPattern = /^\s*([a-z][a-zA-Z0-9_]*)\s*:\s*([A-Z][a-zA-Z0-9_]*)/gm;
        let match;

        while ((match = propPattern.exec(text)) !== null) {
            const propName = match[1];
            const position = match.index;

            // Find class context
            let className = '';
            for (const cr of classRanges) {
                if (position >= cr.start && position < cr.end) {
                    className = cr.name;
                    break;
                }
            }

            // Try both qualified and unqualified lookups
            const boundInfo = bounds.get(propName) ||
                              (className ? bounds.get(`${className}.${propName}`) : undefined);

            if (boundInfo) {
                const line = document.positionAt(match.index).line;
                const lineEnd = document.lineAt(line).range.end;

                // Format the display string
                const displayText = this.formatBoundsDisplay(boundInfo);
                const color = boundInfo.feasible
                    ? new vscode.ThemeColor('debugTokenExpression.number')
                    : new vscode.ThemeColor('errorForeground');

                rangeDecorations.push({
                    range: new vscode.Range(lineEnd, lineEnd),
                    renderOptions: {
                        after: {
                            contentText: ` ${displayText}`,
                            color: color,
                            fontStyle: 'italic'
                        }
                    },
                    hoverMessage: new vscode.MarkdownString(
                        `**Variable Bounds** (${boundInfo.varType})\n\n` +
                        (boundInfo.exactValue ? `Exact: \`${boundInfo.exactValue}\`\n\n` : '') +
                        (boundInfo.minValue ? `Min: \`${boundInfo.minValue}\`\n\n` : '') +
                        (boundInfo.maxValue ? `Max: \`${boundInfo.maxValue}\`\n\n` : '') +
                        (boundInfo.feasible ? '' : '⚠️ Infeasible')
                    )
                });
            }
        }

        editor.setDecorations(this.rangeDecorationType, rangeDecorations);
    }

    /**
     * Format bounds for display
     */
    private formatBoundsDisplay(bounds: VariableBoundsDisplay): string {
        if (!bounds.feasible) {
            return '∅ (infeasible)';
        }

        if (bounds.exactValue !== undefined) {
            return `= ${bounds.exactValue}`;
        }

        const hasMin = bounds.minValue !== undefined;
        const hasMax = bounds.maxValue !== undefined;

        if (hasMin && hasMax) {
            if (bounds.minValue === bounds.maxValue) {
                return `= ${bounds.minValue}`;
            }
            return `∈ [${bounds.minValue}, ${bounds.maxValue}]`;
        } else if (hasMin) {
            return `≥ ${bounds.minValue}`;
        } else if (hasMax) {
            return `≤ ${bounds.maxValue}`;
        }

        return '?';
    }

    private buildValueMap(objects: KObject[]): Map<string, Map<string, string>> {
        const map = new Map<string, Map<string, string>>();

        for (const obj of objects) {
            if (!map.has(obj.className)) {
                map.set(obj.className, new Map());
            }
            const classMap = map.get(obj.className)!;

            for (const [prop, value] of Object.entries(obj.properties)) {
                // Store first value found for each property
                if (!classMap.has(prop)) {
                    classMap.set(prop, value);
                }
            }
        }

        return map;
    }

    private findPropertyValue(
        valueMap: Map<string, Map<string, string>>,
        className: string,
        propName: string
    ): string | undefined {
        const classMap = valueMap.get(className);
        if (classMap) {
            return classMap.get(propName);
        }
        return undefined;
    }

    public dispose(): void {
        this.valueDecorationType.dispose();
        this.satisfiedDecorationType.dispose();
        this.unsatisfiedDecorationType.dispose();
        this.rangeDecorationType.dispose();
        this.disposables.forEach(d => d.dispose());
    }
}

