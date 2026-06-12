# Kotlin Embeddings E2E Test

Raise a PR that adds `src/main/kotlin/EmbeddingsFixture.kt` to trigger indexing.
This file exercises every `CHUNKABLE_NODE_TYPES` construct from the Kotlin parser.

## Fixture file

| Path | Purpose |
|------|---------|
| `src/main/kotlin/EmbeddingsFixture.kt` | One Kotlin file covering all chunk mappings |

## AST node → ChunkType mapping

| Kotlin construct | Tree-sitter node | Expected `chunk_type` | Expected `chunk_name` |
|------------------|------------------|----------------------|----------------------|
| `class SampleClass` | `class_declaration` | `class` | `SampleClass` |
| `fun memberFn` (inside class) | `function_declaration` | `method` | `memberFn` |
| `interface SampleInterface` | `class_declaration` | `interface` | `SampleInterface` |
| `enum class SampleMode` | `class_declaration` | `enum` | `SampleMode` |
| `data class SampleData` | `class_declaration` | `class` | `SampleData` |
| `fun display` (inside data class) | `function_declaration` | `method` | `display` |
| `sealed class SampleSealed` | `class_declaration` | `class` | `SampleSealed` |
| `object SampleObject` | `object_declaration` | `class` | `SampleObject` |
| `fun ping` (inside object) | `function_declaration` | `method` | `ping` |
| `class WithCompanion` | `class_declaration` | `class` | `WithCompanion` |
| `companion object` | `companion_object` | `class` | `Companion` |
| `fun companionFn` (inside companion) | `function_declaration` | `method` | `companionFn` |
| `class WithSecondaryCtor` | `class_declaration` | `class` | `WithSecondaryCtor` |
| `constructor(...)` (secondary) | `secondary_constructor` | `method` | `WithSecondaryCtor` |
| `fun topLevelFn` (file-level) | `function_declaration` | `function` | `topLevelFn` |

Nested sealed subclasses (`NodeA`, `NodeB`, `NodeC`) are too short (< 3 lines) and are **not** chunked — total is exactly **15** chunks.

## Expected parser output (15 chunks)

Ground truth from `KotlinParser.parse()` — use to compare against `code_chunks` rows:

| `chunk_type` | `chunk_name` | `start_line` | `end_line` | `parentName` |
|--------------|--------------|--------------|------------|--------------|
| `class` | `SampleClass` | 6 | 20 | — |
| `method` | `memberFn` | 15 | 19 | `SampleClass` |
| `interface` | `SampleInterface` | 22 | 26 | — |
| `enum` | `SampleMode` | 28 | 32 | — |
| `class` | `SampleData` | 34 | 40 | — |
| `method` | `display` | 35 | 39 | `SampleData` |
| `class` | `SampleSealed` | 42 | 46 | — |
| `class` | `SampleObject` | 48 | 55 | — |
| `method` | `ping` | 50 | 54 | `SampleObject` |
| `class` | `WithCompanion` | 57 | 67 | — |
| `class` | `Companion` | 60 | 66 | — |
| `method` | `companionFn` | 61 | 65 | `Companion` |
| `class` | `WithSecondaryCtor` | 69 | 77 | — |
| `method` | `WithSecondaryCtor` | 72 | 76 | `WithSecondaryCtor` |
| `function` | `topLevelFn` | 79 | 83 | — |

## Verify `file_index`

```sql
SELECT
  file_path,
  chunk_count,
  total_tokens,
  indexing_status,
  file_hash,
  git_blob_sha,
  last_indexed_at
FROM file_index
WHERE repo_name = '<owner>/test-repo-kotlin-support'
  AND file_path = 'src/main/kotlin/EmbeddingsFixture.kt';
```

**Expected**

| Column | Expected value |
|--------|----------------|
| `file_path` | `src/main/kotlin/EmbeddingsFixture.kt` |
| `chunk_count` | `15` |
| `indexing_status` | `completed` |
| `file_hash` | non-null |
| `git_blob_sha` | non-null |
| `total_tokens` | > 0 |

