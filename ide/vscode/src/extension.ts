import * as vscode from 'vscode';
import { KDefinitionProvider } from './providers/definitionProvider';
import { KReferenceProvider } from './providers/referenceProvider';
import { KHoverProvider } from './providers/hoverProvider';
import { KDocumentSymbolProvider } from './providers/documentSymbolProvider';
import { KWorkspaceSymbolProvider } from './providers/workspaceSymbolProvider';
import { KDiagnosticsProvider } from './providers/diagnosticsProvider';
import { KCompletionProvider } from './providers/completionProvider';
import { KFormattingProvider, KOnTypeFormattingProvider } from './providers/formattingProvider';
import { KRenameProvider } from './providers/renameProvider';
import { KSolutionProvider } from './providers/solutionProvider';
import { KInlayHintsProvider } from './providers/inlayHintsProvider';
import { KAutoSolveController } from './autoSolve';
import { KInlineDecorations } from './inlineDecorations';
import { KSolverManager, KProgressPanel } from './solverManager';
import { KConstraintDebugger } from './constraintDebugger';
import { KDebugPanel } from './unifiedDebugPanel';
import { runKFile, runKFileWithArgs, runKFileWithDebug, runKFileWithPythonDebug, runKFileWithFullDebug } from './runner';

// Document selector for K language files
const K_MODE: vscode.DocumentSelector = { language: 'k', scheme: 'file' };

