/**
 * Unified K Debug Panel
 *
 * Combines constraint debugging and solution visualization into a single panel,
 * similar to IntelliJ's Debug tool window that supports multiple sessions.
 *
 * Supports integration with Java/Python debuggers for stepping into external functions.
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as cp from 'child_process';
import * as fs from 'fs';

/**
 * Represents an external function call that can be debugged
 */
interface ExternalFunction {
    name: string;
    language: 'java' | 'python' | 'unknown';
    className?: string;       // Java class name
    methodName?: string;      // Java method or Python function
    sourceFile?: string;      // Path to source file
    sourceLine?: number;      // Line number in source
    packagePath?: string;     // Package/module path
}

/**
 * Represents a debug session for a K file
 */
interface KDebugSession {
    id: string;
    fileName: string;
    filePath: string;
    document: vscode.TextDocument;
    status: 'idle' | 'running' | 'sat' | 'unsat' | 'error';
    constraints: KConstraint[];
    currentStep: number;
    activeConstraints: KConstraint[];
    solutionObjects: SolutionObject[];
    unsatCore?: string[];
    message?: string;
    createdAt: Date;
    // External debugging
    externalFunctions: ExternalFunction[];
    javaDebugSession?: vscode.DebugSession;
    pythonDebugSession?: vscode.DebugSession;
}

interface KConstraint {
    id: number;
    expression: string;
    type: 'req' | 'property' | 'function' | 'class';
    name?: string;
    className?: string;
    line: number;
    enabled: boolean;
    satisfied?: boolean;
    // External function references
    externalCalls?: ExternalFunction[];
}

interface SolutionObject {
    className: string;
    varName?: string;
    properties: { [key: string]: string };
    ref?: string;
}

/**
 * Manages the unified debug panel with multiple session support
 */
export class KDebugPanel {
    private static instance: KDebugPanel | undefined;
    private panel: vscode.WebviewPanel | undefined;
    private sessions: Map<string, KDebugSession> = new Map();
    private activeSessionId: string | undefined;
    private disposables: vscode.Disposable[] = [];

    // Decoration types for editor highlighting
    private processedDecoration: vscode.TextEditorDecorationType;
    private currentStepDecoration: vscode.TextEditorDecorationType;
    private unsatDecoration: vscode.TextEditorDecorationType;

    private constructor(private context: vscode.ExtensionContext) {
        this.processedDecoration = vscode.window.createTextEditorDecorationType({
            backgroundColor: 'rgba(76, 175, 80, 0.15)',
            isWholeLine: true
        });
        this.currentStepDecoration = vscode.window.createTextEditorDecorationType({
            backgroundColor: 'rgba(33, 150, 243, 0.25)',
            isWholeLine: true,
            borderWidth: '0 0 0 3px',
            borderStyle: 'solid',
            borderColor: '#2196f3'
        });
        this.unsatDecoration = vscode.window.createTextEditorDecorationType({
            backgroundColor: 'rgba(244, 67, 54, 0.2)',
            isWholeLine: true
        });
    }

    public static getInstance(context: vscode.ExtensionContext): KDebugPanel {
        if (!KDebugPanel.instance) {
            KDebugPanel.instance = new KDebugPanel(context);
        }
        return KDebugPanel.instance;
    }

    /**
     * Create or show the debug panel
     */
    public show(): void {
        if (this.panel) {
            this.panel.reveal();
            return;
        }

        this.panel = vscode.window.createWebviewPanel(
            'kDebugPanel',
            'K Debug',
            vscode.ViewColumn.Beside,
            {
                enableScripts: true,
                retainContextWhenHidden: true
            }
        );

        this.panel.onDidDispose(() => {
            this.panel = undefined;
        }, null, this.disposables);

        this.panel.webview.onDidReceiveMessage(
            message => this.handleMessage(message),
            null,
            this.disposables
        );

        this.updatePanel();
    }

    /**
     * Start a new debug session for a document
     */
    public async startSession(document: vscode.TextDocument): Promise<void> {
        const sessionId = `session-${Date.now()}`;
        const fileName = path.basename(document.fileName);

        // Parse external function imports first
        const externalFunctions = this.parseExternalFunctions(document);
        const constraints = this.parseConstraints(document);

        // Analyze each constraint for external function calls
        for (const constraint of constraints) {
            this.analyzeConstraintForExternalCalls(constraint, externalFunctions);
        }

        const session: KDebugSession = {
            id: sessionId,
            fileName,
            filePath: document.fileName,
            document,
            status: 'idle',
            constraints,
            currentStep: 0,
            activeConstraints: [],
            solutionObjects: [],
            createdAt: new Date(),
            externalFunctions
        };

        this.sessions.set(sessionId, session);
        this.activeSessionId = sessionId;

        this.show();
        this.updatePanel();
        this.updateDecorations();

        // Run initial solve
        await this.runSolver(sessionId);
    }

    /**
     * Get the active session
     */
    public getActiveSession(): KDebugSession | undefined {
        if (!this.activeSessionId) return undefined;
        return this.sessions.get(this.activeSessionId);
    }

    /**
     * Switch to a different session
     */
    public switchSession(sessionId: string): void {
        if (this.sessions.has(sessionId)) {
            this.activeSessionId = sessionId;
            this.updatePanel();
            this.updateDecorations();
        }
    }

    /**
     * Close a session
     */
    public closeSession(sessionId: string): void {
        this.sessions.delete(sessionId);
        if (this.activeSessionId === sessionId) {
            const nextSession = this.sessions.keys().next();
            this.activeSessionId = nextSession.done ? undefined : nextSession.value;
        }
        this.updatePanel();
        this.clearDecorations();
    }