## Verify `code_chunks`

```sql
SELECT
  chunk_type,
  chunk_name,
  language,
  start_line,
  end_line,
  chunk_signature IS NOT NULL AS has_signature,
  token_count,
  LEFT(chunk_content, 80) AS content_preview
FROM code_chunks
WHERE repo_name = '<owner>/test-repo-kotlin-support'
  AND file_path = 'src/main/kotlin/EmbeddingsFixture.kt'
ORDER BY start_line;
```

**Column checks (every row)**

| Column | Expected |
|--------|----------|
| `language` | `kotlin` |
| `chunk_type` | one of: `class`, `interface`, `enum`, `method`, `function` |
| `chunk_name` | matches construct name from table above |
| `chunk_content` | non-empty Kotlin source |
| `chunk_signature` | non-null for class/interface/enum/method/function |
| `start_line` / `end_line` | `start_line` ≤ `end_line`, span ≥ 3 lines |
| `token_count` | > 0 |
| `metadata` | JSON with `embeddingModel`, `hasContext` |

**Required chunk_name values** (must all be present):

```
SampleClass, memberFn, SampleInterface, SampleMode, SampleData, display,
SampleSealed, SampleObject, ping, WithCompanion, Companion, companionFn,
WithSecondaryCtor, topLevelFn
```

```sql
-- Quick pass/fail: missing required chunk names
SELECT expected.name AS missing_chunk_name
FROM (
  VALUES
    ('SampleClass'), ('memberFn'), ('SampleInterface'), ('SampleMode'),
    ('SampleData'), ('display'), ('SampleSealed'), ('SampleObject'),
    ('ping'), ('WithCompanion'), ('Companion'), ('companionFn'),
    ('WithSecondaryCtor'), ('topLevelFn')
) AS expected(name)
LEFT JOIN code_chunks c
  ON c.chunk_name = expected.name
  AND c.repo_name = '<owner>/test-repo-kotlin-support'
  AND c.file_path = 'src/main/kotlin/EmbeddingsFixture.kt'
WHERE c.id IS NULL;
```

Result should be **0 rows**.

## Verify Pinecone

In Pinecone console, filter by metadata:

```
repo_name = "<owner>/test-repo-kotlin-support"
file_path = "src/main/kotlin/EmbeddingsFixture.kt"
language = "kotlin"
```

**Expected per vector**

| Metadata field | Expected |
|----------------|----------|
| `installation_id` | matches your GitHub App installation |
| `repo_name` | `<owner>/test-repo-kotlin-support` |
| `file_path` | `src/main/kotlin/EmbeddingsFixture.kt` |
| `language` | `kotlin` |
| `chunk_type` | matches `code_chunks.chunk_type` |
| `chunk_name` | matches `code_chunks.chunk_name` |
| `start_line` / `end_line` | matches `code_chunks` row |
| vector dimensions | `3072` |

Vector count in Pinecone should equal `file_index.chunk_count` for this file.

## Edge cases covered

- **Data class** → `class` (not a separate type)
- **Sealed class** → `class`
- **Singleton `object`** → `class`
- **Companion object** → `class` named `Companion`
- **Top-level vs member function** → `function` vs `method`
- **Secondary constructor** → `method` named after enclosing class
- **Imports** → `java.util.List` used in `memberFn`; `emptyList` from `kotlin.collections`
- **KDoc** → preceding block comment on `SampleClass`

## Debugging

If any check fails, share:

1. `file_index` row for `EmbeddingsFixture.kt`
2. All `code_chunks` rows for that file (full `chunk_type`, `chunk_name`, `start_line`, `end_line`)
3. Pinecone metadata for 2–3 sample vectors

That is enough to pinpoint parser vs indexer vs Pinecone issues without a full DB dump.
