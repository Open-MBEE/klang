import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';

/**
 * Auto-solve controller that automatically runs the K solver
 * after edits with debouncing.
 */
export class KAutoSolveController implements vscode.Disposable {
    private timeout: NodeJS.Timeout | undefined;
    private currentProcess: cp.ChildProcess | undefined;
    private statusBarItem: vscode.StatusBarItem;
    private outputChannel: vscode.OutputChannel;
    private lastSolution: KSolution | undefined;
    private disposables: vscode.Disposable[] = [];

    // Event emitters for solution updates
    private _onSolutionUpdate = new vscode.EventEmitter<KSolution>();
    public readonly onSolutionUpdate = this._onSolutionUpdate.event;

    private _onSolvingStart = new vscode.EventEmitter<void>();
    public readonly onSolvingStart = this._onSolvingStart.event;

    private _onSolvingEnd = new vscode.EventEmitter<KSolution | undefined>();
    public readonly onSolvingEnd = this._onSolvingEnd.event;

    // Auto-solve toggle button
    private autoSolveToggle: vscode.StatusBarItem;

    constructor() {
        // Create status bar item for solve status
        this.statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Right,
            100
        );
        this.statusBarItem.command = 'k.showSolution';
        this.updateStatusBar('idle');
        this.statusBarItem.show();

