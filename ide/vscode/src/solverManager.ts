import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as path from 'path';
import * as fs from 'fs';

/**
 * Progress information from the solver
 */
export interface SolverProgress {
    phase: 'parsing' | 'typechecking' | 'translating' | 'solving' | 'cegar' | 'complete';
    iteration?: number;
    satisfiedConstraints?: number;
    totalConstraints?: number;
    partialSolution?: { [key: string]: string | number };
    unresolvedRanges?: { [key: string]: [number, number] };
    elapsedMs: number;
    message?: string;
}

/**
 * Result from the solver
 */
export interface SolverResult {
    status: 'sat' | 'unsat' | 'timeout' | 'error' | 'cancelled';
    solution?: any;
    unsatCore?: string[];
    error?: string;
    raw: string;
    progress: SolverProgress[];
}

/**
 * Manages long-running solve operations with progress reporting,
 * cancellation, and partial solution handling.
 */
export class KSolverManager implements vscode.Disposable {

    private currentProcess: cp.ChildProcess | undefined;
    private progressCallback: ((progress: SolverProgress) => void) | undefined;
    private startTime: number = 0;
    private progressHistory: SolverProgress[] = [];
    private outputChannel: vscode.OutputChannel;
    private statusBarItem: vscode.StatusBarItem;
    private cancellationTokenSource: vscode.CancellationTokenSource | undefined;

