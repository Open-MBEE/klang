import * as vscode from 'vscode';

/**
 * Represents a constraint in the K model
 */
export interface KConstraint {
    name?: string;
    expression: string;
    line: number;
    type: 'hard' | 'soft' | 'optimization';
    satisfied?: boolean;
}

/**
 * Represents a variable and its current state during debugging
 */
export interface KVariable {
    name: string;
    type: string;
    className?: string;
    line: number;
    range?: [number | string, number | string];
    value?: string | number;
}

/**
 * State during constraint debugging
 */
export interface DebugState {
    step: number;
    totalSteps: number;
    currentConstraint?: KConstraint;
    variables: KVariable[];
    status: 'sat' | 'unsat' | 'unknown';
    sampleSolution?: { [key: string]: any };
}

/**
 * Constraint debugger that allows stepping through constraints
 * to see how the solution space narrows.
 */
export class KConstraintDebugger implements vscode.Disposable {

    private debugSession: DebugSession | undefined;
    private statusBarItem: vscode.StatusBarItem;
    private decorationType: vscode.TextEditorDecorationType;
    private currentStepDecorationType: vscode.TextEditorDecorationType;
    private panel: vscode.WebviewPanel | undefined;
    private disposables: vscode.Disposable[] = [];

    // Event emitters
    private _onStateChange = new vscode.EventEmitter<DebugState>();
    public readonly onStateChange = this._onStateChange.event;

    constructor() {
        this.statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Left,
            40
        );

        // Decoration for constraints that have been processed
        this.decorationType = vscode.window.createTextEditorDecorationType({
            backgroundColor: new vscode.ThemeColor('diffEditor.insertedTextBackground'),
            isWholeLine: true
        });