export function activate(context: vscode.ExtensionContext) {
    console.log('K Language extension is now active');

    // Initialize Auto-Solve Controller (status bar, live solving)
    const autoSolveController = new KAutoSolveController();
    context.subscriptions.push(autoSolveController);

    // Initialize Inline Decorations (shows values in editor like debugger)
    const inlineDecorations = new KInlineDecorations();
    context.subscriptions.push(inlineDecorations);

    // Initialize Solver Manager (progress reporting, cancellation)
    const solverManager = new KSolverManager();
    context.subscriptions.push(solverManager);

    // Initialize Constraint Debugger (stepping through constraints)
    const constraintDebugger = new KConstraintDebugger();
    context.subscriptions.push(constraintDebugger);

    // Initialize Unified Debug Panel (combines visualizer + debugger with external function support)
    const unifiedDebugPanel = KDebugPanel.getInstance(context);
    context.subscriptions.push(unifiedDebugPanel);

    // Connect auto-solve to inline decorations
    autoSolveController.onSolutionUpdate(solution => {
        inlineDecorations.showSolution(solution);
    });

    // Register Definition Provider (Go to Definition - Ctrl+Click / F12)
    context.subscriptions.push(
        vscode.languages.registerDefinitionProvider(K_MODE, new KDefinitionProvider())
    );

    // Register Reference Provider (Find All References - Shift+F12)
    context.subscriptions.push(
        vscode.languages.registerReferenceProvider(K_MODE, new KReferenceProvider())
    );

    // Register Hover Provider (Show documentation on hover)
    context.subscriptions.push(
        vscode.languages.registerHoverProvider(K_MODE, new KHoverProvider())
    );

    // Register Document Symbol Provider (Outline view / Ctrl+Shift+O)
    context.subscriptions.push(
        vscode.languages.registerDocumentSymbolProvider(K_MODE, new KDocumentSymbolProvider())
    );

    // Register Workspace Symbol Provider (Ctrl+T to search symbols across workspace)
    context.subscriptions.push(
        vscode.languages.registerWorkspaceSymbolProvider(new KWorkspaceSymbolProvider())
    );

    // Register Completion Provider (IntelliSense)
    context.subscriptions.push(
        vscode.languages.registerCompletionItemProvider(K_MODE, new KCompletionProvider(), '.', ':')
    );

    // Register Formatting Provider
    const formattingProvider = new KFormattingProvider();
    context.subscriptions.push(
        vscode.languages.registerDocumentFormattingEditProvider(K_MODE, formattingProvider)
    );
    context.subscriptions.push(
        vscode.languages.registerDocumentRangeFormattingEditProvider(K_MODE, formattingProvider)
    );

    // Register On-Type Formatting Provider
    context.subscriptions.push(
        vscode.languages.registerOnTypeFormattingEditProvider(K_MODE, new KOnTypeFormattingProvider(), '}', '\n')
    );

    // Register Rename Provider
    context.subscriptions.push(
        vscode.languages.registerRenameProvider(K_MODE, new KRenameProvider())
    );

    // Register Inlay Hints Provider (parameter names, type hints)
    context.subscriptions.push(
        vscode.languages.registerInlayHintsProvider(K_MODE, new KInlayHintsProvider())
    );

    // Register Run Commands
    context.subscriptions.push(
        vscode.commands.registerCommand('k.runFile', runKFile)
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.runFileWithArgs', runKFileWithArgs)
    );

    // Register Run with Debug Command (Java debug agent)
    context.subscriptions.push(
        vscode.commands.registerCommand('k.runWithJavaDebug', async () => {
            const process = await runKFileWithDebug();
            if (process) {
                // Wait a moment for the debug agent to start
                await new Promise(resolve => setTimeout(resolve, 1000));

                // Prompt user to attach debugger
                const attach = await vscode.window.showInformationMessage(
                    'K is running with Java debug agent on port 5005. Attach debugger?',
                    'Attach Java Debugger',
                    'Later'
                );

                if (attach === 'Attach Java Debugger') {
                    // Launch Java debugger attach configuration
                    const debugConfig: vscode.DebugConfiguration = {
                        type: 'java',
                        name: 'Attach to K Java',
                        request: 'attach',
                        hostName: 'localhost',
                        port: 5005
                    };
                    await vscode.debug.startDebugging(undefined, debugConfig);
                }
            }
        })
    );

    // Register Run with Python Debug Command
    context.subscriptions.push(
        vscode.commands.registerCommand('k.runWithPythonDebug', async () => {
            const process = await runKFileWithPythonDebug();
            if (process) {
                await new Promise(resolve => setTimeout(resolve, 1500));

                const attach = await vscode.window.showInformationMessage(
                    'K is running with Python debug enabled on port 5678. Attach debugger?',
                    'Attach Python Debugger',
                    'Later'
                );

                if (attach === 'Attach Python Debugger') {
                    const debugConfig: vscode.DebugConfiguration = {
                        type: 'python',
                        name: 'Attach to K Python',
                        request: 'attach',
                        connect: {
                            host: 'localhost',
                            port: 5678
                        }
                    };
                    await vscode.debug.startDebugging(undefined, debugConfig);
                }
            }
        })
    );

    // Register Run with Full Debug (Java + Python)
    context.subscriptions.push(
        vscode.commands.registerCommand('k.runWithFullDebug', async () => {
            const process = await runKFileWithFullDebug();
            if (process) {
                await new Promise(resolve => setTimeout(resolve, 1500));

                const choice = await vscode.window.showInformationMessage(
                    'K is running with Java (5005) and Python (5678) debug enabled.',
                    'Attach Java',
                    'Attach Python',
                    'Attach Both',
                    'Later'
                );

                if (choice === 'Attach Java' || choice === 'Attach Both') {
                    await vscode.debug.startDebugging(undefined, {
                        type: 'java',
                        name: 'Attach to K Java',
                        request: 'attach',
                        hostName: 'localhost',
                        port: 5005
                    });
                }

                if (choice === 'Attach Python' || choice === 'Attach Both') {
                    await vscode.debug.startDebugging(undefined, {
                        type: 'python',
                        name: 'Attach to K Python',
                        request: 'attach',
                        connect: { host: 'localhost', port: 5678 }
                    });
                }
            }
        })
    );

    // Register Auto-Solve Commands
    context.subscriptions.push(
        vscode.commands.registerCommand('k.showSolution', () => {
            const solution = autoSolveController.getLastSolution();
            if (solution) {
                // Show solution in output or panel
                const outputChannel = vscode.window.createOutputChannel('K Solution');
                outputChannel.clear();
                if (solution.status === 'sat') {
                    outputChannel.appendLine('=== K Solution (as constraints) ===\n');
                    solution.constraints.forEach(c => outputChannel.appendLine(c));
                } else if (solution.status === 'unsat') {
                    outputChannel.appendLine('=== UNSATISFIABLE ===\n');
                    if (solution.unsatCore && solution.unsatCore.length > 0) {
                        outputChannel.appendLine('Unsat Core:');
                        solution.unsatCore.forEach(c => outputChannel.appendLine(`  ${c}`));
                    }
                } else {
                    outputChannel.appendLine(`Status: ${solution.status}`);
                    if (solution.error) {
                        outputChannel.appendLine(`Error: ${solution.error}`);
                    }
                }
                outputChannel.appendLine('\n=== Raw Output ===\n');
                outputChannel.appendLine(solution.raw);
                outputChannel.show();
            } else {
                vscode.window.showInformationMessage('No solution available. Run or auto-solve a K file first.');
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.toggleAutoSolve', async () => {
            const config = vscode.workspace.getConfiguration('k');
            const current = config.get<boolean>('autoSolve.enabled', false);
            const newValue = !current;
            await config.update('autoSolve.enabled', newValue, vscode.ConfigurationTarget.Workspace);
            // Set context for button state
            await vscode.commands.executeCommand('setContext', 'k.autoSolveEnabled', newValue);
            vscode.window.showInformationMessage(`K Auto-Solve: ${newValue ? 'ENABLED ✓' : 'DISABLED'}`);
        })
    );

    // Explicit disable command (shows as different button when enabled)
    context.subscriptions.push(
        vscode.commands.registerCommand('k.disableAutoSolve', async () => {
            const config = vscode.workspace.getConfiguration('k');
            await config.update('autoSolve.enabled', false, vscode.ConfigurationTarget.Workspace);
            await vscode.commands.executeCommand('setContext', 'k.autoSolveEnabled', false);
            vscode.window.showInformationMessage('K Auto-Solve: DISABLED');
        })
    );

    // Initialize auto-solve context
    const initialAutoSolve = vscode.workspace.getConfiguration('k').get<boolean>('autoSolve.enabled', false);
    vscode.commands.executeCommand('setContext', 'k.autoSolveEnabled', initialAutoSolve);

    context.subscriptions.push(
        vscode.commands.registerCommand('k.solveNow', () => {
            const editor = vscode.window.activeTextEditor;
            if (editor && editor.document.languageId === 'k') {
                autoSolveController.solve(editor.document);
            } else {
                vscode.window.showWarningMessage('Open a K file to solve');
            }
        })
    );

    // Register Solution Visualization
    const solutionProvider = new KSolutionProvider(context);
    context.subscriptions.push(
        vscode.commands.registerCommand('k.visualizeSolution', () => {
            console.log('K: visualizeSolution command invoked');
            return solutionProvider.runAndVisualize();
        })
    );
    context.subscriptions.push(solutionProvider);

    // Register Constraint Debugger Commands
    context.subscriptions.push(
        vscode.commands.registerCommand('k.startConstraintDebug', async () => {
            try {
                const editor = vscode.window.activeTextEditor;
                if (editor && editor.document.languageId === 'k') {
                    await constraintDebugger.startSession(editor.document);
                } else {
                    vscode.window.showWarningMessage('Open a K file to debug');
                }
            } catch (error) {
                console.error('Error starting constraint debugger:', error);
                vscode.window.showErrorMessage(`Failed to start debugger: ${error instanceof Error ? error.message : String(error)}`);
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.debugStepNext', () => {
            constraintDebugger.stepNext();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.debugStepPrev', () => {
            constraintDebugger.stepPrev();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.debugRunToEnd', () => {
            constraintDebugger.runToEnd();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.debugStop', () => {
            constraintDebugger.stopSession();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.showConstraintDebugger', () => {
            const editor = vscode.window.activeTextEditor;
            if (editor && editor.document.languageId === 'k') {
                constraintDebugger.startSession(editor.document);
            }
        })
    );

    // Register Unified Debug Panel Commands
    context.subscriptions.push(
        vscode.commands.registerCommand('k.openUnifiedDebugger', async () => {
            const editor = vscode.window.activeTextEditor;
            if (editor && editor.document.languageId === 'k') {
                await unifiedDebugPanel.startSession(editor.document);
            } else {
                vscode.window.showWarningMessage('Open a K file to debug');
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.unifiedDebugStepNext', () => {
            unifiedDebugPanel.stepNext();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.unifiedDebugStepPrev', () => {
            unifiedDebugPanel.stepPrev();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.unifiedDebugRunAll', () => {
            unifiedDebugPanel.runAll();
        })
    );

    // Register Progress Panel Command
    context.subscriptions.push(
        vscode.commands.registerCommand('k.showProgress', () => {
            KProgressPanel.createOrShow(context.extensionUri);
        })
    );

    // Register Solve with Progress Command
    context.subscriptions.push(
        vscode.commands.registerCommand('k.solveWithProgress', async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor || editor.document.languageId !== 'k') {
                vscode.window.showWarningMessage('Open a K file to solve');
                return;
            }

            const progressPanel = KProgressPanel.createOrShow(context.extensionUri);

            const result = await solverManager.solve(editor.document.uri.fsPath, {
                showProgress: true,
                onProgress: (progress) => {
                    progressPanel.updateProgress(progress);
                }
            });

            // Update inline decorations with result
            if (result.status === 'sat' || result.status === 'unsat') {
                inlineDecorations.showSolution({
                    status: result.status,
                    objects: result.solution?.objects || [],
                    constraints: [],
                    unsatCore: result.unsatCore,
                    raw: result.raw
                });
            }
        })
    );

    // Register Clear Decorations Command
    context.subscriptions.push(
        vscode.commands.registerCommand('k.clearDecorations', () => {
            inlineDecorations.clearDecorations();
        })
    );

    // Register Diagnostics Provider (real-time error checking)
    const diagnosticsProvider = new KDiagnosticsProvider();
    context.subscriptions.push(diagnosticsProvider);

    // Update diagnostics on document change
    context.subscriptions.push(
        vscode.workspace.onDidChangeTextDocument(event => {
            if (event.document.languageId === 'k') {
                diagnosticsProvider.scheduleDiagnostics(event.document);
            }
        })
    );

    // Update diagnostics when a K file is opened
    context.subscriptions.push(
        vscode.workspace.onDidOpenTextDocument(document => {
            if (document.languageId === 'k') {
                diagnosticsProvider.scheduleDiagnostics(document);
            }
        })
    );

    // Clear diagnostics when document is closed
    context.subscriptions.push(
        vscode.workspace.onDidCloseTextDocument(document => {
            if (document.languageId === 'k') {
                diagnosticsProvider.clear(document);
            }
        })
    );

    // Run diagnostics on any already-open K files
    vscode.workspace.textDocuments.forEach(document => {
        if (document.languageId === 'k') {
            diagnosticsProvider.scheduleDiagnostics(document);
        }
    });
}

export function deactivate() {}
