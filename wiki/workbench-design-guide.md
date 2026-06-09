# Workbench Design Guide

The ChessRTK Workbench is a dense desktop research tool, not a landing page and not a decorative demo. Design new UI as VS Code on macOS applied to chess tooling: compact editor chrome, clear hierarchy, quiet surfaces, high information density, and a small amount of restrained frosted-glass influence only where it improves orientation. The goal is that a researcher can scan a position, command, dataset, or engine run quickly and trust that the GUI is showing the same deterministic system as the CLI.

This page is the standard for Workbench UI changes. It covers visual direction, layout, controls, copy, accessibility, and verification.

## Core Principles

- **Use the shared chess core.** UI affordances may be visual, but legality, FEN, SAN, UCI, tags, engine settings, and command text still come from the same code paths the CLI uses.
- **Use VS Code for structure.** Major Workbench areas behave like editor groups, sidebars, panels, command palette entries, and contextual status surfaces.
- **Use Apple for finish.** Controls should feel deliberate, legible, consistent, and platform-familiar: clear hierarchy, logical toolbar grouping, useful search, restrained materials, and accessible states.
- **Design for repeated work.** Prioritize scanning, comparison, low friction, and predictable controls over explanation-heavy screens.
- **Keep surfaces calm.** The app should feel like an editor/workbench: flat panes, clear dividers, compact toolbars, and cards only for real grouped content.
- **Centralize styling.** Add palette, spacing, typography, and control styling through the shared Workbench UI layer instead of local ad hoc painting.
- **Make states obvious.** Hover, focus, disabled, selected, error, running, complete, and missing states must be visible in both light and dark modes.
- **Keep output deterministic.** UI previews, command builders, exports, and generated artifacts must not depend on wall-clock noise or hidden random choices.

## Uniformity Contract

Every Workbench change must satisfy these rules before it is considered finished.

| Area | Non-negotiable rule |
| --- | --- |
| Screen shape | Every major surface uses the same structure: optional tab strip, `WorkspaceHeader`, optional toolbar band, main body, optional side rail, optional bottom panel. |
| Styling | All color, font, spacing, radius, border, control, and scroll styling comes from `Theme`, `Ui`, or a shared Workbench primitive. |
| Controls | Buttons, fields, combos, spinners, sliders, toggles, tables, tabs, scroll panes, badges, and empty states use shared factories/stylers. |
| Alignment | Forms use fixed label lanes. Toolbars use leading/center/trailing grouping. Side rails use one consistent width lane. |
| Density | Dense editor layout is the default. Use spacing to clarify groups, not to create large decorative gaps. |
| Hierarchy | The main content is visually dominant; controls, metadata, and captions are secondary. |
| Actions | One primary action per local scope. Destructive actions are isolated. Export/copy/open actions stay trailing. |
| States | Resting, hover, focus, selected, disabled, loading, error, missing, and complete states are all designed and tested. |
| Responsiveness | Narrow windows must not overlap or clip critical text; long text elides or wraps according to the rules below. |
| Verification | UI code changes need headless Workbench regression plus visual inspection or screenshots for affected surfaces. |

Do not ship a panel that looks "close enough" but uses local colors, raw Swing control styling, arbitrary spacing, or a new layout language.

## Reference Model

The Workbench should not copy VS Code or macOS pixel-for-pixel. It should translate their useful rules into ChessRTK's Java Swing implementation.

| Reference | What to copy | What not to copy |
| --- | --- | --- |
| VS Code workbench | Editor groups, compact tabs, split panes, command palette, sidebars, bottom panels, contextual status, dense data surfaces | Extension marketplace clutter, too many contributed views, webview-like landing pages |
| Apple HIG | Visual hierarchy, consistency, readable controls, logical toolbar item grouping, useful search fields, clear button roles, restrained material/depth | Large consumer-app whitespace, oversized rounded controls, ornamental glass, platform-specific gestures Swing cannot support |
| ChessRTK | Board-first chess work, deterministic command previews, research density, CLI parity, in-process engine honesty | A second rules model, fake engine claims, UI-only behavior that cannot reproduce from the CLI |

Use this decision rule:

| Question | Answer |
| --- | --- |
| Where does this feature live? | Choose the closest VS Code container: editor tab, side rail, panel, command palette, context menu, or status surface. |
| How should the controls feel? | Use Apple-style hierarchy: grouped toolbar items, stable control sizes, clear labels/tooltips, restrained accent color, visible focus. |
| How much chrome should it have? | Use VS Code density first; add Apple-like depth only for floating chrome, dialogs, popovers, and transient overlays. |
| How much text should it show? | Use Apple clarity: short labels, useful placeholder text, precise validation. Avoid visible tutorials inside task surfaces. |

## Workbench North Star

The Workbench should feel like one integrated chess research instrument. It is not a loose collection of Swing panels. It is not a chess website, an IDE clone, or a neural-network demo. It is an editor-style desktop tool where the user can move from position, to command, to run, to artifact, to explanation without losing context.

At first glance the app should look like this:

| Layer | Visual target |
| --- | --- |
| Shell | A compact VS Code-like editor frame with clear tabs, split groups, command palette access, and a quiet status strip. |
| Workspace | One full-screen task surface at a time: Dashboard, Board, Engine Lab, Run, Datasets, or Publish. |
| Center | The real artifact: board, graph, table, command builder, dataset chart, or publication preview. |
| Inspector rail | A 320 to 380 px right rail for controls, current selection, mode settings, and secondary lists. |
| Output | Runs, logs, progress, and artifacts appear in persistent output surfaces instead of modal interruptions. |
| Finish | Apple-like clarity: restrained controls, readable text, exact states, and no decorative layout noise. |

The most important visual rule is that the content owns the screen. Chess boards should look like boards, datasets should look like analytics, runs should look like executable command records, and publishing should look like previewing a real artifact. Chrome should help the user orient, not compete with the work.

## Target Information Architecture

The ideal Workbench has six top-level surfaces. Current implementation may keep transitional tabs while migration is in progress, but new design work should place features in these homes.

| Surface | Primary object | Contains | Center of screen | Inspector / secondary area |
| --- | --- | --- | --- | --- |
| `Dashboard` | Session state | Health, current position, recent runs, artifacts, runtime status, quick links | Live summary grid and recent activity | None by default; cards deep-link to surfaces |
| `Board` | Chess position/game | Analyze, Play, Solve, Relations, Draw, board editor, study/review/ECO/endgame tools | One shared `BoardStage` with eval bar and overlays | Mode inspector for moves, engine, puzzle, play, relations, draw, tags, ECO |
| `Engine Lab` | Evaluation/search | Network visualizer, MCTS/PUCT search, tree graph, gauntlet | Shared position view, engine charts, search tree, root move table | Backend/model controls, selected node/tensor details, run controls |
| `Run` | Executable command | Command builder, batch, console/REPL, output, logs | Command form and command result cards | Validation, run options, filters, selected output details |
| `Datasets` | Data artifact | Loaded exports, tensor summaries, label distributions, charts, samples, issues | KPI cards, charts, dense tables | Load/options/details if needed |
| `Publish` | Rendered artifact | Diagram/book/study/collection/cover preview and render configuration | Live publication preview | Manifest fields, validation, render/export controls |

Surface rules:

- Do not add a top-level tab when the feature is a mode of an existing surface.
- `Analyze`, `Play`, `Solve`, `Relations`, `Draw`, and `Board Editor` are Board modes.
- `Network`, `MCTS`, `Tree`, and `Gauntlet` are Engine Lab modes.
- `Commands`, `Batch`, `Console`, and `Logs` are Run modes or Run/output companion views.
- `Datasets` and `Publish` stay top-level because their primary artifacts are not positions or command output.
- `Dashboard` is the only global state surface. It should launch work, not duplicate full workflows.
- Command palette entries may preserve old names like `Open Play` or `Open Logs`, but they should route to the right surface and mode.

When a feature seems to need a new tab, name its primary object first. If the object is a position, engine/search state, command run, dataset, publication, or session status, it belongs in one of the six surfaces above.

## Primary Object Model

Every screen must make one primary object obvious. This prevents the app from becoming a stack of unrelated forms.

| Object | Header context | Main representation | Common actions |
| --- | --- | --- | --- |
| Position | FEN summary, side to move, source, game ply | Board, legal moves, overlays, tags, ECO, engine lines | Load PGN/FEN, copy FEN, analyze, play, solve, export |
| Engine/search state | Backend, model, running state, nodes, nps, selected move | KPI strip, root move table, tree, tensor/activation views | Start, stop, pause, snapshot, export, copy command |
| Command run | Area/action, validity, exit state, artifact path | Command form, command preview, output cards, logs | Run, stop, copy command, open artifact |
| Dataset | File/path, rows, shape, labels, issue count | Summary cards, charts, sample/issue tables | Load, scan, filter, export |
| Publication | Task, source manifest, render state, output path | Live preview page/artboard | Render, check, export, open |
| Session | Active position, recent jobs, health, artifacts | Dashboard cards and status badges | Open surface, refresh, open folder |

The `WorkspaceHeader` should always answer: what object am I looking at, what state is it in, and what is the next primary action?

## What Each Surface Should Look Like

Use these visual targets when adapting the current implementation.

| Surface | Ideal composition |
| --- | --- |
| Dashboard | A calm operational overview. Top row has current position, active jobs, runtime/model health, and recent artifacts. Cards are compact, live, and clickable. No hero banner, no large welcome text. |
| Board | A large square board dominates the center-left. The right rail is a mode inspector with `Analyze`, `Play`, `Solve`, `Relations`, and `Draw`. Switching modes preserves the board and position whenever possible. |
| Engine Lab | Looks like a lab bench for one position. Put a small position/board context beside search and neural evidence. Show KPI strip, root moves, tree/trace/atlas views, and selected-node details. Fill empty MCTS/search states with useful root rows or setup actions, not a giant blank area. |
| Run | Looks like a command workbench. The user builds exact `crtk` commands, sees validation before execution, and gets persistent result cards with command text, exit code, time, output preview, and artifact links. |
| Datasets | Looks like analytics. KPI cards and distribution charts come first, then dense sample/issue tables. The surface should reveal skew, missing labels, bad rows, and shape problems quickly. |
| Publish | Looks like previewing a real page. The preview/artboard owns the center; controls sit in a rail or compact top band. Render status and generated command/output belong in Run/output, not a private terminal block. |
| Console/Logs during migration | If still top-level, style them as Run/output surfaces: dense, searchable, artifact-aware, and not visually separate from command execution. |

