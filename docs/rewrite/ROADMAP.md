# Caustica Engine Rewrite — Agent Execution Roadmap

**Target:** refactoring incrementale e largamente autonomo di Caustica
**Reference implementation:** `xysgottaken2/Caustica` `0.2.0`
**Minecraft:** `26.2`
**Backend:** Vulkan
**Metodo:** extract → verify → abstract → migrate → compare → delete legacy

---

# 1. Scopo

Questo documento traduce la roadmap architetturale di Caustica in un piano eseguibile da un coding agent.

L'agente NON deve interpretare questo lavoro come:

```text
"riscrivi Caustica"
```

ma come:

```text
preserva una reference funzionante
        ↓
estrai una sola ownership/responsabilità
        ↓
compila e testa
        ↓
confronta
        ↓
committa
        ↓
passa al task successivo
```

Il risultato target rimane:

```text
Minecraft / Fabric
        ↓
Scene / adapters
        ↓
Acceleration Structure ownership
        ↓
Frame Pipeline
        ↓
Render Graph
        ↓
Path Tracing
 ├─ ReSTIR
 ├─ SHaRC
 └─ Volumetrics
        ↓
Reconstruction
 ├─ DLSS RR
 ├─ SVGF
 └─ NRD experimental
        ↓
Upscaling
 ├─ DLSS
 ├─ FSR
 ├─ XeSS
 └─ Native
        ↓
Post / HDR / Presentation
        ↓
Vulkan
```

Il percorso per arrivarci deve però rimanere incrementale.

---

# 2. Reference repository verificata

Nel fork analizzato esistono già, tra gli altri:

```text
src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java
src/main/java/dev/comfyfluffy/caustica/rt/RtContext.java
src/main/java/dev/comfyfluffy/caustica/rt/RtGpuExecutor.java
src/main/java/dev/comfyfluffy/caustica/rt/RtDeviceBringup.java
src/main/java/dev/comfyfluffy/caustica/rt/accel/RtAccel.java

src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrain.java
src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtDistantHorizonsTerrain.java

src/main/java/dev/comfyfluffy/caustica/compat/DistantHorizonsCompat.java
src/main/java/dev/comfyfluffy/caustica/compat/VoxyCompat.java

src/main/java/dev/comfyfluffy/caustica/rt/RtSharc.java

src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtSvgfDenoiser.java
src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDlssRr.java
src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtFsrUpscaler.java
src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtXessUpscaler.java
src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtNrdDenoiser.java

src/main/java/dev/comfyfluffy/caustica/rt/entity/RtWeatherCapture.java

shaders/world/lighting.slang
shaders/world/world.rgen.slang
shaders/world/clouds.slang
shaders/world/fog.slang
shaders/display/svgf_reproject.comp
shaders/display/svgf_atrous.comp
```

La reference contiene anche test già utilizzabili:

```text
RestirReservoirMathTest
RtCloudShaderRegressionTest
RtDenoiserShaderRegressionTest
RtFogShaderRegressionTest
RtWeatherCaptureTest
RtDistantHorizonsTerrainTest
RtTerrainLightChangeTest
RtMaterialAbiTest
RtLabPbrTest
...
```

**Nota NRD:** il sorgente e lo shim REBLUR sono presenti, ma nella reference `RtNrdDenoiser.enabled()` è deliberatamente disabilitato/retired. Non usarlo come baseline produttiva.

---

# 3. Regole non negoziabili per l'agente

## R-001 — Una responsabilità per task

Un task non può contemporaneamente:

```text
rifattorizzare ownership
+
cambiare algoritmo
+
ottimizzare shader
+
cambiare tuning
```

Se emergono più lavori, creare task separati.

## R-002 — Nessun opportunistic cleanup

Durante un task sono vietati, salvo richiesta esplicita del task:

- rename non correlati;
- formatting dell'intero file;
- dependency upgrade;
- aggiornamento Minecraft/Fabric;
- modifica di costanti di qualità;
- nuova feature;
- bug fix non necessario al task;
- modifica matematica di ReSTIR/SHaRC/SVGF;
- micro-ottimizzazioni.

## R-003 — Prove before delete

Per una migrazione:

```text
LEGACY
  ↓
NEW IMPLEMENTATION IN PARALLEL
  ↓
VALIDATION
  ↓
NEW DEFAULT
  ↓
LEGACY REMOVAL
```

Mai eliminare il percorso legacy prima di avere un percorso nuovo verificabile.

## R-004 — Il codice deve rimanere buildabile dopo ogni task

Un task non può essere marcato `DONE` se lascia il branch in uno stato volutamente non compilabile.

## R-005 — Nessun `vkDeviceWaitIdle` come scorciatoia

È vietato introdurre `vkDeviceWaitIdle` o equivalenti sul percorso hot per "risolvere" problemi di lifetime/sincronizzazione.

Se necessario soltanto per teardown/recreation già equivalente alla reference, documentarlo.

## R-006 — Nessuna modifica shader durante puro ownership refactor

Se un task è classificato `OWNERSHIP_ONLY`, i file shader sono read-only.

## R-007 — Nessuna modifica della matematica ReSTIR durante la migrazione

M/W caps, age, Jacobian, candidate weighting, temporal/spatial reuse e sampling rimangono invariati finché la migrazione non è completata e confrontata.

## R-008 — LOD proxy safety

Deve rimanere vero:

```text
replacement READY
prima di
retire current proxy
```

## R-009 — GPU lifetime safety

Una risorsa GPU non può essere distrutta finché il timeline token dell'ultimo utilizzo non è completato.

## R-010 — Failure isolation

Il fallimento di un backend opzionale deve produrre:

```text
disable/fallback
```

non:

```text
renderer crash
device lost intenzionale
rimozione di geometria valida
```

## R-011 — Iris non entra nel core

Il refactoring non deve introdurre dipendenze Iris.

## R-012 — Vulkan resta il backend

Non trasformare il rewrite in una HAL multi-backend OpenGL/DX/Vulkan.

## R-013 — NRD fuori dal critical path

NRD/REBLUR resta sperimentale finché non supera una milestone dedicata.

## R-014 — Un solo LOD provider owner

DH e Voxy non devono produrre simultaneamente geometria RT sovrapposta per lo stesso horizon.

## R-015 — Stop sicuro

Se un acceptance criterion non può essere dimostrato:

1. non marcare il task `DONE`;
2. revert delle modifiche del task oppure lasciare commit separato non mergeabile;
3. creare un blocker;
4. bloccare soltanto i task dipendenti;
5. non inventare workaround non richiesti.

---

## R-016 — `DONE` significa integrato, non scaffoldato

Salvo che un task sia marcato esplicitamente `CONTRACT_ONLY`, `DOC_ONLY`, `SHADOW_ONLY` o equivalente:

- creare una classe, record, facade, container o test non è sufficiente;
- il nuovo componente deve essere realmente referenziato dal percorso `src/main` previsto dal task;
- la responsabilità migrata deve essere rimossa dal vecchio owner oppure delegata al nuovo owner;
- stato/ownership duplicati sono ammessi solo durante l'implementazione e non al momento di `DONE`, salvo eccezione esplicita del task;
- una facade mai usata dal runtime è incompleta;
- una API dichiarata `minima` è obbligatoria salvo voce marcata `optional/deferred`;
- i characterization test devono seguire la nuova ownership senza indebolire l'invariante semantico.

Per i task `CONTRACT_ONLY` il risultato deve comunque dimostrare che il contract rappresenta esattamente il comportamento/reference che i task successivi dovranno adottare.

## R-017 — Roadmap canonica e precedenza delle fonti

La copia versionata nel repository deve essere la fonte canonica:

```text
docs/rewrite/ROADMAP.md
```

Ordine di precedenza:

```text
1. docs/rewrite/ROADMAP.md
2. docs/rewrite/INVARIANTS.md
3. docs/rewrite/MIGRATION_STATE.yaml
4. AGENTS.md per regole operative non in conflitto
5. prompt/chat/note esterne
```

Un agente non può dichiarare `DONE` o `PASS` basandosi su una copia esterna più permissiva del roadmap canonico.

