# re-frame Keyword Registrations as Definitions Implementation Plan

**Goal:** `(rf/reg-event-fx :dcw.event/x …)` and its siblings define the keyword they register, so go-to-definition from a dispatch site (`[:dcw.event/x …]`, in any namespace, aliased or not) lands on the registration. Integrant's `ig/init-key` machinery is the template; the same hook learns re-frame's `reg-*` macros and a user-supplied list of project wrappers.

**Tech Stack:** Rust, tree-sitter-clojure, the index's symbol/occurrence split (`src/index/`), `ExtractConfig` + `settings::load` for config.

---

## Design

### Why it is missing

A keyword has no var, so `handlers/definition.rs` resolves it to a colon-prefixed
fqn and looks that fqn up in the symbol index; a hit means "this keyword has a
definition". Only one form currently records such a symbol:
`(defmethod ig/init-key ::x …)` (`extract_integrant_key`). re-frame's
registration macros take the keyword as their first argument and are otherwise
ordinary calls, so their keyword is recorded as a usage only and definition
returns nothing.

The extractor already says where this belongs:

> `defmethod_multifn_fqn` — the single hook point for keyword-defining macros
> (re-frame `reg-*`, spec `s/def` would slot in here).

### Recognition

A top-level list `(HEAD KW …)` is a keyword definition when the head resolves to
one of:

- the built-in set: `re-frame.core` plus `reg-event-db`, `reg-event-ctx`,
  `reg-event-fx`, `reg-sub`, `reg-sub-raw`, `reg-fx`, `reg-cofx`,
  `reg-interceptor`, `reg-global-interceptor`; or
- a fqn listed in `.clj-pulse/config.edn`'s `:keyword-defining-forms`.

`KW` is `children[1]` and must be a `kwd_lit` that `keyword_fqn` resolves
(qualified or auto-resolved; an unqualified `:kw` is too ambiguous, matching the
Integrant rule). Head resolution reuses `head_fqn_candidates`, so `rf/reg-event-fx`,
`re-frame.core/reg-event-fx` and a `:refer`'d bare `reg-event-fx` all match, and
a project wrapper matches through its alias (`rfq/reg-query` → `re-frame.query/reg-query`).

The config list is additive: the built-in set is not configurable, so
`ExtractConfig::default()` keeps meaning "stock behaviour" for the unit tests.
Only the keyword's position is assumed (first argument after the head), which is
true for every re-frame `reg-*` and for the wrappers it targets; a macro taking
its keyword elsewhere needs a new shape, not a new key.

### Recording

`DefKind::IntegrantKey` becomes `DefKind::KeywordDef` — one concept, "a keyword
some form registers as a definition", documented with both producers. Hover
labels it `keyword`; `documentSymbol`/`workspace/symbol` keep `SymbolKind::KEY`.
`Symbol.name_range` spans the whole keyword token (as Integrant does), so
definition lands on the keyword and references/rename treat it as one site.

`JarCacheEntry::format_version` goes 27 → 28: the serialized `DefKind` string
changes and the extractor emits new symbols.

### Occurrences

The registration keyword is the definition, not a usage, so references list the
declaration once and rename rewrites it once. The skip is by *token position*:
`OccurrenceCtx::keyword_defs` holds every `KeywordDef` symbol's `name_range`
start and `record_keyword_occurrence` drops a keyword found there, mirroring the
existing `declared` set. That is depth-correct by construction — a registration
nested in a fn body (how a test fixture stubs an event) has no symbol, so its
keyword stays an ordinary usage instead of vanishing from references and rename.
The head itself stays a usage, as it already is.

Only a top-level form defines, like every other `DefKind`.

## Tasks

- [x] `DefKind::IntegrantKey` → `DefKind::KeywordDef`; update `hover`, `symbols`, `jar_cache` (27 → 28).
- [x] `ExtractConfig.keyword_forms: HashSet<String>`; parse `:keyword-defining-forms` in `settings::load`.
- [x] Extractor: `keyword_def_keyword(children, ns_meta, source, extra)` returning the keyword fqn; the symbol pass pushes a `KeywordDef`; `record_keyword_occurrence` drops a token listed in `OccurrenceCtx::keyword_defs` (position-based, so a nested registration keeps its usage).
- [x] Unit tests: alias, full ns, `:refer`, config-listed wrapper, non-re-frame `reg-*` stays a usage, unqualified keyword is not a definition.
- [x] e2e: fixture with `[re-frame.core :as rf]`, registration in one ns, dispatch in another; definition lands on the registration line; references list the declaration once.
- [x] Docs: `SETTINGS.md` row + note, `ROADMAP` item ticked, `AGENTS.md` invariant, `MEMORY.md` note if the behaviour is worth recording.
- [x] `bb check`: formatting and clippy green; the only failing test is
  `kondo::tests::wait_until_executable_waits_out_a_busy_binary`, which fails
  identically on a clean `HEAD` here (this macOS lets a file with an open writer
  exec; the test asserts `ETXTBSY`).
- [x] `bb e2e`: 220 passed.
- [ ] `bb compare` (needs the pinned clj-kondo + penpot corpora in `.tmp/bench/`).

## Verification

- `bb check`: formatting and clippy `-D warnings` clean; lib 532 passed, every
  other suite green.
- `bb e2e`: 220 passed, including the new re-frame test.
- `bb compare`: not run — it needs the pinned clj-kondo and penpot corpora under
  `.tmp/bench/`. The keyword's definition site is a symbol, so references and
  rename answers keep the same site sets, but that is reasoning, not a
  measurement.