Perfect Workbench screens should pass this test: if the user sees a screenshot without reading the tab name, they should immediately know whether they are working with a board, engine search, command run, dataset, publication, or session dashboard.

## Content-Driven Layout Rules

Different content types deserve different layouts, but the visual language stays the same.

| Content type | Layout rule |
| --- | --- |
| Board work | Board is largest and square; rail supports it; eval/move/history/overlays stay attached to the position. |
| Engine search | Show live progress near the graph/table; selected search nodes drive detail panes; best-line arrows may annotate the board. |
| Neural visualization | Use semantic activation colors, labeled regions, legends, and inspectable hit regions; avoid decorative diagrams with fake precision. |
| Command forms | Required fields first, optional flags collapsed or grouped, exact command preview always visible. |
| Batch work | Treat every row as part of a command run; progress and output go to shared result/log surfaces. |
| Dataset analytics | Summary before detail, charts before raw rows only when they explain the table, filters near the data they filter. |
| Publishing preview | Preview first, controls second; page/artboard proportions are stable; export status is visible but quiet. |
| Logs/output | Searchable, timestamped, artifact-linked, monospace where command-like, and never hidden behind modal alerts. |

Do not force every surface into a card grid. The grid is right for dashboards and analytics; a board, command builder, graph, console, and publication preview each need their own central artifact shape.

## VS Code Structure Rules

Treat the Workbench shell like a code editor around chess/research content.

| VS Code concept | ChessRTK translation | Rule |
| --- | --- | --- |
| Activity Bar / View Container | Top-level Workbench modes and registered views | Add a new top-level area only for a durable workflow, not for one command. |
| Primary/Secondary Sidebar | Side rails beside a board, table, graph, or preview | Group related views; keep 3 to 5 visible side-rail sections on normal screens. |
| Editor Area | Dashboard, Board, Engine Lab, Run, Datasets, Publish | The primary task owns the center; tabs and splits preserve context. |
| Editor Groups | Split Workbench panes | Splits compare or parallelize work; do not use splits for decorative layout. |
| Panel | Console, Logs, artifacts, run output | Put long-running output and diagnostics in panel/log surfaces, not blocking dialogs. |
| Status Bar | Badges, run state, model state, current FEN context | Show contextual state near the affected surface; keep it terse and nonblocking. |
| Command Palette | Workbench command palette and command forms | Use category-prefixed, clear command names; no emojis or cute names. |
| View Toolbar | Local toolbar bands and icon buttons | Actions must apply to the current view, not silently to a different tab. |
| Context Menu | Right-click tab/table/board actions | Use for local, discoverable secondary actions only; primary actions stay visible. |

New Workbench UI must name which container it belongs to. If the answer is unclear, the feature is probably not ready for UI.

## Apple Interaction Rules

Use Apple design as the behavior and polish layer.

| Apple principle | Workbench rule |
| --- | --- |
| Hierarchy | Content is primary, controls are secondary, metadata is muted, destructive actions are visually isolated. |
| Harmony | Use consistent radius, spacing, icon weight, and control height; avoid mixing unrelated visual languages in one panel. |
| Consistency | The same action must appear in the same position and style across panels. |
| Adaptability | Toolbars and forms must survive narrower windows through wrapping rows, elision, overflow menus, or side-rail collapse. |
| Legibility | Use the shared font stack and minimum readable sizes; never shrink text to solve layout. |
| Color discipline | Use color to communicate state or action. Do not use the same accent for unrelated meanings. |
| Material/depth | Use glass/highlight only for floating chrome; permanent task panes stay opaque and readable. |
| Search behavior | Search fields need specific placeholder text and should filter while typing when the data set is local. |
| Button behavior | Buttons in the same group should share height, padding, and visual family; image/icon buttons need tooltips. |
| Toolbar grouping | Leading area orients, center changes mode, trailing area holds persistent actions/search. |

## Visual Identity

The target is VS Code on macOS, with the existing ChessRTK board and research surfaces layered into that shell.

| Design area | Standard |
| --- | --- |
| Shell | VS Code-style compact editor tabs, split groups, hairline separators, opaque editor surfaces |
| Palette | VS Code Visual Studio neutrals with macOS-style blue action accents |
| Density | VS Code dense by default, with comfort controlled through `Theme.Density` |
| Surfaces | Flat top-level panes; Apple-like raised cards only for grouped static content |
| Glass | Restrained Apple-like material cues on backdrop, dialogs, command palette, popovers, and other floating chrome |
| Chess board | Chessboard.js-like tan board and lichess-like interaction feedback |
| Typography | Apple-style legibility through shared UI fonts; monospace only for code, commands, logs, tensors, and source text |

Do not introduce a one-off theme, gradient-heavy panel, oversized hero section, decorative orb/blob background, or marketing-card layout inside the Workbench.

## Harmony Rules

Harmony means the user should feel that every tab belongs to the same application, even when the task changes from playing chess to inspecting tensors.

| Rule | Implementation |
| --- | --- |
| Same structure | Every major tab follows the canonical surface layout. |
| Same vocabulary | Reuse the same labels for the same action: `Run`, `Stop`, `Refresh`, `Copy`, `Export`, `Load`, `Reset`. |
| Same action placement | Primary actions trailing; mode/navigation leading; filters near content; destructive actions isolated. |
| Same control height | Buttons, inputs, combos, spinners, and icon buttons align to `CONTROL_HEIGHT`. |
| Same label lanes | Similar side rails use the same label width in a panel family. |
| Same surfaces | Top-level panes are opaque; cards are only grouped content; glass is only floating chrome. |
| Same state colors | Success/warning/error/info/missing/stale mean the same thing everywhere. |
| Same typography | Titles, section labels, captions, metadata, command text, and table text use the documented font roles. |
| Same verification | Every visual change gets light/dark and narrow/wide checks. |

If two panels solve the same UI problem differently, prefer the existing shared primitive. If no shared primitive fits, create one and migrate both panels to it.

## Banned Patterns And Required Alternatives

| Do not | Use instead |
| --- | --- |
| Raw `new JButton(...)` with local colors/borders | `Ui.button(...)`, `Ui.ghostButton(...)`, `Ui.destructiveButton(...)`, `Ui.iconButton(...)` |
| Raw `JScrollPane` styling | `Ui.scroll(...)` or `Ui.styleScrollPane(...)` |
| Local color constants for theme roles | `Theme` tokens, adding light and dark variants when needed |
| Local font creation | `Theme.font(...)`, `Theme.mono(...)`, `Theme.consoleMono(...)` |
| Full page wrapped in a card | `SurfacePanel` root with flat sections |
| Card inside card | Section header, hairline, or collapsible group |
| Large rounded decorative container | Flat panel, toolbar band, or real card with purpose |
| Gradient/orb/blob decoration | Quiet `BackdropPanel` or no decoration |
| Visible tutorial paragraphs in task screens | Tooltip, concise caption, or empty-state hint |
| Ambiguous button text like `OK` for domain actions | Specific action label like `Apply FEN`, `Run`, `Load PGN` |
| Hidden primary action only in context menu | Visible button plus optional context-menu duplicate |
| Toolbar with unrelated controls in one row | Leading/center/trailing grouped toolbar with separators |
| Search placeholder `search` | Specific placeholder: `search games`, `filter flags`, `filter logs` |
| Error shown only in console | Field border/status near the failed input plus console detail if needed |
| Layout fixed for one window size | Responsive behavior for narrow, normal, and wide widths |
| UI-only chess rules or notation logic | Shared `chess.core` / CLI-backed model |

## Source Of Truth

Use these files before creating a new primitive.

| Need | Use |
| --- | --- |
| Palette, spacing, fonts, radius, control dimensions | `src/application/gui/workbench/ui/Theme.java` |
| Public UI factories | `src/application/gui/workbench/ui/Ui.java` |
| Opaque top-level panes | `src/application/gui/workbench/ui/SurfacePanel.java` |
| Root window backdrop | `src/application/gui/workbench/ui/BackdropPanel.java` |
| Static raised content cards | `src/application/gui/workbench/ui/Card.java` through `Ui.card(...)` |
| Workspace top band | `Ui.workspaceHeader(...)` / `WorkspaceHeader` |
| Section headings | `Ui.sectionHeader(...)` / `SectionHeader` |
| Toolbar bands and rows | `Ui.styleToolbarBand(...)`, `Ui.controlRow(...)`, `Ui.toolbarSeparator(...)` |
| Forms and inputs | `Ui.fieldRow(...)`, `Ui.labelControlRow(...)`, `Ui.optionGroup(...)`, `Ui.styleFields(...)`, `Ui.placeholder(...)` |
| Buttons | `Ui.button(...)`, `Ui.ghostButton(...)`, `Ui.destructiveButton(...)`, `Ui.iconButton(...)` |
| Tables | `Ui.setColumnWidth(...)` and shared table styling through the component tree |
| Empty states | `Ui.emptyState(...)` or `Ui.paintEmptyState(...)` |
| Chessboard geometry and marks | `src/application/gui/workbench/board/BoardStyle.java` |
| Editor tab shell | `src/application/gui/workbench/layout/EditorSplitArea.java` and siblings |

If a panel needs custom painting, it should still consume `Theme` tokens and shared helpers. Local color literals are acceptable only for domain colors that are not theme roles, such as fixed annotation brush colors, and those should be documented by the owning class.

## Spacing And Sizing

Use the spacing scale in `Theme`. Do not invent new spacing constants in feature panels unless a custom-painted surface has a real geometry need.