## R-018 — Unità e domini semantici espliciti

Campi temporali, generazioni, versioni, extent, address e indici devono dichiarare la propria unità/dominio quando esiste rischio di alias semantico.

Esempi vietati:

```text
tick delta usato come secondi senza conversione
light generation usata come scene generation
material epoch usato come scene generation
frame index usato come resource generation senza contract
```

Una conversione o alias semantico non presente nella reference richiede un task esplicito.

## R-019 — Gate PASS = validation + integration completeness

Un gate non passa soltanto perché V1/V2 sono verdi.

Prima di `PASS` deve essere verificato che:

- tutti i task richiesti siano realmente conformi alle rispettive acceptance;
- i componenti che devono essere runtime-integrated siano effettivamente referenziati da `src/main`;
- non rimangano owner/stati duplicati vietati;
- non esistano API minime dichiarate ma mancanti;
- non esistano facade/container creati soltanto per i test;
- le characterization proteggano la semantica e non la posizione fisica legacy;
- le feature richieste dal gate non siano state silenziosamente disabilitate per ottenere PASS.

Se uno di questi punti fallisce, il gate resta `PENDING` anche con build/test verdi.

## R-020 — Chiusura transazionale di ogni task

Ogni task riuscito termina nello stesso ciclo con:

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

Al primo mismatch: `STOP`. Non iniziare il task successivo e non accumulare modifiche di due AER nello stesso commit.

# 4. File di controllo che l'agente deve creare

La prima fase crea:

```text
AGENTS.md
docs/rewrite/ROADMAP.md
docs/rewrite/ARCHITECTURE_TARGET.md
docs/rewrite/INVARIANTS.md
docs/rewrite/MIGRATION_STATE.yaml
docs/rewrite/BLOCKERS.md
docs/rewrite/BASELINE.md
docs/rewrite/tasks/
scripts/agent/
```

`ROADMAP.md` è la source-of-truth di scope, dipendenze, forbidden changes e acceptance.

`MIGRATION_STATE.yaml` è la source-of-truth dello stato di esecuzione.

Nessuno dei due sostituisce l'altro.

Esempio:

```yaml
schema: 1

tasks:
  AER-000:
    state: DONE
    commit: abcdef1
  AER-010:
    state: ACTIVE
  AER-011:
    state: BLOCKED
    blocked_by:
      - AER-010

last_verified_reference: reference/xysgottaken2-caustica-0.2.0
```

Stati ammessi:

```text
PENDING
READY
ACTIVE
BLOCKED
VALIDATING
DONE
SUPERSEDED
```

Un solo task di modifica architetturale può essere `ACTIVE` per worktree.

---

# 5. Protocollo di esecuzione dell'agente

Per ogni ciclo:

```text
1. git status
2. leggere AGENTS.md
3. leggere docs/rewrite/ROADMAP.md
4. leggere MIGRATION_STATE.yaml
5. leggere INVARIANTS.md
6. scegliere il task READY con ID più basso
7. leggere soltanto il codice necessario
8. scrivere un mini pre-plan nel task log
9. implementare
10. eseguire VALIDATE_FAST
11. eseguire test specifici
12. eseguire VALIDATE_BUILD se richiesto
13. eseguire runtime validation se disponibile
14. controllare git diff
15. verificare forbidden changes
16. verificare integration/ownership completeness richiesta dal task
17. aggiornare result.md e MIGRATION_STATE
18. commit atomico
19. verificare worktree clean
20. continuare soltanto dopo il commit
```

Prima del commit:

```text
git diff --check
git status --short
```

L'agente deve controllare che non siano entrati file generati, SDK, DLL non richieste, cache, log o build output.

---

# 6. Livelli di validazione

## V0 — Static

Obbligatorio per ogni task:

```text
git diff --check
nessun conflitto
nessun file generato inatteso
```

## V1 — Unit / characterization tests

Default:

```text
./gradlew test
```

Su Windows:

```text
.\gradlew.bat test
```

Quando un task modifica un sottosistema, eseguire almeno anche i test specifici corrispondenti.

Se la baseline congelata contiene failure note, `V1 PASS` significa match esatto della baseline accettata: nessuna failure nuova, mancante o diversa. Un test di characterization che fallisce per semplice spostamento di ownership deve essere aggiornato per seguire la semantica nuova, non rimosso o indebolito.

## V2 — Build

```text
./gradlew build
```

I native SDK opzionali possono non essere disponibili. Il comportamento deve essere confrontato con la baseline dello stesso host.

Un task non può dichiarare regressione native risolta semplicemente disabilitando il backend.

## V3 — Vulkan runtime smoke

Su ambiente configurato:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Xmx8G -XX:+UseCompactObjectHeaders -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -XX:+UseZGC"
.\gradlew.bat runClient --args="--renderDebugLabels --graphicsBackend VULKAN"
```

Smoke minimo:

```text
menu
load world
30 s stationary
movement
chunk streaming
return menu
reload world
```

## V4 — Feature scenario

Usare scene di baseline:

```text
Overworld
interior
cave
water/glass
Nether
End
weather
DH/Voxy horizon
Elytra
teleport
dimension switch
```

## V5 — Performance gate

Confrontare:

```text
average frame time
P95
P99
VRAM
BLAS build time
TLAS time
LOD publication latency
```

Nessuna "ottimizzazione" è accettata senza misurazione.

---

# 7. Severity delle regressioni

## BLOCKER

- crash;
- `VK_ERROR_DEVICE_LOST`;
- corrupted TLAS/BLAS;
- stale dimension geometry;
- black output;
- missing world;
- deadlock;
- UAF;
- resource leak non bounded;
- test matematico ReSTIR fallito.

## MAJOR

- ghosting nuovo evidente;
- LOD holes persistenti;
- duplicated coarse/fine geometry;
- upscaler history errata;
- P99 > +20% senza motivo;
- VRAM growth non bounded.

## MINOR

- log noise;
- naming;
- debug counter mancante;
- piccola differenza non funzionale documentata.

Un task con BLOCKER o MAJOR non è `DONE`.

---

# 8. Formato obbligatorio dei commit

```text
refactor(<subsystem>): <azione breve>

Task: AER-XXX
Behaviour change: none | <descrizione>
Shader math change: none | <descrizione>
Invariant impact:
- GPU-001: unchanged
- ...

Validation:
- V0 PASS
- V1 PASS
- V2 PASS
- V3 NOT AVAILABLE / PASS
```

Non usare commit tipo:

```text
cleanup
misc fixes
refactor stuff
wip
```

Un AER non è completato finché il commit atomico non esiste e `git status --short` è vuoto. Il gate successivo non può iniziare su un worktree con modifiche residue del task precedente.

---

# 9. Invarianti iniziali

## GPU

### GPU-001
Nessuna risorsa GPU viene distrutta prima del completamento dell'ultimo utilizzo.

### GPU-002
Una BLAS referenziata da un TLAS pubblicato non può essere distrutta.

### GPU-003
Una risorsa condivisa tra queue deve avere sincronizzazione/ownership equivalente o più forte della reference.

### GPU-004
Nessun nuovo `waitIdle` sul hot path.

## Temporal

### TMP-001
Frame N legge soltanto history di frame precedenti.

### TMP-002
Camera cut/teleport/dimension change deve invalidare gli stati temporali incompatibili.

### TMP-003
Resolution change invalida gli history buffer la cui dimensione non coincide.

### TMP-004
Un backend temporale non può ricevere contemporaneamente `current` e `previous` aliasati in modo non previsto.

## ReSTIR

### RST-001
Il previous reservoir è read-only durante la generazione del current reservoir.

### RST-002
Ping-pong index semantically identical alla reference.

### RST-003
La matematica del reservoir non cambia durante `OWNERSHIP_ONLY`.

### RST-004
Scene/light generation incompatibile invalida il reuse.

## LOD

### LOD-001
Il proxy pubblicato rimane valido durante la preparazione della sostituzione.

### LOD-002
Coarse viene rimosso soltanto quando la copertura fine richiesta è completa.

### LOD-003
`sourceKey + sourceVersion` invariati permettono reuse.

### LOD-004
Batch size rimane bounded.

### LOD-005
World/dimension transition non riusa snapshot della dimensione precedente.

### LOD-006
Provider failure non ritira full terrain valido.

### LOD-007
DH/Voxy non sono owner simultanei dello stesso distant horizon.

## Reconstruction/upscale

### REC-001
Un solo reconstruction/denoiser temporale principale per frame.

### UPS-001
Un solo upscaler temporale per frame.

### UPS-002
Jitter, render resolution e reset history sono coerenti con l'upscaler attivo.

## Compatibility

### CMP-001
Caustica core non dipende da Iris.

### CMP-002
Il core non richiede DH o Voxy per partire.

---

# 10. Dependency graph globale

```text
AER-000 ─┬─ AER-001 ─ AER-002 ─ AER-003 ─ AER-004
         │
         └───────────────────────────────────── GATE-0
                                                  │
                       ┌──────────────────────────┴────────────────────┐
                       ▼                                               ▼
                   AER-010                                         AER-020
                       │                                               │
                   AER-011                                         AER-021
                       │                                               │
                   AER-012                                      ┌────┴────┐
                       │                                        ▼         ▼
                   AER-013                                   AER-022   AER-023
                  ┌────┼────┐                                    \       /
                  ▼    ▼    ▼                                     AER-024
             AER-014 015 016                                         │
                  \    |    /                                        ▼
                    GATE-1                                         AER-025
                                                                    │
                                                                 AER-026
                                                                    │
                                                                 AER-027
                                                                    │
                                                                  GATE-2

