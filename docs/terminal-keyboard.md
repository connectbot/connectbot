# Terminal keyboard configuration contract

Keyboard layouts, buttons, macros, and the singleton keyboard settings record use
UUID primary keys. Kotlin UUIDs are stored as canonical lowercase TEXT, never as
SQLite rowids. Layout duplication creates new layout and button identities but
retains shared macro references. Existing host and profile identifiers remain
numeric; a profile's nullable keyboard_layout_id is a UUID foreign key.

## Layouts

A layout owns button_width and button_height in density-independent pixels. Both
default to 45 × 30 dp. All buttons, including modifiers, macro buttons, and app
controls, use those dimensions. A button's column_span and row_span multiply the
base dimensions. Changing dimensions never rewrites button coordinates or spans.

Positions are zero-based physical rows and columns in a single arrangement shared
by portrait and landscape. Hidden buttons retain their reserved cells. Positions
must be nonnegative, spans positive, and rectangles must not overlap. Dimensions
must be finite, positive, and at most 1000 dp; each grid axis is limited to 1000
cells, with a maximum extent of 3000 dp per axis and 256 buttons per layout.
These are resource limits, not enum encodings.

Button kinds and targets are explicit protocol strings:

- `key`: `escape`, `tab`, `enter`, `backspace`, `delete`, `insert`, `arrow_up`,
  `arrow_down`, `arrow_left`, `arrow_right`, `home`, `end`, `page_up`, `page_down`,
  and `f1` through `f12`.
- `modifier`: `ctrl`, `alt`, or `shift`. Tap cycles off → one-shot → locked → off.
- `app`: `text_input`, `toggle_compose`, or `toggle_ime`.
- `macro`: references a shared macro UUID in macro_id; target is unused.

Default layout UUID: `5a46e491-229f-4ff9-a9ce-7b975f300001`.
Settings record UUID: `5a46e491-229f-4ff9-a9ce-7b975f300002`.
Seeded button UUIDs are name UUIDs derived from UTF-8 `layoutUuid/index`, where
index follows KeyboardDefaults' documented initial ordering. They remain stable
on migration and reset. Labels and resource identifiers are not wire identifiers.

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
Full Android backup restores that selection. Numeric host/profile IDs retain their
existing import remapping behavior. Older exports without keyboard tables work.
