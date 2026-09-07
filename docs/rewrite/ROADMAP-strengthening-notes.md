# Strengthened Roadmap — Change Notes

Questa revisione mantiene tutti gli AER e tutti i gate del roadmap originale, ma rende esplicite acceptance che prima potevano essere interpretate in modo troppo permissivo.

## Modifiche globali

- `DONE` distinto da semplice scaffold.
- Source-of-truth canonica: `docs/rewrite/ROADMAP.md`.
- Source priority formalizzata.
- Chiusura transazionale task: validation → diff audit → result/state → commit → clean tree.
- Gate PASS richiede `integration completeness`, non solo V1/V2.
- Protezione contro alias semantici fra unità/generation domains.
- Baseline-equivalent definita come match esatto delle failure congelate.
- Gate closure report obbligatorio.

## Eccezioni esplicite

Sono marcati come intenzionalmente non-authoritative al momento della creazione:

- AER-021 `LodMeshSnapshot + LodMeshSource`: `CONTRACT_ONLY`;
- AER-041 Resource ownership audit: `DOC_ONLY`;
- AER-050 `ReconstructionBackend`: `CONTRACT_ONLY`;
- AER-053 `UpscalerBackend`: `CONTRACT_ONLY`;
- AER-060 `RtScene` minimale: `CONTRACT_ONLY`;
- AER-080 Graph in shadow mode: `SHADOW_ONLY`;
- AER-093 Final report: `DOC_ONLY`.

Questo evita che la regola globale costringa integrazioni premature contrarie alla sequenza originale.

## GATE-1

Rafforzati AER-011..016 dopo l'evidenza che classi/test potevano essere marcati DONE senza wiring runtime. In particolare:

- `FrameContext`: unità `deltaTimeSeconds`, source runtime shadow e scene-generation domain;
- `TemporalResetReason`: mapping reale dei reset legacy;
- `TemporalState`: wiring production reale;
- `RestirHistory`: owner reale, characterization semantica, `reset` non inventato;
- `SharcRadianceCache`: facade realmente adottata;
- `SvgfResources`: owner runtime reale.

## Gate specifici

- GATE-0: baseline/documenti/reference realmente autonomi dalla chat.
- GATE-2: provider LOD, selector, coverage/planner/session realmente nel runtime.
- GATE-3: FramePipeline/passes realmente eseguiti.
- GATE-4: GPU/AS ownership e lifetime authority reali.
- GATE-5: backend selection/fallback e feature modules realmente authoritative.
- GATE-6: scene assembly realmente sorgente TLAS.
- GATE-7: environment modules realmente authoritative senza duplicate parameter calculation.
- GATE-8: graph shadow → execution → barrier authority → queue scheduling verificati per stadio.
- FINAL: audit repository-wide per dead scaffold, legacy authority residua e duplicate ownership.

## Non modificato

- Ordine degli AER.
- Dependency graph sostanziale.
- Algoritmi/shader target.
- Feature scope.
- Soglie performance originali.
- Principio extract → verify → abstract → migrate.
