# Caustica Rewrite Agent Rules

This repository is being migrated incrementally from the frozen Caustica 0.2.0
reference. `docs/rewrite/MIGRATION_STATE.yaml` is the source of truth for task state.

## Operating rule

Work on exactly one architectural task at a time. Select the READY task with the
lowest ID whose dependencies are DONE. Preserve reference behaviour unless the task
explicitly authorizes a behaviour change.

For every task:

1. run `git status`;
2. read this file;
3. read `docs/rewrite/MIGRATION_STATE.yaml`;
4. read `docs/rewrite/INVARIANTS.md`;
5. read the task requirements from the execution roadmap / task log;
6. write a short pre-plan in `docs/rewrite/tasks/AER-XXX-result.md` when useful;
7. edit only the files required by that task;
8. run the required validation;
9. inspect `git diff` and forbidden changes;
10. update migration state;
11. create one atomic commit.

Before every task commit run at minimum:

```text
git diff --check
git status --short
```

## Non-negotiable rules

- One responsibility per task. Do not combine ownership refactors, algorithm changes,
  shader tuning, feature work, dependency upgrades, or unrelated cleanup.
- Do not perform opportunistic renames, broad formatting, or micro-optimizations.
- Prove a replacement before deleting its legacy path.
- The repository must remain buildable after every completed task.
- Never introduce `vkDeviceWaitIdle`/equivalent on the hot path as a lifetime fix.
- Shader files are read-only during `OWNERSHIP_ONLY` tasks.
- ReSTIR reservoir mathematics, caps, age, Jacobian, candidate weighting, and
  temporal/spatial reuse remain unchanged during migration.
- A replacement LOD proxy must be READY before the current proxy is retired.
- A GPU resource may not be destroyed before the timeline token of its last use has
  completed.
- Optional backend failure must disable/fallback, never intentionally crash the
  renderer or remove otherwise-valid geometry.
- Iris must not enter the core dependency graph.
- Vulkan remains the renderer backend; do not create a multi-backend HAL.
- NRD/REBLUR remains experimental and outside the critical path.
- DH and Voxy must not simultaneously own overlapping RT geometry for the same
  distant horizon.
- If acceptance cannot be demonstrated, do not mark the task DONE. Revert or isolate
  the task, record a blocker, and block only dependent tasks.

## Validation levels

### V0 — static (every task)

```text
git diff --check
```

Also verify no conflicts, generated build output, downloaded SDKs, cache files,
logs, or unexpected binaries entered the tree.

### V1 — unit / characterization

```text
./gradlew test
```

Windows:

```text
.\\gradlew.bat test
```

Use subsystem-specific tests in addition to the full unit suite when applicable.

### V2 — build

```text
./gradlew build
```

Compare optional native-SDK limitations against `docs/rewrite/BASELINE.md`; never
claim a native regression fixed merely by disabling the backend.

### V3 — Vulkan runtime smoke

When a configured target host is available, verify menu, world load, 30 seconds
stationary, movement/chunk streaming, return to menu, and world reload with Vulkan.

### V4/V5

Feature scenarios and performance gates are required where the task/gate asks for
them. No optimization is accepted without measurement.

## Forbidden-change check

Before marking a task DONE, confirm its diff contains no files or semantic changes
forbidden by that task. In particular, ownership-only refactors must have zero shader
diff and zero algorithm/tuning changes.

## Commit format

```text
refactor(<subsystem>): <short action>

Task: AER-XXX
Behaviour change: none | <description>
Shader math change: none | <description>
Invariant impact:
- <ID>: unchanged | <description>

Validation:
- V0 PASS
- V1 PASS / NOT AVAILABLE
- V2 PASS / NOT AVAILABLE
- V3 PASS / NOT AVAILABLE
```

Documentation/infrastructure tasks may use an appropriate `docs(...)` or
`build(...)` subject while retaining the task metadata in the body.

## Stop condition

Do not start architectural refactoring before GATE-0. A BLOCKER or MAJOR regression
prevents task completion. If a task cannot be safely completed, write the evidence to
`docs/rewrite/BLOCKERS.md` and stop only the dependency chain that relies on it.


## Canonical roadmap governance

## 1. Source of truth

Ordine di precedenza obbligatorio:

```text
1. docs/rewrite/ROADMAP.md
2. docs/rewrite/INVARIANTS.md
3. docs/rewrite/MIGRATION_STATE.yaml
4. AGENTS.md per regole operative non in conflitto
5. prompt/chat/note esterne
```

