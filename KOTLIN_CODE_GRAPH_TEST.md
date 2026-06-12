# Kotlin Code-Graph (Neo4j) E2E Test

Companion to [KOTLIN_EMBEDDINGS_TEST.md](./KOTLIN_EMBEDDINGS_TEST.md). This document validates the **Neo4j code graph** built by `KotlinExtractor`, not the SQL/Pinecone embeddings pipeline.

## Fixture file

| Path | Purpose |
|------|---------|
| `src/main/kotlin/GraphFixture.kt` | Single Kotlin file exercising every `EntityType` + `RelationshipType` the Kotlin extractor emits |

`EmbeddingsFixture.kt` is also indexed and contributes class/method/enum entities, but `GraphFixture.kt` is the file that exclusively covers the relationship edge cases (annotations, generics, INSTANTIATES, IMPLEMENTS, wildcard/alias imports).

## What this fixture covers

### EntityType coverage (Kotlin extractor)

The extractor populates these 6 `EntityType` values (out of 12 in `types/index.ts`):

| EntityType | Produced by | Example |
|------------|-------------|---------|
| `class` | `class`, `data class`, `sealed class`, `annotation class`, `object`, `companion object` | `Dog`, `MathUtil`, `Marker` |
| `interface` | `interface` | `Greeter`, `Sized` |
| `enum` | `enum class` | (in `EmbeddingsFixture.kt`: `SampleMode`) |
| `method` | member `fun`, primary/secondary `constructor` | `Dog.greet`, `Dog.<init>` |
| `function` | top-level `fun` | `topLevelMaker`, `fetchSomething` |
| `variable` | `val`/`var` properties, `const val`, `val`/`var` constructor params | `Dog.nickname`, `MathUtil.MAX_RETRIES` |

Not produced by this extractor (by design): `file` (implicit), `module` (only as IMPORTS target), `type` (only as USES_TYPE / DECORATES target reference), `const` (Kotlin constants are emitted as `variable` with `metadata.kind = 'constant'`), `namespace`, `package`.

### RelationshipType coverage

The extractor emits these 8 `RelationshipType` values (out of 11 in `types/index.ts`):

| RelType | Triggered by | Example in fixture |
|---------|--------------|--------------------|
| `IMPORTS` | `import_header` (named, wildcard `.*`, alias `as`) | `import java.util.UUID`, `import kotlin.math.*`, `import kotlin.collections.List as KList` |
| `CONTAINS` | file → class/interface/enum/function, class → method/property | `GraphFixture.kt` → `Dog`, `Dog` → `Dog.greet` |
| `EXTENDS` | `delegation_specifier` with `constructor_invocation` | `Dog : Animal("canine")` |
| `IMPLEMENTS` | bare `delegation_specifier` interface | `Dog : Animal(), Greeter, Sized` |
| `CALLS` | `call_expression` (non-builtin callee) | `GraphFactory.makeDog` calls `UUID.randomUUID()` |
| `INSTANTIATES` | `call_expression` with capitalized callee, no receiver | `topLevelMaker` instantiates `Dog`, `GraphFactory` |
| `DECORATES` | annotation on class / method / property | `@Marker` on `Animal`, `@Tagged("dog")` on `Dog` |
| `USES_TYPE` | generic type-parameter bound | `class Box<T : Marker>` |

Not produced (by design): `EXPORTS` (TypeScript-only), `DEPENDS_ON` (not emitted by any current extractor).

## Expected entity counts (per fixture)

`GraphFixture.kt` alone should contribute the following entities. Counts are exact and stable; if they drift, the extractor or the fixture changed.

| EntityType | Count | Names |
|------------|------:|-------|
| `class` | 9 | `Marker`, `Tagged`, `Animal`, `Dog`, `Box`, `GraphFactory`, `MathUtil` (= 7 class-decls) + `Marker`/`Tagged` are `annotation class` → still `class` |
| `interface` | 2 | `Greeter`, `Sized` |
| `method` | 13 | `Tagged.<init>`, `Animal.<init>`, `Animal.describe`, `Dog.<init>`, `Dog.describe`, `Dog.greet`, `Box.<init>`, `Box.unbox`, `GraphFactory.makeDog`, `GraphFactory.newCounter`, `MathUtil.joinNumbers`, `MathUtil.padWidth` (+ secondary if any) |
| `function` | 2 | `fetchSomething`, `topLevelMaker` |
| `variable` | ~8 | `Tagged.value`, `Animal.species`, `Dog.nickname`, `Dog.size`, `Box.value`, `MathUtil.MAX_RETRIES`, etc. |

Note: exact counts may vary by ±1 due to fwcd grammar quirks around `annotation class`; use the **type-coverage** test below (every EntityType present at least once) as the authoritative gate.

## Local verification (no DB required)