GATE-1 + GATE-2
       │
       ▼
AER-030 → 031 → 032 → 033 → 034 → 035 → GATE-3
                                      │
                                      ▼
AER-040 → 041 → 042 → 043 → 044 → 045 → 046 → GATE-4
                                      │
            ┌─────────────────────────┴──────────────────────────┐
            ▼                                                    ▼
       AER-050...058                                        AER-060...064
            │                                                    │
          GATE-5                                               GATE-6
            └─────────────────────────┬──────────────────────────┘
                                      ▼
                              AER-070...073
                                      │
                                    GATE-7
                                      ▼
                              AER-080...084
                                      │
                                    GATE-8
                                      ▼
                              AER-090...093
                                      │
                                  FINAL GATE
```

---

# 11. Fase 0 — Bootstrap e baseline

## AER-000 — Freeze reference

**Tipo:** documentation / git
**Rischio:** basso
**Dipendenze:** nessuna

### Goal

Creare una reference immutabile.

### Azioni

1. registrare commit SHA reale del repository;
2. creare tag/branch:
   `reference/xysgottaken2-caustica-0.2.0`;
3. registrare versioni da `gradle.properties`;
4. registrare Java, OS, GPU, driver, Vulkan SDK;
5. registrare presenza SDK NGX/FSR/NRD/XeSS.

### File

Nuovi:

```text
docs/rewrite/BASELINE.md
```

### Forbidden

Nessuna modifica a `src/`, `shaders/`, `native/`.

### Acceptance

- reference identificabile;
- working tree clean;
- baseline documentata.

### Commit

```text
docs(rewrite): freeze 0.2.0 reference baseline
```

---

## AER-001 — Validation scripts

**Dipendenze:** AER-000

Creare:

```text
scripts/agent/validate-fast.sh
scripts/agent/validate-fast.ps1
scripts/agent/validate-build.sh
scripts/agent/validate-build.ps1
```

`validate-fast`:

```text
git diff --check
gradle test
```

`validate-build`:

```text
gradle test
gradle build
```

Non scaricare SDK proprietari automaticamente.

### Acceptance

- exit code non-zero su failure;
- output chiaro;
- non modifica source tree.

---

## AER-002 — Agent control documents

**Dipendenze:** AER-001

Creare:

```text
AGENTS.md
docs/rewrite/ARCHITECTURE_TARGET.md
docs/rewrite/INVARIANTS.md
docs/rewrite/MIGRATION_STATE.yaml
docs/rewrite/BLOCKERS.md
```

Copiare in `INVARIANTS.md` le invarianti di questo documento.

### Acceptance

L'agente successivo può determinare senza chat esterna:

```text
cosa fare
cosa non fare
qual è il prossimo task
come validarlo
```

---

## AER-003 — Dev-only migration gates

**Tipo:** infrastructure
**Dipendenze:** AER-002

Introdurre feature gate esclusivamente di sviluppo:

```text
engine.frameContextV2
engine.temporalV2
engine.lodV2
engine.pipelineV2
engine.gpuOwnershipV2
engine.sceneV2
engine.renderGraphV2
```

Preferire una classe interna non esposta all'utente finale.

### Scope primario

```text
src/main/java/dev/comfyfluffy/caustica/
```

Nuovo package suggerito:

```text
dev.comfyfluffy.caustica.rewrite
```

### Acceptance

Con tutti i flag `false`, il percorso runtime deve essere identico alla reference.

---

## AER-004 — Characterization baseline

**Dipendenze:** AER-003

Non modificare algoritmi.

Aggiungere test per comportamento oggi non protetto:

```text
ReSTIR current/previous addressing
temporal reset reasons
LOD source reuse
LOD incomplete replacement retains old proxy
provider priority Voxy → DH
upscaler mutual exclusion
NRD disabled baseline
```

Usare i test esistenti come base, in particolare:

```text
RestirReservoirMathTest
RtDistantHorizonsTerrainTest
RtDenoiserShaderRegressionTest
```

### Acceptance

La reference passa tutti i characterization test prima del refactoring.

---

# GATE-0 — Baseline frozen

Richiede:

```text
AER-000..004 DONE
V1 PASS
V2 PASS oppure baseline-equivalent environment limitation documentata
baseline/invariants/control files presenti e coerenti
reference tag/commit immutabile identificato
```

### Integration completeness

Prima di PASS:

- `ROADMAP.md`, `INVARIANTS.md`, `MIGRATION_STATE.yaml`, `BASELINE.md` e validation scripts devono essere utilizzabili senza dipendere dalla chat;
- characterization baseline deve essere eseguita e congelata prima dei refactor successivi;
- eventuali failure baseline accettate devono essere enumerate esattamente;
- nessun artefatto SDK/build deve essere incluso nel checkpoint;
- il reference tag non deve essere modificato per far passare la baseline.

---

# 12. Fase 1 — Estrarre stato a basso rischio

## AER-010 — `GpuCapabilities` read-only snapshot

**Tipo:** extraction
**Dipendenze:** GATE-0
**Rischio:** medio-basso

### Goal

Separare la descrizione delle capability dal codice che abilita le feature in `RtDeviceBringup`.

### Scope

```text
RtDeviceBringup.java
new: rt/device/GpuCapabilities.java
tests
```

### Regola

`RtDeviceBringup` continua a fare il bring-up. `GpuCapabilities` inizialmente è soltanto una vista immutabile dello stato già calcolato.

### Acceptance

- nessuna estensione Vulkan richiesta in più o in meno;
- SER/OMM/XeSS gating invariato;
- stesso log capability.

---

## AER-011 — `FrameContext`

**Dipendenze:** AER-010
**Rischio:** medio

Creare un record immutabile contenente soltanto dati già disponibili per frame:

```text
frameIndex
deltaTimeSeconds
display extent
render extent
camera current
camera previous
jitter
world identity
dimension
scene generation
```

### Semantica obbligatoria

`deltaTimeSeconds` è espresso in secondi. Se la source disponibile è `DeltaTracker.getRealtimeDeltaTicks()`, il valore in tick deve essere convertito esplicitamente usando la durata effettiva del tick disponibile nel runtime. `getGameTimeDeltaPartialTick()` resta interpolation state e non è frame duration.

`sceneGeneration` usa un token canonico se esiste. Se la reference non ne possiede uno, `0` significa esplicitamente `legacy/unversioned`. È vietato aliasare `lightGeneration` o material generation/material epoch a scene generation.

### Runtime integration

`FrameContext` deve essere realmente creato o ricevuto da almeno un seam `src/main` in modalità read-only/shadow. Il percorso legacy continua però a produrre il comportamento: nessun consumer deve essere migrato prematuramente.

### Forbidden

- non spostare ancora resource ownership;
- non modificare shader push layout;
- non modificare jitter;
- non modificare temporal behavior.

### Acceptance

- snapshot realmente presente nel production runtime;
- legacy path ancora semanticamente autorevole;
- nessun tick delta reinterpretato come secondi senza conversione;
- camera/matrici defensively immutable;
- scene generation non aliasata ad altri domini;
- nessun cambiamento visivo intenzionale.

---

## AER-012 — `TemporalResetReason`

**Dipendenze:** AER-011

Definire enum/bitset:

```text
WORLD_CHANGE
DIMENSION_CHANGE
CAMERA_CUT
TELEPORT
RESOLUTION_CHANGE
FOV_CHANGE
RESOURCE_RELOAD
MATERIAL_GENERATION_CHANGE
MANUAL
```

### Runtime integration

Non basta creare l'enum/bitset. Ogni sorgente di reset temporale legacy identificata deve produrre nel production runtime uno o più `TemporalResetReason`, senza cambiare destinatario, ordine, timing o numero effettivo dei reset.

Durante AER-012 il reset può continuare a raggiungere direttamente il destinatario legacy; la reason accompagna/traduce l'evento senza modificarne l'effetto.

Il result del task deve elencare i call-site/eventi legacy mappati.

### Acceptance

- mapping completo dei reset legacy identificati;
- nessun reset anonimo residuo per i call-site mappati;
- reason realmente prodotte da `src/main`, non soltanto dai test;
- stessi destinatari, ordine, timing e numero dei reset rispetto alla reference;
- ogni reset legacy produce un motivo esplicito e loggabile.

---

## AER-013 — `TemporalState` coordinator

**Dipendenze:** AER-012

Creare un coordinator senza possedere ancora le immagini dei backend.

Responsabilità:

```text
collect reset reasons
snapshot once per frame
broadcast reset request
clear consumed reasons
```

### Runtime integration

Il coordinator deve essere realmente istanziato/cablato nel percorso runtime legacy. Deve ricevere le reason AER-012, associare `FrameContext`, broadcastare verso gli stessi destinatari legacy, consumare reason solo dopo consegna riuscita e conservarle in caso di failure del consumer.

Non può possedere immagini/backend resources e non può essere un componente usato soltanto dai test.

### Acceptance

- coordinator realmente attivo in `src/main`;
- snapshot al massimo una volta per `frameIndex`;
- reason AER-012 raccolte realmente;
- stesso numero/timing/destinatari dei reset della reference;
- reason non perse su consumer failure;
- nessuna ownership di immagini backend;
- nessun coordinator morto/non referenziato.

---

## AER-014 — Estrarre `RestirHistory`

**Tipo:** OWNERSHIP_ONLY
**Dipendenze:** AER-013
**Rischio:** medio-alto

### Source attuale

`RtComposite` possiede:

```text
restirReservoirs[2]
restirWriteIndex
restirResourcesEnabled
allocation
fill/clear
device addresses
destroy
```

### Target

```text
rt/lighting/RestirHistory.java
```

API minima:

```java
ensure(...)
previousAddress()
currentAddress()
advance()
destroy()
```

`reset(...)` è obbligatorio soltanto se la reference possiede una semantica di reset distinta da `ensure(...)`/`destroy()`. Se tale semantica non esiste, il result deve documentare `reset: deferred/omitted — no separate legacy behavior`. Non inventare una nuova politica di reset per soddisfare il nome dell'API.

### Ownership rule

Alla chiusura del task `RestirHistory` è il vero owner della coppia reservoir, parity/write index, enabled/resource state, allocation/clear lifecycle e destruction. `RtComposite` può mantenere helper deleganti, non una seconda copia dello stato.

### Allowed

```text
RtComposite.java
new RestirHistory.java
tests
characterization ownership update
```

### Forbidden

```text
lighting.slang
world.rgen.slang
reservoir math
config tuning
new waitIdle
```

### Characterization

Il test deve seguire il nuovo owner e continuare a verificare semanticamente:

```text
previous = opposite ping-pong half
current = write-index half
push order = previous, current
advance only after accepted execute
```

### Acceptance

- `RestirHistory` realmente usato dal runtime;
- nessuna ownership ReSTIR duplicata residua in `RtComposite`;
- indirizzi current/previous e parity semanticamente identici;
- eventuale reset separato mantiene esattamente la semantica legacy;
- `RestirReservoirMathTest` PASS;
- characterization ReSTIR PASS;
- shader diff = zero;
- nessun nuovo waitIdle.

---

## AER-015 — Estrarre ownership/config SHaRC

**Tipo:** OWNERSHIP_ONLY
**Dipendenze:** AER-013

Target:

```text
rt/lighting/SharcRadianceCache.java
```

Spostare da `RtComposite`:

```text
world/dimension tracking
reset
debug state
parameter materialization
cache address access
```

La migrazione può essere graduale durante l'implementazione, ma prima di `DONE` tutte le responsabilità elencate devono passare realmente attraverso `SharcRadianceCache` nel production runtime.

`RtSharc` può rimanere implementation detail per GPU/cache/dispatch/storage internals. Una facade composta soltanto da forwarding method ma bypassata da `RtComposite` è incompleta.

### Forbidden

- `sharc.slang` math change;
- nuova cache policy;
- cambiamento shader parameters;
- cambiamento reset timing.

### Acceptance

- `SharcRadianceCache` realmente referenziato/usato da `src/main`;
- world/dimension tracking, reset, debug state, parameter materialization e cache address access attraversano il nuovo boundary;
- nessuna responsabilità migrata rimane duplicata in `RtComposite`;
- stessi parametri inviati allo shader;
- stessi eventi e timing di reset;
- shader/cache math invariata.

---

## AER-016 — Estrarre `SvgfResources`

**Tipo:** OWNERSHIP_ONLY
**Dipendenze:** AER-013

Spostare da `RtComposite`:

```text
svgfHistoryPing/Pong
svgfMomentsPing/Pong
svgfFilterPing/Pong
svgfPrevViewZ
svgfPrevNormal
history parity
hasHistory
previous camera
```

Target:

```text
rt/reconstruction/SvgfResources.java
```

### Ownership rule

Prima di `DONE`, `SvgfResources` è il vero owner runtime dello stato/risorse elencate. `RtComposite` deve rimuovere i field duplicati oppure delegare al nuovo owner. Non basta creare il container e un test isolato.

`RtSvgfDenoiser` continua a registrare gli stessi dispatch.

### Acceptance

- `SvgfResources` realmente usato dal production runtime;
- history/moments/filter ping-pong, previous view-Z/normal, parity, `hasHistory` e previous camera posseduti dal nuovo owner;
- nessuno stato SVGF migrato rimane duplicato in `RtComposite`;
- shader diff zero;
- descriptor behavior invariato;
- parity/history semantics equivalenti alla reference;
- `RtDenoiserShaderRegressionTest` PASS.

---

# GATE-1 — Temporal/state extraction

Richiede:

```text
AER-010..016 DONE
V1 PASS
V2 PASS
V3 smoke PASS se disponibile
nessun cambiamento visivo intenzionale
integration completeness PASS
```

### Integration completeness

Audit obbligatorio:

```text
FrameContext realmente creato/ricevuto dal runtime
TemporalResetReason realmente prodotto dai reset legacy
TemporalState realmente collegato
RestirHistory vero owner del ping-pong ReSTIR
SharcRadianceCache realmente adottato per AER-015
SvgfResources vero owner dello stato/risorse AER-016
```

Cercare anche componenti production mai referenziati, ownership/stato duplicati, API minime mancanti e characterization ancora accoppiate alla posizione fisica legacy.

Se uno di questi punti fallisce, GATE-1 resta `PENDING` anche con V1/V2 verdi.

---

# 13. Fase 2 — LOD: generalizzare ciò che funziona già

## AER-020 — Spostare `LodMesh` in tipo neutrale

**Dipendenze:** GATE-0
**Rischio:** basso

Oggi `LodMesh` vive in `DistantHorizonsCompat`.

Creare:

```text
rt/lod/LodMesh.java
```

Preservare campi/semantica esistenti:

```text
key
version
originX/Y/Z
width
dataPointWidth
opaque
transparent
```

### Acceptance

DH e Voxy producono lo stesso contenuto byte-for-byte rispetto alla reference.

---

## AER-021 — `LodMeshSnapshot` + `LodMeshSource`

**Tipo:** CONTRACT_ONLY

**Dipendenze:** AER-020

Creare contract minimale derivato dal comportamento reale, non da un provider teorico.

```java
interface LodMeshSource {
    LodMeshSnapshot snapshot(...);
    long revision();
    int renderDistanceChunks();
    void reset();
}
```

Non introdurre ancora public DH terrain API.

### Acceptance

Contract sufficiente a rappresentare esattamente DH/Voxy reference.

---

## AER-022 — `DhLodMeshSource`

**Dipendenze:** AER-021

Wrappare la logica esistente di:

```text
DistantHorizonsCompat
DistantHorizonsLodBufferMixin
```

Il Mixin può continuare a catturare VBO DH.

### Important

Non riscrivere il meshing DH.

### Acceptance

Snapshot DH equivalente per key/version/origin/width/data.

---

## AER-023 — `VoxyBridgeLodMeshSource`

**Dipendenze:** AER-021

Wrappare `VoxyCompat`.

Preservare reflection verso:

```text
me.cortex.voxy.client.compat.CausticaBridge
```

Aggiungere controllo ABI/versione se il bridge lo permette senza rompere compatibilità.

### Acceptance

- Voxy compatibile → source attivo;
- bridge assente/incompatibile → clean fallback;
- nessun claim di compatibilità con Voxy standard.

---

## AER-024 — `LodProviderSelector`

**Dipendenze:** AER-022, AER-023

Estrarre dalla compatibilità DH la policy:

```text
Voxy snapshot valido
→ Voxy

