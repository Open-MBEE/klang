import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';

let outputChannel: vscode.OutputChannel | undefined;

function getOutputChannel(): vscode.OutputChannel {
    if (!outputChannel) {
        outputChannel = vscode.window.createOutputChannel('K Language');
    }
    return outputChannel;
}

/**
 * Find the K installation path from configuration or common locations
 */
export async function findKInstallation(): Promise<string | undefined> {
    const config = vscode.workspace.getConfiguration('k');
    const configuredPath = config.get<string>('installation.path');

    if (configuredPath && configuredPath.trim()) {
        // User configured a path
        const kScript = findKScript(configuredPath);
        if (kScript) {
            return kScript;
        }
        vscode.window.showWarningMessage(
            `K installation not found at configured path: ${configuredPath}`
        );
    }

    // Try to find K in common locations
    const commonPaths = [
        // Workspace-relative (developer mode)
        vscode.workspace.workspaceFolders?.[0]?.uri.fsPath,
        // Parent of workspace (if workspace is inside klang)
        path.join(vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || '', '..'),
        // Home directory installations
        path.join(process.env.HOME || '', 'klang'),
        path.join(process.env.HOME || '', '.klang'),
        path.join(process.env.HOME || '', 'git', 'klang'),
        // System installations
        '/usr/local/klang',
        '/opt/klang',
    ];

    for (const basePath of commonPaths) {
        if (!basePath) continue;
        const kScript = findKScript(basePath);
        if (kScript) {
            return kScript;
        }
    }

    return undefined;
}

/**
 * Find the K runner script in a given directory
 */
function findKScript(basePath: string): string | undefined {
    const candidates = [
        path.join(basePath, 'export', 'k'),
        path.join(basePath, 'k'),
        path.join(basePath, 'bin', 'k'),
    ];

    for (const candidate of candidates) {
        if (fs.existsSync(candidate)) {
            return candidate;
        }
    }
    return undefined;
}

/**
 * Get Java home from configuration or environment
 * Prefers SDKMAN Java (likely ARM64) over system Java (might be x86_64)
 */
function getJavaHome(): string | undefined {
    const config = vscode.workspace.getConfiguration('k');
    const configuredJava = config.get<string>('java.home');

    if (configuredJava && configuredJava.trim()) {
        return configuredJava;
    }

    // Check SDKMAN first - this is likely ARM64 on Apple Silicon
    const sdkmanCurrent = path.join(process.env.HOME || '', '.sdkman', 'candidates', 'java', 'current');
    if (fs.existsSync(sdkmanCurrent)) {
        return sdkmanCurrent;
    }

    // Check for specific SDKMAN Java versions
    const sdkmanBase = path.join(process.env.HOME || '', '.sdkman', 'candidates', 'java');
    if (fs.existsSync(sdkmanBase)) {
        // Look for any Java 21 or 8 installation
        try {
            const versions = fs.readdirSync(sdkmanBase);
            for (const ver of versions) {
                if (ver.includes('21') || ver.includes('tem') || ver.includes('zulu')) {
                    const javaPath = path.join(sdkmanBase, ver);
                    if (fs.existsSync(path.join(javaPath, 'bin', 'java'))) {
                        return javaPath;
                    }
                }
            }
        } catch (e) {
            // Ignore errors reading directory
        }
    }

    // Fall back to environment JAVA_HOME (might be x86_64 on Apple Silicon)
    if (process.env.JAVA_HOME) {
        return process.env.JAVA_HOME;
    }

    return undefined;
}

/**
 * Run a K file
 */
