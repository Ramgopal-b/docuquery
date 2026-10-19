# DocuQuery – DQL / API client for OpenText Documentum

An in-house desktop tool for querying and maintaining the Documentum repository
(EDMS) – a replacement for the day-to-day dqMan features. Written from scratch
on top of OpenText DFC, which is already licensed with your Documentum installation.

## Layout

Same layout as dqMan: menu bar (Session, Jobs, Edit, View, Functions, Favorites, Extras, Windows, Help);
toolbar (Session, Jobs, Copy, Disconnect | DQL, API, DQL Script, API Script | Navigator, Object, History |
Favorites | Back, Next | Run, Step, Pause, Stop); session tab; Repository Navigator (Folders, Types, Groups,
Users, Favorites) on the left; "DQL Query" editor with "Results (n)" grid on the right; status bar with
rows/time, line : column and session number.

* Max rows: **Extras > Options**  ·  Edit in grid on/off: **Extras > Edit values in result grid**
* Back / Next: previous / next query together with its results
* Step / Pause are shown for the same layout but are not active yet
* Not available yet (shown greyed out, as in dqMan): Copy registered Table, Change Objecttype, Mapping Lists

## Features

| Area | What it does |
|---|---|
| Sessions | Log in to any repository from the docbroker list; several sessions open side by side as tabs |
| DQL editor | Syntax highlighting, line numbers, undo/redo, run selection or all (F5 / Ctrl+Enter), Max rows limit, Stop |
| Results grid | dqMan-style: row numbers, filter funnel in every column header (contains / pick a value), sort, Find box, copy cell/rows, export to CSV or Excel (.xlsx), double-click an id to open the object |
| **Edit in grid** | Double-click a cell (or F2), type the new value, press **Enter** – the attribute is saved to the repository immediately, no extra tab. Saved cells turn green; if the server refuses (permissions, lock, wrong type) the old value comes back and the error is shown. Repeating values: separate with `;`. `r_object_id` is added to your select automatically (hidden) when missing. `r_`/`i_` system attributes and ID columns stay read-only. Switch off with **Extras > Edit values in result grid** for a read-only grid. |
| Repository Navigator | **Folders** (cabinets/folders/documents, lazy-loaded, lock indicator) · **Types** (Object Types hierarchy + Registered Tables) · **Groups** (Groups & Roles; members panel with All/Users/Groups) · **Users** (All Users; groups of the selected user) · **Favorites** (Standard Favorites + User's Favorites) · filter + search on every tab |
| Menus (as dqMan) | **Session**: New Session Window, Disconnect, Copy, Refresh Valuelists, Session Alias, Locale, Close, Exit · **Edit**: Run (F9), Clear, Export to (CSV/Excel/HTML/Clipboard), Save to Favorites, Search (Ctrl+F) / Search again (F3), dfc.properties · **Functions**: Dump Config Objects, Flush Caches, Last SQL statement, Server Session Statistics, Tracing · **Favorites**: Administration, Audittrail, Data Dictionary, DocApp, Documents, Workflows + your own · **Extras**: Dump & Load, Consistency Checker, Import from File / Clipboard, Compare Objects, Compare Data, Navigate Object (F12), Navigate / Dump Object from Clipboard (Alt+N / Alt+D), Options · **Windows**: Arrange (Cascade / Vertical / Horizontal), session list |
| Object viewer | All attributes with type and repeating flag; edit single and repeating values and Save; Dump; Export content; Check out / Check in (minor, major or same version) / Cancel checkout; Destroy |
| Content | Import files into a folder (format detected from the file extension), export content to disk |
| API console | iapi-style commands: `dump,c,<id>`, `get,c,<id>,attr`, `set,c,<id>,attr` + value, `save,c,<id>`, `?,c,<dql>` … with command history |
| DQL Script / API Script | Run many statements in one go, stop-on-error, log output, open/save script files |
| History & Favorites | Every executed query is kept (last 500); save named favourite queries |

Settings, history and favourites are stored in `%USERPROFILE%\.docuquery`.

## Requirements and installation

No dqMan is needed. Each PC needs **DFC** (dfc.jar + libraries, from OpenText),
**dfc.properties** (docbroker host/port) and **Java 17+**. These are put into one
ready package once, then every PC just installs that package.

1. **Once:** run `make-package.bat` → asks for the DFC folder, an existing dfc.properties
   (or docbroker host + port to create one) and a Java folder to bundle → creates
   `docuquery-ready.zip`.
2. **Every PC:** unzip `docuquery-ready.zip` → `install.bat` → Desktop shortcut **DocuQuery**.

Full step-by-step instructions, including where to get DFC (OpenText My Support,
the Content Server, or a DA/D2/Webtop `WEB-INF\lib`): **INSTALL.txt**.

DFC and dfc.properties belong to your licensed OpenText installation and are not
part of docuquery.zip itself.

**Inside the program:** Edit > dfc.properties (host / port), and **DFC setup...** in the
login window or under Help when DFC is missing. Restart DocuQuery after DFC changes.

**DFC files are kept in the DocuQuery folder only** – nothing is read from
`C:\Documentum`, `Program Files` or environment variables:

| What | Location |
|---|---|
| DFC jars | `DocuQuery\lib\dfc.jar`, `lib\*.jar`, `lib\lib\*.jar` |
| dfc.properties | `DocuQuery\config\dfc.properties` |
| DFC data / cache | `DocuQuery\data\` (set automatically at start-up) |
| Java | `DocuQuery\jre\` → `%JAVA_HOME%` → `java` on PATH |

Scripts: `make-package.bat` (build the ready package), `install.bat`, `uninstall.bat`,
`run.bat` (`run.bat console` shows the DFC log), `build.bat` (rebuild from source, needs a JDK).

## Rebuilding from source

`docuquery.jar` is pre-built. To change the code and rebuild it, install a **JDK 17+**
and run **build.bat**. It compiles against your DFC jars.

Linux/macOS: `./build.sh` and `./run.sh`, with the DFC jars in `lib/` and `dfc.properties` in `config/`.

## Notes

* All DFC calls run on a background thread per session, so the UI stays responsive.
  **Stop** ends row fetching; a statement already running on the server finishes there.
* Attributes starting with `r_` or `i_` are read-only in the grid and in the object viewer.
* In-grid editing has no undo – it saves on Enter (Esc cancels while typing). Untick
  **Extras > Edit values in result grid** when you only want to look at production data.
* API commands are routed to get / exec / set automatically by command name. If a
  command is routed wrongly, choose GET / EXEC / SET in the console's mode box.
* Destroy, DQL `delete` / `update` statements and API `destroy` change data permanently.
  Test against a non-production repository first.

## Source layout

```
src/com/sedin/docuquery/
  App.java            entry point
  MainFrame.java      menu, toolbar, session tabs
  LoginDialog.java    repository login
  DocConnection.java  DFC session + background thread
  SessionPanel.java   navigator + editor tabs + status bar
  NavigatorPanel.java folders / types / groups / users / favorites
  Tools.java          Functions + Extras menu actions
  StandardFavorites.java ready-made queries (Favorites menu)
  DfcSetup.java       dfc.properties editor + DFC setup wizard
  DqlPanel.java       DQL editor + results
  ResultPanel.java    results grid, filter, export, context menu
  ObjectPanel.java    attribute viewer/editor
  ApiPanel.java       API console
  ScriptPanel.java    DQL / API script runner
  QueryListDialog.java history & favourites
  Ops.java            dump, import/export, checkout/checkin, destroy, run job
  QueryResult.java, Api.java, Dfc.java, CodeEditor.java, Export.java, Prefs.java, Ui.java
```