    constructor() {
        this.outputChannel = vscode.window.createOutputChannel('K Solver Progress');
        this.statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Left,
            50
        );
    }

    /**
     * Run the solver with progress reporting
     */
    public async solve(
        filePath: string,
        options: {
            timeout?: number;
            showProgress?: boolean;
            onProgress?: (progress: SolverProgress) => void;
        } = {}
    ): Promise<SolverResult> {

        const timeout = options.timeout || 60000; // Default 60 seconds
        const showProgress = options.showProgress !== false;

        this.progressCallback = options.onProgress;
        this.progressHistory = [];
        this.startTime = Date.now();

        // Create cancellation token
        this.cancellationTokenSource = new vscode.CancellationTokenSource();

        if (showProgress) {
            return this.solveWithProgressUI(filePath, timeout);
        } else {
            return this.solveInternal(filePath, timeout);
        }
    }

    /**
     * Solve with VS Code progress UI
     */
    private solveWithProgressUI(filePath: string, timeout: number): Promise<SolverResult> {
        return new Promise((resolve, reject) => {
            vscode.window.withProgress({
                location: vscode.ProgressLocation.Notification,
                title: `Solving ${path.basename(filePath)}`,
                cancellable: true
            }, async (progress, token) => {

                // Link cancellation
                token.onCancellationRequested(() => {
                    this.cancel();
                });

                // Set up progress reporting
                let lastPercent = 0;
                const originalCallback = this.progressCallback;

                this.progressCallback = (p: SolverProgress) => {
                    // Update VS Code progress
                    const percent = p.totalConstraints
                        ? Math.round((p.satisfiedConstraints || 0) / p.totalConstraints * 100)
                        : undefined;

                    if (percent !== undefined && percent > lastPercent) {
                        progress.report({
                            increment: percent - lastPercent,
                            message: this.formatProgressMessage(p)
                        });
                        lastPercent = percent;
                    } else {
                        progress.report({
                            message: this.formatProgressMessage(p)
                        });
                    }

                    // Update status bar
                    this.updateStatusBar(p);

                    // Call original callback if provided
                    if (originalCallback) {
                        originalCallback(p);
                    }
                };

                try {
                    const result = await this.solveInternal(filePath, timeout);
                    this.statusBarItem.hide();
                    resolve(result);
                } catch (error) {
                    this.statusBarItem.hide();
                    reject(error);
                }
            });
        });
    }

    /**
     * Internal solve implementation
     */
    private async solveInternal(filePath: string, timeout: number): Promise<SolverResult> {
        const kScript = await this.findKScript();
        if (!kScript) {
            return {
                status: 'error',
                error: 'K installation not found',
                raw: '',
                progress: this.progressHistory
            };
        }

        return new Promise((resolve) => {
            const kInstallDir = path.dirname(path.dirname(kScript));

            // Set up environment
            const env: NodeJS.ProcessEnv = { ...process.env };
            const javaHome = this.findJavaHome();
            if (javaHome) {
                env.JAVA_HOME = javaHome;
                env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
            }

            // Report initial progress
            this.reportProgress({
                phase: 'parsing',
                elapsedMs: Date.now() - this.startTime,
                message: 'Starting...'
            });

            // Spawn K process
            this.currentProcess = cp.spawn(kScript, [filePath], {
                cwd: kInstallDir,
                env,
                shell: true
            });

            let stdout = '';
            let stderr = '';

            this.currentProcess.stdout?.on('data', (data: string | Buffer) => {
                const chunk = data.toString();
                stdout += chunk;
                this.parseProgressFromOutput(chunk);
            });

            this.currentProcess.stderr?.on('data', (data: string | Buffer) => {
                stderr += data.toString();
            });

            // Set timeout
            const timeoutId = setTimeout(() => {
                if (this.currentProcess) {
                    this.reportProgress({
                        phase: 'complete',
                        elapsedMs: Date.now() - this.startTime,
                        message: 'Timeout - returning partial results'
                    });
                    this.currentProcess.kill();
                    resolve({
                        status: 'timeout',
                        raw: stdout + stderr,
                        progress: this.progressHistory
                    });
                }
            }, timeout);

            // Handle cancellation
            this.cancellationTokenSource?.token.onCancellationRequested(() => {
                clearTimeout(timeoutId);
                if (this.currentProcess) {
                    this.currentProcess.kill();
                    resolve({
                        status: 'cancelled',
                        raw: stdout + stderr,
                        progress: this.progressHistory
                    });
                }
            });

            this.currentProcess.on('close', () => {
                clearTimeout(timeoutId);
                this.currentProcess = undefined;

                this.reportProgress({
                    phase: 'complete',
                    elapsedMs: Date.now() - this.startTime,
                    message: 'Complete'
                });

                const result = this.parseResult(stdout + stderr);
                result.progress = this.progressHistory;
                resolve(result);
            });

            this.currentProcess.on('error', (err) => {
                clearTimeout(timeoutId);
                this.currentProcess = undefined;
                resolve({
                    status: 'error',
                    error: err.message,
                    raw: stdout + stderr,
                    progress: this.progressHistory
                });
            });
        });
    }

    /**
     * Cancel the current solve operation
     */
    public cancel(): void {
        if (this.cancellationTokenSource) {
            this.cancellationTokenSource.cancel();
        }
        if (this.currentProcess) {
            this.currentProcess.kill();
            this.currentProcess = undefined;
        }
    }

    /**
     * Pause the current solve (if supported)
     * Returns the current partial state
     */
    public async pause(): Promise<SolverProgress | undefined> {
        // For now, we can't truly pause Z3, but we can capture state
        // In future, this could use incremental solving checkpoints
        if (this.progressHistory.length > 0) {
            return this.progressHistory[this.progressHistory.length - 1];
        }
        return undefined;
    }

    /**
     * Parse progress information from solver output
     */
    private parseProgressFromOutput(output: string): void {
        const elapsed = Date.now() - this.startTime;

        // Detect phase changes from output
        if (output.includes('Processing')) {
            this.reportProgress({
                phase: 'parsing',
                elapsedMs: elapsed,
                message: 'Parsing...'
            });
        } else if (output.includes('Type checking')) {
            this.reportProgress({
                phase: 'typechecking',
                elapsedMs: elapsed,
                message: 'Type checking...'
            });
        } else if (output.includes('PARSE TREE')) {
            this.reportProgress({
                phase: 'translating',
                elapsedMs: elapsed,
                message: 'Translating to SMT...'
            });
        } else if (output.includes('(check-sat)') || output.includes('Solving')) {
            this.reportProgress({
                phase: 'solving',
                elapsedMs: elapsed,
                message: 'Solving constraints...'
            });
        }

        // Detect CEGAR iterations
        const cegarMatch = output.match(/CEGAR iteration (\d+)/i);
        if (cegarMatch) {
            this.reportProgress({
                phase: 'cegar',
                iteration: parseInt(cegarMatch[1]),
                elapsedMs: elapsed,
                message: `CEGAR iteration ${cegarMatch[1]}`
            });
        }

        // Detect constraint counts
        const constraintMatch = output.match(/(\d+)\s*\/\s*(\d+)\s*constraints/i);
        if (constraintMatch) {
            this.reportProgress({
                phase: 'solving',
                satisfiedConstraints: parseInt(constraintMatch[1]),
                totalConstraints: parseInt(constraintMatch[2]),
                elapsedMs: elapsed
            });
        }
    }

    /**
     * Report progress update
     */
    private reportProgress(progress: SolverProgress): void {
        this.progressHistory.push(progress);

        if (this.progressCallback) {
            this.progressCallback(progress);
        }

        // Log to output channel
        this.outputChannel.appendLine(
            `[${this.formatElapsed(progress.elapsedMs)}] ${progress.phase}: ${progress.message || ''}`
        );
    }

    /**
     * Parse the final result
     */
    private parseResult(output: string): SolverResult {
        if (output.includes('UNSAT') || output.includes('unsatisfiable')) {
            return {
                status: 'unsat',
                unsatCore: this.parseUnsatCore(output),
                raw: output,
                progress: []
            };
        }

        if (output.includes('+--------+') || output.includes('Completed successfully')) {
            return {
                status: 'sat',
                solution: this.parseSolution(output),
                raw: output,
                progress: []
            };
        }

        if (output.includes('error') || output.includes('Exception')) {
            return {
                status: 'error',
                error: this.extractError(output),
                raw: output,
                progress: []
            };
        }

        return {
            status: 'error',
            error: 'Unknown result',
            raw: output,
            progress: []
        };
    }

    private parseUnsatCore(output: string): string[] {
        const core: string[] = [];
        const coreMatch = output.match(/unsat(?:isfiable)?\s+core[:\s]*([\s\S]*?)(?:\n\n|$)/i);
        if (coreMatch) {
            core.push(...coreMatch[1].split('\n').filter(l => l.trim()));
        }
        return core;
    }

    private parseSolution(output: string): any {
        // Basic solution parsing - returns the table data
        const solution: any = { objects: [] };
        const rowPattern = /\|\s*(\w*)\s*\|\s*(Ref \d+)\s*\|([^|]+)\|/g;
        let match;
        while ((match = rowPattern.exec(output)) !== null) {
            solution.objects.push({
                varName: match[1].trim(),
                ref: match[2].trim(),
                value: match[3].trim()
            });
        }
        return solution;
    }

    private extractError(output: string): string {
        const errorMatch = output.match(/(?:Exception|Error)[:\s]*(.+?)(?:\n|$)/i);
        return errorMatch ? errorMatch[1] : 'Unknown error';
    }

    private formatProgressMessage(p: SolverProgress): string {
        let msg = `${p.phase}`;
        if (p.iteration !== undefined) {
            msg += ` (iteration ${p.iteration})`;
        }
        if (p.satisfiedConstraints !== undefined && p.totalConstraints !== undefined) {
            const pct = Math.round(p.satisfiedConstraints / p.totalConstraints * 100);
            msg += ` - ${pct}%`;
        }
        msg += ` [${this.formatElapsed(p.elapsedMs)}]`;
        return msg;
    }

    private formatElapsed(ms: number): string {
        if (ms < 1000) {
            return `${ms}ms`;
        } else if (ms < 60000) {
            return `${(ms / 1000).toFixed(1)}s`;
        } else {
            const mins = Math.floor(ms / 60000);
            const secs = Math.round((ms % 60000) / 1000);
            return `${mins}m ${secs}s`;
        }
    }

    private updateStatusBar(p: SolverProgress): void {
        let icon = '$(sync~spin)';
        if (p.phase === 'complete') {
            icon = '$(check)';
        } else if (p.phase === 'cegar') {
            icon = '$(debug-step-into)';
        }

        this.statusBarItem.text = `${icon} K: ${this.formatProgressMessage(p)}`;
        this.statusBarItem.show();
    }

    private async findKScript(): Promise<string | undefined> {
        const config = vscode.workspace.getConfiguration('k');
        const configuredPath = config.get<string>('installation.path');

        if (configuredPath) {
            const candidates = [
                path.join(configuredPath, 'export', 'k'),
                path.join(configuredPath, 'k'),
                path.join(configuredPath, 'bin', 'k')
            ];
            for (const candidate of candidates) {
                if (fs.existsSync(candidate)) {
                    return candidate;
                }
            }
        }

        const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
        if (workspaceRoot) {
            const wsCandidate = path.join(workspaceRoot, 'export', 'k');
            if (fs.existsSync(wsCandidate)) {
                return wsCandidate;
            }
        }

        return undefined;
    }

    private findJavaHome(): string | undefined {
        const home = process.env.HOME || '';
        const sdkmanJava = path.join(home, '.sdkman', 'candidates', 'java', 'current');
        if (fs.existsSync(sdkmanJava)) {
            return sdkmanJava;
        }
        if (process.env.JAVA_HOME && fs.existsSync(process.env.JAVA_HOME)) {
            return process.env.JAVA_HOME;
        }
        return undefined;
    }

    public dispose(): void {
        this.cancel();
        this.outputChannel.dispose();
        this.statusBarItem.dispose();
        this.cancellationTokenSource?.dispose();
    }
}

