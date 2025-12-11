import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';

/**
 * Data structure for a solver solution
 */
interface SolverSolution {
    status: 'SAT' | 'UNSAT' | 'UNKNOWN' | 'ERROR';
    objects: SolverObject[];
    statistics: SolverStatistics;
    errors?: string[];
    unsatCore?: string[];
}

interface SolverObject {
    variable: string;
    ref: string;
    className: string;
    properties: { [key: string]: string };
}

interface SolverStatistics {
    packages: number;
    classes: number;
    properties: number;
    functions: number;
    constraints: number;
    solveTime?: string;
}

/**
 * Provides visualization of K solver results
 */
export class KSolutionProvider {
    private panel: vscode.WebviewPanel | undefined;
    private currentSolution: SolverSolution | undefined;

    constructor(private context: vscode.ExtensionContext) {}

    /**
     * Run solver and show results
     */
    async runAndVisualize(fileUri?: vscode.Uri): Promise<void> {
        const uri = fileUri || vscode.window.activeTextEditor?.document.uri;
        if (!uri || !uri.fsPath.endsWith('.k')) {
            vscode.window.showErrorMessage('Please open a K file first');
            return;
        }

        // Show progress
        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: 'Running K Solver',
            cancellable: true
        }, async (progress, token) => {
            progress.report({ message: 'Solving constraints...' });

            try {
                const output = await this.runKSolver(uri.fsPath, token);
                this.currentSolution = this.parseOutput(output);
                this.showSolutionPanel();
            } catch (error) {
                if (token.isCancellationRequested) {
                    vscode.window.showInformationMessage('Solver cancelled');
                } else {
                    vscode.window.showErrorMessage(`Solver error: ${error}`);
                }
            }
        });
    }

    private async runKSolver(filePath: string, token: vscode.CancellationToken): Promise<string> {
        return new Promise((resolve, reject) => {
            const kScript = this.findKScript();
            if (!kScript) {
                reject(new Error('K installation not found'));
                return;
            }

            const env: NodeJS.ProcessEnv = { ...process.env };
            const javaHome = this.findJavaHome();
            if (javaHome) {
                env.JAVA_HOME = javaHome;
                env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
            }

            const kDir = path.dirname(path.dirname(kScript));
            const child = cp.spawn(kScript, [filePath], {
                cwd: kDir,
                env,
                shell: true
            });

            let stdout = '';
            let stderr = '';

            child.stdout?.on('data', (data: Buffer) => {
                stdout += data.toString();
            });

            child.stderr?.on('data', (data: Buffer) => {
                stderr += data.toString();
            });

            child.on('close', (code) => {
                resolve(stdout + stderr);
            });

            child.on('error', reject);

            token.onCancellationRequested(() => {
                child.kill();
                reject(new Error('Cancelled'));
            });

            // Timeout after 60 seconds
            setTimeout(() => {
                child.kill();
                reject(new Error('Solver timeout (60s)'));
            }, 60000);
        });
    }

    private parseOutput(output: string): SolverSolution {
        const solution: SolverSolution = {
            status: 'UNKNOWN',
            objects: [],
            statistics: {
                packages: 0,
                classes: 0,
                properties: 0,
                functions: 0,
                constraints: 0
            }
        };

        const lines = output.split('\n');

        // Parse statistics
        const statsSection = output.match(/STATISTICS:([\s\S]*?)(?:No instance|Extra objects|$)/);
        if (statsSection) {
            const statsText = statsSection[1];
            const packagesMatch = statsText.match(/packages\s*:\s*(\d+)/);
            const classesMatch = statsText.match(/class definitions\s*:\s*(\d+)/);
            const propsMatch = statsText.match(/properties\s*:\s*(\d+)/);
            const funcsMatch = statsText.match(/functions\s*:\s*(\d+)/);
            const constrsMatch = statsText.match(/constraints\s*:\s*(\d+)/);

            if (packagesMatch) solution.statistics.packages = parseInt(packagesMatch[1]);
            if (classesMatch) solution.statistics.classes = parseInt(classesMatch[1]);
            if (propsMatch) solution.statistics.properties = parseInt(propsMatch[1]);
            if (funcsMatch) solution.statistics.functions = parseInt(funcsMatch[1]);
            if (constrsMatch) solution.statistics.constraints = parseInt(constrsMatch[1]);
        }

        // Parse objects table
        const tableMatch = output.match(/\+[-+]+\+([\s\S]*?)\+[-+]+\+[\s\S]*$/);
        if (tableMatch) {
            const tableContent = tableMatch[1];
            const rows = tableContent.split('\n').filter(line => line.includes('|') && !line.match(/^\+[-+]+\+$/));

            for (const row of rows) {
                const cells = row.split('|').map(s => s.trim()).filter(Boolean);
                if (cells.length >= 3) {
                    const variable = cells[0] || '';
                    const ref = cells[1] || '';
                    const valueStr = cells[2] || '';

                    // Parse the value: ClassName(prop1::val1, prop2::val2, ...)
                    const valueMatch = valueStr.match(/^(\w+)\((.*)\)$/);
                    if (valueMatch) {
                        const className = valueMatch[1];
                        const propsStr = valueMatch[2];
                        const properties: { [key: string]: string } = {};

                        // Parse properties
                        const propMatches = propsStr.match(/(\w+)::\s*([^,)]+)/g);
                        if (propMatches) {
                            for (const pm of propMatches) {
                                const [propName, propValue] = pm.split('::').map(s => s.trim());
                                properties[propName] = propValue;
                            }
                        }

                        solution.objects.push({ variable, ref, className, properties });
                    }
                }
            }

            solution.status = 'SAT';
        }

        // Check for UNSAT
        if (output.includes('UNSAT') || output.includes('unsatisfiable')) {
            solution.status = 'UNSAT';

            // Try to extract UNSAT core
            const coreMatch = output.match(/UNSAT core:([\s\S]*?)(?:$|\n\n)/);
            if (coreMatch) {
                solution.unsatCore = coreMatch[1].trim().split('\n').map(s => s.trim()).filter(Boolean);
            }
        }

        // Check for errors
        if (output.includes('Error') || output.includes('Exception')) {
            const errorLines = lines.filter(l => l.includes('Error') || l.includes('Exception'));
            solution.errors = errorLines.slice(0, 5);
            if (!solution.objects.length && solution.status !== 'UNSAT') {
                solution.status = 'ERROR';
            }
        }

        // Check for type checking success with no instances
        if (output.includes('Type checking completed') && output.includes('No errors')) {
            if (!solution.objects.length && solution.status === 'UNKNOWN') {
                solution.status = 'SAT';
            }
        }

        return solution;
    }

    private showSolutionPanel(): void {
        if (!this.currentSolution) {
            return;
        }

        if (this.panel) {
            this.panel.reveal();
        } else {
            this.panel = vscode.window.createWebviewPanel(
                'kSolution',
                'K Solution',
                vscode.ViewColumn.Beside,
                {
                    enableScripts: true,
                    retainContextWhenHidden: true
                }
            );

            this.panel.onDidDispose(() => {
                this.panel = undefined;
            });
        }

        this.panel.webview.html = this.getWebviewContent(this.currentSolution);
    }

    private getWebviewContent(solution: SolverSolution): string {
        const statusColor = {
            'SAT': '#4caf50',
            'UNSAT': '#f44336',
            'UNKNOWN': '#ff9800',
            'ERROR': '#f44336'
        }[solution.status];

        const statusIcon = {
            'SAT': '✓',
            'UNSAT': '✗',
            'UNKNOWN': '?',
            'ERROR': '⚠'
        }[solution.status];

        const objectsHtml = solution.objects.map(obj => `
            <div class="object">
                <div class="object-header">
                    <span class="ref">${obj.ref}</span>
                    <span class="class-name">${obj.className}</span>
                    ${obj.variable ? `<span class="var-name">${obj.variable}</span>` : ''}
                </div>
                <div class="properties">
                    ${Object.entries(obj.properties).map(([key, value]) => `
                        <div class="property">
                            <span class="prop-name">${key}</span>
                            <span class="prop-value">${value}</span>
                        </div>
                    `).join('')}
                </div>
            </div>
        `).join('');

        const errorsHtml = solution.errors?.length ? `
            <div class="errors">
                <h3>Errors</h3>
                ${solution.errors.map(e => `<div class="error">${this.escapeHtml(e)}</div>`).join('')}
            </div>
        ` : '';

        const unsatCoreHtml = solution.unsatCore?.length ? `
            <div class="unsat-core">
                <h3>UNSAT Core (conflicting constraints)</h3>
                ${solution.unsatCore.map(c => `<div class="constraint">${this.escapeHtml(c)}</div>`).join('')}
            </div>
        ` : '';

        return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>K Solution</title>
    <style>
        body {
            font-family: var(--vscode-font-family);
            padding: 20px;
            color: var(--vscode-foreground);
            background-color: var(--vscode-editor-background);
        }
        .status {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 15px;
            border-radius: 8px;
            background-color: ${statusColor}22;
            border-left: 4px solid ${statusColor};
            margin-bottom: 20px;
        }
        .status-icon {
            font-size: 24px;
            color: ${statusColor};
        }
        .status-text {
            font-size: 18px;
            font-weight: bold;
        }
        .statistics {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
            gap: 10px;
            margin-bottom: 20px;
        }
        .stat {
            padding: 10px;
            background-color: var(--vscode-input-background);
            border-radius: 4px;
            text-align: center;
        }
        .stat-value {
            font-size: 24px;
            font-weight: bold;
            color: var(--vscode-textLink-foreground);
        }
        .stat-label {
            font-size: 12px;
            opacity: 0.8;
        }
        h2 {
            border-bottom: 1px solid var(--vscode-panel-border);
            padding-bottom: 10px;
            margin-top: 30px;
        }
        .objects {
            display: flex;
            flex-direction: column;
            gap: 15px;
        }
        .object {
            border: 1px solid var(--vscode-panel-border);
            border-radius: 8px;
            overflow: hidden;
        }
        .object-header {
            display: flex;
            gap: 10px;
            padding: 10px;
            background-color: var(--vscode-input-background);
        }
        .ref {
            padding: 2px 8px;
            background-color: var(--vscode-badge-background);
            color: var(--vscode-badge-foreground);
            border-radius: 4px;
            font-family: monospace;
        }
        .class-name {
            font-weight: bold;
            color: var(--vscode-symbolIcon-classForeground);
        }
        .var-name {
            opacity: 0.7;
            font-style: italic;
        }
        .properties {
            padding: 10px;
        }
        .property {
            display: flex;
            gap: 10px;
            padding: 4px 0;
            border-bottom: 1px dotted var(--vscode-panel-border);
        }
        .property:last-child {
            border-bottom: none;
        }
        .prop-name {
            min-width: 100px;
            font-family: monospace;
            color: var(--vscode-symbolIcon-fieldForeground);
        }
        .prop-value {
            font-family: monospace;
        }
        .errors, .unsat-core {
            margin-top: 20px;
            padding: 15px;
            background-color: #f4433622;
            border-radius: 8px;
        }
        .error, .constraint {
            font-family: monospace;
            padding: 5px;
            margin: 5px 0;
        }
    </style>
</head>
<body>
    <div class="status">
        <span class="status-icon">${statusIcon}</span>
        <span class="status-text">${solution.status === 'SAT' ? 'Satisfiable - Solution Found' : 
            solution.status === 'UNSAT' ? 'Unsatisfiable - No Solution' :
            solution.status === 'ERROR' ? 'Error' : 'Unknown'}</span>
    </div>

    <div class="statistics">
        <div class="stat">
            <div class="stat-value">${solution.statistics.packages}</div>
            <div class="stat-label">Packages</div>
        </div>
        <div class="stat">
            <div class="stat-value">${solution.statistics.classes}</div>
            <div class="stat-label">Classes</div>
        </div>
        <div class="stat">
            <div class="stat-value">${solution.statistics.properties}</div>
            <div class="stat-label">Properties</div>
        </div>
        <div class="stat">
            <div class="stat-value">${solution.statistics.functions}</div>
            <div class="stat-label">Functions</div>
        </div>
        <div class="stat">
            <div class="stat-value">${solution.statistics.constraints}</div>
            <div class="stat-label">Constraints</div>
        </div>
    </div>

    ${errorsHtml}
    ${unsatCoreHtml}

    ${solution.objects.length ? `
        <h2>Objects (${solution.objects.length})</h2>
        <div class="objects">
            ${objectsHtml}
        </div>
    ` : '<p>No instance objects created.</p>'}
</body>
</html>`;
    }

    private findKScript(): string | undefined {
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

        const home = process.env.HOME || '';
        const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || '';

        const commonPaths = [
            path.join(workspaceRoot, 'export', 'k'),
            path.join(path.dirname(workspaceRoot), 'export', 'k'),
            path.join(home, 'klang', 'export', 'k'),
            path.join(home, 'git', 'klang', 'export', 'k'),
        ];

        for (const p of commonPaths) {
            if (fs.existsSync(p)) {
                return p;
            }
        }

        return undefined;
    }

    private findJavaHome(): string | undefined {
        const config = vscode.workspace.getConfiguration('k');
        const configuredJava = config.get<string>('java.home');
        if (configuredJava) {
            return configuredJava;
        }

        const home = process.env.HOME || '';
        const sdkmanCurrent = path.join(home, '.sdkman', 'candidates', 'java', 'current');
        if (fs.existsSync(sdkmanCurrent)) {
            return sdkmanCurrent;
        }

        return process.env.JAVA_HOME;
    }

    private escapeHtml(text: string): string {
        return text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    dispose(): void {
        this.panel?.dispose();
    }
}

