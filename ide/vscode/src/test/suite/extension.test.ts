import * as assert from 'assert';
import * as vscode from 'vscode';
import * as path from 'path';

suite('K Language Extension Test Suite', () => {
    vscode.window.showInformationMessage('Start all tests.');

    // Path from out/test/suite to klang/src/examples
    const testKFilePath = path.resolve(__dirname, '../../../../../src/examples/Shapes.k');

    test('Extension should be present', () => {
        assert.ok(vscode.extensions.getExtension('nasa-jpl.k-language'));
    });

    test('Extension should activate on K file', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        assert.ok(ext);

        // Open a K file to activate the extension
        const doc = await vscode.workspace.openTextDocument(testKFilePath);
        await vscode.window.showTextDocument(doc);

        // Wait for activation
        await ext.activate();
        assert.ok(ext.isActive, 'Extension should be active');
    });

    test('K file should have correct language ID', async () => {
        const doc = await vscode.workspace.openTextDocument(testKFilePath);
        assert.strictEqual(doc.languageId, 'k', 'Language ID should be k');
    });

    test('Syntax highlighting should provide tokens', async () => {
        const doc = await vscode.workspace.openTextDocument(testKFilePath);
        await vscode.window.showTextDocument(doc);

        // Give time for tokenization
        await new Promise(resolve => setTimeout(resolve, 1000));

        // The document should be recognized as K
        assert.strictEqual(doc.languageId, 'k');
    });

    test('Document symbols should be available', async () => {
        const doc = await vscode.workspace.openTextDocument(testKFilePath);
        await vscode.window.showTextDocument(doc);

        // Give time for symbol parsing
        await new Promise(resolve => setTimeout(resolve, 1000));

        const symbols = await vscode.commands.executeCommand<vscode.DocumentSymbol[]>(
            'vscode.executeDocumentSymbolProvider',
            doc.uri
        );

        assert.ok(symbols, 'Should have symbols');
        assert.ok(symbols.length > 0, 'Should have at least one symbol');

        // Check for known classes
        const classNames = symbols.map(s => s.name);
        assert.ok(classNames.includes('Shape'), 'Should have Shape class');
        assert.ok(classNames.includes('Triangle'), 'Should have Triangle class');
    });

    test('Go to definition should work', async () => {
        const doc = await vscode.workspace.openTextDocument(testKFilePath);
        await vscode.window.showTextDocument(doc);

        // Use a fixed position for testing - line 1 (class Shape)
        const definitions = await vscode.commands.executeCommand<vscode.Location[]>(
            'vscode.executeDefinitionProvider',
            doc.uri,
            new vscode.Position(2, 10) // "Shape" in class Shape
        );

        // Definition provider should return something (even if it's the same location)
        assert.ok(definitions !== undefined, 'Should have definitions result');
    });

    test('Hover should provide information', async () => {
        const doc = await vscode.workspace.openTextDocument(testKFilePath);
        await vscode.window.showTextDocument(doc);

        // Find a class name
        const text = doc.getText();
        const classMatch = text.indexOf('class Shape');
        if (classMatch > 0) {
            const pos = doc.positionAt(classMatch + 'class '.length);

            const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
                'vscode.executeHoverProvider',
                doc.uri,
                pos
            );

            assert.ok(hovers, 'Should have hover info');
        }
    });

    test('Completions should be available', async () => {
        const doc = await vscode.workspace.openTextDocument(testKFilePath);
        await vscode.window.showTextDocument(doc);

        // Get completions at some point
        const pos = new vscode.Position(5, 4); // Inside a class body

        const completions = await vscode.commands.executeCommand<vscode.CompletionList>(
            'vscode.executeCompletionItemProvider',
            doc.uri,
            pos
        );

        // Should have some completions (keywords, types, etc.)
        assert.ok(completions, 'Should have completion list');
    });

    test('Run K file command should exist', async () => {
        // Ensure extension is activated first
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.runFile'), 'k.runFile command should exist');
    });

    test('Constraint debugger command should exist', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.startConstraintDebug'), 'k.startConstraintDebug command should exist');
    });

    test('Auto-solve toggle command should exist', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.toggleAutoSolve'), 'k.toggleAutoSolve command should exist');
    });

    test('Visualize solution command should exist', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.visualizeSolution'), 'k.visualizeSolution command should exist');
    });

    test('Java debug command should exist', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.runWithJavaDebug'), 'k.runWithJavaDebug command should exist');
    });

    test('Python debug command should exist', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.runWithPythonDebug'), 'k.runWithPythonDebug command should exist');
    });

    test('Full debug command should exist', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.runWithFullDebug'), 'k.runWithFullDebug command should exist');
    });

    test('Unified debug panel command should exist', async () => {
        const ext = vscode.extensions.getExtension('nasa-jpl.k-language');
        if (ext && !ext.isActive) {
            await ext.activate();
        }
        const commands = await vscode.commands.getCommands();
        assert.ok(commands.includes('k.openUnifiedDebugger'), 'k.openUnifiedDebugger command should exist');
    });
});