        // Decoration for the current constraint being evaluated
        this.currentStepDecorationType = vscode.window.createTextEditorDecorationType({
            backgroundColor: new vscode.ThemeColor('editor.findMatchHighlightBackground'),
            isWholeLine: true,
            after: {
                contentText: ' ◄ current',
                color: new vscode.ThemeColor('debugIcon.breakpointCurrentStackframeForeground'),
                fontStyle: 'italic'
            }
        });
    }

    /**
     * Start a new debug session for the given document
     */
    public async startSession(document: vscode.TextDocument): Promise<void> {
        // Parse constraints from the document
        const constraints = this.parseConstraints(document);
        const variables = this.parseVariables(document);

        if (constraints.length === 0) {
            vscode.window.showInformationMessage('No constraints found in this file');
            return;
        }

        this.debugSession = {
            document,
            constraints,
            variables,
            currentStep: 0,
            activeConstraints: []
        };

        // Show debug UI
        this.showDebugPanel();
        this.updateStatusBar();
        this.updateDecorations();

        // Report initial state
        this._onStateChange.fire(this.getCurrentState());
    }

    /**
     * Step to the next constraint
     */
    public stepNext(): void {
        if (!this.debugSession) return;

        if (this.debugSession.currentStep < this.debugSession.constraints.length) {
            const constraint = this.debugSession.constraints[this.debugSession.currentStep];
            this.debugSession.activeConstraints.push(constraint);
            this.debugSession.currentStep++;

            // Simulate evaluating constraints (in real impl, would call solver)
            this.evaluateCurrentState();

            this.updateDecorations();
            this.updateStatusBar();
            this.updatePanel();
            this._onStateChange.fire(this.getCurrentState());
        }
    }

    /**
     * Step to the previous constraint (undo)
     */
    public stepPrev(): void {
        if (!this.debugSession || this.debugSession.currentStep <= 0) return;

        this.debugSession.currentStep--;
        this.debugSession.activeConstraints.pop();

        this.evaluateCurrentState();

        this.updateDecorations();
        this.updateStatusBar();
        this.updatePanel();
        this._onStateChange.fire(this.getCurrentState());
    }

    /**
     * Jump to a specific step
     */
    public goToStep(step: number): void {
        if (!this.debugSession) return;

        step = Math.max(0, Math.min(step, this.debugSession.constraints.length));

        this.debugSession.currentStep = step;
        this.debugSession.activeConstraints = this.debugSession.constraints.slice(0, step);

        this.evaluateCurrentState();

        this.updateDecorations();
        this.updateStatusBar();
        this.updatePanel();
        this._onStateChange.fire(this.getCurrentState());
    }

    /**
     * Run to completion
     */
    public runToEnd(): void {
        if (!this.debugSession) return;
        this.goToStep(this.debugSession.constraints.length);
    }

    /**
     * Stop the debug session
     */
    public stopSession(): void {
        if (this.debugSession) {
            const editor = vscode.window.activeTextEditor;
            if (editor) {
                editor.setDecorations(this.decorationType, []);
                editor.setDecorations(this.currentStepDecorationType, []);
            }
        }

        this.debugSession = undefined;
        this.statusBarItem.hide();
        this.panel?.dispose();
        this.panel = undefined;
    }

    /**
     * Get the current debug state
     */
    public getCurrentState(): DebugState {
        if (!this.debugSession) {
            return {
                step: 0,
                totalSteps: 0,
                variables: [],
                status: 'unknown'
            };
        }

        const currentConstraint = this.debugSession.currentStep < this.debugSession.constraints.length
            ? this.debugSession.constraints[this.debugSession.currentStep]
            : undefined;

        return {
            step: this.debugSession.currentStep,
            totalSteps: this.debugSession.constraints.length,
            currentConstraint,
            variables: this.debugSession.variables,
            status: this.debugSession.currentStatus || 'unknown',
            sampleSolution: this.debugSession.sampleSolution
        };
    }

    /**
     * Parse constraints from a K document
     */
    private parseConstraints(document: vscode.TextDocument): KConstraint[] {
        const constraints: KConstraint[] = [];
        const text = document.getText();

        // Pattern for req constraints (hard)
        const reqPattern = /^\s*req\s+(?:([A-Z][a-zA-Z0-9_]*)\s*:)?\s*(.+)$/gm;
        let match;

        while ((match = reqPattern.exec(text)) !== null) {
            const line = document.positionAt(match.index).line;
            constraints.push({
                name: match[1] || undefined,
                expression: match[2].trim(),
                line,
                type: 'hard'
            });
        }

        // Pattern for soft req constraints
        const softPattern = /^\s*soft\s+req\s+(?:([A-Z][a-zA-Z0-9_]*)\s*:)?\s*(.+)$/gm;
        while ((match = softPattern.exec(text)) !== null) {
            const line = document.positionAt(match.index).line;
            constraints.push({
                name: match[1] || undefined,
                expression: match[2].trim(),
                line,
                type: 'soft'
            });
        }

        // Pattern for minimize/maximize
        const optPattern = /^\s*(minimize|maximize)\s+(.+)$/gm;
        while ((match = optPattern.exec(text)) !== null) {
            const line = document.positionAt(match.index).line;
            constraints.push({
                name: match[1],
                expression: match[2].trim(),
                line,
                type: 'optimization'
            });
        }

        // Sort by line number
        constraints.sort((a, b) => a.line - b.line);

        return constraints;
    }

    /**
     * Parse variables from a K document
     */
    private parseVariables(document: vscode.TextDocument): KVariable[] {
        const variables: KVariable[] = [];
        const text = document.getText();

        // Track current class
        let currentClass = '';
        const classPattern = /^\s*(?:class|assoc)\s+([A-Z][a-zA-Z0-9_]*)/gm;
        const classPositions: { name: string; start: number }[] = [];

        let match;
        while ((match = classPattern.exec(text)) !== null) {
            classPositions.push({ name: match[1], start: match.index });
        }

        // Pattern for property declarations
        const propPattern = /^\s*([a-z][a-zA-Z0-9_]*)\s*:\s*([A-Z][a-zA-Z0-9_<>,\s]*)/gm;

        while ((match = propPattern.exec(text)) !== null) {
            const line = document.positionAt(match.index).line;
            const position = match.index;

            // Find which class this belongs to
            currentClass = '';
            for (let i = classPositions.length - 1; i >= 0; i--) {
                if (position > classPositions[i].start) {
                    currentClass = classPositions[i].name;
                    break;
                }
            }

            variables.push({
                name: match[1],
                type: match[2].trim(),
                className: currentClass || undefined,
                line,
                range: this.getInitialRange(match[2].trim())
            });
        }

        return variables;
    }

    /**
     * Get initial range for a type
     */
    private getInitialRange(type: string): [string, string] | undefined {
        switch (type) {
            case 'Int':
                return ['-∞', '+∞'];
            case 'Real':
                return ['-∞', '+∞'];
            case 'Bool':
                return ['false', 'true'];
            default:
                return undefined;
        }
    }

    /**
     * Simulate evaluating the current constraint set
     * In a real implementation, this would call the K solver incrementally
     */
    private evaluateCurrentState(): void {
        if (!this.debugSession) return;

        // For demo purposes, simulate narrowing ranges
        // In real implementation, this would query Z3 incrementally

        const step = this.debugSession.currentStep;
        const total = this.debugSession.constraints.length;

        // Simulate that we're still SAT until we add all constraints
        this.debugSession.currentStatus = step < total ? 'sat' : 'sat';

        // Update variable ranges based on active constraints
        // This is a simplified simulation
        for (const variable of this.debugSession.variables) {
            if (variable.type === 'Int' || variable.type === 'Real') {
                // Simulate range narrowing
                const narrowFactor = step / Math.max(total, 1);
                if (variable.range) {
                    // Just for visualization - in real impl, would get from Z3
                    variable.range = [
                        `${Math.round(-1000 * (1 - narrowFactor * 0.5))}`,
                        `${Math.round(1000 * (1 - narrowFactor * 0.5))}`
                    ];
                }
            }
        }

        // Generate a sample solution (simulated)
        if (this.debugSession.currentStatus === 'sat') {
            this.debugSession.sampleSolution = {};
            for (const v of this.debugSession.variables) {
                if (v.type === 'Int') {
                    this.debugSession.sampleSolution[v.name] = Math.round(Math.random() * 100);
                } else if (v.type === 'Real') {
                    this.debugSession.sampleSolution[v.name] = Math.round(Math.random() * 100 * 10) / 10;
                } else if (v.type === 'Bool') {
                    this.debugSession.sampleSolution[v.name] = Math.random() > 0.5;
                }
            }
        }
    }

    private updateDecorations(): void {
        const editor = vscode.window.activeTextEditor;
        if (!editor || !this.debugSession) return;

        const processedDecorations: vscode.DecorationOptions[] = [];
        const currentDecoration: vscode.DecorationOptions[] = [];

        // Mark processed constraints
        for (let i = 0; i < this.debugSession.currentStep; i++) {
            const constraint = this.debugSession.constraints[i];
            processedDecorations.push({
                range: editor.document.lineAt(constraint.line).range,
                hoverMessage: `Step ${i + 1}: ${constraint.satisfied !== false ? '✓ Satisfied' : '✗ Failed'}`
            });
        }

        // Mark current constraint
        if (this.debugSession.currentStep < this.debugSession.constraints.length) {
            const current = this.debugSession.constraints[this.debugSession.currentStep];
            currentDecoration.push({
                range: editor.document.lineAt(current.line).range
            });
        }

        editor.setDecorations(this.decorationType, processedDecorations);
        editor.setDecorations(this.currentStepDecorationType, currentDecoration);
    }

    private updateStatusBar(): void {
        if (!this.debugSession) {
            this.statusBarItem.hide();
            return;
        }

        const state = this.getCurrentState();
        this.statusBarItem.text = `$(debug-step-over) K Debug: Step ${state.step}/${state.totalSteps} [${state.status.toUpperCase()}]`;
        this.statusBarItem.tooltip = 'Click to show constraint debugger';
        this.statusBarItem.command = 'k.showConstraintDebugger';
        this.statusBarItem.show();
    }

    private showDebugPanel(): void {
        if (this.panel) {
            this.panel.reveal();
            return;
        }

        this.panel = vscode.window.createWebviewPanel(
            'kConstraintDebugger',
            'K Constraint Debugger',
            vscode.ViewColumn.Beside,
            {
                enableScripts: true
            }
        );

        this.panel.onDidDispose(() => {
            this.panel = undefined;
        }, null, this.disposables);

        // Handle messages from the webview
        this.panel.webview.onDidReceiveMessage(message => {
            switch (message.type) {
                case 'stepNext':
                    this.stepNext();
                    break;
                case 'stepPrev':
                    this.stepPrev();
                    break;
                case 'goToStep':
                    this.goToStep(message.step);
                    break;
                case 'runToEnd':
                    this.runToEnd();
                    break;
                case 'stop':
                    this.stopSession();
                    break;
            }
        }, null, this.disposables);

        this.updatePanel();
    }

    private updatePanel(): void {
        if (!this.panel || !this.debugSession) return;

        const state = this.getCurrentState();
        this.panel.webview.html = this.getPanelHtml(state);
    }

    private getPanelHtml(state: DebugState): string {
        const constraintsList = this.debugSession?.constraints.map((c, i) => {
            const status = i < state.step ? 'processed' : (i === state.step ? 'current' : 'pending');
            const icon = i < state.step ? '✓' : (i === state.step ? '▶' : '○');
            return `
                <div class="constraint ${status}" onclick="goToStep(${i})">
                    <span class="icon">${icon}</span>
                    <span class="step">Step ${i + 1}</span>
                    <span class="name">${c.name || c.type}</span>
                    <span class="expr">${this.escapeHtml(c.expression)}</span>
                </div>
            `;
        }).join('') || '';

        const variablesList = state.variables.map(v => `
            <div class="variable">
                <span class="var-name">${v.className ? v.className + '.' : ''}${v.name}</span>
                <span class="var-type">: ${v.type}</span>
                ${v.range ? `<span class="var-range">∈ [${v.range[0]}, ${v.range[1]}]</span>` : ''}
                ${state.sampleSolution?.[v.name] !== undefined ? 
                    `<span class="var-value">= ${state.sampleSolution[v.name]}</span>` : ''}
            </div>
        `).join('');

        return `<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            font-family: var(--vscode-font-family);
            padding: 16px;
            color: var(--vscode-foreground);
        }
        .controls {
            display: flex;
            gap: 8px;
            margin-bottom: 16px;
            flex-wrap: wrap;
        }
        button {
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            padding: 8px 16px;
            border-radius: 4px;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 4px;
        }
        button:hover {
            background: var(--vscode-button-hoverBackground);
        }
        button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .status {
            padding: 8px 16px;
            border-radius: 4px;
            margin-bottom: 16px;
            font-weight: bold;
        }
        .status.sat { background: var(--vscode-testing-iconPassed); color: white; }
        .status.unsat { background: var(--vscode-testing-iconFailed); color: white; }
        .status.unknown { background: var(--vscode-descriptionForeground); }
        
        h3 {
            margin-top: 16px;
            margin-bottom: 8px;
            border-bottom: 1px solid var(--vscode-panel-border);
            padding-bottom: 4px;
        }
        
        .constraint {
            padding: 8px;
            margin: 4px 0;
            border-radius: 4px;
            cursor: pointer;
            display: flex;
            gap: 8px;
            align-items: flex-start;
        }
        .constraint:hover {
            background: var(--vscode-list-hoverBackground);
        }
        .constraint.processed {
            background: var(--vscode-diffEditor-insertedTextBackground);
        }
        .constraint.current {
            background: var(--vscode-editor-findMatchHighlightBackground);
            border-left: 3px solid var(--vscode-debugIcon-breakpointCurrentStackframeForeground);
        }
        .constraint.pending {
            opacity: 0.6;
        }
        .constraint .icon {
            flex-shrink: 0;
            width: 20px;
        }
        .constraint .step {
            flex-shrink: 0;
            width: 50px;
            color: var(--vscode-descriptionForeground);
        }
        .constraint .name {
            flex-shrink: 0;
            font-weight: bold;
            min-width: 80px;
        }
        .constraint .expr {
            font-family: var(--vscode-editor-font-family);
            color: var(--vscode-debugTokenExpression-string);
        }
        
        .variable {
            padding: 4px 8px;
            font-family: var(--vscode-editor-font-family);
        }
        .var-name {
            color: var(--vscode-symbolIcon-variableForeground);
        }
        .var-type {
            color: var(--vscode-symbolIcon-classForeground);
        }
        .var-range {
            color: var(--vscode-debugTokenExpression-number);
            margin-left: 8px;
        }
        .var-value {
            color: var(--vscode-debugTokenExpression-value);
            margin-left: 8px;
            font-weight: bold;
        }
        
        .progress-indicator {
            margin: 8px 0;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .progress-bar {
            flex: 1;
            height: 8px;
            background: var(--vscode-progressBar-background);
            border-radius: 4px;
            overflow: hidden;
        }
        .progress-fill {
            height: 100%;
            background: var(--vscode-progressBar-foreground);
            transition: width 0.3s;
        }
    </style>
</head>
<body>
    <h2>Constraint Debugger</h2>
    
    <div class="controls">
        <button onclick="stepPrev()" ${state.step === 0 ? 'disabled' : ''}>
            ◀ Prev
        </button>
        <button onclick="stepNext()" ${state.step >= state.totalSteps ? 'disabled' : ''}>
            Next ▶
        </button>
        <button onclick="runToEnd()" ${state.step >= state.totalSteps ? 'disabled' : ''}>
            ▶▶ Run All
        </button>
        <button onclick="stop()">
            ⏹ Stop
        </button>
    </div>
    
    <div class="status ${state.status}">
        Status: ${state.status.toUpperCase()}
    </div>
    
    <div class="progress-indicator">
        <span>Step ${state.step} / ${state.totalSteps}</span>
        <div class="progress-bar">
            <div class="progress-fill" style="width: ${state.totalSteps > 0 ? (state.step / state.totalSteps * 100) : 0}%"></div>
        </div>
    </div>
    
    <h3>Constraints</h3>
    <div class="constraints-list">
        ${constraintsList}
    </div>
    
    <h3>Variables</h3>
    <div class="variables-list">
        ${variablesList}
    </div>
    
    <script>
        const vscode = acquireVsCodeApi();
        
        function stepNext() {
            vscode.postMessage({ type: 'stepNext' });
        }
        function stepPrev() {
            vscode.postMessage({ type: 'stepPrev' });
        }
        function goToStep(step) {
            vscode.postMessage({ type: 'goToStep', step: step });
        }
        function runToEnd() {
            vscode.postMessage({ type: 'runToEnd' });
        }
        function stop() {
            vscode.postMessage({ type: 'stop' });
        }
    </script>
</body>
</html>`;
    }

    private escapeHtml(text: string): string {
        return text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    public dispose(): void {
        this.stopSession();
        this.statusBarItem.dispose();
        this.decorationType.dispose();
        this.currentStepDecorationType.dispose();
        this._onStateChange.dispose();
        this.disposables.forEach(d => d.dispose());
    }
}

interface DebugSession {
    document: vscode.TextDocument;
    constraints: KConstraint[];
    variables: KVariable[];
    currentStep: number;
    activeConstraints: KConstraint[];
    currentStatus?: 'sat' | 'unsat' | 'unknown';
    sampleSolution?: { [key: string]: any };
}