export async function runKFile(fileUri?: vscode.Uri): Promise<void> {
    // Get the file to run
    let filePath: string;

    if (fileUri) {
        filePath = fileUri.fsPath;
    } else if (vscode.window.activeTextEditor?.document.languageId === 'k') {
        filePath = vscode.window.activeTextEditor.document.uri.fsPath;
    } else {
        vscode.window.showErrorMessage('No K file selected');
        return;
    }

    // Save the file first
    const doc = vscode.workspace.textDocuments.find(d => d.uri.fsPath === filePath);
    if (doc?.isDirty) {
        await doc.save();
    }

    // Find K installation
    const kScript = await findKInstallation();

    if (!kScript) {
        const action = await vscode.window.showErrorMessage(
            'K language installation not found. Please configure the path in settings.',
            'Open Settings'
        );
        if (action === 'Open Settings') {
            vscode.commands.executeCommand('workbench.action.openSettings', 'k.installation.path');
        }
        return;
    }

    // Get the K installation root directory
    const kInstallDir = path.dirname(path.dirname(kScript)); // Go up from export/k to klang root

    // Prepare environment - let the k script handle library paths
    // macOS SIP strips DYLD_LIBRARY_PATH from child processes anyway
    const env: NodeJS.ProcessEnv = { ...process.env };

    const javaHome = getJavaHome();
    if (javaHome) {
        env.JAVA_HOME = javaHome;
        env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
    }

    // Get output channel
    const output = getOutputChannel();
    const config = vscode.workspace.getConfiguration('k');

    if (config.get<boolean>('run.showOutput', true)) {
        output.show(true);
    }

    output.appendLine(`\n${'='.repeat(60)}`);
    output.appendLine(`Running: ${path.basename(filePath)}`);
    output.appendLine(`Time: ${new Date().toLocaleTimeString()}`);
    output.appendLine(`${'='.repeat(60)}\n`);

    // Debug: Show Java being used
    if (env.JAVA_HOME) {
        output.appendLine(`JAVA_HOME: ${env.JAVA_HOME}`);
    }
    output.appendLine(`K Script: ${kScript}`);
    output.appendLine(`Working Dir: ${kInstallDir}\n`);

    // Run the K file
    const startTime = Date.now();

    const child = cp.spawn(kScript, [filePath], {
        cwd: kInstallDir,  // Run from klang root so script's relative paths work
        env,
        shell: true
    });

    child.stdout?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.stderr?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.on('close', (code: number | null) => {
        const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);
        output.appendLine(`\n${'='.repeat(60)}`);
        if (code === 0) {
            output.appendLine(`✓ Completed successfully in ${elapsed}s`);
        } else {
            output.appendLine(`✗ Exited with code ${code} in ${elapsed}s`);
        }
        output.appendLine(`${'='.repeat(60)}\n`);
    });

    child.on('error', (err: Error) => {
        output.appendLine(`\n✗ Error: ${err.message}`);
        vscode.window.showErrorMessage(`Failed to run K file: ${err.message}`);
    });
}

/**
 * Run a K file with custom arguments
 */
export async function runKFileWithArgs(fileUri?: vscode.Uri): Promise<void> {
    const args = await vscode.window.showInputBox({
        prompt: 'Enter arguments for the K file',
        placeHolder: 'e.g., --verbose --output result.json'
    });

    if (args === undefined) {
        return; // Cancelled
    }

    // For now, just run without args - extend later as needed
    await runKFile(fileUri);
}

/**
 * Run K file with Java debug agent attached (for stepping into external functions)
 */
export async function runKFileWithDebug(fileUri?: vscode.Uri, debugPort: number = 5005): Promise<cp.ChildProcess | undefined> {
    // Get the file to run
    let filePath: string;

    if (fileUri) {
        filePath = fileUri.fsPath;
    } else if (vscode.window.activeTextEditor?.document.languageId === 'k') {
        filePath = vscode.window.activeTextEditor.document.uri.fsPath;
    } else {
        vscode.window.showErrorMessage('No K file selected');
        return undefined;
    }

    // Save the file first
    const doc = vscode.workspace.textDocuments.find(d => d.uri.fsPath === filePath);
    if (doc?.isDirty) {
        await doc.save();
    }

    // Find K installation
    const kScript = await findKInstallation();

    if (!kScript) {
        vscode.window.showErrorMessage('K language installation not found');
        return undefined;
    }

    const kInstallDir = path.dirname(path.dirname(kScript));

    const env: NodeJS.ProcessEnv = { ...process.env };

    const javaHome = getJavaHome();
    if (javaHome) {
        env.JAVA_HOME = javaHome;
        env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
    }

    // Add Java debug agent
    env.JAVA_TOOL_OPTIONS = `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${debugPort}`;

    const output = getOutputChannel();
    output.show(true);

    output.appendLine(`\n${'='.repeat(60)}`);
    output.appendLine(`Running (DEBUG MODE): ${path.basename(filePath)}`);
    output.appendLine(`Debug port: ${debugPort}`);
    output.appendLine(`${'='.repeat(60)}\n`);
    output.appendLine(`Java debug agent attached on port ${debugPort}`);
    output.appendLine(`Attach your debugger to localhost:${debugPort}\n`);

    const startTime = Date.now();

    const child = cp.spawn(kScript, [filePath], {
        cwd: kInstallDir,
        env,
        shell: true
    });

    child.stdout?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.stderr?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.on('close', (code: number | null) => {
        const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);
        output.appendLine(`\n${'='.repeat(60)}`);
        if (code === 0) {
            output.appendLine(`✓ Debug session completed in ${elapsed}s`);
        } else {
            output.appendLine(`✗ Debug session exited with code ${code} in ${elapsed}s`);
        }
        output.appendLine(`${'='.repeat(60)}\n`);
    });

    return child;
}

/**
 * Run K file with Python debugging enabled
 *
 * Since K calls Python via subprocess, we need to:
 * 1. Set PYTHONBREAKPOINT to enable debugpy
 * 2. Configure Python to listen on a debug port
 */