/**
 * Creates a progress panel webview showing detailed solve progress
 */
export class KProgressPanel {
    public static currentPanel: KProgressPanel | undefined;
    private readonly panel: vscode.WebviewPanel;
    private disposables: vscode.Disposable[] = [];

    private constructor(panel: vscode.WebviewPanel) {
        this.panel = panel;
        this.panel.onDidDispose(() => this.dispose(), null, this.disposables);
    }

    public static createOrShow(extensionUri: vscode.Uri): KProgressPanel {
        const column = vscode.ViewColumn.Beside;

        if (KProgressPanel.currentPanel) {
            KProgressPanel.currentPanel.panel.reveal(column);
            return KProgressPanel.currentPanel;
        }

        const panel = vscode.window.createWebviewPanel(
            'kProgress',
            'K Solver Progress',
            column,
            {
                enableScripts: true
            }
        );

        KProgressPanel.currentPanel = new KProgressPanel(panel);
        KProgressPanel.currentPanel.updateContent([]);
        return KProgressPanel.currentPanel;
    }

    public updateContent(progressHistory: SolverProgress[]): void {
        this.panel.webview.html = this.getHtml(progressHistory);
    }

    public updateProgress(progress: SolverProgress): void {
        // Send progress update to webview
        this.panel.webview.postMessage({
            type: 'progress',
            data: progress
        });
    }

