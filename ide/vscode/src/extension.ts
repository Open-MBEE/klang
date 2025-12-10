import * as vscode from 'vscode';
import { KDefinitionProvider } from './providers/definitionProvider';
import { KReferenceProvider } from './providers/referenceProvider';
import { KHoverProvider } from './providers/hoverProvider';
import { KDocumentSymbolProvider } from './providers/documentSymbolProvider';
import { KWorkspaceSymbolProvider } from './providers/workspaceSymbolProvider';
import { runKFile, runKFileWithArgs } from './runner';

const K_MODE: vscode.DocumentFilter = { language: 'k', scheme: 'file' };

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

    // Register Run Commands
    context.subscriptions.push(
        vscode.commands.registerCommand('k.runFile', runKFile)
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('k.runFileWithArgs', runKFileWithArgs)
    );
}

export function deactivate() {}