export async function runKFileWithPythonDebug(fileUri?: vscode.Uri, debugPort: number = 5678): Promise<cp.ChildProcess | undefined> {
    let filePath: string;

    if (fileUri) {
        filePath = fileUri.fsPath;
    } else if (vscode.window.activeTextEditor?.document.languageId === 'k') {
        filePath = vscode.window.activeTextEditor.document.uri.fsPath;
    } else {
        vscode.window.showErrorMessage('No K file selected');
        return undefined;
    }

    const doc = vscode.workspace.textDocuments.find(d => d.uri.fsPath === filePath);
    if (doc?.isDirty) {
        await doc.save();
    }

    const kScript = await findKInstallation();

    if (!kScript) {
        vscode.window.showErrorMessage('K language installation not found');
        return undefined;
    }

    const kInstallDir = path.dirname(path.dirname(kScript));

    const env: NodeJS.ProcessEnv = { ...process.env };

    const javaHome = getJavaHome();
    if (javaHome) {
        env.JAVA_HOME = javaHome;
        env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
    }

    // Set up Python debugging via debugpy
    // K's PythonExternalFunctions will inherit this environment
    env.K_PYTHON_DEBUG = '1';
    env.K_PYTHON_DEBUG_PORT = String(debugPort);

    // Tell Python to use debugpy when starting
    env.PYTHONBREAKPOINT = 'debugpy.breakpoint';

    const output = getOutputChannel();
    output.show(true);

    output.appendLine(`\n${'='.repeat(60)}`);
    output.appendLine(`Running (PYTHON DEBUG MODE): ${path.basename(filePath)}`);
    output.appendLine(`Python debug port: ${debugPort}`);
    output.appendLine(`${'='.repeat(60)}\n`);
    output.appendLine(`Note: Python functions called by K will be debuggable.`);
    output.appendLine(`Make sure 'debugpy' is installed: pip install debugpy\n`);

    const startTime = Date.now();

    const child = cp.spawn(kScript, [filePath], {
        cwd: kInstallDir,
        env,
        shell: true
    });

    child.stdout?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.stderr?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.on('close', (code: number | null) => {
        const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);
        output.appendLine(`\n${'='.repeat(60)}`);
        if (code === 0) {
            output.appendLine(`✓ Python debug session completed in ${elapsed}s`);
        } else {
            output.appendLine(`✗ Python debug session exited with code ${code} in ${elapsed}s`);
        }
        output.appendLine(`${'='.repeat(60)}\n`);
    });

    return child;
}

/**
 * Run K file with both Java and Python debugging enabled
 */
export async function runKFileWithFullDebug(fileUri?: vscode.Uri): Promise<cp.ChildProcess | undefined> {
    let filePath: string;

    if (fileUri) {
        filePath = fileUri.fsPath;
    } else if (vscode.window.activeTextEditor?.document.languageId === 'k') {
        filePath = vscode.window.activeTextEditor.document.uri.fsPath;
    } else {
        vscode.window.showErrorMessage('No K file selected');
        return undefined;
    }

    const doc = vscode.workspace.textDocuments.find(d => d.uri.fsPath === filePath);
    if (doc?.isDirty) {
        await doc.save();
    }

    const kScript = await findKInstallation();

    if (!kScript) {
        vscode.window.showErrorMessage('K language installation not found');
        return undefined;
    }

    const kInstallDir = path.dirname(path.dirname(kScript));

    const env: NodeJS.ProcessEnv = { ...process.env };

    const javaHome = getJavaHome();
    if (javaHome) {
        env.JAVA_HOME = javaHome;
        env.PATH = `${path.join(javaHome, 'bin')}:${env.PATH}`;
    }

    // Java debug on port 5005
    env.JAVA_TOOL_OPTIONS = '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005';

    // Python debug on port 5678
    env.K_PYTHON_DEBUG = '1';
    env.K_PYTHON_DEBUG_PORT = '5678';
    env.PYTHONBREAKPOINT = 'debugpy.breakpoint';

    const output = getOutputChannel();
    output.show(true);

    output.appendLine(`\n${'='.repeat(60)}`);
    output.appendLine(`Running (FULL DEBUG MODE): ${path.basename(filePath)}`);
    output.appendLine(`${'='.repeat(60)}\n`);
    output.appendLine(`Java debug:   localhost:5005`);
    output.appendLine(`Python debug: localhost:5678`);
    output.appendLine(`\nYou can attach debuggers for both languages.\n`);

    const startTime = Date.now();

    const child = cp.spawn(kScript, [filePath], {
        cwd: kInstallDir,
        env,
        shell: true
    });

    child.stdout?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.stderr?.on('data', (data: Buffer) => {
        output.append(data.toString());
    });

    child.on('close', (code: number | null) => {
        const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);
        output.appendLine(`\n${'='.repeat(60)}`);
        if (code === 0) {
            output.appendLine(`✓ Full debug session completed in ${elapsed}s`);
        } else {
            output.appendLine(`✗ Full debug session exited with code ${code} in ${elapsed}s`);
        }
        output.appendLine(`${'='.repeat(60)}\n`);
    });

    return child;
}