    private getHtml(progressHistory: SolverProgress[]): string {
        const progressItems = progressHistory.map(p => `
            <div class="progress-item ${p.phase}">
                <span class="time">${this.formatElapsed(p.elapsedMs)}</span>
                <span class="phase">${p.phase}</span>
                ${p.iteration !== undefined ? `<span class="iteration">Iteration ${p.iteration}</span>` : ''}
                ${p.message ? `<span class="message">${p.message}</span>` : ''}
                ${p.satisfiedConstraints !== undefined ? `
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: ${Math.round((p.satisfiedConstraints / (p.totalConstraints || 1)) * 100)}%"></div>
                    </div>
                ` : ''}
            </div>
        `).join('');

        return `<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            font-family: var(--vscode-font-family);
            padding: 10px;
            color: var(--vscode-foreground);
        }
        .progress-item {
            padding: 8px;
            margin: 4px 0;
            border-radius: 4px;
            background: var(--vscode-editor-background);
            border-left: 3px solid var(--vscode-textLink-foreground);
        }
        .progress-item.complete {
            border-left-color: var(--vscode-testing-iconPassed);
        }
        .progress-item.cegar {
            border-left-color: var(--vscode-debugIcon-stepOverForeground);
        }
        .time {
            color: var(--vscode-descriptionForeground);
            margin-right: 10px;
        }
        .phase {
            font-weight: bold;
            text-transform: capitalize;
        }
        .iteration {
            margin-left: 10px;
            color: var(--vscode-debugTokenExpression-number);
        }
        .message {
            display: block;
            margin-top: 4px;
            color: var(--vscode-descriptionForeground);
        }
        .progress-bar {
            height: 4px;
            background: var(--vscode-progressBar-background);
            border-radius: 2px;
            margin-top: 8px;
            overflow: hidden;
        }
        .progress-fill {
            height: 100%;
            background: var(--vscode-progressBar-foreground);
            transition: width 0.3s;
        }
        h2 {
            border-bottom: 1px solid var(--vscode-panel-border);
            padding-bottom: 8px;
        }
        .controls {
            margin-bottom: 16px;
        }
        button {
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            padding: 6px 12px;
            border-radius: 4px;
            cursor: pointer;
            margin-right: 8px;
        }
        button:hover {
            background: var(--vscode-button-hoverBackground);
        }
        .partial-solution {
            margin-top: 16px;
            padding: 12px;
            background: var(--vscode-textBlockQuote-background);
            border-radius: 4px;
        }
        .partial-solution h3 {
            margin-top: 0;
        }
        .var-item {
            font-family: var(--vscode-editor-font-family);
            padding: 2px 0;
        }
        .var-name {
            color: var(--vscode-symbolIcon-variableForeground);
        }
        .var-value {
            color: var(--vscode-debugTokenExpression-value);
        }
        .var-range {
            color: var(--vscode-debugTokenExpression-number);
        }
    </style>
</head>
<body>
    <h2>Solver Progress</h2>
    <div class="controls">
        <button onclick="cancel()">⏹ Cancel</button>
        <button onclick="pause()">⏸ Pause</button>
    </div>
    <div id="progress-list">
        ${progressItems}
    </div>
    <div id="partial-solution" class="partial-solution" style="display: none;">
        <h3>Partial Solution</h3>
        <div id="partial-vars"></div>
    </div>
    <script>
        const vscode = acquireVsCodeApi();
        
        window.addEventListener('message', event => {
            const message = event.data;
            if (message.type === 'progress') {
                updateProgress(message.data);
            }
        });
        
        function updateProgress(p) {
            const list = document.getElementById('progress-list');
            const item = document.createElement('div');
            item.className = 'progress-item ' + p.phase;
            item.innerHTML = \`
                <span class="time">\${formatElapsed(p.elapsedMs)}</span>
                <span class="phase">\${p.phase}</span>
                \${p.iteration !== undefined ? '<span class="iteration">Iteration ' + p.iteration + '</span>' : ''}
                \${p.message ? '<span class="message">' + p.message + '</span>' : ''}
            \`;
            list.appendChild(item);
            list.scrollTop = list.scrollHeight;
            
            if (p.partialSolution) {
                showPartialSolution(p.partialSolution, p.unresolvedRanges);
            }
        }
        
        function showPartialSolution(solution, ranges) {
            const container = document.getElementById('partial-solution');
            const varsDiv = document.getElementById('partial-vars');
            container.style.display = 'block';
            
            let html = '';
            for (const [name, value] of Object.entries(solution)) {
                html += '<div class="var-item"><span class="var-name">' + name + '</span> = <span class="var-value">' + value + '</span></div>';
            }
            if (ranges) {
                for (const [name, range] of Object.entries(ranges)) {
                    html += '<div class="var-item"><span class="var-name">' + name + '</span> ∈ <span class="var-range">[' + range[0] + ', ' + range[1] + ']</span></div>';
                }
            }
            varsDiv.innerHTML = html;
        }
        
        function formatElapsed(ms) {
            if (ms < 1000) return ms + 'ms';
            if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
            const mins = Math.floor(ms / 60000);
            const secs = Math.round((ms % 60000) / 1000);
            return mins + 'm ' + secs + 's';
        }
        
        function cancel() {
            vscode.postMessage({ type: 'cancel' });
        }
        
        function pause() {
            vscode.postMessage({ type: 'pause' });
        }
    </script>
</body>
</html>`;
    }

    private formatElapsed(ms: number): string {
        if (ms < 1000) return `${ms}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        const mins = Math.floor(ms / 60000);
        const secs = Math.round((ms % 60000) / 1000);
        return `${mins}m ${secs}s`;
    }

    public dispose(): void {
        KProgressPanel.currentPanel = undefined;
        this.panel.dispose();
        this.disposables.forEach(d => d.dispose());
    }
}