Prima di iniziare un AER, leggere sempre `ROADMAP.md`, lo stato corrente e le invarianti rilevanti. Una copia esterna più permissiva non può ridurre acceptance o forbidden changes del roadmap versionato.

## 2. Significato di DONE

Salvo task esplicitamente marcati `CONTRACT_ONLY`, `DOC_ONLY`, `SHADOW_ONLY` o equivalente, `DONE` richiede integrazione reale nel production runtime.

Non sono sufficienti da soli:

- classe/record/facade/container creati;
- unit test isolato PASS;
- compilazione riuscita;
- API presente ma mai referenziata da `src/main`.

Per extraction/ownership/coordinator/facade:

- il nuovo component deve essere usato dal percorso runtime previsto;
- la responsabilità migrata deve essere rimossa o delegata dal vecchio owner;
- nessuna doppia authority/stato duplicato al momento di DONE, salvo eccezione esplicita;
- API minima obbligatoria completa salvo `optional/deferred` documentato;
- characterization aggiornata all'owner nuovo senza indebolire la semantica.

## 3. Eccezioni intenzionali

- `CONTRACT_ONLY`: il task può chiudersi senza adozione runtime solo se il contract rappresenta esattamente il comportamento reale che i task dipendenti adotteranno.
- `DOC_ONLY`: nessuna modifica production necessaria; il documento deve descrivere lo stato reale, non un target futuro spacciato per attuale.
- `SHADOW_ONLY`: il componente deve osservare/descrivere il runtime reale ma non diventa ancora authority di esecuzione.

Non usare queste etichette per aggirare acceptance.

## 4. Task transaction

Ogni AER riuscito termina nello stesso ciclo con:

```text
acceptance verificata
validation richiesta PASS
git diff --check PASS
review del diff
forbidden-change audit
result.md aggiornato
MIGRATION_STATE coerente
commit atomico
worktree clean
```

Al primo mismatch: STOP. Non iniziare il task successivo. Non accumulare due AER nello stesso commit.

## 5. Baseline-equivalent validation

Se la baseline congelata contiene failure note, PASS significa match esatto dell'insieme accettato: nessuna nuova failure, nessuna failure diversa, nessuna failure attesa scomparsa senza spiegazione.

Non “aggiustare” un characterization eliminandolo quando cambia ownership: aggiornalo per seguire il nuovo owner e conservare l'invariante.

## 6. Gate closure

Un gate richiede sia validation sia `integration completeness`.

Audit minimo:

- tutti gli AER richiesti soddisfano davvero le acceptance;
- componenti runtime-required realmente referenziati da `src/main`;
- nessuna ownership/stato duplicato vietato;
- nessuna API minima mancante;
- nessuna facade/container morta usata soltanto dai test;
- nessuna feature disabilitata per ottenere PASS;
- characterization semanticamente valida;
- limitazioni runtime dichiarate come `NOT AVAILABLE`, non `PASS`.

Se l'audit fallisce, il gate resta `PENDING` anche con V1/V2 verdi.

## 7. Semantic-domain safety

Non aliasare domini solo perché hanno lo stesso tipo numerico. Esempi:

```text
realtime delta ticks != seconds
light generation != scene generation
material epoch != scene generation
frame index != resource generation
```

Conversioni/alias non presenti nella reference richiedono contract esplicito o task dedicato.

## 8. Stop conditions

STOP immediato per:

- nuova failure rispetto alla baseline accettata;
- acceptance non dimostrabile;
- ownership/lifetime incerta su risorsa GPU critica;
- shader/math change in task che lo vieta;
- nuovo `waitIdle` hot-path;
- provider/backend multipli autorevoli quando il roadmap ne richiede uno;
- worktree non riconducibile a un singolo AER.

## 9. Gate report

Ogni gate deve produrre/aggiornare un report che distingua chiaramente:

```text
PASS
BASELINE-EQUIVALENT
NOT AVAILABLE
NOT TESTED
BLOCKED
```

Il report deve includere task/commit, acceptance evidence, integration audit, validation, forbidden-change audit, baseline issues e limitazioni dell'host.

## 10. Sequenza

Default: un solo AER ACTIVE per worktree, sequenziale. Il gate/fase successivo non inizia finché il gate corrente non è realmente PASS e il worktree non è clean.