        // Create auto-solve toggle button
        this.autoSolveToggle = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Right,
            99
        );
        this.autoSolveToggle.command = 'k.toggleAutoSolve';
        this.updateAutoSolveToggle();
        this.autoSolveToggle.show();

        // Listen for config changes to update toggle button
        this.disposables.push(
            vscode.workspace.onDidChangeConfiguration(e => {
                if (e.affectsConfiguration('k.autoSolve.enabled')) {
                    this.updateAutoSolveToggle();
                }
            })
        );

        // Create output channel
        this.outputChannel = vscode.window.createOutputChannel('K Solver');

        // Register event handlers
        this.disposables.push(
            vscode.workspace.onDidChangeTextDocument(e => {
                if (e.document.languageId === 'k' && this.isAutoSolveEnabled()) {
                    this.scheduleAutoSolve(e.document);
                }
            })
        );

        this.disposables.push(
            vscode.workspace.onDidSaveTextDocument(doc => {
                if (doc.languageId === 'k' && this.isAutoSolveEnabled()) {
                    // Solve immediately on save (no debounce)
                    this.solve(doc);
                }
            })
        );
    }

    private isAutoSolveEnabled(): boolean {
        const config = vscode.workspace.getConfiguration('k');
        return config.get<boolean>('autoSolve.enabled', false);
    }

    private getDebounceMs(): number {
        const config = vscode.workspace.getConfiguration('k');
        return config.get<number>('autoSolve.debounceMs', 1000);
    }

    private getTimeoutMs(): number {
        const config = vscode.workspace.getConfiguration('k');
        return config.get<number>('autoSolve.timeoutMs', 5000);
    }

    /**
     * Schedule an auto-solve with debouncing
     */
    public scheduleAutoSolve(document: vscode.TextDocument): void {
        // Clear existing timeout
        if (this.timeout) {
            clearTimeout(this.timeout);
        }

        // Cancel any in-progress solve
        this.cancelCurrentSolve();

        // Update status
        this.updateStatusBar('waiting');

        // Schedule new solve
        this.timeout = setTimeout(() => {
            this.solve(document);
        }, this.getDebounceMs());
    }

    /**
     * Cancel the current solve operation
     */
    public cancelCurrentSolve(): void {
        if (this.currentProcess) {
            this.currentProcess.kill();
            this.currentProcess = undefined;
        }
    }

    /**
     * Run the solver on the given document
     */
    public async solve(document: vscode.TextDocument): Promise<KSolution | undefined> {
        const filePath = document.uri.fsPath;

        // Find K installation
        const kScript = await this.findKScript();
        if (!kScript) {
            this.updateStatusBar('error', 'K not found');
            return undefined;
        }

        // Cancel any existing process
        this.cancelCurrentSolve();

        // Update status
        this.updateStatusBar('solving');
        this._onSolvingStart.fire();

        const startTime = Date.now();

        try {
            const result = await this.runKSolver(kScript, filePath);
            const solution = this.parseSolution(result);

            const elapsed = Date.now() - startTime;
            this.lastSolution = solution;

            if (solution.status === 'sat') {
                this.updateStatusBar('sat', `SAT (${solution.objects.length} objects, ${elapsed}ms)`);
            } else if (solution.status === 'unsat') {
                this.updateStatusBar('unsat', `UNSAT (${elapsed}ms)`);
            } else if (solution.status === 'timeout') {
                this.updateStatusBar('timeout', `Timeout (${elapsed}ms)`);
            } else if (solution.status === 'error') {
                this.updateStatusBar('error', solution.error || 'Error');
            } else {
                this.updateStatusBar('unknown', `Unknown (${elapsed}ms)`);
            }

            this._onSolutionUpdate.fire(solution);
            this._onSolvingEnd.fire(solution);

            return solution;

        } catch (error) {
            const elapsed = Date.now() - startTime;
            this.updateStatusBar('error', `Error (${elapsed}ms)`);
            this._onSolvingEnd.fire(undefined);
            return undefined;
        }
    }

    private runKSolver(kScript: string, filePath: string): Promise<string> {
        return new Promise((resolve, reject) => {
            const kInstallDir = path.dirname(path.dirname(kScript));

            // Set up environment
            const env: NodeJS.ProcessEnv = { ...process.env };
            const javaHome = this.findJavaHome();
            if (javaHome) {
                env.JAVA_HOME = javaHome;
                env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
            }

            // Spawn K process
            this.currentProcess = cp.spawn(kScript, [filePath], {
                cwd: kInstallDir,
                env,
                shell: true
            });

            let stdout = '';
            let stderr = '';

            this.currentProcess.stdout?.on('data', (data) => {
                stdout += data.toString();
            });

            this.currentProcess.stderr?.on('data', (data) => {
                stderr += data.toString();
            });

            // Set timeout
            const timeoutId = setTimeout(() => {
                if (this.currentProcess) {
                    this.currentProcess.kill();
                    resolve('TIMEOUT');
                }
            }, this.getTimeoutMs());

            this.currentProcess.on('close', () => {
                clearTimeout(timeoutId);
                this.currentProcess = undefined;
                resolve(stdout + stderr);
            });

            this.currentProcess.on('error', (err) => {
                clearTimeout(timeoutId);
                this.currentProcess = undefined;
                reject(err);
            });
        });
    }

    /**
     * Parse K solver output into structured solution
     */
    private parseSolution(output: string): KSolution {
        // Check for timeout
        if (output === 'TIMEOUT') {
            return {
                status: 'timeout',
                objects: [],
                constraints: [],
                raw: output
            };
        }

        // Check for type errors
        if (output.includes('does not type check') || output.includes('[TypeChecker]')) {
            const errorMatch = output.match(/\[TypeChecker]\s*(.+)/);
            return {
                status: 'error',
                error: errorMatch ? errorMatch[1] : 'Type error',
                objects: [],
                constraints: [],
                raw: output
            };
        }

        // Check for UNSAT
        if (output.includes('UNSAT') || output.includes('unsatisfiable')) {
            // Try to extract unsat core
            const unsatCore = this.parseUnsatCore(output);
            return {
                status: 'unsat',
                objects: [],
                constraints: [],
                unsatCore,
                raw: output
            };
        }

        // Parse SAT solution
        if (output.includes('SAT') || output.includes('+--------+')) {
            const objects = this.parseObjects(output);
            const constraints = this.generateConstraintForm(objects);
            return {
                status: 'sat',
                objects,
                constraints,
                raw: output
            };
        }

        // Check for successful completion without explicit SAT
        if (output.includes('Completed successfully')) {
            const objects = this.parseObjects(output);
            const constraints = this.generateConstraintForm(objects);
            return {
                status: 'sat',
                objects,
                constraints,
                raw: output
            };
        }

        return {
            status: 'unknown',
            objects: [],
            constraints: [],
            raw: output
        };
    }

    /**
     * Parse objects from K output table
     */
    private parseObjects(output: string): KObject[] {
        const objects: KObject[] = [];

        // Pattern for table rows: |        |Ref N|ClassName(prop1::val1, prop2::val2, ...)|
        const rowPattern = /\|\s*(\w*)\s*\|\s*(Ref \d+)\s*\|([^|]+)\|/g;
        let match;

        while ((match = rowPattern.exec(output)) !== null) {
            const varName = match[1].trim();
            const ref = match[2].trim();
            const valueStr = match[3].trim();

            // Parse the value: ClassName(prop1::val1, prop2::val2, ...)
            const classMatch = valueStr.match(/^(\w+)\((.+)\)$/);
            if (classMatch) {
                const className = classMatch[1];
                const propsStr = classMatch[2];

                // Parse properties
                const properties: { [key: string]: string } = {};
                const propPattern = /(\w+)::([^,)]+)/g;
                let propMatch;
                while ((propMatch = propPattern.exec(propsStr)) !== null) {
                    properties[propMatch[1]] = propMatch[2].trim();
                }

                objects.push({
                    varName: varName || undefined,
                    ref,
                    className,
                    properties
                });
            }
        }

        return objects;
    }

    /**
     * Generate K constraint form from objects
     */
    private generateConstraintForm(objects: KObject[]): string[] {
        const constraints: string[] = [];

        // Create variable names for unnamed refs
        const refToVar = new Map<string, string>();
        let varCounter = 1;

        for (const obj of objects) {
            if (obj.varName) {
                refToVar.set(obj.ref, obj.varName);
            } else {
                const varName = `${obj.className.toLowerCase()}${varCounter++}`;
                refToVar.set(obj.ref, varName);
            }
        }

        // Generate constraints
        for (const obj of objects) {
            const varName = refToVar.get(obj.ref)!;

            // Type declaration
            constraints.push(`${varName} : ${obj.className}`);

            // Property constraints
            for (const [prop, value] of Object.entries(obj.properties)) {
                // Check if value is a reference
                if (value.startsWith('Ref ')) {
                    const refVar = refToVar.get(value);
                    if (refVar) {
                        constraints.push(`${varName}.${prop} = ${refVar}`);
                    } else {
                        constraints.push(`${varName}.${prop} = ${value}`);
                    }
                } else {
                    constraints.push(`${varName}.${prop} = ${value}`);
                }
            }

            constraints.push(''); // Empty line between objects
        }

        return constraints;
    }

    /**
     * Parse UNSAT core from output
     */
    private parseUnsatCore(output: string): string[] {
        const core: string[] = [];

        // Look for unsat core section
        const coreMatch = output.match(/unsat(?:isfiable)?\s+core[:\s]*([\s\S]*?)(?:\n\n|$)/i);
        if (coreMatch) {
            const coreText = coreMatch[1];
            const lines = coreText.split('\n').filter(l => l.trim());
            core.push(...lines);
        }

        return core;
    }

    private updateStatusBar(
        status: 'idle' | 'waiting' | 'solving' | 'sat' | 'unsat' | 'timeout' | 'error' | 'unknown',
        message?: string
    ): void {
        switch (status) {
            case 'idle':
                this.statusBarItem.text = '$(check) K';
                this.statusBarItem.tooltip = 'K Language - Idle';
                this.statusBarItem.backgroundColor = undefined;
                break;
            case 'waiting':
                this.statusBarItem.text = '$(clock) K';
                this.statusBarItem.tooltip = 'K Language - Waiting to solve...';
                this.statusBarItem.backgroundColor = undefined;
                break;
            case 'solving':
                this.statusBarItem.text = '$(sync~spin) K Solving...';
                this.statusBarItem.tooltip = 'K Language - Solving...';
                this.statusBarItem.backgroundColor = undefined;
                break;
            case 'sat':
                this.statusBarItem.text = '$(check) K: SAT';
                this.statusBarItem.tooltip = `K Language - ${message || 'Satisfiable'}`;
                this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.prominentBackground');
                break;
            case 'unsat':
                this.statusBarItem.text = '$(x) K: UNSAT';
                this.statusBarItem.tooltip = `K Language - ${message || 'Unsatisfiable'}`;
                this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
                break;
            case 'timeout':
                this.statusBarItem.text = '$(watch) K: Timeout';
                this.statusBarItem.tooltip = `K Language - ${message || 'Solver timeout'}`;
                this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
                break;
            case 'error':
                this.statusBarItem.text = '$(error) K: Error';
                this.statusBarItem.tooltip = `K Language - ${message || 'Error'}`;
                this.statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
                break;
            default:
                this.statusBarItem.text = '$(question) K';
                this.statusBarItem.tooltip = `K Language - ${message || 'Unknown status'}`;
                this.statusBarItem.backgroundColor = undefined;
        }
    }

    public getLastSolution(): KSolution | undefined {
        return this.lastSolution;
    }

    private updateAutoSolveToggle(): void {
        const enabled = this.isAutoSolveEnabled();
        if (enabled) {
            this.autoSolveToggle.text = '$(debug-start) Auto';
            this.autoSolveToggle.tooltip = 'Auto-Solve: ON (click to disable)';
            this.autoSolveToggle.backgroundColor = new vscode.ThemeColor('statusBarItem.prominentBackground');
        } else {
            this.autoSolveToggle.text = '$(debug-pause) Auto';
            this.autoSolveToggle.tooltip = 'Auto-Solve: OFF (click to enable)';
            this.autoSolveToggle.backgroundColor = undefined;
        }
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

        // Check workspace
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
        // Check for SDKMAN
        const home = process.env.HOME || '';
        const sdkmanJava = path.join(home, '.sdkman', 'candidates', 'java', 'current');
        if (fs.existsSync(sdkmanJava)) {
            return sdkmanJava;
        }

        // Check JAVA_HOME
        if (process.env.JAVA_HOME && fs.existsSync(process.env.JAVA_HOME)) {
            return process.env.JAVA_HOME;
        }

        return undefined;
    }

    public dispose(): void {
        this.cancelCurrentSolve();
        if (this.timeout) {
            clearTimeout(this.timeout);
        }
        this.statusBarItem.dispose();
        this.autoSolveToggle.dispose();
        this.outputChannel.dispose();
        this._onSolutionUpdate.dispose();
        this._onSolvingStart.dispose();
        this._onSolvingEnd.dispose();
        this.disposables.forEach(d => d.dispose());
    }
}

/**
 * Represents a K solution
 */
export interface KSolution {
    status: 'sat' | 'unsat' | 'timeout' | 'error' | 'unknown';
    objects: KObject[];
    constraints: string[];
    unsatCore?: string[];
    error?: string;
    raw: string;
}

/**
 * Represents an object in a K solution
 */
export interface KObject {
    varName?: string;
    ref: string;
    className: string;
    properties: { [key: string]: string };
}