    /**
     * Step to the next constraint
     */
    public async stepNext(): Promise<void> {
        const session = this.getActiveSession();
        if (!session || session.currentStep >= session.constraints.length) return;

        const constraint = session.constraints[session.currentStep];
        session.activeConstraints.push(constraint);
        session.currentStep++;

        await this.runSolver(session.id);
        this.updatePanel();
        this.updateDecorations();
    }

    /**
     * Step to the previous constraint
     */
    public async stepPrev(): Promise<void> {
        const session = this.getActiveSession();
        if (!session || session.currentStep <= 0) return;

        session.currentStep--;
        session.activeConstraints.pop();

        await this.runSolver(session.id);
        this.updatePanel();
        this.updateDecorations();
    }

    /**
     * Run all constraints
     */
    public async runAll(): Promise<void> {
        const session = this.getActiveSession();
        if (!session) return;

        session.currentStep = session.constraints.length;
        session.activeConstraints = [...session.constraints];

        await this.runSolver(session.id);
        this.updatePanel();
        this.updateDecorations();
    }

    /**
     * Toggle a constraint's enabled state
     */
    public async toggleConstraint(index: number): Promise<void> {
        const session = this.getActiveSession();
        if (!session || index < 0 || index >= session.constraints.length) return;

        session.constraints[index].enabled = !session.constraints[index].enabled;
        await this.runSolver(session.id);
        this.updatePanel();
    }

    /**
     * Run the K solver for a session
     */
    private async runSolver(sessionId: string): Promise<void> {
        const session = this.sessions.get(sessionId);
        if (!session) return;

        session.status = 'running';
        this.updatePanel();

        try {
            const output = await this.executeKSolver(session.filePath);
            const result = this.parseSolverOutput(output);

            session.status = result.status;
            session.solutionObjects = result.objects;
            session.unsatCore = result.unsatCore;
            session.message = result.message;
        } catch (error) {
            session.status = 'error';
            session.message = error instanceof Error ? error.message : String(error);
        }

        this.updatePanel();
    }

    /**
     * Execute the K solver
     */
    private executeKSolver(filePath: string): Promise<string> {
        return new Promise((resolve, reject) => {
            const kScript = this.findKScript();
            if (!kScript) {
                reject(new Error('K installation not found'));
                return;
            }

            const env = { ...process.env };
            const javaHome = this.findJavaHome();
            if (javaHome) {
                env.JAVA_HOME = javaHome;
                env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
            }

            const child = cp.spawn(kScript, [filePath], {
                cwd: path.dirname(path.dirname(kScript)),
                env,
                shell: true
            });

            let output = '';
            child.stdout?.on('data', (data: Buffer) => output += data.toString());
            child.stderr?.on('data', (data: Buffer) => output += data.toString());
            child.on('close', () => resolve(output));
            child.on('error', reject);

            setTimeout(() => {
                child.kill();
                reject(new Error('Solver timeout'));
            }, 60000);
        });
    }

    private findKScript(): string | undefined {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (workspaceFolder) {
            const kScript = path.join(workspaceFolder.uri.fsPath, 'export', 'k');
            if (require('fs').existsSync(kScript)) {
                return kScript;
            }
        }
        return undefined;
    }

    private findJavaHome(): string | undefined {
        const sdkmanJava = path.join(process.env.HOME || '', '.sdkman', 'candidates', 'java', 'current');
        if (require('fs').existsSync(sdkmanJava)) {
            return sdkmanJava;
        }
        return process.env.JAVA_HOME;
    }

    /**
     * Parse solver output
     */
    private parseSolverOutput(output: string): {
        status: 'sat' | 'unsat' | 'error';
        objects: SolutionObject[];
        unsatCore?: string[];
        message?: string;
    } {
        const objects: SolutionObject[] = [];
        let status: 'sat' | 'unsat' | 'error' = 'sat';
        let unsatCore: string[] | undefined;
        let message: string | undefined;

        // Check for errors
        if (output.includes('Exception') || output.includes('Error')) {
            status = 'error';
            message = output.match(/Exception.*$/m)?.[0] || 'Unknown error';
        }

        // Check for UNSAT
        if (output.includes('UNSAT') || output.includes('unsatisfiable')) {
            status = 'unsat';
            // Try to extract unsat core
            const coreMatch = output.match(/Unsat core:([^]*?)(?=\n\n|$)/);
            if (coreMatch) {
                unsatCore = coreMatch[1].split('\n').filter(l => l.trim());
            }
        }

        // Parse objects table
        const lines = output.split('\n');
        for (const line of lines) {
            if (line.includes('|') && /\|Ref \d+\|/.test(line)) {
                const parts = line.split('|');
                if (parts.length >= 4) {
                    const varName = parts[1]?.trim() || undefined;
                    const ref = parts[2]?.trim();
                    const valueStr = parts[3]?.trim() || '';

                    const valueMatch = valueStr.match(/^(\w+)\((.+)\)$/);
                    if (valueMatch) {
                        const className = valueMatch[1];
                        const propsStr = valueMatch[2];
                        const properties: { [key: string]: string } = {};

                        // Parse properties like "prop1::val1, prop2:: Ref 3"
                        const propRegex = /(\w+)\s*::\s*([^,]+?)(?=,\s*\w+\s*::|$)/g;
                        let propMatch;
                        while ((propMatch = propRegex.exec(propsStr)) !== null) {
                            properties[propMatch[1]] = propMatch[2].trim();
                        }

                        objects.push({ className, varName, properties, ref });
                    }
                }
            }
        }

        if (objects.length > 0 && status !== 'error') {
            status = 'sat';
        }

        return { status, objects, unsatCore, message };
    }