A self-contained Node script extracts both fixtures and prints counts:

```bash
# From the neatcode repo root
node neatcode-backend/scripts/verify-kotlin-graph.mjs
```

Expected tail of output:

```
=== EntityType counts ===
   class        17
   enum         1
   function     3
   interface    3
   method       24
   variable     47

=== RelationshipType counts ===
   CALLS          11
   CONTAINS       84
   DECORATES       2
   EXTENDS         3
   IMPLEMENTS      2
   IMPORTS         6
   INSTANTIATES    4
   USES_TYPE       1

ALL EXPECTED ENTITY + RELATIONSHIP TYPES PRESENT.
```

Exit status `0` indicates every expected EntityType + RelationshipType was emitted at least once. Non-zero means coverage regressed.

## Neo4j Cypher verification (after PR-triggered indexing)

After the PR is opened or merged and `GraphIndexer` finishes, run the following Cypher in the Neo4j browser. Replace `<INSTALLATION_ID>` and `<owner>` with your real values.

### 1. File node exists

```cypher
MATCH (f:File {
  installationId: '<INSTALLATION_ID>',
  repoName: '<owner>/test-repo-kotlin-support',
  filePath: 'src/main/kotlin/GraphFixture.kt'
})
RETURN f.filePath, f.language
LIMIT 1;
```

Expected: 1 row, `language = 'kotlin'`.

### 2. Every EntityType produced for this file

```cypher
MATCH (n)
WHERE n.installationId = '<INSTALLATION_ID>'
  AND n.repoName = '<owner>/test-repo-kotlin-support'
  AND n.filePath = 'src/main/kotlin/GraphFixture.kt'
RETURN labels(n)[0] AS label, n.entityType AS entityType, count(*) AS n
ORDER BY label;
```

Expected labels (any subset of): `Class`, `Interface`, `Method`, `Function`, `Variable`. (`Enum` only appears from `EmbeddingsFixture.kt`.)

### 3. Every RelationshipType produced by Kotlin files

```cypher
MATCH (a)-[r]->(b)
WHERE a.installationId = '<INSTALLATION_ID>'
  AND a.repoName = '<owner>/test-repo-kotlin-support'
  AND (a.filePath ENDS WITH '.kt' OR b.filePath ENDS WITH '.kt')
RETURN type(r) AS relType, count(*) AS n
ORDER BY relType;
```

Expected to include: `IMPORTS`, `CONTAINS`, `EXTENDS`, `IMPLEMENTS`, `CALLS`, `INSTANTIATES`, `DECORATES`, `USES_TYPE`.

### 4. Spot-checks per relationship type

```cypher
// IMPORTS — named, wildcard, aliased
MATCH (f:File {filePath: 'src/main/kotlin/GraphFixture.kt'})-[r:IMPORTS]->(m:Module)
RETURN m.name, r.importType, r.importedNames
ORDER BY m.name;
```

Expected to include rows for `java.util.UUID`, `java.util.concurrent.atomic.AtomicLong`, wildcard `kotlin.math.*` (importType=`namespace`), and aliased `kotlin.collections.List` (importedNames contains `KList`).

```cypher
// EXTENDS / IMPLEMENTS — Dog extends Animal, implements Greeter + Sized
MATCH (c:Class {name: 'Dog'})-[r]->(b)
WHERE type(r) IN ['EXTENDS', 'IMPLEMENTS']
RETURN type(r) AS rel, labels(b)[0] AS label, b.name
ORDER BY rel, b.name;
```

Expected 3 rows: `EXTENDS Animal`, `IMPLEMENTS Greeter`, `IMPLEMENTS Sized`.

```cypher
// CALLS — GraphFactory.makeDog calls UUID.randomUUID, Dog ctor, etc.
MATCH (caller)-[r:CALLS]->(callee)
WHERE caller.filePath = 'src/main/kotlin/GraphFixture.kt'
RETURN caller.name, callee.name, r.argumentCount, r.callLine
ORDER BY r.callLine;
```

Expected to include a call to `UUID.randomUUID` and `AtomicLong` with non-null `argumentCount` and `callLine`.

```cypher
// INSTANTIATES — topLevelMaker → Dog, GraphFactory; makeDog → Dog
MATCH (caller)-[r:INSTANTIATES]->(target:Class)
WHERE caller.filePath = 'src/main/kotlin/GraphFixture.kt'
RETURN caller.name, target.name
ORDER BY caller.name, target.name;
```

Expected at least: `topLevelMaker → Dog`, `topLevelMaker → GraphFactory`, `GraphFactory.makeDog → Dog`.

```cypher
// DECORATES — @Marker on Animal, @Tagged on Dog
MATCH (ann)-[:DECORATES]->(target)
WHERE target.filePath = 'src/main/kotlin/GraphFixture.kt'
RETURN ann.name, target.name
ORDER BY target.name;
```

