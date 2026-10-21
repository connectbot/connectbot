# Terminal keyboard configuration contract

Keyboard layouts, buttons, macros, and the singleton keyboard settings record use
UUID primary keys. Kotlin UUIDs are stored as canonical lowercase TEXT, never as
SQLite rowids. Layout duplication creates new layout and button identities but
retains shared macro references. Host and profile identifiers also use UUIDs;
both hosts and profiles have a nullable keyboard_layout_id UUID foreign key.

## Selecting and customizing

Profiles select a layout or inherit the app default. Hosts can override the
profile selection; clearing the override restores inheritance. The effective
order is host override, profile selection, then app default. The selector shows
the effective name, source, preset description, and keyboard preview.

Change opens the shared picker. Customize creates a new layout and button IDs,
retaining shared macro references. In a host or profile form, Done returns the
copy to the parent draft; only the parent Save commits it. Discarding the parent
leaves no unused copy. New-host automation follows the same draft/save boundary.

The terminal menu also offers Terminal keyboard. Saved-host changes persist
immediately and update open sessions. Temporary connections keep their choice
only for that connection. Customizing from the terminal saves a new library
layout; temporary connections do not become saved hosts.

Layouts and macros have separate library tabs. Macro selection from a key or an
automation action supports creation and import without leaving the workflow.
Editing a macro used elsewhere offers a copy for only the current key/action or
an explicitly shared edit. Shared edits save globally even if a parent draft is
later discarded. Usage entries include keyboard layouts and host automation;
referenced macros cannot be deleted.

Editors retain drafts across navigation and recreation, and ask before discarding
changes. Host password entry is excluded from persisted form state.

## Layouts

A layout owns button_width and button_height in density-independent pixels. Both
default to 45 × 30 dp. All buttons, including modifiers, macro buttons, and app
controls, use those dimensions. A button's column_span and row_span multiply the
base dimensions. Changing dimensions never rewrites button coordinates or spans.

The editor uses Reorderable's LazyVerticalGrid with the same key faces as the
console. Tap a key to open its properties, or touch and hold it to drag. There is
no separate reorder list or visible drag handle. Editor keys are one row tall;
column spans and gaps remain supported. Opening an older layout for editing
reduces any multi-row keys to one row in the draft; saving persists that change.
The stored/exported row_span field remains readable for older layouts.

Positions are zero-based physical rows and columns, shared by portrait and
landscape. Each item has a layer number from 0 through 4 (default 0). Rectangles may overlap
only on different layers. Hidden buttons retain their reserved cells. Positions
must be nonnegative and spans positive. Dimensions
must be finite, positive, and at most 1000 dp; each grid axis is limited to 1000
cells, with a maximum extent of 3000 dp per axis and 256 buttons per layout.
These are resource limits, not enum encodings.

Button kinds and targets are explicit protocol strings:

- `key`: `escape`, `tab`, `enter`, `backspace`, `delete`, `insert`, `arrow_up`,
  `arrow_down`, `arrow_left`, `arrow_right`, `home`, `end`, `page_up`, `page_down`,
  and `f1` through `f12`.
- `modifier`: `ctrl`, `alt`, or `shift`. Tap cycles off → one-shot → locked → off.
- `app`: `text_input`, `toggle_compose`, `toggle_ime`, or `paste`.
- `layer`: `next`, or the destination layer number `0` through `4`.
- `macro`: references a shared macro UUID in macro_id; target is unused.

Default layout UUID: `5a46e491-229f-4ff9-a9ce-7b975f300001`.
Settings record UUID: `5a46e491-229f-4ff9-a9ce-7b975f300002`.
Seeded button UUIDs are name UUIDs derived from UTF-8 `layoutUuid/index`, where
index follows KeyboardDefaults' original identity slots. Removing a default
button does not renumber surviving identities; visible columns remain compact.
They remain stable on migration and reset. Text input is optional in custom layouts
and is omitted from fresh, reset, and fallback defaults. Existing saved layouts
are preserved. Labels and resource identifiers are not wire identifiers.

## Presets, sizing, and layers

The Add layout picker offers Default plus four editable presets: Citrus (compact
terminal controls), Last Stop (roomy terminal controls), Claw (optimized for Claude
Code), and Scribe (optimized for Codex CLI). Presets are templates, not additional
seeded layouts. Creating or cancelling a draft does not change the database or
app-default selection. Saving includes its labelled shortcut definitions in the
same transaction. `preset_key` records the originating template so reset restores
its keys and dimensions while retaining the layout name and UUID. Duplicates keep
that template reference; unknown preset IDs use the ordinary default on reset.

`fit_to_width` defaults to false, preserving existing fixed-dimension layouts.
All four new presets enable it. The grid divides available width proportionally
by column spans, reserves the same geometry across layers, and shrinks row height
when bounded terminal space requires it. Fitted grids have no scrolling. Button
labels remain single-line and compress horizontally to fit; accessibility retains
the action label and full key combination. Citrus uses two 36 dp rows, the other
presets two 44 dp rows. The editor can change these preferred heights.