| Token | Pixels | Use |
| --- | --- | --- |
| `SPACE_XS` | 4 | Tight gaps inside controls, table/header trim, small row separation |
| `SPACE_SM` | 8 | Label-to-control gaps, compact row gaps, toolbar group internals |
| `SPACE_MD` | 12 | Panel padding, card padding, default content gaps |
| `SPACE_LG` | 16 | Section separation, toolbar group separation, header action gap |
| `SPACE_XL` | 24 | Major page bands and report section breaks |
| `RADIUS` | 7 | Controls, cells, and compact surface corners |
| `CONTROL_HEIGHT` | 32 | Buttons, combos, compact spinners, icon-button height |
| `CONTROL_HEIGHT_TALL` | 40 | Larger text inputs or primary modal controls |
| `TABLE_ROW_HEIGHT` | 28 | Dense data rows |
| `CONTENT_MAX_WIDTH` | 1440 | Wide report/content cap |

Common component widths:

| Component | Width |
| --- | --- |
| Compact side-rail label | 68 to 92 px |
| Standard form label | 110 to 140 px |
| Draw/Play style label | 78 px |
| Relation controls label | 92 px |
| Small option group control | 120 px |
| Command-form lead column | 248 px |
| Command-form value column | 280 px |
| Long FEN/value field | 520 px |
| Board side rail | 320 to 380 px, usually 360 px |
| Dashboard summary card column | minimum 300 px |
| Larger dashboard/detail card column | minimum 420 px |
| Icon-only button | 34 px wide by `CONTROL_HEIGHT` high |

If a label is wider than the chosen label lane, do not let it push the control column. Shorten the label, use a tooltip, or use `Ui.elide(...)` in custom painting.

## Layout

Workbench screens are task surfaces. A good surface has one clear job, a compact header, a predictable toolbar, and content that can scan without horizontal wandering. Use this order from top to bottom:

1. Optional `WorkspaceHeader`: title and location on the leading side, live context in the middle, primary actions on the trailing side.
2. Optional toolbar band: navigation/mode controls leading, scoped controls centered, persistent actions/search trailing.
3. Main body: primary work area in the center, secondary details in a side rail, status/logs at the bottom only when they are persistent.
4. Empty/loading/error state inside the content area, not as a separate page shell.

- Use `SurfacePanel` or `Ui.panel(...)` for top-level editor content.
- Use `WorkspaceHeader` for the primary title, live context, and main actions.
- Use `SectionHeader` for local content groups that need a title, short detail, and trailing status/control.
- Use `Ui.styleToolbarBand(...)` for toolbar strips directly under tabs or mode switchers.
- Use `Ui.centeredViewport(...)` for report-like content with a readable maximum width.
- Use `Ui.contentGrid(...)` for dashboards or responsive collections of independent cards.
- Keep form controls top-aligned with `Ui.addVerticalFiller(...)` when a form panel stretches.
- Pin fixed-format elements with stable sizes: boards, tabs, icon buttons, table rows, eval bars, charts, and counters should not resize when labels or states change.

Avoid nested cards, page sections styled as cards, and rounded containers around every row. Use whitespace, hairlines, and section headers first.

### Canonical Surface Layout

Every major Workbench surface or mode should fit one of these two layouts.

Layout A: editor surface with optional side rail

| Region | Swing position | Required | Contents | Resize behavior |
| --- | --- | --- | --- | --- |
| Root | `SurfacePanel(new BorderLayout(...))` | Yes | Owns one complete Workbench surface | Fills editor host |
| Header | `BorderLayout.NORTH` inside root or wrapper | Usually | `WorkspaceHeader` with title, context, actions | Fixed height, context elides |
| Toolbar | Below header in a stacked north panel | Optional | Mode, search/filter, view controls, scoped actions | One row if possible; wraps only through shared flow rows |
| Body | `BorderLayout.CENTER` | Yes | Primary work area | Grows and shrinks first |
| Side rail | `BorderLayout.EAST` or split secondary | Optional | Inspectors, settings, move list, local controls | 320 to 380 px preferred, collapsible when narrow |
| Bottom panel | `BorderLayout.SOUTH` or separate Workbench panel | Optional | Logs, run output, artifact list | Height-capped and scrollable |
| Floating layer | `JLayeredPane` / modal overlay | Optional | Command palette, dialogs, toasts | Does not change base layout |

Layout B: dashboard/report surface

| Region | Swing position | Required | Contents | Resize behavior |
| --- | --- | --- | --- | --- |
| Root | `SurfacePanel(new BorderLayout(...))` | Yes | Whole report/dashboard | Fills editor host |
| Header | `BorderLayout.NORTH` | Yes | `WorkspaceHeader` | Fixed height |
| Scroll body | `BorderLayout.CENTER` | Yes | `Ui.scroll(Ui.centeredViewport(...))` or `Ui.contentGrid(...)` | Scrolls vertically |
| Cards/grid | Inside scroll body | Optional | Summary cards, charts, previews | Reflows by min column width |
| Tables | Inside scroll body or main body | Optional | Dense records/details | Fill width; important columns pinned |

Use Layout A for Board, Engine Lab, Run, Publish, and the inspector modes inside them. Use Layout B for Dashboard, report-like publishing summaries, dataset overviews, and static diagnostics.

### Region Rules

| Region | Must do | Must not do |
| --- | --- | --- |
| Header | Show stable title, changing context, and local primary actions | Contain long help text, filters, or dense settings |
| Toolbar | Hold controls that affect the current view | Become a second header or a paragraph row |
| Body | Show the main artifact: board, table, graph, preview, console, or form | Compete with side rail for attention |
| Side rail | Support the body with controls, inspectors, and secondary lists | Become the only place to understand the screen |
| Bottom panel | Show persistent output/logs | Replace validation or local status that belongs near a field |
| Floating layer | Ask focused questions or show transient feedback | Host permanent workflow content |

### Resizing Rules

| Width class | Behavior |
| --- | --- |
| Narrow, below 1000 px | Side rails collapse, move to tabs, or become vertically stacked below the main body. Toolbars keep essential actions and elide context. |
| Normal, 1000 to 1600 px | Default layout: main body plus 320 to 380 px side rail. Toolbar remains one row. |
| Wide, above 1600 px | Main body can grow; report content may cap at `CONTENT_MAX_WIDTH`; side rail does not grow beyond its useful width. |

If a wide window makes a table, graph, or form harder to scan, cap or center the content. If a narrow window clips buttons, remove secondary text, move actions into overflow, or stack the side rail. Do not shrink fonts.

### Workbench Chrome Anatomy

Every screen should be easy to identify from its chrome alone.

| Chrome zone | Inspired by | Contents | Hard rule |
| --- | --- | --- | --- |
| Editor tab strip | VS Code editor tabs | Tab name, close affordance, split/overflow controls | Tab names are nouns; state does not live in the tab label unless it is essential. |
| Workspace header | Apple title/toolbar grouping | Title, one-line context, primary actions | Title is stable; context changes with selected FEN/job/model. |
| Toolbar band | VS Code view toolbar + Apple toolbar groups | Mode switcher, filters, search, view actions | One visual row when possible; group by function and frequency. |
| Side rail | VS Code sidebar | Controls, inspectors, move lists, settings for the center view | Side rail supports the center; it is not a second main screen. |
| Bottom panel | VS Code panel/status area | Logs, console output, run artifacts | Long-running output goes here, not in modal alerts. |
| Floating chrome | Apple popover/sheet feel | Command palette, dialogs, confirmation, toasts | Raised/frosted cues allowed; content panes stay opaque. |

### Toolbar Placement

Use Apple-style leading/center/trailing grouping inside VS Code-like toolbar density.

| Position | Put here | Examples |
| --- | --- | --- |
| Leading | Navigation, mode identity, local back/forward, sidebar toggles | Analyze mode switcher, board transport, visible section selector |
| Center | Current-view controls that change representation | Table filters, graph depth, draw tool mode, tree layout controls |
| Trailing | Persistent actions, search, inspectors, overflow/more | Run, Copy command, Export, Open folder, Search, More |

Search placement:

- Global or cross-view search belongs on the trailing side of the header/toolbar.
- Local list/table filtering belongs directly above or beside the list it filters.
- Search placeholders must name the searched content: `filter flags`, `search games`, `filter jobs`, not plain `search`.
- If the dataset is local and small enough, filter while typing.
- If search starts an expensive operation, debounce it and show a running state.

### Screen Recipes

Use these layouts as defaults.

| Surface type | Layout |
| --- | --- |
| Board workspace | One shared board stage in `CENTER`, eval bar attached to the board, 320 to 380 px right inspector rail, `SplitPaneStyler` divider around 0.65 to 0.70 |
| Board analysis mode | Board remains stable; legal moves, engine controls, tags, ECO, review, study, endgame, and editor tools live in rail sections or local tabs |
| Board play mode | Board and move history remain dominant; opponent/settings controls sit in compact side groups, not across the board |
| Engine Lab | Search/evaluator evidence owns the center; backend/model controls and selected-node/tensor details sit in the rail |
| Run workspace | Template/command selection first, required inputs above optional flags, exact command preview visible, run/copy actions grouped together |
| Dashboard | `WorkspaceHeader`, then `Ui.contentGrid(300)` for summaries and `Ui.contentGrid(420)` for larger detail cards |
| Datasets | Header and load actions on top, summary cards before charts/tables, tables fill the remaining vertical space |
| Publish | Source/task controls in a side or top panel, preview takes the main area, render actions stay near preview status |
| Relations mode | Board/graph visualization centered, channel and opacity controls in a right rail with fixed label widths |
| Logs/console mode | Header actions right, scrollable log body center, status badges inline with rows |
| Modal/dialog | Floating chrome, max-width content, primary action bottom right, cancel/close adjacent or top right depending on dialog type |

### Surface Home Rules

Use this table before creating, moving, or renaming a view.

