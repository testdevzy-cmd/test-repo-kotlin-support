# test-repo-kotlin-support

Minimal repo to validate Kotlin codebase-embeddings via PR-triggered indexing.

## Quick start

1. Raise a PR adding `src/main/kotlin/EmbeddingsFixture.kt`
2. Wait for indexing to complete
3. Follow [KOTLIN_EMBEDDINGS_TEST.md](./KOTLIN_EMBEDDINGS_TEST.md) to verify `file_index`, `code_chunks`, and Pinecone

## Files

| File | Role |
|------|------|
| `src/main/kotlin/EmbeddingsFixture.kt` | Single Kotlin fixture covering all parser chunk types |
| `KOTLIN_EMBEDDINGS_TEST.md` | Expected chunk mappings + SQL/Pinecone verification queries |
