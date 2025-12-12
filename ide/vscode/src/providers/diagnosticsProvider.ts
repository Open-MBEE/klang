import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';

/**
 * Provides real-time diagnostics for K files by running the type checker
 */
export class KDiagnosticsProvider {
    private diagnosticCollection: vscode.DiagnosticCollection;
    private timeout: NodeJS.Timeout | undefined;

    constructor() {
        this.diagnosticCollection = vscode.languages.createDiagnosticCollection('k');
    }

    /**
     * Schedule diagnostics update (debounced)
     */
    public scheduleDiagnostics(document: vscode.TextDocument): void {
        if (document.languageId !== 'k') {
            return;
        }

        if (this.timeout) {
            clearTimeout(this.timeout);
        }

        // Debounce - wait 500ms after last change before running diagnostics
        this.timeout = setTimeout(() => {
            this.updateDiagnostics(document);
        }, 500);
    }

    /**
     * Run the K type checker and update diagnostics
     */
    private async updateDiagnostics(document: vscode.TextDocument): Promise<void> {
        const filePath = document.uri.fsPath;

        // Find K installation
        const kScript = await this.findKScript();
        if (!kScript) {
            return; // K not installed, skip diagnostics
        }

        const kInstallDir = path.dirname(path.dirname(kScript));

        // Run K with type-check-only flag (if available) or parse the output
        const env: NodeJS.ProcessEnv = { ...process.env };
        const javaHome = this.findJavaHome();
        if (javaHome) {
            env.JAVA_HOME = javaHome;
            env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
        }

        try {
            const result = await this.runKTypeCheck(kScript, filePath, kInstallDir, env);
            const diagnostics = this.parseErrors(result, document);
            this.diagnosticCollection.set(document.uri, diagnostics);
        } catch (error) {
            // If K fails to run, clear diagnostics
            this.diagnosticCollection.set(document.uri, []);
        }
    }

    private runKTypeCheck(
        kScript: string,
        filePath: string,
        cwd: string,
        env: NodeJS.ProcessEnv
    ): Promise<string> {
        return new Promise((resolve, reject) => {
            const child = cp.spawn(kScript, [filePath], {
                cwd,
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
                // Return combined output for parsing
                resolve(stdout + stderr);
            });

            child.on('error', reject);

            // Timeout after 10 seconds
            setTimeout(() => {
                child.kill();
                reject(new Error('Type check timeout'));
            }, 10000);
        });
    }

    /**
     * Parse K compiler/type checker output to extract errors
     */
    private parseErrors(output: string, document: vscode.TextDocument): vscode.Diagnostic[] {
        const diagnostics: vscode.Diagnostic[] = [];
        const lines = output.split('\n');
        const text = document.getText();

        // Pattern for type errors with line numbers
        const linePatterns = [
            /(?:Error|error|ERROR)\s+(?:at\s+)?line\s+(\d+)(?:,?\s*(?:column|col)\s*(\d+))?[:\s]+(.+)/i,
            /\[error\]\s+[^:]+:(\d+):(\d+):\s*(.+)/i,
            /Type\s+error\s+.*?(?:at\s+)?line\s+(\d+)[:\s]+(.+)/i,
            /(\d+):(\d+):\s*error:\s*(.+)/i,
        ];

        // Pattern for K TypeChecker errors: [TypeChecker] expr does not type check. Type1 and Type2 are not equivalent.
        const typeCheckerPattern = /\[TypeChecker\]\s+(.+?)\s+does not type check\.\s*(.+)/;

        for (const line of lines) {
            // Check for "Type checking completed. No errors found." first
            if (line.includes('Type checking completed') && line.includes('No errors')) {
                return [];
            }

            // Try line-based patterns first
            let matched = false;
            for (const pattern of linePatterns) {
                const match = line.match(pattern);
                if (match) {
                    const lineNum = parseInt(match[1], 10) - 1;
                    const colNum = match[2] ? parseInt(match[2], 10) - 1 : 0;
                    const message = match[3] || match[2] || 'Unknown error';

                    if (lineNum >= 0 && lineNum < document.lineCount) {
                        const range = new vscode.Range(
                            lineNum,
                            colNum,
                            lineNum,
                            document.lineAt(lineNum).text.length
                        );

                        diagnostics.push(new vscode.Diagnostic(
                            range,
                            message.trim(),
                            vscode.DiagnosticSeverity.Error
                        ));
                    }
                    matched = true;
                    break;
                }
            }

            if (matched) continue;

            // Try TypeChecker pattern (no line number - we'll search for the expression)
            const tcMatch = line.match(typeCheckerPattern);
            if (tcMatch) {
                const expression = tcMatch[1].trim();
                const details = tcMatch[2].trim();
                const message = `${expression}: ${details}`;

                // Try to find the expression in the source code
                const location = this.findExpressionInSource(expression, text, document);

                diagnostics.push(new vscode.Diagnostic(
                    location,
                    message,
                    vscode.DiagnosticSeverity.Error
                ));
            }
        }

        return diagnostics;
    }

    /**
     * Try to locate an expression in the source code
     */
    private findExpressionInSource(
        expression: string,
        text: string,
        document: vscode.TextDocument
    ): vscode.Range {
        // Normalize expression (remove extra spaces)
        const normalizedExpr = expression.replace(/\s+/g, ' ').trim();

        // Try to find exact match first
        let index = text.indexOf(normalizedExpr);

        // If not found, try variations
        if (index === -1) {
            // Try without spaces around operators
            const compactExpr = expression.replace(/\s+/g, '');
            index = text.replace(/\s+/g, '').indexOf(compactExpr);
            if (index !== -1) {
                // Re-find in original to get correct position
                // This is approximate - find first occurrence of key parts
                const parts = expression.split(/\s+/);
                for (const part of parts) {
                    if (part.length > 1 && !/^[<>=!&|]+$/.test(part)) {
                        const partIndex = text.indexOf(part);
                        if (partIndex !== -1) {
                            index = partIndex;
                            break;
                        }
                    }
                }
            }
        }

        if (index !== -1) {
            const pos = document.positionAt(index);
            const endPos = document.positionAt(index + normalizedExpr.length);
            return new vscode.Range(pos, endPos);
        }

        // Fall back to first line if expression not found
        return new vscode.Range(0, 0, 0, document.lineAt(0).text.length);
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

        // Check common locations
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

    public clear(document: vscode.TextDocument): void {
        this.diagnosticCollection.delete(document.uri);
    }

    public dispose(): void {
        if (this.timeout) {
            clearTimeout(this.timeout);
        }
        this.diagnosticCollection.dispose();
    }
}