| Feature or mode | Target home | Rule |
| --- | --- | --- |
| Analyze | `Board` | Position-first rail mode over the shared board. |
| Play | `Board` | Human-vs-engine mode over the shared board, with Play owning input while active. |
| Solve/Puzzles | `Board` | Puzzle mode; may keep an independent puzzle board only when puzzle progression requires it. |
| Relations | `Board` | Read-only tactical overlay mode over the shared board. |
| Draw | `Board` | Annotation/export mode over the shared board. |
| Board editor | `Board` | Setup/editor section in Analyze or a Board mode, not a separate top-level surface. |
| Network | `Engine Lab` | Evaluator/model visualization mode. |
| MCTS/Search Tree | `Engine Lab` | Search mode with root moves, tree, selected node, and backend controls. |
| Gauntlet | `Engine Lab` | Engine comparison mode with reproducible command/status output. |
| Commands | `Run` | Build mode for exact `crtk` commands. |
| Batch | `Run` | Batch mode for repeated command runs over positions/scripts. |
| Console | `Run` | Output/REPL mode or first-class output companion during migration. |
| Logs | `Run` plus `Dashboard` | Output/log browser and recent-run dashboard card. |
| Datasets | `Datasets` | Top-level analytics surface. |
| Publish | `Publish` | Top-level preview/render surface. |

### Surface And Mode Blueprints

Use these as the target shape when adapting current Workbench implementation. Rows that belong to `Board`, `Engine Lab`, or `Run` are modes or inspector states, not necessarily top-level tabs.

| Surface/mode | Header title | Header context | Leading toolbar | Center/body | Side rail | Trailing actions |
| --- | --- | --- | --- | --- | --- | --- |
| Dashboard | `Dashboard` | Session health, recent artifact count, active jobs | None unless mode switch needed | Summary grid, recent jobs, artifacts | None by default | Refresh, open session folder |
| Analyze | `Analyze` | Current FEN summary, side to move, active source | Board transport, analysis mode | Board stage with eval bar | Moves, engine, ECO, tags, review/study tools | Load PGN, copy FEN, export board |
| Board editor | `Board Editor` or section within Analyze | Edited FEN validity | Piece/setup mode | Board stage | Piece palette, side to move, castling, en passant | Apply, copy FEN, reset |
| Play | `Play` | Opponent preset, side, current result/status | New game/start source | Board stage and move history | Opponent, strength, custom backend, game controls | New game, resign, copy PGN |
| Draw | `Draw` | Markup count and selected tool | Tool mode, undo/redo | Board stage | Shape, brush, color, opacity, target controls | Clear, export, copy |
| Commands | `Commands` | Selected command and validity | Area/action selection | Required inputs, optional flags, command preview | Optional command help/details if needed | Run, copy command, clear |
| Batch | `Batch` | Row count, valid rows, active job | Source mode | Position/script editor and result table | Validation, run options | Run, stop, import, export |
| Console | `Console` | Active run or idle status | None | Scrollable terminal-style output | None unless filters are needed | Copy, clear, open log |
| Logs | `Logs` | Selected log, job count | Filter/search | Log table and selected log output | Artifact details if useful | Refresh, open folder, copy path, clean |
| Datasets | `Datasets` | Loaded file, row count, shape | Dataset mode | Summary cards, charts, tables | Load/options/details when useful | Load, refresh, export/copy |
| Publish | `Publish` | Selected task and output state | Task/source selection | Preview canvas | Manifest/task fields, validation | Render, check, export/open |
| Puzzles | `Puzzles` | Loaded puzzle set, index, solve state | Puzzle navigation | Board stage and solution line | Puzzle metadata, tags, reveal controls | Load, reveal, next, copy |
| Network | `Engine Lab` | Model, backend, position/inference state | Network/view mode | Visualization canvas, activation boards/charts | Model/runtime/options | Load, refresh, export |
| Relations | `Relations` | Current FEN, enabled channel count | Sync/source mode | Board/graph visualization | Channel toggles, opacity, max arrows | Sync, copy, export |
| MCTS | `Search Tree` | Search status, nodes, root move | Search mode/depth | Tree graph/table and root rows | Backend, weights, selected node details | Start, stop, snapshot, export |
| Gauntlet | `Gauntlet` | Candidate/baseline, games, WDL/Elo | Setup/run mode | Game list, summary table, selected game board | Engine configs, budgets, filters | Start, stop, copy command, open artifacts |
| Settings | `Settings` | Active preference group | Group selector | Form sections | None unless preview needed | Apply, reset, close |

Uniform adaptation rule: if a current view cannot be described by this table, first decide whether it belongs in the center body, side rail, toolbar, bottom panel, floating layer, or one of the six target surfaces. Do not add new chrome until that decision is made.

### Shell Skeleton

Use `BorderLayout` for top-level screens. Put the header and toolbar in `NORTH`, the working canvas in `CENTER`, and avoid absolute positioning.

```java
SurfacePanel root = new SurfacePanel(new BorderLayout(0, Theme.SPACE_MD));
WorkspaceHeader header = Ui.workspaceHeader("Datasets", contextText, actions);
root.add(header, BorderLayout.NORTH);

JPanel body = Ui.transparentPanel(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_MD));
body.add(mainContent, BorderLayout.CENTER);
body.add(sideRail, BorderLayout.EAST);
root.add(body, BorderLayout.CENTER);
```

### Toolbar Recipe

Toolbar bands are horizontal and single purpose. Put navigation/mode controls on the leading side, view-shaping controls in the center when needed, and actions/search on the trailing side. Separate unrelated control groups with `Ui.toolbarSeparator()`.

```java
JPanel toolbar = Ui.transparentPanel(new BorderLayout());
toolbar.add(Ui.controlRow(FlowLayout.LEFT, modeSwitch, backButton, forwardButton), BorderLayout.WEST);
toolbar.add(Ui.controlRow(FlowLayout.CENTER, depthSlider, Ui.toolbarSeparator(), filterChips), BorderLayout.CENTER);
toolbar.add(Ui.controlRow(FlowLayout.RIGHT, searchField, refreshButton, exportButton), BorderLayout.EAST);
Ui.styleToolbarBand(toolbar, Theme.pad(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_SM, Theme.SPACE_MD));
```

Do not place long explanatory text in a toolbar. If the user needs context, put it in the `WorkspaceHeader` context string or in a section caption.

### Form Recipe

Use fixed label lanes so controls line up. Required fields go first, optional controls go below, and destructive actions stay out of the main run/apply action row.

```java
JPanel form = Ui.transparentPanel(new GridBagLayout());
GridBagConstraints c = Ui.constraints();
Ui.grid(form, Ui.fieldRow("FEN", fenField, 120), c, 0, 0, 2, 1);
Ui.grid(form, Ui.fieldRow("Time", durationField, 120), c, 0, 1, 1, 1);
Ui.grid(form, Ui.fieldRow("Lines", multipvSpinner, 120), c, 1, 1, 1, 1);
Ui.grid(form, Ui.controlRow(FlowLayout.RIGHT, runButton, copyButton), c, 0, 2, 2, 1);
Ui.addVerticalFiller(form, c, 3, 2);
```

For narrow side rails, prefer `Ui.labelControlRow("Search", control, 78)` or another fixed lane from the spacing table. For full command forms, keep the larger command-form columns so every row starts at the same x coordinate.

### Side Rail Recipe

Side rails are compact inspectors. They should feel like a VS Code sidebar with Apple-style form discipline.

```java
SurfacePanel rail = new SurfacePanel(new BorderLayout(0, Theme.SPACE_MD));
rail.setPreferredSize(new Dimension(360, 520));

JPanel sections = Ui.transparentPanel(new GridLayout(0, 1, 0, Theme.SPACE_MD));
sections.add(Ui.titled("Engine", engineControls));
sections.add(Ui.titled("Moves", Ui.scroll(movesTable)));
sections.add(Ui.collapsible("Advanced", advancedControls, false));

rail.add(Ui.scroll(Ui.fillViewport(sections)), BorderLayout.CENTER);
rail.add(Ui.controlRow(FlowLayout.RIGHT, applyButton), BorderLayout.SOUTH);
```

Side rail content order:

1. Status or mode summary, only if the header context is not enough.
2. Required controls.
3. Current selection details or list.
4. Optional/advanced controls in a collapsible section.
5. Local action row, trailing aligned.

### Table Recipe

Tables are for exact comparison. They should be dense, stable, and readable.

```java
JTable table = new JTable(model);
Ui.styleComponentTree(table);
table.setRowSelectionAllowed(true);
table.setColumnSelectionAllowed(false);
Ui.setColumnWidth(table.getColumnModel(), 0, 80);
Ui.setColumnWidth(table.getColumnModel(), 1, 180);
JScrollPane scroller = Ui.scroll(table);
```

Table rules:

- Row height is `Theme.TABLE_ROW_HEIGHT`.
- Header text is short and muted.
- Numeric columns align consistently within a table.
- Important columns have preferred widths.
- Status is a badge or short text, not a paragraph.
- Empty table content uses `Ui.emptyState(...)`, not a blank white box.
- Sorting/filtering controls sit immediately above the table.

### Empty, Loading, And Error Recipe

Every surface must define these states:

| State | Layout | Copy | Actions |
| --- | --- | --- | --- |
| Empty | Centered in the content area or compact inside side rail | `No dataset loaded`, `No log selected`, `No search yet` | One clear next action when useful |
| Loading | Same footprint as the eventual content when possible | `Loading model`, `Running search`, `Rendering preview` | Stop/cancel only if supported |
| Error | Near the failed control or content | Specific path/FEN/command failure | Retry, browse, reset, or copy error |
| Missing optional dependency | Status badge plus compact explanation | `Missing model`, `Stockfish unavailable` | Configure, browse, or fall back |
| Disabled | Control remains visible and readable | Tooltip explains why unavailable | No hidden state |

Do not replace a whole screen with an error page unless the entire screen cannot function.

## Surfaces And Elevation

Top-level work areas are flat and opaque. That keeps repainting clean, makes the app feel like an editor, and avoids muddy text over translucent content.

| Surface | Use it for | Avoid |
| --- | --- | --- |
| `SurfacePanel` | Main editor regions, side rails, mode content | Rounded panels, shadows, transparent panes |
| Toolbar band | Tab-local controls and mode switchers | Floating card toolbars |
| `Ui.card(...)` | Setup forms, chart panels, summaries, grouped static content | Wrapping full pages or placing cards inside cards |
| Backdrop wash | Window edge/background treatment | Visible decorative gradients in content |
| Floating chrome | Command palette, modal overlays, dialogs, popovers, toasts | Permanent content sections |