else DH valido
→ DH

else disabled
```

### Acceptance

`LOD-007` verificato da unit test.

---

## AER-025 — Rename `RtDistantHorizonsTerrain` → `RtLodTerrain`

**Dipendenze:** AER-024

Rename semantico senza refactor interno sostanziale.

### Acceptance

- behavior diff zero;
- test rinominati/migrati;
- nessuna modifica algoritmica.

---

## AER-026 — Estrarre `LodCoverageResolver`

**Dipendenze:** AER-025

Estrarre logica equivalente a:

```text
removeFullyCoveredCoarseMeshes(...)
```

### Unit cases

```text
complete coverage → remove coarse
partial coverage → keep coarse
mixed version → safe behavior
negative coordinates
boundary touch only
```

### Acceptance

Output identico alla reference sui casi esistenti.

---

## AER-027 — Estrarre `LodBatchPlanner`

**Dipendenze:** AER-026

Spostare:

```text
MAX_BUILD_QUADS = 131072
batchKey
slice planning
reuse vs rebuild decision
```

senza cambiare il limite.

### Acceptance

Stessi batch keys e stessa suddivisione a parità di snapshot.

---

## AER-028 — `LodBuildSession` state machine

**Dipendenze:** AER-027

Formalizzare gli stati impliciti:

```text
PLANNED
PACKING
GPU_BUILDING
CHECKPOINT_READY
PUBLISHED
FINAL
CANCELLED
```

Preservare progressive checkpoint e old-proxy retention.

### Acceptance

Test espliciti:

```text
old proxy remains during incomplete replacement
progressive checkpoint can publish
final checkpoint drops stale
cancelled generation never publishes
```

---

# GATE-2 — LOD abstraction

Richiede:

```text
AER-020..028 DONE
RtDistantHorizonsTerrainTest equivalent/new tests PASS
DH smoke PASS quando disponibile
Voxy smoke PASS quando disponibile
integration completeness PASS
```

Il renderer deve poter usare `RtLodTerrain` senza conoscere quale provider ha prodotto lo snapshot.

### Integration completeness

Verificare:

- `LodMesh` neutrale usato realmente da DH e Voxy senza conversioni divergenti;
- `LodMeshSource` rappresenta esattamente entrambi i provider; essendo `CONTRACT_ONLY`, non richiede da solo ownership runtime prima di AER-022/023;
- DH e Voxy source reali alimentano il percorso runtime tramite il selector;
- `LodProviderSelector` è l'unico owner della policy Voxy → DH → disabled;
- `RtLodTerrain` non contiene più policy specifica del provider;
- `LodCoverageResolver`, `LodBatchPlanner` e `LodBuildSession` sono realmente usati dal runtime e non soltanto testati isolatamente;
- progressive checkpoint, reuse e old-proxy retention rimangono semanticamente equivalenti;
- nessun horizon viene pubblicato contemporaneamente da due provider owner.

---

# 14. Fase 3 — Introdurre una FramePipeline lineare

Questa fase NON implementa ancora un Render Graph.

## AER-030 — `FramePass` + `FramePipeline`

**Dipendenze:** GATE-1, GATE-2

Creare:

```java
interface FramePass {
    void execute(FrameContext frame);
}
```

e:

```text
FramePipeline
```

Inizialmente può contenere un solo `LegacyCompositePass` che chiama il percorso attuale.

### Acceptance

Behavior identico.

---

## AER-031 — `PrepareFramePass`

Spostare soltanto preparazione di stato/capability/reset che non registra lavoro shader sostanziale.

### Acceptance

Ordine chiamate legacy invariato.

---

## AER-032 — `PathTracePass` wrapper

Estrarre la chiamata/recording del path trace senza modificare shader e binding ABI.

### Acceptance

- output raw path-traced identico nei test/capture disponibili;
- shader diff zero.

---

## AER-033 — `ReconstructionPass` wrapper

Raggruppare selezione:

```text
DLSS-RR
or
SVGF
```

senza ancora introdurre interfaccia generica.

NRD resta disabled.

### Acceptance

Stesso backend scelto nelle stesse condizioni.

---

## AER-034 — `UpscalePass` wrapper

Raggruppare:

```text
DLSS path
FSR
XeSS
Native
```

Preservare mutual exclusion.

---

## AER-035 — `PostPresentPass`

Raggruppare:

```text
exposure
bloom/display transform
HDR/SDR
presentation
```

Senza cambiare ordine.

---

# GATE-3 — Explicit linear pipeline

Richiede:

```text
FramePipeline runtime attiva:
Prepare
→ PathTrace
→ Reconstruction
→ Upscale
→ Post/Present
integration completeness PASS
V1/V2 PASS
V3 smoke PASS se disponibile
```

`RtComposite` può ancora possedere risorse, ma non deve essere l'unico luogo in cui l'ordine dei macro-pass è comprensibile.

### Integration completeness

Verificare:

- `FramePipeline` viene realmente eseguita dal production runtime;
- ogni pass AER-031..035 è nel percorso runtime e non un wrapper morto;
- l'ordine macro osservato è equivalente alla reference;
- le responsabilità spostate nei pass non restano duplicate come orchestrazione attiva parallela in `RtComposite`;
- PathTrace mantiene shader/binding ABI;
- Reconstruction sceglie lo stesso backend nelle stesse condizioni;
- Upscale mantiene mutual exclusion;
- Post/Present mantiene ordine HDR/SDR/presentation;
- nessun Render Graph viene introdotto anticipatamente.

---

# 15. Fase 4 — Ownership GPU e acceleration structures

## AER-040 — `DeferredDeletionQueue` facade

**Dipendenze:** GATE-3

Non sostituire subito `RtGpuExecutor`.

Creare facade sopra:

```text
retireAfterGraphics(...)
```

Target:

```text
rt/gpu/DeferredDeletionQueue.java
```

### Acceptance

Stessi timeline token e timing di distruzione.

---

## AER-041 — Resource ownership audit

**Tipo:** DOC_ONLY

**Dipendenze:** AER-040

Nessun refactor importante.

Produrre:

```text
docs/rewrite/GPU_OWNERSHIP.md
```

Tabella:

```text
resource
owner
creator
last-use tracker
destroy path
queue
recreation trigger
```

Coprire almeno:

```text
RtBuffer
RtImage
ReSTIR buffers
SVGF images
LOD BLAS/backing/scratch
terrain BLAS
entity BLAS
TLAS
upscaler outputs
```

### Acceptance

Ogni risorsa critica ha un owner dichiarato.

---

## AER-042 — `AccelerationStructureManager` facade

**Dipendenze:** AER-041

Non riscrivere `RtAccel`.

Creare un facade che inizialmente delega ai metodi statici `RtAccel`.

API minimale:

```text
prepareStaticBlas
prepareUpdatableBlas
refit
compact
buildTlas
retire
```

### Acceptance

Stessi flags Vulkan e stessa geometria.

---

## AER-043 — Migrare LOD AS ownership

**Dipendenze:** AER-042

`RtLodTerrain` non deve più decidere direttamente come distruggere AS/backing/scratch.

### Acceptance

- LOD progressive/reuse invariato;
- nessun leak;
- nessun early destroy.

---

## AER-044 — Migrare terrain AS ownership

**Dipendenze:** AER-043

Migrare `RtTerrain` progressivamente verso `AccelerationStructureManager`.

### Forbidden

Non modificare contemporaneamente terrain meshing.

---

## AER-045 — Migrare entity AS ownership

**Dipendenze:** AER-044

Migrare static/update/refit entity BLAS paths.

### Acceptance

Animation/refit behavior equivalente.

---

## AER-046 — Central GPU lifetime validation

**Dipendenze:** AER-045

Aggiungere debug assertions/counters dove possibile:

```text
retired resources pending
AS live count
BLAS live bytes
deferred destroy queue depth
```

### Acceptance

Nessuna distruzione AS critica bypassa il manager salvo eccezioni documentate.

---

# GATE-4 — GPU ownership

Richiede:

```text
AER-040..046 DONE
GPU-001..004 dimostrabili
Vulkan validation senza nuovi errori
LOD/terrain/entity smoke PASS
integration completeness PASS
```

### Integration completeness

Verificare:

- `GPU_OWNERSHIP.md` descrive gli owner reali osservati nel codice, non un target futuro;
- `DeferredDeletionQueue` è usata dove il task dichiara migrazione, con token/timing equivalenti;
- `AccelerationStructureManager` è realmente il boundary runtime per le ownership migrate;
- LOD, terrain ed entity non bypassano il manager per destroy/retire critici salvo eccezioni documentate;
- nessuna AS/backing/scratch ownership resta duplicata;
- nessun early destroy, leak non bounded o nuovo `waitIdle`;
- flags/geometria/refit/compaction restano equivalenti alla reference;
- i counters/assertions AER-046 osservano il runtime reale.

---

# 16. Fase 5 — Abstractions solo dopo extraction

## AER-050 — `ReconstructionBackend`

**Tipo:** CONTRACT_ONLY

**Dipendenze:** GATE-4

Ora che gli state object sono separati, introdurre:

```java
interface ReconstructionBackend {
    boolean available(...);
    void requestReset(...);
    ReconstructionResult execute(...);
    void destroy();
}
```

Non forzare tutti i backend ad avere input che non usano.

---

## AER-051 — SVGF backend

Migrare `RtSvgfDenoiser + SvgfResources`.

### Acceptance

Reference non-DLSS:

```text
PathTracer → SVGF
```

equivalente.

---

## AER-052 — DLSS-RR backend

Wrappare `RtDlssRr`.

Preservare:

```text
feature recreation
quality
jitter
motion
depth
reset history
failure latch
```

---

## AER-053 — `UpscalerBackend`

**Tipo:** CONTRACT_ONLY

Contract:

```text
availability
recommended render extent
history reset
dispatch
destroy
```

Un solo backend selezionabile per frame.

---

## AER-054 — FSR backend

Wrappare `RtFsrUpscaler`.

Preservare FidelityFX 3.1 path e reversed-Z semantics.

---

## AER-055 — XeSS backend

Wrappare `RtXessUpscaler`.

Preservare capability gating proveniente da device bring-up.

---

## AER-056 — Native/off backend

Creare backend esplicito per render resolution = display resolution e nessun temporal upscale.

Serve a eliminare `null means native`.

---

## AER-057 — NRD quarantine

Spostare NRD sotto:

```text
rt/reconstruction/experimental/nrd/
```

oppure equivalente logico.

Non riattivarlo.

Documentare:

```text
source present
native shim present
runtime path retired
not baseline
```

### Acceptance

Nessun utente/backend selection path lo sceglie accidentalmente.

---

## AER-058 — `RestirSystem` + `SharcRadianceCache`

Solo adesso creare facade funzionali sopra gli state object estratti.

Il path tracer deve ricevere bindings/parameters dai sistemi, non da campi sparsi di `RtComposite`.

### Forbidden

Nessuna modifica matematica.

---

# GATE-5 — Feature modules

Richiede:

```text
SVGF backend PASS
DLSS-RR backend PASS dove disponibile
FSR/XeSS backend PASS dove disponibili
Native/off backend PASS
ReSTIR tests PASS
SHaRC behavior PASS
NRD resta experimental
integration completeness PASS
```

### Integration completeness

Verificare:

- i contract `ReconstructionBackend` e `UpscalerBackend` sono implementati dai backend previsti e realmente usati dal selector/runtime;
- un solo reconstruction backend e un solo upscaler backend sono attivi per frame secondo la policy reference;
- fallback opzionali disabilitano il singolo backend senza corrompere il renderer;
- Native/off elimina `null means native` nel runtime;
- NRD non è selezionabile accidentalmente;
- `RestirSystem`/`SharcRadianceCache` forniscono realmente bindings/parameters al path tracer, senza campi equivalenti sparsi ancora autorevoli in `RtComposite`;
- jitter, motion, depth, reversed-Z, reset history e capability gating restano equivalenti.

---

# 17. Fase 6 — Scene minimale, non Scene DB completo

## AER-060 — `RtScene` minimale

**Tipo:** CONTRACT_ONLY

**Dipendenze:** GATE-4

Creare una struttura che rappresenti soltanto ciò che serve realmente per TLAS/frame:

```text
full terrain instances
entity instances
LOD instances
light view
material view
scene generation
```

Non creare ancora cinque database generici.

---

## AER-061 — Terrain scene contribution

`RtTerrain` espone un immutable/frame snapshot di istanze invece di far conoscere al consumer i dettagli interni.

---

## AER-062 — Entity scene contribution

Stesso principio per `RtEntities`.

---

## AER-063 — LOD scene contribution

`RtLodTerrain` espone istanze pubblicate, non build session internals.

---

## AER-064 — Scene assembly

Creare:

```text
SceneAssembler
```

che unisce le tre contribution e produce input TLAS.

### Acceptance

A parità di frame, instance count/mask/customIndex/SBT offset equivalenti alla reference.

---

# GATE-6 — Minimal scene ownership

Richiede:

```text
AER-060..064 DONE
TLAS input equivalence tests/debug comparison
integration completeness PASS
```

Non è ancora richiesto `MeshDatabase/TextureDatabase/...`.

### Integration completeness

Verificare:

- `RtScene`/`SceneAssembler` producono realmente l'input TLAS del production runtime;
- terrain/entity/LOD contribution sono snapshot pubblicati e non espongono build internals;
- il consumer TLAS non deve conoscere dettagli interni di `RtTerrain`, `RtEntities` o `RtLodTerrain` oltre al contract;
- instance count, mask, customIndex e SBT offset sono equivalenti frame-per-frame alla reference;
- `scene generation` ha un dominio canonico e non viene aliasata a light/material generation;
- nessun secondo percorso parallelo continua a costruire un TLAS autorevole bypassando la scene assembly, salvo shadow comparison esplicitamente gated.

---

# 18. Fase 7 — Environment extraction

## AER-070 — `EnvironmentParameters`

**Dipendenze:** GATE-3

Centralizzare:

```text
dimension
sun/moon
weather state
fog parameters
cloud parameters
time
```

Senza cambiare shader ABI nel primo task.

---

## AER-071 — Fog module

Estrarre orchestration/config/bindings relativi a `fog.slang`.

Il fog resta participating medium nel path; non trasformarlo in post-process.

---

## AER-072 — Cloud module

Estrarre orchestration/config/history di `clouds.slang`.

Preservare:

```text
camera visibility
reflection participation
cloud shadows
```

---

## AER-073 — Weather scene adapter

Trasformare `RtWeatherCapture` in producer compatibile con scene/AS ownership senza cambiare la ricostruzione rain/snow.

---

# GATE-7 — Environment modules

Richiede:

```text
Overworld PASS
Nether PASS
End PASS
rain PASS
snow PASS
cloud/fog regression tests PASS
integration completeness PASS
```

### Integration completeness

Verificare:

- `EnvironmentParameters` è il boundary runtime autorevole per dimension/sun-moon/weather/fog/cloud/time migrati;
- fog orchestration/config/bindings passano dal modulo estratto senza trasformarlo in post-process;
- cloud orchestration/config/history passano dal modulo estratto preservando camera visibility, reflections e shadows;
- weather adapter produce realmente scene/AS input senza cambiare rain/snow reconstruction;
- `RtComposite` non mantiene calcoli parametrici duplicati ancora autorevoli per le responsabilità migrate;
- shader ABI/math resta invariata salvo task esplicito futuro.

---

# 19. Fase 8 — Render Graph come evoluzione della pipeline

Solo ora si introduce il vero Render Graph.

## AER-080 — Graph in shadow mode

**Tipo:** SHADOW_ONLY

**Dipendenze:** GATE-3, GATE-4, GATE-5, GATE-6

Creare strutture:

```text
GraphPass
GraphResource
GraphAccess
```

ma NON eseguire ancora il graph.

Il graph descrive la `FramePipeline` corrente e viene usato per debug/validation.

### Acceptance

Topological order calcolato = ordine FramePipeline esistente.

---

## AER-081 — Resource declarations

Ogni pass dichiara:

```text
read
write
read/write
queue
stage
layout expectation
```

Nessuna barriera automatica ancora.

### Acceptance

Il graph riesce a diagnosticare:

```text
read-before-write
multiple writer ambiguity
undeclared resource
```

---

## AER-082 — Render Graph execution with explicit legacy barriers

Il graph inizia a eseguire i pass, ma usa ancora le barrier/transition esistenti.

### Acceptance

Behavior identico alla FramePipeline.

Feature gate:

```text
engine.renderGraphV2
```

deve consentire A/B nello stesso commit.

---

## AER-083 — Automatic barrier synthesis

Migrare una famiglia di risorse alla volta:

```text
1. post images
2. denoiser images
3. upscaler images
4. path-trace outputs
```

Non iniziare da AS.

Per ogni famiglia:

```text
old explicit barriers
vs
generated barriers
```

A/B + Vulkan validation.

---

## AER-084 — Queue dependency scheduling

Solo dopo AER-083.

Integrare:

```text
graphics
compute
transfer/build
timeline dependencies
```

Mantenere inizialmente lo stesso overlap della reference; ottimizzazione successiva.

### Non-obiettivo iniziale

Transient aliasing.

Aggiungerlo soltanto in un task futuro se il profiler mostra beneficio.

---

# GATE-8 — Render Graph

Richiede:

```text
same pass order semantics
automatic barriers validated
no new Vulkan validation errors
no P99 regression > agreed threshold
no new waitIdle
integration completeness PASS
```

### Integration completeness

Verificare:

- AER-080 shadow graph descrive realmente la FramePipeline runtime e il topological order coincide;
- resource declarations coprono tutte le risorse dei pass migrati e diagnosticano read-before-write/multiple-writer/undeclared access;
- AER-082 esegue realmente il graph dietro feature gate mantenendo barrier legacy e supportando A/B;
- per ogni famiglia migrata in AER-083, la barrier generata sostituisce realmente quella legacy corrispondente dopo A/B PASS; non mantenere due authority di sincronizzazione non documentate;
- queue dependencies AER-084 rispettano timeline/ownership e inizialmente lo stesso overlap della reference;
- nessun AS viene usato come prima famiglia per automatic barrier synthesis;
- nessun transient aliasing viene introdotto senza task/profiling dedicato.

---

# 20. Fase 9 — Cleanup finale

## AER-090 — Ridurre `RtComposite`

**Dipendenze:** GATE-8

Rimuovere soltanto ownership già migrata.

Target concettuale:

```java
renderFrame(FrameContext frame) {
    framePipeline.execute(frame);
}
```

oppure delega equivalente al RenderGraph.

### Acceptance

`RtComposite` non possiede più direttamente:

```text
ReSTIR history
SVGF history
LOD lifecycle
AS lifetime
upscaler lifetime
```

---

## AER-091 — Legacy path removal

Rimuovere i percorsi V2/legacy soltanto uno alla volta.

Ordine:

```text
LOD
temporal state
reconstruction
upscaler
scene
pipeline
render graph
```

Dopo ogni rimozione eseguire V1/V2/V3.

---

## AER-092 — Feature gate cleanup

Rimuovere i dev flag che non hanno più due implementazioni.

Un flag non deve sopravvivere soltanto "nel caso serva".

---

## AER-093 — Final architecture/benchmark report

**Tipo:** DOC_ONLY

Produrre:

```text
docs/rewrite/FINAL_REPORT.md
```

Confronto:

```text
reference 0.2.0
vs
rewrite
```

Per:

```text
feature parity
tests
CPU
GPU
P95
P99
VRAM
BLAS counts
LOD rebuild/reuse
device compatibility
known regressions
known improvements
```

---

# FINAL GATE

Il rewrite è considerato completato soltanto se tutte le condizioni funzionali, architetturali, di safety, performance e **integration completeness** sono dimostrate. Build/test verdi da soli non sono sufficienti.

Il rewrite è considerato completato soltanto se:

## Functional

```text
Overworld
Nether
End
entities
particles/weather
water/glass
LabPBR
LOD DH
LOD Voxy bridge
ReSTIR
SHaRC
SVGF
DLSS RR
FSR
XeSS
HDR/SDR
```

sono preservati dove la reference li supportava.

## Architecture

- Vulkan resta backend;
- Iris non è dependency;
- DH/Voxy sono adapters;
- `RtComposite` non è più il proprietario universale;
- GPU lifetime centralizzato;
- AS lifetime centralizzato;
- temporal reset centralizzato;
- pipeline esplicita;
- Render Graph deriva dalla pipeline verificata.

## Safety

- nessun nuovo `waitIdle` hot path;
- nessun stale geometry cross-dimension;
- nessuna UAF GPU nota;
- nessun device-lost riproducibile;
- nessun LOD hole persistente;
- nessuna coarse/fine duplicated geometry pubblicata.

## Integration completeness

Prima del FINAL PASS eseguire un audit repository-wide:

- nessun componente `src/main` introdotto dal rewrite e richiesto dall'architettura resta morto/non referenziato;
- nessuna ownership migrata rimane duplicata nel legacy;
- nessuna API minima obbligatoria è mancante;
- i dev gate rimasti hanno ancora due implementazioni reali oppure vengono rimossi in AER-092;
- il percorso legacy rimosso in AER-091 non è ancora raggiungibile;
- `RtComposite` contiene soltanto orchestration/delega prevista dal target;
- GPU/AS/temporal/scene/pipeline/render-graph authority hanno un owner univoco;
- `FINAL_REPORT.md` descrive il codice realmente committato e validato.

Qualsiasi scaffold morto, doppia authority non autorizzata o percorso legacy ancora autorevole blocca il FINAL PASS.

## Performance

Il progetto deve definire una soglia prima del final gate.

Default proposto, se non viene definita diversamente:

```text
Average GPU frame time: non peggiore di +5%
P95:                   non peggiore di +7%
P99:                   non peggiore di +10%
VRAM steady state:     non peggiore di +10%
```

Una regressione oltre soglia richiede:

```text
fix
oppure
waiver documentato con beneficio misurato
```

---

# 21. Human gates minimi

L'obiettivo è che l'agente lavori autonomamente tra gate.

## Human Gate H0 — Baseline

Verificare una volta:

```text
reference parte
scene baseline corrette
benchmark registrato
```

## Human Gate H1 — Dopo GATE-2

Controllo visivo LOD:

```text
DH/Voxy
seam
streaming
teleport
```

## Human Gate H2 — Dopo GATE-5

Controllo:

```text
ReSTIR
SHaRC
SVGF
DLSS/FSR/XeSS
ghosting
```

## Human Gate H3 — Dopo GATE-8

Controllo Vulkan:

```text
validation layers
frame stability
HDR/SDR
P99
```

## Human Gate H4 — Final

Accettazione finale.

L'agente NON deve richiedere review umana per ogni task se test e acceptance sono soddisfatti.

---

# 22. Blocker protocol

Formato:

```markdown
## BLOCKER-XXX

