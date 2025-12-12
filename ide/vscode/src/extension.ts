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
import { runKFile, runKFileWithArgs } from './runner';

// Document selector for K language files
const K_MODE: vscode.DocumentSelector = { language: 'k', scheme: 'file' };

export function activate(context: vscode.ExtensionContext) {
    console.log('K Language extension is now active');

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

    // Register Solution Visualization
    const solutionProvider = new KSolutionProvider(context);
    context.subscriptions.push(
        vscode.commands.registerCommand('k.visualizeSolution', () => {
            console.log('K: visualizeSolution command invoked');
            return solutionProvider.runAndVisualize();
        })
    );
    context.subscriptions.push(solutionProvider);

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