The frosted-glass idea is a restraint, not a theme. Use `Theme.GLASS_HIGHLIGHT`, backdrop colors, and existing floating primitives rather than painting translucent panels over live content.

Surface styling rules:

- Main editor surfaces use `Theme.PANEL_SOLID`, not translucent fills.
- Side rails use the same surface family as the main pane, separated by a hairline or split divider.
- Cards sit one elevation above the page and must have a clear grouping purpose.
- Floating chrome may use `Theme.GLASS_HIGHLIGHT`, soft borders, and shadow, but must keep text over an opaque or solid-blended surface.
- Shadows should be shallow and functional. If the element is not movable, modal, or floating, it probably does not need a shadow.
- Border radius stays close to `Theme.RADIUS`; do not introduce large pill/card radii just for decoration.

## Color And Theme

All visual roles should be theme tokens. Update both `applyLightPalette` and `applyDarkPalette` when adding a token, and refresh tests that pin palette behavior.

Important token groups:

| Role | Tokens |
| --- | --- |
| Backgrounds | `BG`, `PANEL`, `PANEL_SOLID`, `ELEVATED`, `ELEVATED_SOLID`, `CARD` |
| Lines and text | `LINE`, `TEXT`, `MUTED`, foreground roles |
| Actions | `ACCENT`, `ACCENT_HOVER`, `ACCENT_PRESSED`, button variant tokens |
| Inputs | `INPUT`, `INPUT_BORDER`, `INPUT_FOCUS`, `FOCUS_RING`, `TEXT_SELECTION` |
| Status | `STATUS_SUCCESS_*`, `STATUS_WARNING_*`, `STATUS_ERROR_*`, `STATUS_INFO_*` |
| Board | `BOARD_LIGHT`, `BOARD_DARK`, `BOARD_HIGHLIGHT`, `LEGAL_TARGET`, `BOARD_ARROW` |
| Network | `NN_POSITIVE`, `NN_NEGATIVE`, `NN_POLICY`, `NN_VALUE`, `NN_NEUTRAL` |

Do not hard-code a light-only or dark-only value in a panel. If the value represents a reusable role, add or reuse a token.

Color behavior:

- VS Code neutral surfaces carry most of the interface; Apple-style blue is for primary actions, focus, and selected interactive state.
- Use semantic status colors only for status: success, warning, error, info, ready, running, complete, missing, stale.
- Do not use accent blue for passive headings, decorative lines, or noninteractive illustrations.
- Board and annotation colors are domain colors; they must remain visually distinct from status colors.
- In custom painting, always test light and dark mode because alpha over dark surfaces changes perceived contrast.

## Typography And Density

Use `Theme.font(...)`, `Theme.mono(...)`, or `Theme.consoleMono(...)`. Do not construct arbitrary fonts in feature panels.

| Text role | Token |
| --- | --- |
| Page title | `FONT_PAGE_TITLE` |
| Section title | `FONT_SECTION_TITLE` |
| Body/control text | `FONT_BODY`, `FONT_CONTROL` |
| Dense table text | `FONT_DENSE_TABLE` |
| Caption and metadata | `FONT_CAPTION`, `FONT_METADATA` |
| Code, commands, logs | `FONT_MONO`, `consoleMono(...)` |

Keep headings proportional to their container. A compact panel should not use hero-size type. Let `Theme.Density` scale fonts globally; do not scale text with viewport width.

Text hierarchy:

| Role | Placement | Rule |
| --- | --- | --- |
| Page title | Workspace header only | Stable noun, usually one or two words |
| Context | Workspace header center | One line, elided, tooltip contains full value |
| Section title | Above grouped content | Short noun phrase, no punctuation |
| Caption | Under/next to a control group | One line explaining ambiguity only |
| Metadata | Tables, badges, timestamps, model names | Muted, compact, scannable |
| Command/code | Command previews, logs, FEN, JSON, tensors | Monospace, selectable/copyable where useful |

## Controls

Controls should look and behave the same across panels.

| Task | Preferred control |
| --- | --- |
| Main action | `Ui.button(text, Theme.ButtonVariant.PRIMARY, listener)` |
| Standard action | `Ui.button(text, Theme.ButtonVariant.SECONDARY, listener)` |
| Low-emphasis action | `Ui.ghostButton(...)` |
| Destructive action | `Ui.destructiveButton(...)` |
| Icon-only utility | `Ui.iconButton(label, listener)` |
| Two or more modes | `Ui.segmentedControl(...)` |
| Binary setting | `ToggleBox`, styled checkbox, or settings chip row |
| Numeric value | styled spinner, slider, or validated text field |
| Option set | styled combo box or popup menu |
| Section collapse | `Ui.collapsible(...)` |
| Loading/progress | shared progress bar, status badge, toast, or loading overlay |

Icon-only buttons must have a tooltip and accessible name. Text buttons should be reserved for clear commands. Do not create raw Swing controls unless you immediately pass them through the shared styling helpers.

## VS Code And Apple Component Translation

Use this table when choosing or styling a component.

| Workbench element | VS Code role | Apple styling behavior | Implementation |
| --- | --- | --- | --- |
| Major tab | Editor tab | Compact, label-first, close affordance only when useful | `EditorSplitArea`, `EditorTab` |
| Split pane | Editor group split | Divider is functional and quiet | `SplitPaneStyler` |
| Side rail | Sidebar/view container | Related controls grouped, 3 to 5 sections max before tabs/collapsible groups | `SurfacePanel`, `Ui.titled(...)`, `Ui.collapsible(...)` |
| Bottom logs | Panel | Output is scrollable, persistent, and nonmodal | `Console`, `LogPanel`, `Ui.scroll(...)` |
| Command palette | Command palette / quick pick | Searchable, category names clear, keyboard-friendly | `CommandPalette` |
| Toolbar icon | View/editor toolbar action | Symbol or short text, tooltip required, grouped by function | `Ui.iconButton(...)`, `Ui.button(...)` |
| Search/filter | Quick pick/search field | Specific placeholder, live filtering where cheap | Styled `JTextField`, `Ui.placeholder(...)` |
| Inspector/control group | Sidebar view | Label lane fixed, controls aligned, captions sparse | `Ui.labelControlRow(...)`, `Ui.fieldRow(...)` |
| Status | Status bar item / notification | Terse, semantic, near affected content | `StatusBadge`, `Toast` |
| Dialog | Modal/sheet | Focused task, primary action trailing, destructive isolated | `Ui.showConfirmDialog(...)`, `ModalOverlay` |

## Element Styling Rules

This table is the default styling contract for common UI elements.

| Element | Layout | Style | Behavior |
| --- | --- | --- | --- |
| Workspace header | Full width, top of surface | `PANEL_SOLID`, bottom `LINE`, title + eliding context + right actions | Context text gets tooltip with full value |
| Toolbar band | Directly below tab/header | Opaque `PANEL_SOLID`, bottom hairline, `SPACE_SM`/`SPACE_MD` padding | One row, no wrapping paragraphs |
| Section header | Above a local group | Bold section title, muted one-line detail, trailing status/control | Detail text should fit one line |
| Card | Inside content grid or grouped static area | `Ui.card(...)`, raised `CARD`, `CARD_BORDER`, shared radius | No nested cards; no full-page card wrappers |
| Text button | In action rows/header actions | `AppButton` via `Ui.button(...)`, variant by hierarchy | One primary per local scope |
| Icon button | Toolbars, transport, small utilities | `Ui.iconButton(...)`, 34 x 32, no visible text | Tooltip and accessible name required |
| Text field | Forms/search/filter/FEN input | `Ui.styleFields(...)`, `INPUT`, focus ring, placeholder if useful | Validate live when invalid input can block Run |
| Text area | Logs, scripts, multiline positions | `Ui.styleAreas(...)`, monospace when code-like | Keep line wrapping intentional |
| Combo box | Option set with many choices | `Ui.styleCombo(...)` | Use direct choices; avoid a combo inside a combo-like chip |
| Spinner | Numeric bounded values | `Ui.styleSpinner(...)` or integer variant | Set min/max/step in the model |
| Slider | Opacity, strength, visual scale | `Ui.styleSlider(...)`, labelled with fixed lane | Pair with numeric/readable value when precision matters |
| Checkbox/toggle | Binary setting | `ToggleBox` or `Ui.styleCheckBox(...)` | Label names the on/off behavior |
| Segmented switcher | Small mode set | `Ui.segmentedControl(...)` | Best for 2 to 5 mutually-exclusive modes |
| Table | Dense records, moves, jobs | Shared table styling, row height 28, hidden grid, hover row | Pin important column widths |
| Scroll pane | Any scrollable body | `Ui.scroll(...)`; matching viewport surface when embedded | Avoid raw scrollbars and mismatched viewport fills |
| Empty state | No data, no result, no selection | `Ui.emptyState(...)` or `Ui.paintEmptyState(...)` | Short title, one-line hint, optional action row |
| Status badge | Job/model/config state | Semantic status tokens | Reserve width if text changes often |
| Toast | Transient confirmation or warning | Bottom-right toast layer | Do not use for persistent errors |
| Modal overlay | Blocking choice or focused dialog | Floating layer with styled confirm/error content | Keep primary action bottom right |
| Split pane | Board/detail or editor split | `SplitPaneStyler` | Divider should not dominate content |
| Tabbed pane | Local tabs inside a surface | `Ui.tabbedPane()` / `Ui.styleTabs(...)` | Use scrollable single row for many tabs |

Side-rail rules:

| Rule | Detail |
| --- | --- |
| Width | Prefer 320 to 380 px; 360 px is the normal board-work default. |
| Sections | Keep 3 to 5 visible sections; collapse advanced groups. |
| Labels | Use 68 to 92 px fixed lanes for compact controls. |
| Tables | Let tables fill available height; pin important columns. |
| Actions | Keep primary side-rail action at top or bottom right of its local group. |
| Empty state | Show a compact empty state inside the rail, not in the board center. |

Button hierarchy:

| Variant | Use | Do not use for |
| --- | --- | --- |
| Primary | Run, Apply, Load, Start, Export when it is the main action | Multiple same-weight actions in one row |
| Secondary | Copy, Refresh, Browse, Import, Save as supporting actions | Destructive changes |
| Ghost | Low-emphasis navigation, reveal, optional utility | Primary task completion |
| Destructive | Stop, Clear, Delete, Resign, Reset dangerous state | Normal cancel/close |

Control placement:

- Primary actions go right in the `WorkspaceHeader`, right in a toolbar, or bottom right in dialogs.
- Mode selectors go left because they change what the surface is.
- Filters/search fields go left after mode selectors.
- Export/copy/open-folder actions go right.
- Destructive actions go after a separator or in a clearly named danger group.
- Status badges sit near the thing they describe, not in a remote footer.
- Validation messages live on the field tooltip/status, not as paragraphs above the form.

## State Styling Matrix

Every reusable component must implement these states consistently.

| State | Visual treatment | Interaction rule |
| --- | --- | --- |
| Resting | Neutral surface, readable text, no excess border emphasis | Ready for normal interaction |
| Hover | Slight fill or border change, never layout shift | Pointer only; do not change value |
| Focus | `FOCUS_RING` or themed focus border visible in light/dark | Keyboard users can see current target |
| Pressed | Button/control uses pressed token briefly | Action fires once |
| Selected | Selection fill or accent underline, text remains readable | Current mode/tab/row is unambiguous |
| Disabled | Muted text, disabled background/border, tooltip explains reason when non-obvious | Cannot receive action but remains legible |
| Loading | Progress indicator, spinner, animated badge, or busy text | Long tasks do not freeze UI |
| Error | Error border/status token near failing input/content | User can identify exact failed field/path/command |
| Warning | Warning badge/text, not red | Warns without blocking unless action would be unsafe |
| Success/complete | Success badge or transient toast | Does not permanently dominate the surface |
| Missing | Warning/missing badge plus fallback path | Shows whether fallback is active |
| Stale | Warning/stale badge near old result | User can refresh or rerun |

State implementation rules:

- State changes must not resize controls.
- State text that changes often must reserve width or elide.
- Disabled icon buttons keep their icon but mute it.
- Focus and hover must survive theme refresh.
- Selection and focus can coexist; selection shows value, focus shows keyboard target.
- A red/error style is reserved for invalid input or destructive failure, not "current danger mode" decoration.

## Responsive And Overflow Rules

The Workbench must remain usable at laptop widths and large desktop widths.

| Problem | Required response |
| --- | --- |
| Header context is too long | Elide in the header and put full text in tooltip. |
| Toolbar actions do not fit | Keep primary and search visible; move secondary actions into overflow or context menu. |
| Side rail crowds the board | Collapse side rail into tabs or move below body. |
| Form row does not fit | Stack fields vertically but keep label lanes aligned within each group. |
| Table columns overflow | Pin important columns; let low-priority columns elide or scroll. |
| Chart labels overlap | Reserve label lane, elide labels, or show details in tooltip. |
| Button text clips | Shorten label or switch to icon button with tooltip; do not shrink font. |
| Dialog is too tall | Make body scroll, keep action row fixed. |

Minimum visual checks:

- 1000 px wide light mode.
- 1000 px wide dark mode.
- 1600 px wide light mode.
- 1600 px wide dark mode.
- At least one empty state.
- At least one invalid/error state.
- At least one disabled state.

## Forms And Command Builders

Command UI mirrors the CLI. The command preview is not decorative; it is the exact runnable command users can copy or execute.

- Keep command order noun-then-verb.
- Prefer named flags and explicit defaults over hidden state.
- Validate before running and explain invalid values through field state and tooltip.
- Disable stale mutually-exclusive options rather than leaving contradictory selections active.
- Use direct selector choices for simple formats instead of nested controls.
- Keep generated command text stable and quote arguments deterministically.
- Use `FieldValidator` for numeric inputs, ranges, and optional units.

When a form exposes engine or model behavior, be honest about fallback and fidelity. Do not imply bit-exact LC0/BT4 parity.

Command palette and menu rules:

- Command names use `Category: Action` when they appear in a global palette.
- Names start with verbs for actions: `Analyze: Run`, `Board: Export SVG`, `Game: Load PGN`.
- Names start with nouns for views: `Open Search Tree`, `Open Engine Lab`, `Open Logs`.
- Do not use emoji, jokes, or internal codenames in command names.
- Do not override common keyboard shortcuts without a Workbench-wide reason.
- Context menus repeat local secondary actions; they do not hide the only way to run a primary action.
- Any toolbar command that matters should also be reachable from a menu, command palette, or visible form action.

## Board And Chess Interaction

The board is the most important visual surface in the app. It should be quiet, immediate, and familiar to chess users.

- Use `BoardStyle` for square geometry, board colors, highlights, legal targets, arrows, and shared painting.
- Keep board input close to lichess behavior: drag pieces, right-drag arrows, right-click circles, and clear legal-move feedback.
- Do not add instructional tooltips over normal board play.
- Do not steal arrow keys from text fields, tables, lists, combos, or spinners.
- Keep board overlays layered predictably: coordinates, highlights, drag state, premove state, arrows, markup, and pieces should not fight each other.
- Use shared board rendering/export helpers so screen output, PNG, and SVG stay visually aligned.

Any change that touches board geometry, input, overlays, or export needs visual verification plus `WorkbenchBoardRegression` through the aggregate Workbench regression test.

Board layout rules:

| Element | Placement | Rule |
| --- | --- | --- |
| Board | Center of the primary area | Keep square, stable, and larger than any adjacent inspector. |
| Eval bar | Attached to board edge | Does not steal focus or resize the board unexpectedly. |
| Move list | Right rail or adjacent table | Dense rows, SAN readable, current move highlighted. |
| Engine controls | Side rail or toolbar | Budget controls use fixed labels and validated values. |
| Board editor | Side rail plus board interaction | Direct editing state is explicit and easy to exit. |
| Draw tools | Side rail controls, board interaction center | Tool mode visible; color/opacity controls use fixed label lanes. |
| Export actions | Header/toolbar trailing side | Exported screen, PNG, and SVG should match the same board styling. |

## Data, Charts, And Network Views

Research views should be information-dense without becoming unreadable.

- Tables should use compact row heights, hidden grid lines, hover state, sortable headers, and stable column widths.
- Charts should clear their own background and show shared empty states when no data exists.
- Network visualizations should use semantic positive/negative/policy/value colors, not arbitrary gradients.
- Use monospace text for tensor names, command snippets, logs, and code-like data.
- Prefer readable legends and labels over decorative color volume.

If labels can overflow, use `Ui.elide(...)`, fixed label lanes, reserved badge widths, or a tooltip with the full text.

Data layout rules:

- Put summary cards above detailed tables only when they change how the table is interpreted.
- Use tables for comparison and exact values; use charts for shape, distribution, or trend.
- Put filters directly above the table/chart they affect.
- Put export/copy actions on the trailing side of the toolbar or section header.
- Use status badges for model/runtime availability; use toasts only for transient confirmations.
- When a neural view has no model, show explicit missing-model state and the configured path when useful.
- Do not create decorative neural diagrams that imply unavailable inference detail.

## Copy

Workbench copy should be short and operational.

- Use top-level titles like `Dashboard`, `Board`, `Engine Lab`, `Run`, `Datasets`, and `Publish`.
- Use mode titles like `Analyze`, `Play`, `Solve`, `Relations`, `Draw`, `Search`, `Tree`, `Gauntlet`, `Build`, and `Logs`.
- Use action labels that name the command: `Run`, `Load PGN`, `Copy command`, `Export SVG`, `Reset`, `Stop`.
- Use captions only where they reduce ambiguity in a form or status surface.
- Avoid visible paragraphs explaining how the UI works when the affordance can carry the job.
- Use tooltips for compact icon controls and precise validation messages.
- Report missing optional models or engines as explicit availability states, not soft placeholders.

For user-facing engine/network text, keep neural-network honesty: ChessRTK evaluators are usable research evaluators, not bit-exact upstream LC0/BT4 implementations.

Copy style:

| UI text | Standard |
| --- | --- |
| Button | Verb or verb phrase: `Run`, `Load PGN`, `Copy command` |
| Toggle/checkbox | State as a noun phrase: `Show coordinates`, `Use animations` |
| Field label | Short noun: `FEN`, `Time`, `Lines`, `Threads` |
| Placeholder | Specific input hint: `paste FEN`, `filter flags`, `search games` |
| Tooltip | One sentence naming action and shortcut if relevant |
| Empty title | State, not instruction: `No dataset loaded` |
| Empty hint | One concrete next step |
| Error | What failed and what input/path caused it |
| Badge | One or two words: `Ready`, `Running`, `Missing`, `Stale` |

Icon and symbol rules:

- Use shared icon/button primitives; do not hand-paint a symbol unless no shared primitive exists.
- Prefer familiar symbols for back, close, search, copy, export, open, refresh, stop, and settings.
- Icon-only controls require tooltip and accessible name.
- Do not put a text label inside a rounded rectangle when a standard icon button is clearer.
- Keep icon stroke/fill weight visually consistent with the rest of the toolbar.
- Do not use color alone to distinguish icons; selected/disabled/hover states need shape, opacity, or background change too.

## Accessibility And Interaction

Every control should be reachable, legible, and understandable without relying on color alone.

- Keep text contrast passing in both light and dark modes.
- Ensure disabled controls remain readable and visibly disabled.
- Give icon-only buttons accessible names and tooltips.
- Preserve keyboard behavior for editor tabs, forms, and board navigation.
- Do not let long labels push action buttons offscreen.
- Reserve stable dimensions for controls whose text changes at runtime.
- Show focus rings for keyboard users.
- Keep modal overlays, command palette, toasts, and popups on the correct `Theme.Z_*` layer.
- Ensure search/filter fields expose their purpose through placeholder and accessible name.
- Prefer live validation for local forms; never launch a command with known-invalid inputs.
- Keep pointer targets at least `CONTROL_HEIGHT` high unless the component is table text.
- Keep hover/focus feedback subtle but present, matching VS Code density and Apple clarity.