Task:
AER-XXX

Observed:
...

Expected:
...

Evidence:
- log
- stack trace
- test
- minimal reproduction

Invariant at risk:
GPU-001

Changes reverted:
yes/no

Safe next task:
AER-YYY

Tasks blocked:
- AER-ZZZ
```

L'agente può continuare task indipendenti soltanto se il dependency graph lo consente.

---

# 23. Task completion report

Per ogni task salvare opzionalmente:

```text
docs/rewrite/tasks/AER-XXX-result.md
```

Template:

```markdown
# AER-XXX Result

## Summary
...

## Files changed
...

## Behavior changes
None.

## Invariants
...

## Validation
- V0: PASS
- V1: PASS
- V2: PASS
- V3: PASS / unavailable

## Performance
No intended change / measurements.

## Follow-ups
...
```

Non duplicare centinaia di righe di log: salvare solo evidenza necessaria.

---

# 24. Strategie che l'agente NON deve applicare

## Big-bang `engine2/`

Vietato costruire un secondo renderer completo in parallelo prima dell'integrazione.

## Generic abstraction first

Vietato creare interfacce con dieci metodi ipotetici quando esiste un solo consumer reale.

Prima:

```text
extract concrete ownership
```

poi:

```text
abstract common contract
```

## Shader rewrite durante Java refactor

Vietato.

## Nuovo LOD mesher nel critical path

Il fast path DH/Voxy esistente è la baseline.

Una futura public DH API / native mesher è un progetto separato.

## NRD revival durante reconstruction migration

Vietato.

Prima completare:

```text
SVGF abstraction
DLSS-RR abstraction
```

poi eventualmente aprire una milestone NRD separata.

## Render Graph prima di FramePipeline

Vietato.

Il Render Graph deve formalizzare una pipeline già esplicita e verificata.

---

# 25. Success criteria per l'autonomia dell'agente

Il piano sta funzionando correttamente se:

```text
ogni commit compila
ogni task riuscito termina con commit atomico e worktree clean
ogni task ha scope piccolo
le regressioni sono bisectabili
gli shader cambiano raramente durante ownership refactor
RtComposite diminuisce progressivamente
legacy e new path convivono soltanto per finestre brevi
nessun componente richiesto resta scaffold morto
nessun gate PASSa senza integration completeness
nessun task richiede "riscrivi tutto"
```

Segnale di allarme:

```text
un singolo task modifica > 8-10 file core non-test
```

In quel caso l'agente deve valutare una divisione del task.

Eccezioni possibili:

```text
rename meccanico
package move
generated binding update
```

ma devono essere dichiarate prima.

---

# 26. Ordine operativo raccomandato

Percorso principale:

```text
AER-000
001
002
003
004
[GATE-0]