Preset layer 0 contains its main two rows, layer 1 provides editable F1–F12 and
modifier controls, and layers 2–4 start empty. The initial layer is the first
populated layer, normally 0. A layer is populated when it has a visible non-switch
button. FN (`kind=layer`, `target=next`) cycles ascending populated layers and wraps
around. Numbered layer buttons (`target=0`…`4`) select their destination directly
and default to labels L0…L4. Empty destinations are disabled; selecting the current
layer is a no-op. Reachable layers must offer a route back to the initial layer,
through either direct or cycling buttons. Switching layers never sends terminal
input or consumes sticky modifiers. The active layer is per terminal/layout and
is reset when either changes; it is not stored or exported.

These fields are included in the unreleased Room schema 17 and its existing
16→17 migration. Existing buttons migrate to layer 0, existing layouts remain
fixed-width, and preset template keys are nullable. Older JSON exports may omit
the new fields and use the same defaults. No separate schema 18 migration is needed.

## Macro document version 1

Room stores an opaque JSON document in keyboard_macros.document. Its version is
independent of Room's schema version. An example:

```json
{"version":1,"actions":[{"type":"key","character":"b","modifiers":["ctrl"]},{"type":"text","text":"l"}]}
```

Actions have the following forms:

- `{"type":"text","text":"…"}`: nonempty Unicode text. Encode using the active
  connection's configured charset. Do not normalize newlines, append Enter, or
  apply sticky modifiers. This is direct text output, not bracketed paste.
- `{"type":"key","key":"arrow_up","modifiers":["shift"]}`: resolve a semantic
  key through the current terminal backend, including its terminal modes.
- `{"type":"key","character":"b","modifiers":["ctrl"]}`: dispatch one Unicode
  scalar with explicit modifiers. Exactly one of key and character is required.
- `{"type":"bytes","hex":"00ff1b"}`: send exact bytes. Hex consists of complete
  pairs, has no whitespace, and is canonicalized to lowercase. No character-set
  conversion, parsing of terminal escapes, or sticky-modifier application occurs.

Modifiers are `ctrl`, `alt`, and `shift`, without duplicates. The document has
1–256 steps and at most 65536 UTF-16 code units. Serialization never uses Kotlin
class names, enum ordinals, Android key codes, or terminal-library constants.
Unknown versions, action types, keys, or fields reject the *entire* invocation
before any output or modifier changes. The original document remains intact in
Room and exports. Change semantics only via a new version and explicit migration;
do not reinterpret existing documents after an editor/backend refactor.

Macro steps use explicit modifiers. Accepted execution consumes one-shot sticky
state and preserves locks. Invocations target the session captured on tap and
are queued in order. Disconnect/failure stops execution without replay on reconnect.
Each invocation captures the transport and connection generation at tap time.
Disconnect and reconnect invalidate queued invocations and cancel active execution;
delayed semantic output remains bound to its captured connection while callbacks drain.
The runtime adapter must finish queuing a semantic key's output before advancing
to text or bytes, even when terminal output callbacks are asynchronous.

## Editor language version 1

The editor language is a presentation of the document, not the stored source of
truth. One action per line; blank lines are ignored. There are no comments,
variables, delays, recursion, or scripting.

```text
key ctrl+b
text "ls -la"
key enter
bytes 1b 5b 41
key alt+"+"
```

Text and quoted characters use JSON string escapes. Single ASCII alphanumeric
characters can be unquoted. A key can instead be a named key from the catalog.
Modifiers are lowercase prefixes separated by `+`. Raw bytes are whitespace-
separated pairs of hexadecimal digits. Switching editors preserves action values
and order, not whitespace formatting. Invalid text remains editable with a line
and column diagnostic and cannot be saved or converted to the visual editor.

The former compose-mode visibility preference is migrated once into the seeded
layout and removed. After migration, visibility is controlled only by Room.

## Backup and import

Filtered Android backups include keyboard tables and profile references.
Configuration exports include layouts, macros, and items before profiles. Import
preserves UUID identity, skips existing UUIDs, and keeps the app-default selection.
Matching UUIDs retain their local contents even if the imported record differs;
equal contents with distinct UUIDs remain distinct records.
Full Android backup restores that selection. Host/profile UUIDs retain their
existing import remapping behavior. Older exports without keyboard tables work.

## Shared macros and portable files

Keyboard keys and post-login automation use the same UUID macro library. A key's
Assign macro menu selects a saved macro; the post-login Run macro action selects
that same identity. Manage macros in post-login automation opens the shared
library, including its editor and individual import/export controls. A macro
cannot be deleted while referenced by a key or post-login action.

Individual files use the macro document's version and actions plus a name:

```json
{"version":1,"name":"Build","actions":[{"type":"text","text":"make\n"}]}
```

Imports allocate a fresh macro UUID. Exports are standard JSON; individual macro,
full configuration backup, and color scheme imports accept JSON5 syntax, including
comments, single quotes, unquoted property names, and trailing commas. Existing
JSON files remain supported. Full exports and Android backups retain shared
macro identities and references from both keys and post-login automation. The
optional automation macro_id column is part of unreleased schema 17.
