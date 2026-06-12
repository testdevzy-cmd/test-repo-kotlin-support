# test-repo-kotlin-support

Minimal repo to validate Kotlin codebase-embeddings via PR-triggered indexing.

## Quick start

1. Raise a PR from `develop` → `main`
2. On PR open, first-time indexing runs against `main` (currently `MainSeed.kt` only)
3. After merge, merge indexing processes `EmbeddingsFixture.kt` — run checks then
4. Follow [KOTLIN_EMBEDDINGS_TEST.md](./KOTLIN_EMBEDDINGS_TEST.md) to verify `file_index`, `code_chunks`, and Pinecone

## Files

| File | Role |
|------|------|
| `src/main/kotlin/EmbeddingsFixture.kt` | Single Kotlin fixture covering all parser chunk types |
| `KOTLIN_EMBEDDINGS_TEST.md` | Expected chunk mappings + SQL/Pinecone verification queries |
