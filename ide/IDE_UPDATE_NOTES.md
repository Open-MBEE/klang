# IDE Plugin Updates - December 12, 2025

## Summary of Changes

This update adds several new features to both the IntelliJ and VS Code plugins for the K language.

### New: Auto-Solve Mode (VS Code)

The extension now supports automatic solving as you type:

- **Status bar indicator**: Shows solving status (SAT/UNSAT/Error)
- **Debounced solving**: Waits after edits before solving (configurable)
- **Solve on save**: Immediate solve when saving
- **Solution as constraints**: Output solution in readable K constraint format

**Settings:**
- `k.autoSolve.enabled`: Enable/disable auto-solve (default: false)
- `k.autoSolve.debounceMs`: Delay after edits (default: 1000ms)
- `k.autoSolve.timeoutMs`: Max solve time (default: 5000ms)
- `k.autoSolve.onSave`: Solve on save (default: true)

**Commands:**
- `K: Toggle Auto-Solve` - Enable/disable auto-solve
- `K: Solve Now` - Manually trigger solve
- `K: Show Last Solution` - View solution as constraints

### New: K Debugging & Solving Vision Document

Created comprehensive design document at `docs/K_DEBUGGING_AND_SOLVING.md` covering:

1. **Auto-solve mode** with debounce and cancellation
2. **Solution visualization** as object diagrams (UML-like)
3. **UNSAT analysis** with fix suggestions
4. **Constraint-level debugging** (stepping through constraints)
5. **CEGAR loop debugging** for external functions
6. **Unified solver integration** with progress reporting

### IntelliJ Plugin (v0.5.0)

#### New Features
1. **Code Folding** (`KFoldingBuilder.kt`)
   - Fold class bodies
   - Fold function bodies
   - Fold block comments (`/* ... */` and `===...===`)
   - Smart placeholder text based on comment type

2. **Breadcrumb Navigation** (`KBreadcrumbsProvider.kt`)
   - Shows navigation path: package → class → function/constraint
   - Integrates with IntelliJ's breadcrumb bar

3. **Live Templates** (`K.xml`)
   - `class` - Class definition
   - `classext` - Class extending another
   - `fun` - Function definition
   - `prop` - Property declaration
   - `req` - Constraint
   - `reqn` - Named constraint
   - `forall` - Universal quantifier
   - `exists` - Existential quantifier
   - `package` - Package declaration

4. **Improved Commenter**
   - Uses `-- ` for line comments (K preferred style)
   - Uses `/* */` for block comments (more universally recognized)

5. **Enhanced Semantic Highlighting**
   - Class definition names
   - Class references (in extends, type annotations)
   - Function names
   - Property names
   - Constraint names
   - Parameters
   - Customizable in Settings → Editor → Color Scheme → K

6. **Bug Fixes**
   - Added backward compatibility method for `KFormattingModelBuilder`
   - Added missing PSI element types (`PROPERTY_DECLARATION`, `CONSTRAINT_DEFINITION`)

### VS Code Extension (v0.5.0)

#### New Features
1. **Code Snippets** (`snippets/k.json`)
   - All the same templates as IntelliJ
   - Plus: `soft` constraint, `import`, `assoc`, `if`, `/**`, `===`

2. **Improved Folding Configuration**
   - Better support for `===...===` style comments
   - Auto-closing pairs for `/* */`
   - Added `wordPattern` for better word selection

3. **Enhanced Syntax Highlighting**
   - Property definitions with type annotations
   - Named constraints
   - Extends clause with inherited class highlighting
   
4. **Inlay Hints** (`inlayHintsProvider.ts`)
   - Parameter names shown at function call sites
   - Type hints for long property lines

#### Documentation Updates
- Updated `README.md` with all current features
- Updated feature descriptions and requirements

### Files Changed

**IntelliJ:**
- `src/main/kotlin/nasa/jpl/klang/ide/folding/KFoldingBuilder.kt` (NEW)
- `src/main/kotlin/nasa/jpl/klang/ide/breadcrumbs/KBreadcrumbsProvider.kt` (NEW)
- `src/main/kotlin/nasa/jpl/klang/ide/templates/KTemplateContext.kt` (NEW)
- `src/main/resources/liveTemplates/K.xml` (NEW)
- `src/main/resources/META-INF/plugin.xml` (UPDATED)
- `src/main/kotlin/nasa/jpl/klang/ide/KCommenter.kt` (UPDATED)
- `src/main/kotlin/nasa/jpl/klang/ide/psi/KElementTypes.kt` (UPDATED)
- `src/main/kotlin/nasa/jpl/klang/ide/psi/KTokenTypes.kt` (UPDATED)
- `src/main/kotlin/nasa/jpl/klang/ide/formatting/KFormattingModelBuilder.kt` (UPDATED)
- `src/main/kotlin/nasa/jpl/klang/ide/highlighting/KAnnotator.kt` (UPDATED - semantic highlighting)
- `src/main/kotlin/nasa/jpl/klang/ide/highlighting/KSyntaxHighlighter.kt` (UPDATED - new color keys)
- `src/main/kotlin/nasa/jpl/klang/ide/highlighting/KColorSettingsPage.kt` (UPDATED - new attributes)
- `build.gradle.kts` (UPDATED - version)
- `README.md` (UPDATED)

**VS Code:**
- `src/autoSolve.ts` (NEW - auto-solve controller with status bar)
- `snippets/k.json` (NEW)
- `src/providers/inlayHintsProvider.ts` (NEW)
- `src/extension.ts` (UPDATED - auto-solve, inlay hints registration)
- `syntaxes/k.tmLanguage.json` (UPDATED - semantic scopes)
- `package.json` (UPDATED - auto-solve settings and commands)
- `language-configuration.json` (UPDATED)
- `README.md` (UPDATED)

**Documentation:**
- `docs/K_DEBUGGING_AND_SOLVING.md` (NEW - vision document)

### Building

**IntelliJ:**
```bash
cd ide/jetbrains
./gradlew clean buildPlugin
# Plugin will be in build/distributions/
```

**VS Code:**
```bash
cd ide/vscode
npm run compile
npm run package  # Creates .vsix file
```

### Testing

**IntelliJ:**
1. Install plugin from `build/distributions/k-language-0.5.0.zip`
2. Open a `.k` file
3. Test:
   - Code folding (click arrows in gutter)
   - Breadcrumbs (top of editor)
   - Live templates (type `class` then Tab)
   - Comments (Ctrl+/)
   - Semantic highlighting (class names should be bold, functions italic)
   - Color customization (Settings → Editor → Color Scheme → K)

**VS Code:**
1. Press F5 in the `ide/vscode` folder
2. Open a `.k` file
3. Test:
   - Snippets (type `class` and select from IntelliSense)
   - Folding (click arrows in gutter)
   - Inlay hints (may need to enable in settings)
   - All previous features

### Next Steps (Future)

**Phase 1: Solution Experience**
- [ ] Solution graph visualization (webview with D3.js)
- [ ] UNSAT core display with fix suggestions
- [ ] Quick fix actions in editor

**Phase 2: Constraint Debugging**
- [ ] Constraint stepping UI
- [ ] Variable range visualization
- [ ] Constraint breakpoints

**Phase 3: CEGAR & External Functions**
- [ ] CEGAR iteration viewer
- [ ] External function call tracing
- [ ] Java/Python debugger integration

**Phase 4: Unified Solver**
- [ ] Progress reporting UI
- [ ] Interrupt/resume capability
- [ ] Partial solution handling

See `docs/K_DEBUGGING_AND_SOLVING.md` for full vision.