## Implementation Audit Targets

Use this section when adapting the current Workbench. Search first, classify every hit, then replace local styling with the shared primitive unless an allowed exception applies.

| Search target | Why it matters | Preferred fix | Allowed exception |
| --- | --- | --- | --- |
| `new JButton` | Raw buttons drift in height, padding, color, focus, tooltip, and disabled behavior | Use `Ui.button(...)`, `Ui.ghostButton(...)`, `Ui.destructiveButton(...)`, or `Ui.iconButton(...)` | Shared button primitives and scrollbar arrow-button internals |
| `Ui.button` with one-symbol labels | Transport and toolbar controls look like text buttons instead of icon controls | Use `Ui.iconButton(...)` or add a shared transport-button primitive with tooltip and accessible name | Math signs in numeric steppers only when the control is explicitly a stepper |
| `new JScrollPane` | Scrollbars and viewport fills drift between panels | Use `Ui.scroll(...)` or immediately call `Ui.styleScrollPane(...)` | Custom scroll panes inside shared UI primitives |
| `new Color` outside `Theme`, `BoardStyle`, or visualization helpers | Theme roles become local and usually break either light or dark mode | Add or reuse a `Theme` token and update both palettes | Domain colors: board squares, annotation swatches, tensor heatmaps, fixed export colors |
| `setFont` outside painting/rendering code | Typography drifts and density changes stop working | Use `Theme.font(...)`, `Theme.mono(...)`, or shared stylers | `Graphics` painting that already uses `Theme` fonts or renderer code restoring host font |
| `setBorder` with `BorderFactory` in feature panels | Padding, radius, and line color become inconsistent | Use `Theme.pad(...)`, `Theme.lineBorder(...)`, `Ui.card(...)`, or shared section primitives | Custom painter borders and shared UI primitive internals |
| `setPreferredSize` in layouts | Panels become fixed to one viewport and clip on narrow windows | Use layout constraints, min/max sizes, split weights, or responsive collapse | Fixed-format elements: board, icon button, side rail, table row, chart legend, badge |
| Missing `setToolTipText` on icon controls | Compact controls become inaccessible and ambiguous | Add one-sentence tooltip and accessible name | Text buttons whose visible label fully names the action |
| Missing accessible name on search, icon, and custom-painted controls | Keyboard/screen-reader behavior cannot explain the control | Set `getAccessibleContext().setAccessibleName(...)` | Plain labels directly associated with simple text fields |
| `FlowLayout.LEFT` action rows with many controls | Toolbars lose leading/center/trailing hierarchy | Split into toolbar groups with `BorderLayout` and separators | Small local rows inside a form section |
| Top-level `JPanel` roots | Main surfaces miss shell styling and theme refresh behavior | Use `SurfacePanel` or a shared root primitive | Lightweight child groups inside an already styled surface |
| Local empty-state labels | Empty/error/loading states get different copy and spacing | Use `Ui.emptyState(...)` or `Ui.paintEmptyState(...)` | Tiny table cell placeholders |

Recommended audit commands:

```bash
rg -n "new JButton|new JScrollPane|new Color\\(|setFont\\(|setBorder\\(|setPreferredSize\\(" src/application/gui/workbench
rg -n "setToolTipText\\(|setAccessibleName" src/application/gui/workbench
rg -n "Ui\\.button\\(\"[^A-Za-z0-9\"]" src/application/gui/workbench
rg -n "FlowLayout\\.LEFT|FlowLayout\\.RIGHT|new JPanel\\(" src/application/gui/workbench
```

These commands find queues for review, not automatic failures. A hit is acceptable only when it matches the exception column or lives inside a shared primitive that is centralizing the behavior.

### Exception Policy

| Exception | Required proof |
| --- | --- |
| Domain color | The color represents chess, annotation, tensor, export, or measured visualization data, not a reusable UI role. |
| Custom painting | The painter consumes `Theme` or `BoardStyle` tokens and restores graphics state. |
| Fixed size | The component has a fixed-format reason and the surrounding layout still works at narrow and wide widths. |
| Shared primitive internals | The code lives in `ui`, `layout`, or `board` infrastructure and exists to style many callers. |
| Generated or platform Swing behavior | Replacing it would reduce correctness or native behavior; document why near the code if non-obvious. |

## Visual QA Targets

When adapting the current Workbench, inspect the failure-prone layers directly. A surface is not visually done until these cases have been checked for the affected tab.

| Area | What to inspect | Pass condition |
| --- | --- | --- |
| Editor shell | Tabs, split dividers, header, toolbar, side rail, bottom panel | Same spacing, line weight, and surface color across tabs |
| Overlays | Modal layer, command palette, popovers, tooltips, toasts | Correct z-order, readable surface, no hidden base controls receiving accidental focus |
| Board layers | Coordinates, highlights, legal targets, arrows, drag preview, pieces | Layers do not overlap incoherently and exported images match on-screen styling |
| Dense tables | Header, hover, selection, disabled rows, empty rows | Row height stable, important columns pinned, long text elides or tooltips |
| Forms | Required fields, optional fields, validation, disabled controls | Label lanes align and errors appear near the failed input |
| Toolbars | Leading, center, trailing groups, overflow, search | Primary action and search remain findable at narrow width |
| Floating feedback | Toasts, progress, missing dependency badges, stale state badges | Status is close to the affected content and does not permanently dominate |
| Theme switch | Light to dark and dark to light refresh | No stale colors, mismatched scroll panes, unreadable text, or missing focus rings |
| Resize | 1000 px and 1600 px wide windows | No clipped button text, overlapping labels, or side rail crowding |
| Keyboard | Tab order, focus rings, escape/enter in dialogs, search focus | Keyboard target is visible and shortcuts do not steal input from fields |

For custom painting, also inspect one empty state, one selected state, one hover/focus state, and one disabled or missing-data state. These states catch most visual drift before it spreads to other tabs.

## Preview And Component Inspection Commands

Use these commands while changing Workbench UI. They are meant to make layout, styling, and component-tree drift visible before a full manual review.

### Java Entry Points

These are the Java classes behind the preview and inspection scripts.

| Need | Java class | Normal command |
| --- | --- | --- |
| Full focused Workbench UI regression | `testing.WorkbenchRegressionTest` | `java -Djava.awt.headless=true -cp out testing.WorkbenchRegressionTest` |
| Theme/control/editor-shell checks | `testing.WorkbenchUiRegression` | Called by `WorkbenchRegressionTest` |
| Board/rendering/input checks | `testing.WorkbenchBoardRegression` | Called by `WorkbenchRegressionTest` |
| Headless panel screenshots | `testing.WorkbenchShots` | `scripts/workbench_shots.sh --panel draw --themes LIGHT,DARK --dump-components` |
| Deterministic sample panel factories | `testing.WorkbenchShotPanels` | Used by `WorkbenchShots`; add new screenshot fixtures here |
| Text dump of Swing component tree | `testing.WorkbenchComponentDebug` | Used by `WorkbenchShots --dump-components` |
| Component counts and class summary | `testing.WorkbenchComponentStats` | Used by `WorkbenchComponentDebug` |
| Live real-window preview | `testing.WorkbenchPreviewLauncher` | `scripts/launch_workbench_preview.sh --panel draw --dump-components` |
| Real-window PNG capture | `testing.WorkbenchRobotCapture` | `scripts/capture_workbench_preview.sh --panel draw --dump-components` |

Direct Java commands after compiling:

```bash
find src -name '*.java' | sort > /tmp/crtk-srcs.txt
javac --release 17 -d out @/tmp/crtk-srcs.txt

java -Djava.awt.headless=true -cp out testing.WorkbenchRegressionTest
java -Djava.awt.headless=true -cp out testing.WorkbenchShots artifacts/workbench-shots 1600 draw LIGHT,DARK artifacts/workbench-shots/components
java -Djava.awt.headless=true -cp out testing.WorkbenchShots artifacts/workbench-shots 1000 dashboard,draw,commands LIGHT,DARK artifacts/workbench-shots/components
```

Inside a focused regression or screenshot fixture under `src/testing`, use `WorkbenchComponentDebug` when you need to inspect a component that is not yet covered by `WorkbenchShots`:

```java
JComponent panel = WorkbenchShotPanels.draw();
panel.setSize(1600, 900);
WorkbenchComponentDebug.write(panel, "draw_manual",
        Path.of("artifacts/workbench-shots/components/draw_manual.components.txt"));
```

Use that snippet only from the `testing` package. `WorkbenchShotPanels` is a test fixture holder, not production UI code.

### Fast UI Regression

Run this after any Workbench UI code change:

```bash
find src -name '*.java' | sort > /tmp/crtk-srcs.txt
javac --release 17 -d out @/tmp/crtk-srcs.txt
java -Djava.awt.headless=true -cp out testing.WorkbenchRegressionTest
git diff --check
```

Run the full recommended gate before publishing broad UI changes:

```bash
./scripts/run_regression_suite.sh recommended
```

### Headless Screenshot Checks

List the panels that the deterministic screenshot tool can render:

```bash
scripts/workbench_shots.sh --list-panels
```

Render the affected panel in both themes:

```bash
scripts/workbench_shots.sh --panel draw --themes LIGHT,DARK --width 1600 --dump-components
```

Render a narrow layout and a normal layout for the same panel:

```bash
scripts/workbench_shots.sh --panel draw --themes LIGHT,DARK --width 1000 --out artifacts/workbench-shots/draw-1000 --dump-components
scripts/workbench_shots.sh --panel draw --themes LIGHT,DARK --width 1600 --out artifacts/workbench-shots/draw-1600 --dump-components
```

Render several panels that are likely affected by a shared primitive:

```bash
scripts/workbench_shots.sh --panels dashboard,analyze,draw,commands,logs --themes LIGHT,DARK --width 1600 --dump-components
```

Open or inspect the generated PNG files in `artifacts/workbench-shots`. Screenshots should be checked for clipping, overlaps, mismatched scrollpane backgrounds, stale light/dark colors, unstable side rails, and missing empty/error/disabled states.

### Component Dump Checks