Expected: `Marker → Animal`, `Tagged → Dog`.

```cypher
// USES_TYPE — Box<T : Marker>
MATCH (src)-[:USES_TYPE]->(t)
WHERE src.filePath = 'src/main/kotlin/GraphFixture.kt'
RETURN src.name, t.name;
```

Expected: at least `Box → Marker`.

### 5. Signature-mismatch metadata (Kotlin-specific edge cases)

```cypher
// vararg + default-value tracking via parameter counts
MATCH (m:Method)
WHERE m.filePath = 'src/main/kotlin/GraphFixture.kt'
  AND m.name IN ['MathUtil.joinNumbers', 'MathUtil.padWidth', 'Tagged.<init>']
RETURN m.name,
       m.parameterCount,
       m.requiredParameterCount,
       m.hasRestParameter
ORDER BY m.name;
```

Expected:

| name | parameterCount | requiredParameterCount | hasRestParameter |
|------|---------------:|----------------------:|:----------------:|
| `MathUtil.joinNumbers` | 2 | 1 | true |
| `MathUtil.padWidth` | 1 | 0 | false |
| `Tagged.<init>` | 1 | 1 | false |

### 6. Constant marker on `const val`

```cypher
MATCH (v:Variable {name: 'MathUtil.MAX_RETRIES'})
RETURN v.name, v.metadata;
```

Expected `v.metadata` JSON contains `"kind":"constant"` and `"isConst":true`.

## Edge cases this fixture proves the extractor handles

- Wildcard import (`import kotlin.math.*`) → `IMPORTS` with `importType='namespace'`
- Aliased import (`as KList`) → `importedNames` contains the alias, `metadata.aliasOf` retained
- `annotation class` declaration (Kotlin-specific) → emitted as `class` with `metadata.kind = 'annotation class'`
- `internal` visibility modifier → `visibility = 'internal'`, `isExported = false`
- `suspend` top-level function → `isAsync = true`, `metadata.isSuspend = true`
- `vararg` parameter → `hasRestParameter = true`, NOT counted in `requiredParameterCount`
- Default-valued parameter → counted in `parameterCount` but NOT in `requiredParameterCount`
- `const val` UPPER_CASE inside `object` → `Variable` with `metadata.kind = 'constant'`
- Primary-constructor `val`/`var` params → emit BOTH a `method` (ctor) AND a `variable` (property)
- Capitalized callee without receiver (`Dog("rex")`, `GraphFactory()`) → `INSTANTIATES`
- Receiver-prefixed call (`UUID.randomUUID()`) → `CALLS` only, no INSTANTIATES
- Generic bound (`<T : Marker>`) → `USES_TYPE` to `Marker`
- Builtin-skip list (`println`, `emptyList`, etc.) → NOT emitted as CALLS (noise reduction)

## How to verify by opening a PR

1. **Branch + commit on the test repo** (already on `develop`):
   ```bash
   cd test-repos/test-repo-kotlin-support
   git checkout -b feat/code-graph-fixture
   git add src/main/kotlin/GraphFixture.kt KOTLIN_CODE_GRAPH_TEST.md
   git commit -m "test(code-graph): add Kotlin extractor verification fixture"
   git push -u origin feat/code-graph-fixture
   ```
2. **Open a PR** `feat/code-graph-fixture` → `develop` (or `main`, depending on the indexing trigger you want to exercise).
3. **Wait for the bot** to pick up the PR and run `GraphIndexer` for the head ref.
4. **Run the Cypher queries** from section "Neo4j Cypher verification" above against the production Neo4j instance for the same `installationId` / `repoName`.
5. **Compare** against the expected results. All 6 EntityTypes (excluding `enum`, which lives in `EmbeddingsFixture.kt`) and all 8 RelationshipTypes should appear for `GraphFixture.kt`.
6. **Local pre-flight** (no DB needed):
   ```bash
   node neatcode-backend/scripts/verify-kotlin-graph.mjs
   ```
   Exit code 0 confirms the extractor produces every expected type before the PR is even opened.

## Debugging

If a Cypher query returns 0 rows where rows are expected:

1. Confirm `GraphIndexer` ran for the PR head — check logs for `Kotlin extraction complete` lines.
2. Re-run the local script (`node neatcode-backend/scripts/verify-kotlin-graph.mjs`) — if it passes locally but Neo4j is empty, the persistence layer (`graph-repository.ts`) is the issue, not the extractor.
3. Inspect `entityType` / `language` filters — Neo4j filters are scoped per `installationId` + `repoName`.
4. For any failing single rel type, narrow the Cypher to `WHERE r.type = '<TYPE>'` and remove `filePath` filter to see if the rel exists under a different file (path mismatch is a common cause).