010
011
012
013
014
015
016
[GATE-1]

020
021
022
023
024
025
026
027
028
[GATE-2]

030
031
032
033
034
035
[GATE-3]

040
041
042
043
044
045
046
[GATE-4]

050
051
052
053
054
055
056
057
058
[GATE-5]

060
061
062
063
064
[GATE-6]

070
071
072
073
[GATE-7]

080
081
082
083
084
[GATE-8]

090
091
092
093
[FINAL]
```

È consentita esecuzione parallela soltanto per branch senza file ownership sovrapposto e senza dipendenza semantica.

Default: **sequenziale**.

---

# 27. Priorità in caso di budget limitato

Se si vuole ottenere il massimo miglioramento architetturale senza completare tutto:

## Cut 1 — Molto utile

Completare:

```text
GATE-0
GATE-1
GATE-2
GATE-3
```

Risultato:

- stato temporale meno monolitico;
- LOD provider-agnostic;
- FramePipeline esplicita;
- refactoring molto più semplice da proseguire.

## Cut 2 — Core robusto

Aggiungere:

```text
GATE-4
GATE-5
```

Risultato:

- ownership GPU/AS più sicura;
- reconstruction/upscalers modulari;
- ReSTIR/SHaRC isolati.

## Cut 3 — Engine target

Aggiungere:

```text
GATE-6
GATE-7
GATE-8
FINAL
```

---

# 28. Definizione di "autonomous success"

L'agente può essere considerato sufficientemente autonomo se completa almeno un'intera sequenza tra due human gate senza:

- chiedere decisioni architetturali già definite;
- introdurre waiver non autorizzati;
- disabilitare feature per far passare i test;
- saltare validation;
- modificare algoritmi durante extraction;
- lasciare working tree sporco;
- accumulare commit WIP.

La qualità del progetto viene misurata non dal numero di righe riscritte ma da:

```text
feature parity
+
bounded regression surface
+
clear ownership
+
testability
+
bisectability
+
Vulkan correctness
```

---

# 29. Prompt operativo iniziale per l'agente

Usare un prompt breve; il dettaglio deve stare nei file repository.

```text
Execute the Caustica rewrite according to AGENTS.md,
docs/rewrite/ROADMAP.md, and docs/rewrite/MIGRATION_STATE.yaml.