When `--dump-components` is used, the screenshot tool writes text dumps under `artifacts/workbench-shots/components`. These dumps show class names, bounds, preferred sizes, opacity, fonts, backgrounds, borders, tooltips, accessible names, and button text.

Inspect one dump directly:

```bash
sed -n '1,180p' artifacts/workbench-shots/components/draw_LIGHT.components.txt
```

Search dumps for common UI drift:

```bash
rg -n "class=.*JButton|class=.*JScrollPane|tooltip=<null>|accessibleName=<null>" artifacts/workbench-shots/components
rg -n "preferred=|minimum=|maximum=|bounds:" artifacts/workbench-shots/components/draw_LIGHT.components.txt
rg -n "background=|foreground=|font=|border=|ui=" artifacts/workbench-shots/components/draw_LIGHT.components.txt
```

Use component dumps to answer concrete questions:

| Question | What to inspect |
| --- | --- |
| Is a button using the shared style? | Button class, UI class, font, border, height, tooltip, accessible name |
| Is a side rail too wide? | Root and side-rail bounds and preferred width |
| Is a label pushing controls offscreen? | Label bounds, preferred width, and neighboring control x positions |
| Is a scroll pane mismatched? | `JScrollPane`, `JViewport`, background, opacity, border, and UI class |
| Did theme refresh leave stale colors? | Compare light and dark dump backgrounds, foregrounds, borders, and UI classes |

### Live Preview

Launch the real Swing Workbench with isolated preview preferences:

```bash
scripts/launch_workbench_preview.sh --panel draw --width 1500 --height 950 --dump-components
```

Stop the preview:

```bash
kill "$(cat artifacts/workbench-live/workbench.pid)"
```

Capture the real Workbench window to a PNG. This uses `xvfb-run` automatically when no display is available and `xvfb-run` exists:

```bash
scripts/capture_workbench_preview.sh --panel draw --width 1000 --height 760 --out artifacts/workbench-live/draw-1000.png --dump-components
```

Use the live preview for behavior that the offscreen renderer cannot fully prove: modal overlays, command palette focus, toasts, lazy tabs, window resizing, popup menus, keyboard traversal, and drag/drop.

### Adding A New Screenshot Target

If a new component or tab cannot be seen through an existing screenshot target, add a focused target instead of relying on manual clicking.

1. Add a static factory in `src/testing/WorkbenchShotPanels.java` that builds the component with deterministic sample data.
2. Add a `shootIfEnabled(...)` call in `src/testing/WorkbenchShots.java` with a stable panel name, width, height, and factory.
3. Add the panel name to `KNOWN_PANELS` in `scripts/workbench_shots.sh`.
4. Render it with `scripts/workbench_shots.sh --panel <name> --themes LIGHT,DARK --dump-components`.
5. Add or update a focused regression if the component exposes behavior that can drift.

Keep screenshot fixtures deterministic: no wall-clock labels, random IDs, local machine paths, network calls, model weights, or hidden user preferences.

## Current Workbench Migration Process

When adapting an existing panel to this guide, use this process. Do not redesign the whole app in one commit.

1. **Classify the panel.** Write down its container type: editor surface, side rail, bottom panel, floating dialog, command palette, or status surface.
2. **Choose the home surface.** Match it to the closest row in [Target Information Architecture](#target-information-architecture) and [Surface Home Rules](#surface-home-rules).
3. **Choose the blueprint.** Match it to the closest row in [Surface And Mode Blueprints](#surface-and-mode-blueprints).
4. **Normalize root layout.** Convert top-level layout to `SurfacePanel` plus `BorderLayout`, `WorkspaceHeader`, optional toolbar band, body, and optional side rail.
5. **Normalize toolbar.** Move controls to leading/center/trailing groups. Remove explanatory text from the toolbar.
6. **Normalize controls.** Replace raw Swing styling with `Ui` factories/stylers. Add tooltips and accessible names to icon buttons.
7. **Normalize forms.** Apply fixed label lanes, validated fields, grouped actions, and clear disabled states.
8. **Normalize tables.** Apply shared table styling, pinned columns, hover rows, compact row height, and empty states.
9. **Normalize states.** Add empty, loading, error, missing, disabled, and stale states where relevant.
10. **Normalize copy.** Shorten labels, remove visible instruction paragraphs, move details into tooltips/captions/status.
11. **Normalize screenshots.** Render or inspect light/dark and narrow/wide layouts.
12. **Add regression.** Pin the behavior or structure that should not drift again.

Recommended migration order:

| Order | Scope | Why |
| --- | --- | --- |
| 1 | Shared primitives: `Theme`, `Ui`, `SurfacePanel`, toolbar helpers, table styling | Gives every later screen the same language. |
| 2 | Editor shell and tab controls | Affects every screen and establishes VS Code structure. |
| 3 | Board surface: Analyze, Play, Solve, Relations, Draw, Board Editor | Board-first workflows are the visual anchor of the app. |
| 4 | Run surface: Build, Batch, Console, Logs/output | CLI parity, validation, and output consistency should converge early. |
| 5 | Engine Lab: Evaluator, Search, Tree, Gauntlet | Engine workflows need clear state, budget, model honesty, and result layout. |
| 6 | Datasets and Publish | These are distinct artifact surfaces with strong existing content shape. |
| 7 | Dashboard live links and recent-run/artifact cards | The dashboard becomes useful once surfaces and output states are normalized. |

## Code Review Acceptance Criteria

A Workbench UI PR is acceptable only if it answers these questions clearly.

| Question | Pass condition |
| --- | --- |
| Which primary object does this serve? | Position, engine/search state, command run, dataset, publication, or session status is named. |
| Which target surface owns it? | Dashboard, Board, Engine Lab, Run, Datasets, or Publish is the clear home. |
| Which VS Code container does this use? | The implementation maps to editor, side rail, panel, command palette, context menu, status, or floating chrome. |
| Which Apple behavior rule applies? | Toolbar grouping, button hierarchy, search placement, color meaning, accessibility, or material/depth is explicit. |
| Are shared primitives used? | No local copies of button styling, scroll styling, table styling, or color palettes. |
| Is the layout stable? | Narrow/wide and light/dark views do not clip, overlap, or shift unexpectedly. |
| Are states complete? | Empty/loading/error/disabled/missing states are visible and understandable. |
| Is copy operational? | Labels are short; no visible tutorial prose in the task surface. |
| Are actions placed consistently? | Primary actions trailing, modes leading, destructive isolated, search/filter near target. |
| Is behavior deterministic? | Command previews, exports, and generated outputs are stable. |
| Is it tested? | Focused regression plus visual check for any custom rendering or layout-sensitive change. |

## Implementation Checklist

Before editing a panel:

1. Identify the user job and the primary state the surface must show.
2. Name the primary object: position, engine/search state, command run, dataset, publication, or session status.
3. Name the target surface: Dashboard, Board, Engine Lab, Run, Datasets, or Publish.
4. Name the VS Code container it belongs to: editor, side rail, panel, command palette, status, context menu, or floating chrome.
5. Apply the Apple behavior layer: hierarchy, grouped controls, search placement, consistent buttons, legible text, and accessible focus.
6. Choose the existing primitive from `Ui`, `Theme`, `BoardStyle`, or the layout package.
7. Add a new primitive only if multiple panels will reuse it or the behavior is complex enough to centralize.
8. Test both light and dark modes.
9. Test narrow and wide window widths.
10. Verify empty, loading, error, disabled, and success states.
11. Verify keyboard and tooltip behavior.
12. Add or update a focused regression when the behavior is stable enough to pin.

During code review, reject changes that introduce local styling copies, duplicate board geometry, hidden command defaults, nested card layouts, emoji/cute command names, decorative glass, or unverified custom painting.

## Verification

For docs-only changes to this guide, run:

```bash
./scripts/run_regression_suite.sh docs
```

For UI code changes, run at least:

```bash
javac --release 17 -d out $(find src -name "*.java")
java -Djava.awt.headless=true -cp out testing.WorkbenchRegressionTest
git diff --check
```

For board or rendering changes, add visual verification. Launch the app, use the affected workflow, and capture or inspect both light and dark appearances. The offscreen screenshot helper can render focused panels for comparison:

```bash
scripts/workbench_shots.sh --panel draw --themes LIGHT,DARK
```

Before publishing shared Workbench changes, run:

```bash
./scripts/run_regression_suite.sh recommended
```

## External Design References

Use these as inspiration, then translate them through ChessRTK's Swing primitives and deterministic workflow constraints.

- [VS Code UX Guidelines](https://code.visualstudio.com/api/ux-guidelines/overview) - workbench containers, editor area, sidebars, panels, status bar, command palette, and view toolbars.
- [VS Code Command Palette](https://code.visualstudio.com/api/ux-guidelines/command-palette) - clear command names, categories, keyboard shortcuts, and no emoji command names.
- [VS Code Sidebars](https://code.visualstudio.com/api/ux-guidelines/sidebars) - grouped views, descriptive names, and avoiding excessive view containers.
- [VS Code Status Bar](https://code.visualstudio.com/api/ux-guidelines/status-bar) - contextual workspace and active-view status.
- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines) - hierarchy, harmony, consistency, and platform-familiar interaction.
- [Apple Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars) - leading/center/trailing toolbar grouping, deliberate item choice, search/actions placement, and overflow behavior.
- [Apple Color](https://developer.apple.com/design/human-interface-guidelines/color) - consistent color meaning, status communication, and appearance-mode adaptation.
- [Apple Search Fields](https://developer.apple.com/design/human-interface-guidelines/search-fields) - useful placeholder text, live filtering, scope controls, and search placement.
- [Apple Typography](https://developer.apple.com/design/human-interface-guidelines/typography) - legibility and hierarchy through type.
- [Apple Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons) - consistent button sizing, clear labels, hover tooltips, and image-button restraint.

## Related Pages

- [Desktop Workbench](workbench.md) - user-facing Workbench walkthrough.
- [Development Notes](development-notes.md) - source layout, build, and contributor conventions.
- [Quality and Testing](quality-and-testing.md) - regression suite and docs target.
- [Architecture](architecture.md) - one shared core behind CLI and GUI.