    /**
     * Parse constraints from document
     */
    private parseConstraints(document: vscode.TextDocument): KConstraint[] {
        const constraints: KConstraint[] = [];
        const text = document.getText();
        let id = 0;

        // Find class contexts
        const classRanges: { name: string; start: number; end: number }[] = [];
        const classPattern = /^\s*(?:class|assoc)\s+([A-Z][a-zA-Z0-9_]*)[^{]*\{/gm;
        let classMatch;
        while ((classMatch = classPattern.exec(text)) !== null) {
            const startLine = document.positionAt(classMatch.index).line;
            classRanges.push({ name: classMatch[1], start: startLine, end: -1 });
        }

        // Find end of classes
        for (let i = 0; i < classRanges.length; i++) {
            const startPos = document.lineAt(classRanges[i].start).range.start;
            let braceDepth = 0;
            let foundFirst = false;
            for (let line = classRanges[i].start; line < document.lineCount; line++) {
                const lineText = document.lineAt(line).text;
                for (const char of lineText) {
                    if (char === '{') { braceDepth++; foundFirst = true; }
                    if (char === '}') braceDepth--;
                    if (foundFirst && braceDepth === 0) {
                        classRanges[i].end = line;
                        break;
                    }
                }
                if (classRanges[i].end !== -1) break;
            }
        }

        // Find req constraints
        const reqPattern = /^\s*req\s+(?:(\w+)\s*:)?\s*(.+)/gm;
        let reqMatch;
        while ((reqMatch = reqPattern.exec(text)) !== null) {
            const line = document.positionAt(reqMatch.index).line;
            const className = classRanges.find(c => line >= c.start && line <= c.end)?.name;
            constraints.push({
                id: id++,
                type: 'req',
                name: reqMatch[1] || undefined,
                expression: reqMatch[2].trim(),
                className,
                line,
                enabled: true
            });
        }

        return constraints;
    }

    /**
     * Handle messages from the webview
     */
    private handleMessage(message: any): void {
        switch (message.type) {
            case 'switchSession':
                this.switchSession(message.sessionId);
                break;
            case 'closeSession':
                this.closeSession(message.sessionId);
                break;
            case 'stepNext':
                this.stepNext();
                break;
            case 'stepPrev':
                this.stepPrev();
                break;
            case 'runAll':
                this.runAll();
                break;
            case 'toggleConstraint':
                this.toggleConstraint(message.index);
                break;
            case 'refresh':
                this.runSolver(this.activeSessionId!);
                break;
            case 'stepIntoExternal':
                this.handleStepIntoExternal(message.constraintIndex, message.callIndex);
                break;
            case 'goToExternal':
                this.handleGoToExternal(message.constraintIndex, message.callIndex);
                break;
            case 'goToStep':
                this.goToStep(message.step);
                break;
            case 'toggleAutoSolve':
                this.handleToggleAutoSolve(message.enabled);
                break;
        }
    }

    /**
     * Jump to a specific constraint step
     */
    public async goToStep(step: number): Promise<void> {
        const session = this.getActiveSession();
        if (!session) return;

        step = Math.max(0, Math.min(step, session.constraints.length));
        session.currentStep = step;
        session.activeConstraints = session.constraints.slice(0, step);

        await this.runSolver(session.id);
        this.updatePanel();
        this.updateDecorations();
    }

    /**
     * Handle auto-solve toggle from the panel
     */
    private async handleToggleAutoSolve(enabled: boolean): Promise<void> {
        const config = vscode.workspace.getConfiguration('k');
        await config.update('autoSolve.enabled', enabled, vscode.ConfigurationTarget.Workspace);
        await vscode.commands.executeCommand('setContext', 'k.autoSolveEnabled', enabled);
        this.updatePanel();
    }

    /**
     * Check if auto-solve is enabled
     */
    private isAutoSolveEnabled(): boolean {
        return vscode.workspace.getConfiguration('k').get<boolean>('autoSolve.enabled', false);
    }

    /**
     * Handle stepping into an external function
     */
    private async handleStepIntoExternal(constraintIndex: number, callIndex: number): Promise<void> {
        const session = this.getActiveSession();
        if (!session) return;

        const constraint = session.constraints[constraintIndex];
        const func = constraint?.externalCalls?.[callIndex];
        if (!func) return;

        if (func.language === 'java') {
            await this.stepIntoJavaFunction(func);
        } else if (func.language === 'python') {
            await this.stepIntoPythonFunction(func);
        }
    }

    /**
     * Handle navigating to an external function
     */
    private async handleGoToExternal(constraintIndex: number, callIndex: number): Promise<void> {
        const session = this.getActiveSession();
        if (!session) return;

        const constraint = session.constraints[constraintIndex];
        const func = constraint?.externalCalls?.[callIndex];
        if (!func) return;

        await this.goToExternalFunction(func);
    }

    /**
     * Update editor decorations
     */
    private updateDecorations(): void {
        const session = this.getActiveSession();
        const editor = vscode.window.activeTextEditor;
        if (!session || !editor || editor.document.uri.fsPath !== session.filePath) {
            return;
        }

        const processed: vscode.DecorationOptions[] = [];
        const current: vscode.DecorationOptions[] = [];
        const unsat: vscode.DecorationOptions[] = [];

        for (let i = 0; i < session.currentStep && i < session.constraints.length; i++) {
            const c = session.constraints[i];
            processed.push({ range: editor.document.lineAt(c.line).range });
        }

        if (session.currentStep < session.constraints.length) {
            const c = session.constraints[session.currentStep];
            current.push({ range: editor.document.lineAt(c.line).range });
        }

        if (session.unsatCore) {
            for (const core of session.unsatCore) {
                // Try to find the line for this constraint
                const constraint = session.constraints.find(c =>
                    c.expression.includes(core) || (c.name && core.includes(c.name))
                );
                if (constraint) {
                    unsat.push({ range: editor.document.lineAt(constraint.line).range });
                }
            }
        }

        editor.setDecorations(this.processedDecoration, processed);
        editor.setDecorations(this.currentStepDecoration, current);
        editor.setDecorations(this.unsatDecoration, unsat);
    }

    private clearDecorations(): void {
        const editor = vscode.window.activeTextEditor;
        if (editor) {
            editor.setDecorations(this.processedDecoration, []);
            editor.setDecorations(this.currentStepDecoration, []);
            editor.setDecorations(this.unsatDecoration, []);
        }
    }

    /**
     * Update the webview panel
     */
    private updatePanel(): void {
        if (!this.panel) return;
        this.panel.webview.html = this.getWebviewHtml();
    }

    private getWebviewHtml(): string {
        const session = this.getActiveSession();
        const sessions = Array.from(this.sessions.values());

        // Session tabs
        const tabsHtml = sessions.map(s => `
            <button class="session-tab ${s.id === this.activeSessionId ? 'active' : ''}"
                    onclick="switchSession('${s.id}')">
                ${this.escapeHtml(s.fileName)}
                <span class="status-dot ${s.status}"></span>
                <span class="close-btn" onclick="event.stopPropagation(); closeSession('${s.id}')">×</span>
            </button>
        `).join('');

        // Constraints list - with external function indicators
        const constraintsHtml = session ? session.constraints.map((c, i) => {
            const status = i < session.currentStep ? 'processed' : (i === session.currentStep ? 'current' : 'pending');
            const icon = i < session.currentStep ? '✓' : (i === session.currentStep ? '▶' : '○');

            // Generate external call buttons
            const externalCallsHtml = c.externalCalls?.map((call, callIdx) => {
                const langIcon = call.language === 'java' ? '☕' : call.language === 'python' ? '🐍' : '📦';
                const callName = call.methodName ? `${call.name}.${call.methodName}` : call.name;
                return `
                    <span class="external-call" title="External ${call.language} function">
                        <span class="lang-icon">${langIcon}</span>
                        <span class="call-name">${this.escapeHtml(callName)}</span>
                        <button class="step-into-btn" onclick="event.stopPropagation(); stepIntoExternal(${i}, ${callIdx})" 
                                title="Step into ${call.language} debugger">⏎</button>
                        <button class="goto-btn" onclick="event.stopPropagation(); goToExternal(${i}, ${callIdx})"
                                title="Go to source">→</button>
                    </span>
                `;
            }).join('') || '';

            return `
                <div class="constraint ${status} ${c.enabled ? '' : 'disabled'}" onclick="goToStep(${i})">
                    <input type="checkbox" ${c.enabled ? 'checked' : ''} 
                           onclick="event.stopPropagation(); toggleConstraint(${i})">
                    <span class="icon">${icon}</span>
                    <span class="step">${i + 1}</span>
                    ${c.className ? `<span class="class">${c.className}.</span>` : ''}
                    ${c.name ? `<span class="name">${c.name}:</span>` : ''}
                    <span class="expr">${this.escapeHtml(c.expression)}</span>
                    ${externalCallsHtml ? `<div class="external-calls">${externalCallsHtml}</div>` : ''}
                </div>
            `;
        }).join('') : '<div class="empty">No session active</div>';

        // External functions summary
        const externalFuncsHtml = session?.externalFunctions.length ? `
            <div class="section">
                <h3>EXTERNAL IMPORTS</h3>
                <div class="external-funcs">
                    ${session.externalFunctions.map(f => `
                        <div class="external-func">
                            <span class="lang-badge ${f.language}">${f.language.toUpperCase()}</span>
                            <span class="func-name">${f.name}</span>
                            ${f.sourceFile ? `<span class="source-found" title="Source found">✓</span>` : `<span class="source-missing" title="Source not found">?</span>`}
                        </div>
                    `).join('')}
                </div>
            </div>
        ` : '';

        // Solution objects - formatted nicely, filtering out nested objects shown inline
        const topLevelObjects = this.filterTopLevelObjects(session?.solutionObjects || []);
        const objectsHtml = topLevelObjects.map(obj => {
            const propsHtml = Object.entries(obj.properties).map(([k, v]) => {
                // Format ref properties to show inline object if available
                const refMatch = v.match(/^Ref (\d+)$/);
                if (refMatch) {
                    const referencedObj = session?.solutionObjects.find(o => o.ref === v);
                    if (referencedObj) {
                        const nestedProps = Object.entries(referencedObj.properties)
                            .map(([nk, nv]) => `${nk}: ${nv}`)
                            .join(', ');
                        return `<div class="prop"><span class="prop-name">${k}</span>: <span class="prop-ref">${referencedObj.className}(${nestedProps})</span></div>`;
                    }
                }
                return `<div class="prop"><span class="prop-name">${k}</span>: <span class="prop-value">${this.escapeHtml(v)}</span></div>`;
            }).join('');
            return `
                <div class="solution-object">
                    <div class="obj-header">
                        <span class="obj-class">${obj.className}</span>
                        ${obj.varName ? `<span class="obj-var">${obj.varName}</span>` : ''}
                    </div>
                    <div class="obj-props">${propsHtml}</div>
                </div>
            `;
        }).join('') || '<div class="empty">No objects</div>';

        // Unsat core display
        const unsatHtml = session?.unsatCore ? `
            <div class="unsat-panel">
                <h4>⚠ Unsatisfiable - Conflicting Constraints:</h4>
                ${session.unsatCore.map(c => `<div class="unsat-item">${this.escapeHtml(c)}</div>`).join('')}
            </div>
        ` : '';

        return `<!DOCTYPE html>
<html>
<head>
    <style>
        body { 
            font-family: var(--vscode-font-family); 
            padding: 0; 
            margin: 0;
            color: var(--vscode-foreground);
        }
        
        /* Session Tabs - like IntelliJ debug sessions */
        .session-tabs {
            display: flex;
            background: var(--vscode-tab-inactiveBackground);
            border-bottom: 1px solid var(--vscode-panel-border);
            overflow-x: auto;
        }
        .session-tab {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 8px 12px;
            border: none;
            background: transparent;
            color: var(--vscode-foreground);
            cursor: pointer;
            border-bottom: 2px solid transparent;
            white-space: nowrap;
        }
        .session-tab:hover {
            background: var(--vscode-tab-hoverBackground);
        }
        .session-tab.active {
            background: var(--vscode-tab-activeBackground);
            border-bottom-color: var(--vscode-focusBorder);
        }
        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: gray;
        }
        .status-dot.sat { background: #4caf50; }
        .status-dot.unsat { background: #f44336; }
        .status-dot.running { background: #2196f3; animation: pulse 1s infinite; }
        .status-dot.error { background: #ff9800; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
        .close-btn {
            opacity: 0.5;
            font-size: 16px;
            line-height: 1;
        }
        .close-btn:hover { opacity: 1; }
        
        /* Main content */
        .content { padding: 12px; }
        
        /* Controls */
        .controls {
            display: flex;
            gap: 8px;
            margin-bottom: 12px;
            flex-wrap: wrap;
        }
        button {
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            padding: 6px 12px;
            border-radius: 4px;
            cursor: pointer;
        }
        button:hover { background: var(--vscode-button-hoverBackground); }
        button:disabled { opacity: 0.5; cursor: not-allowed; }
        
        /* Status */
        .status-bar {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 8px 12px;
            margin-bottom: 12px;
            border-radius: 4px;
        }
        .status-bar.sat { background: rgba(76, 175, 80, 0.2); }
        .status-bar.unsat { background: rgba(244, 67, 54, 0.2); }
        .status-bar.running { background: rgba(33, 150, 243, 0.2); }
        .status-bar.error { background: rgba(255, 152, 0, 0.2); }
        .auto-solve-toggle {
            display: flex;
            align-items: center;
            gap: 4px;
            font-size: 12px;
            cursor: pointer;
            padding: 4px 8px;
            background: var(--vscode-button-secondaryBackground);
            border-radius: 4px;
        }
        .auto-solve-toggle:hover {
            background: var(--vscode-button-secondaryHoverBackground);
        }
        .auto-solve-toggle input {
            margin: 0;
            cursor: pointer;
        }
        
        /* Sections */
        .section { margin-bottom: 16px; }
        .section h3 { 
            margin: 0 0 8px 0; 
            font-size: 13px; 
            color: var(--vscode-descriptionForeground);
        }
        
        /* Constraints */
        .constraint {
            display: flex;
            align-items: flex-start;
            flex-wrap: wrap;
            gap: 8px;
            padding: 6px 8px;
            border-radius: 4px;
            cursor: pointer;
            font-family: var(--vscode-editor-font-family);
            font-size: 12px;
        }
        .constraint:hover { background: var(--vscode-list-hoverBackground); }
        .constraint.processed { background: rgba(76, 175, 80, 0.1); }
        .constraint.current { 
            background: rgba(33, 150, 243, 0.2);
            border-left: 3px solid #2196f3;
        }
        .constraint.disabled { opacity: 0.4; text-decoration: line-through; }
        .constraint .icon { width: 16px; }
        .constraint .step { color: var(--vscode-descriptionForeground); width: 24px; }
        .constraint .class { color: var(--vscode-symbolIcon-classForeground); }
        .constraint .name { font-weight: bold; }
        .constraint .expr { color: var(--vscode-debugTokenExpression-string); }
        
        /* External function calls */
        .external-calls {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            width: 100%;
            margin-top: 4px;
            padding-left: 48px;
        }
        .external-call {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            background: var(--vscode-badge-background);
            color: var(--vscode-badge-foreground);
            padding: 2px 6px;
            border-radius: 3px;
            font-size: 11px;
        }
        .external-call .lang-icon { font-size: 12px; }
        .external-call .call-name { font-family: var(--vscode-editor-font-family); }
        .external-call .step-into-btn,
        .external-call .goto-btn {
            background: transparent;
            border: none;
            color: inherit;
            cursor: pointer;
            padding: 0 2px;
            font-size: 10px;
            opacity: 0.7;
        }
        .external-call .step-into-btn:hover,
        .external-call .goto-btn:hover {
            opacity: 1;
            background: rgba(255,255,255,0.1);
        }
        
        /* External imports section */
        .external-funcs {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }
        .external-func {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 4px 8px;
            background: var(--vscode-input-background);
            border-radius: 4px;
            font-size: 12px;
        }
        .lang-badge {
            padding: 2px 6px;
            border-radius: 3px;
            font-size: 10px;
            font-weight: bold;
        }
        .lang-badge.java { background: #b07219; color: white; }
        .lang-badge.python { background: #3572A5; color: white; }
        .lang-badge.unknown { background: gray; color: white; }
        .source-found { color: #4caf50; }
        .source-missing { color: #ff9800; }
        
        /* Solution objects */
        .solution-object {
            background: var(--vscode-input-background);
            border: 1px solid var(--vscode-panel-border);
            border-radius: 4px;
            margin-bottom: 8px;
            overflow: hidden;
        }
        .obj-header {
            display: flex;
            justify-content: space-between;
            padding: 6px 10px;
            background: var(--vscode-toolbar-hoverBackground);
        }
        .obj-class { font-weight: bold; color: var(--vscode-symbolIcon-classForeground); }
        .obj-var { font-style: italic; color: var(--vscode-symbolIcon-variableForeground); }
        .obj-props { padding: 6px 10px; }
        .prop { font-size: 12px; padding: 2px 0; }
        .prop-name { color: var(--vscode-symbolIcon-fieldForeground); }
        .prop-value { color: var(--vscode-debugTokenExpression-value); }
        .prop-ref { 
            color: var(--vscode-symbolIcon-classForeground); 
            font-style: italic;
        }
        
        /* Unsat panel */
        .unsat-panel {
            background: rgba(244, 67, 54, 0.1);
            border: 1px solid #f44336;
            border-radius: 4px;
            padding: 10px;
            margin-bottom: 12px;
        }
        .unsat-panel h4 { margin: 0 0 8px 0; color: #f44336; }
        .unsat-item {
            font-family: var(--vscode-editor-font-family);
            font-size: 12px;
            padding: 4px 8px;
            background: rgba(244, 67, 54, 0.1);
            border-radius: 2px;
            margin: 2px 0;
        }
        
        .empty {
            color: var(--vscode-descriptionForeground);
            font-style: italic;
            padding: 8px;
        }
        
        /* Progress bar */
        .progress {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 12px;
        }
        .progress-bar {
            flex: 1;
            height: 6px;
            background: var(--vscode-progressBar-background);
            border-radius: 3px;
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
    <div class="session-tabs">
        ${tabsHtml || '<div class="empty" style="padding:8px">No sessions</div>'}
    </div>
    
    <div class="content">
        ${session ? `
            <div class="status-bar ${session.status}">
                <strong>${session.status.toUpperCase()}</strong>
                ${session.message ? `<span>${this.escapeHtml(session.message)}</span>` : ''}
                <span style="flex:1"></span>
                <label class="auto-solve-toggle">
                    <input type="checkbox" id="autoSolveCheck" onchange="toggleAutoSolve(this.checked)"
                           ${this.isAutoSolveEnabled() ? 'checked' : ''}>
                    Auto-solve
                </label>
            </div>
            
            ${unsatHtml}
            
            <div class="controls">
                <button onclick="stepPrev()" ${session.currentStep === 0 ? 'disabled' : ''}>◀ Prev</button>
                <button onclick="stepNext()" ${session.currentStep >= session.constraints.length ? 'disabled' : ''}>Next ▶</button>
                <button onclick="runAll()">▶▶ Run All</button>
                <button onclick="refresh()">↻ Refresh</button>
            </div>
            
            <div class="progress">
                <span>${session.currentStep}/${session.constraints.length}</span>
                <div class="progress-bar">
                    <div class="progress-fill" style="width: ${session.constraints.length ? (session.currentStep / session.constraints.length * 100) : 0}%"></div>
                </div>
            </div>
            
            ${externalFuncsHtml}
            
            <div class="section">
                <h3>CONSTRAINTS</h3>
                <div class="constraints-list">${constraintsHtml}</div>
            </div>
            
            <div class="section">
                <h3>SOLUTION OBJECTS</h3>
                <div class="objects-list">${objectsHtml}</div>
            </div>
        ` : '<div class="empty">Start a debug session by opening a K file and running "K: Debug Constraints"</div>'}
    </div>
    <script>
        const vscode = acquireVsCodeApi();
        function switchSession(id) { vscode.postMessage({ type: 'switchSession', sessionId: id }); }
        function closeSession(id) { vscode.postMessage({ type: 'closeSession', sessionId: id }); }
        function stepNext() { vscode.postMessage({ type: 'stepNext' }); }
        function stepPrev() { vscode.postMessage({ type: 'stepPrev' }); }
        function runAll() { vscode.postMessage({ type: 'runAll' }); }
        function refresh() { vscode.postMessage({ type: 'refresh' }); }
        function toggleConstraint(idx) { vscode.postMessage({ type: 'toggleConstraint', index: idx }); }
        function goToStep(idx) { vscode.postMessage({ type: 'goToStep', step: idx }); }
        // External function debugging
        function stepIntoExternal(constraintIdx, callIdx) {
            vscode.postMessage({ type: 'stepIntoExternal', constraintIndex: constraintIdx, callIndex: callIdx });
        }
        function goToExternal(constraintIdx, callIdx) {
            vscode.postMessage({ type: 'goToExternal', constraintIndex: constraintIdx, callIndex: callIdx });
        }
        function toggleAutoSolve(enabled) {
            vscode.postMessage({ type: 'toggleAutoSolve', enabled: enabled });
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

    /**
     * Filter to show only top-level objects (not those that are referenced by other objects)
     * This avoids showing the same object twice - once as a nested ref and once standalone
     */
    private filterTopLevelObjects(objects: SolutionObject[]): SolutionObject[] {
        // Collect all refs that are referenced by other objects
        const referencedRefs = new Set<string>();
        for (const obj of objects) {
            for (const value of Object.values(obj.properties)) {
                const refMatch = value.match(/^Ref (\d+)$/);
                if (refMatch) {
                    referencedRefs.add(value);
                }
            }
        }

        // Return objects that are either:
        // 1. Named top-level variables, OR
        // 2. Not referenced by any other object (likely the "root" objects)
        return objects.filter(obj => {
            // Always show named variables
            if (obj.varName) return true;
            // Show objects that aren't nested in another object
            return !referencedRefs.has(obj.ref || '');
        });
    }

    // ========================================================================
    // EXTERNAL FUNCTION DEBUGGING (Java/Python Integration)
    // ========================================================================

    /**
     * Parse K file to find import statements and external function declarations
     * K supports importing Java and Python functions via:
     *   import java com.example.MyClass
     *   import python my_module
     */
    private parseExternalFunctions(document: vscode.TextDocument): ExternalFunction[] {
        const functions: ExternalFunction[] = [];
        const text = document.getText();

        // Parse Java imports: import java com.example.ClassName
        const javaImportPattern = /^\s*import\s+java\s+([\w.]+)/gm;
        let javaMatch;
        while ((javaMatch = javaImportPattern.exec(text)) !== null) {
            const fullClassName = javaMatch[1];
            const parts = fullClassName.split('.');
            const className = parts[parts.length - 1];
            const packagePath = parts.slice(0, -1).join('.');

            functions.push({
                name: className,
                language: 'java',
                className: className,
                packagePath: packagePath,
                sourceFile: this.findJavaSourceFile(packagePath, className)
            });
        }

        // Parse Python imports: import python module_name
        const pythonImportPattern = /^\s*import\s+python\s+([\w.]+)/gm;
        let pythonMatch;
        while ((pythonMatch = pythonImportPattern.exec(text)) !== null) {
            const moduleName = pythonMatch[1];
            functions.push({
                name: moduleName,
                language: 'python',
                packagePath: moduleName,
                sourceFile: this.findPythonSourceFile(moduleName)
            });
        }

        return functions;
    }

    /**
     * Try to find the Java source file for a class
     */
    private findJavaSourceFile(packagePath: string, className: string): string | undefined {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
        if (!workspaceFolder) return undefined;

        // Convert package to path: com.example.MyClass -> com/example/MyClass.java
        const relativePath = packagePath.replace(/\./g, '/') + '/' + className + '.java';

        // Common Java source locations
        const possiblePaths = [
            path.join(workspaceFolder, 'src', 'main', 'java', relativePath),
            path.join(workspaceFolder, 'src', relativePath),
            path.join(workspaceFolder, relativePath),
        ];

        for (const p of possiblePaths) {
            if (fs.existsSync(p)) {
                return p;
            }
        }

        return undefined;
    }

    /**
     * Try to find the Python source file for a module
     */
    private findPythonSourceFile(moduleName: string): string | undefined {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
        if (!workspaceFolder) return undefined;

        // Convert module to path: my_module.submodule -> my_module/submodule.py
        const relativePath = moduleName.replace(/\./g, '/') + '.py';

        const possiblePaths = [
            path.join(workspaceFolder, relativePath),
            path.join(workspaceFolder, 'src', relativePath),
            path.join(workspaceFolder, moduleName + '.py'),
        ];

        for (const p of possiblePaths) {
            if (fs.existsSync(p)) {
                return p;
            }
        }

        return undefined;
    }

    /**
     * Detect external function calls in a constraint expression
     */
    private detectExternalCalls(expression: string, availableFunctions: ExternalFunction[]): ExternalFunction[] {
        const calls: ExternalFunction[] = [];

        for (const func of availableFunctions) {
            // Look for function calls: ClassName.methodName() or moduleName.function()
            const pattern = new RegExp(`\\b${func.name}\\s*\\.\\s*(\\w+)\\s*\\(`, 'g');
            let match;
            while ((match = pattern.exec(expression)) !== null) {
                calls.push({
                    ...func,
                    methodName: match[1]
                });
            }
        }

        return calls;
    }

    /**
     * Step into an external Java function
     * Launches the VS Code Java debugger and sets a breakpoint
     */
    public async stepIntoJavaFunction(func: ExternalFunction): Promise<void> {
        if (func.language !== 'java' || !func.sourceFile) {
            vscode.window.showErrorMessage('Cannot find Java source file for ' + func.name);
            return;
        }

        // Check if Java debugger extension is available
        const javaExtension = vscode.extensions.getExtension('vscjava.vscode-java-debug');
        if (!javaExtension) {
            const install = await vscode.window.showErrorMessage(
                'Java Debugger extension is required for stepping into Java code.',
                'Install Extension'
            );
            if (install) {
                vscode.commands.executeCommand('workbench.extensions.search', 'vscjava.vscode-java-debug');
            }
            return;
        }

        // Open the source file
        const doc = await vscode.workspace.openTextDocument(func.sourceFile);
        const editor = await vscode.window.showTextDocument(doc);

        // Find the method line and set a breakpoint
        if (func.methodName) {
            const methodLine = this.findMethodLine(doc, func.methodName);
            if (methodLine !== undefined) {
                // Add breakpoint
                const bp = new vscode.SourceBreakpoint(
                    new vscode.Location(doc.uri, new vscode.Position(methodLine, 0))
                );
                vscode.debug.addBreakpoints([bp]);

                // Scroll to method
                editor.revealRange(new vscode.Range(methodLine, 0, methodLine + 10, 0));
            }
        }

        // Create debug configuration for Java
        const debugConfig: vscode.DebugConfiguration = {
            type: 'java',
            name: 'K Debug - Java',
            request: 'attach',
            hostName: 'localhost',
            port: 5005, // Standard Java debug port
            projectName: path.basename(vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || '')
        };

        // Show info about connecting
        vscode.window.showInformationMessage(
            'To debug Java code, ensure your K application is running with: ' +
            '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005'
        );

        // Start debug session
        const session = this.getActiveSession();
        if (session) {
            try {
                const started = await vscode.debug.startDebugging(
                    vscode.workspace.workspaceFolders?.[0],
                    debugConfig
                );
                if (started) {
                    session.javaDebugSession = vscode.debug.activeDebugSession;
                }
            } catch (error) {
                console.error('Failed to start Java debugger:', error);
            }
        }
    }

    /**
     * Step into an external Python function
     */
    public async stepIntoPythonFunction(func: ExternalFunction): Promise<void> {
        if (func.language !== 'python' || !func.sourceFile) {
            vscode.window.showErrorMessage('Cannot find Python source file for ' + func.name);
            return;
        }

        // Check if Python debugger extension is available
        const pythonExtension = vscode.extensions.getExtension('ms-python.python');
        if (!pythonExtension) {
            const install = await vscode.window.showErrorMessage(
                'Python extension is required for stepping into Python code.',
                'Install Extension'
            );
            if (install) {
                vscode.commands.executeCommand('workbench.extensions.search', 'ms-python.python');
            }
            return;
        }

        // Open the source file
        const doc = await vscode.workspace.openTextDocument(func.sourceFile);
        const editor = await vscode.window.showTextDocument(doc);

        // Find the function and set a breakpoint
        if (func.methodName) {
            const funcLine = this.findPythonFunctionLine(doc, func.methodName);
            if (funcLine !== undefined) {
                const bp = new vscode.SourceBreakpoint(
                    new vscode.Location(doc.uri, new vscode.Position(funcLine, 0))
                );
                vscode.debug.addBreakpoints([bp]);
                editor.revealRange(new vscode.Range(funcLine, 0, funcLine + 10, 0));
            }
        }

        // Create debug configuration for Python
        const debugConfig: vscode.DebugConfiguration = {
            type: 'python',
            name: 'K Debug - Python',
            request: 'attach',
            connect: {
                host: 'localhost',
                port: 5678 // Standard debugpy port
            }
        };

        vscode.window.showInformationMessage(
            'To debug Python code, ensure your K application is running with debugpy: ' +
            'python -m debugpy --listen 5678 --wait-for-client your_script.py'
        );

        const session = this.getActiveSession();
        if (session) {
            try {
                const started = await vscode.debug.startDebugging(
                    vscode.workspace.workspaceFolders?.[0],
                    debugConfig
                );
                if (started) {
                    session.pythonDebugSession = vscode.debug.activeDebugSession;
                }
            } catch (error) {
                console.error('Failed to start Python debugger:', error);
            }
        }
    }

    /**
     * Find the line number of a Java method in a document
     */
    private findMethodLine(doc: vscode.TextDocument, methodName: string): number | undefined {
        const text = doc.getText();
        // Match method declarations like: public void methodName( or private int methodName(
        const pattern = new RegExp(
            `^\\s*(?:public|private|protected)?\\s*(?:static)?\\s*\\w+\\s+${methodName}\\s*\\(`,
            'gm'
        );
        const match = pattern.exec(text);
        if (match) {
            return doc.positionAt(match.index).line;
        }
        return undefined;
    }

    /**
     * Find the line number of a Python function in a document
     */
    private findPythonFunctionLine(doc: vscode.TextDocument, funcName: string): number | undefined {
        const text = doc.getText();
        // Match function definitions like: def function_name(
        const pattern = new RegExp(`^\\s*def\\s+${funcName}\\s*\\(`, 'gm');
        const match = pattern.exec(text);
        if (match) {
            return doc.positionAt(match.index).line;
        }
        return undefined;
    }

    /**
     * Navigate to an external function definition without starting debugger
     */
    public async goToExternalFunction(func: ExternalFunction): Promise<void> {
        if (!func.sourceFile) {
            vscode.window.showErrorMessage(`Cannot find source file for ${func.name}`);
            return;
        }

        const doc = await vscode.workspace.openTextDocument(func.sourceFile);
        const editor = await vscode.window.showTextDocument(doc);

        // Find and go to the specific method/function if known
        let targetLine: number | undefined;
        if (func.methodName) {
            if (func.language === 'java') {
                targetLine = this.findMethodLine(doc, func.methodName);
            } else if (func.language === 'python') {
                targetLine = this.findPythonFunctionLine(doc, func.methodName);
            }
        }

        if (targetLine !== undefined) {
            const pos = new vscode.Position(targetLine, 0);
            editor.selection = new vscode.Selection(pos, pos);
            editor.revealRange(new vscode.Range(pos, pos), vscode.TextEditorRevealType.InCenter);
        }
    }

    /**
     * Check if a constraint has external function calls and update the UI
     */
    private analyzeConstraintForExternalCalls(constraint: KConstraint, externalFunctions: ExternalFunction[]): void {
        constraint.externalCalls = this.detectExternalCalls(constraint.expression, externalFunctions);
    }

    public dispose(): void {
        // Stop any attached debug sessions
        for (const session of this.sessions.values()) {
            if (session.javaDebugSession) {
                vscode.debug.stopDebugging(session.javaDebugSession);
            }
            if (session.pythonDebugSession) {
                vscode.debug.stopDebugging(session.pythonDebugSession);
            }
        }

        this.panel?.dispose();
        this.disposables.forEach(d => d.dispose());
        this.clearDecorations();
        this.processedDecoration.dispose();
        this.currentStepDecoration.dispose();
        this.unsatDecoration.dispose();
    }
}