Work only on the next READY task whose dependencies are DONE.
Treat docs/rewrite/ROADMAP.md as the canonical scope/acceptance source.
Read that task's requirements and the relevant invariants before editing.

Preserve reference behavior unless the task explicitly authorizes a behavior change.
Do not perform unrelated cleanup or algorithmic changes.

Run the required validation levels.
If acceptance cannot be demonstrated, revert or isolate the task changes,
record a blocker in docs/rewrite/BLOCKERS.md, and do not mark the task DONE.

After a successful task, prove its integration/ownership acceptance, update its result
and MIGRATION_STATE.yaml, create one atomic commit using the required commit format,
verify the worktree is clean, then continue with the next READY task.
Do not pass a gate until its integration-completeness audit succeeds.
```

---

## 29.1 — Gate closure report obbligatorio

Ogni gate deve produrre o aggiornare un report in `docs/rewrite/` che contenga almeno:

```text
task/commit inclusi
acceptance per-task
integration completeness evidence
V0/V1/V2/V3/V4/V5 applicabili
baseline-equivalent failures esatte
forbidden-change audit
runtime-unavailable limitations
known baseline issues non corretti
```

Il report deve distinguere chiaramente `PASS`, `NOT AVAILABLE`, `BASELINE-EQUIVALENT` e `NOT TESTED`. `NOT AVAILABLE` non può essere trasformato in `PASS`.

# 30. Principio finale

La roadmap architetturale descrive **dove deve arrivare Caustica**.

Questa Agent Execution Roadmap descrive **come un agente può arrivarci senza dover comprendere e riscrivere l'intero renderer in una singola operazione**.

Il principio operativo è:

```text
MAKE CURRENT BEHAVIOR EXPLICIT
          ↓
MOVE OWNERSHIP
          ↓
VERIFY
          ↓
INTRODUCE THE SMALLEST USEFUL ABSTRACTION
          ↓
VERIFY
          ↓
DELETE LEGACY
```

Non:

```text
DESIGN PERFECT ENGINE
          ↓
REWRITE EVERYTHING
          ↓
DEBUG EVERYTHING AT ONCE
```
