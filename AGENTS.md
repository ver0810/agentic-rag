# AgenticRAG — Agent Guide

## Repo structure (quick look)

```
agenticrag/                  multi-module Maven (Java 17, Spring Boot 3.4)
├── bootstrap/               Spring Boot app, REST APIs, RAG orchestration
├── infra-ai/                AI abstraction: chat, embedding, model routing
├── framework/               Redis, RocketMQ, rate-limit, cache adapters
├── frontend/                React 19 + Vite + TypeScript
├── doc-parser-service/      Python FastAPI deep-learning doc parser
├── resources/databases/     scheme.sql + init.sql (pgvector)
├── scripts/                 Eval scripts (PowerShell + Python)
├── uploads/                 Local file storage (gitignored)
└── artifacts/               Eval report output (gitignored)
```

## Repo structure (detailed)

```
agenticrag/                           multi-module Maven (Java 17, Spring Boot 3.4)
├── pom.xml                           root POM: modules = [bootstrap, infra-ai, framework]
├── .env                              gitignored; auto-loaded by DotenvEnvironmentPostProcessor
├── .mvn-local-settings.xml           redirects local Maven repo to .m2repo/
├── mvnw.cmd                          Maven wrapper (use this, not system mvn)
│
├── bootstrap/                        RUNNABLE MODULE (@SpringBootApplication, :8080)
│   ├── pom.xml                       depends on infra-ai, framework
│   └── src/
│       ├── main/java/com/agenticrag/
│       │   ├── AgenticragApplication.java         entrypoint
│       │   ├── config/
│       │   │   ├── AsyncConfiguration.java
│       │   │   └── DotenvEnvironmentPostProcessor.java   loads .env from project root
│       │   ├── common/                ApiException, GlobalExceptionHandler
│       │   ├── user/
│       │   │   ├── controller/        POST /user/login, /register, /refresh, /logout, /password/update, GET /user/info, /list, DELETE /user/{id}
│       │   │   ├── service/           user + auth service
│       │   │   ├── auth/              JWT utilities
│       │   │   ├── ai/                user-level AI provider config (POST /user/ai-settings/save, /switch, /verify)
│       │   │   ├── dao/               entity + mapper (MyBatis-Plus)
│       │   │   └── dto/
│       │   ├── chat/
│       │   │   ├── controller/        GET /chat/sessions, /messages, PUT /session/{id}/title, POST /chat, /chat/stream
│       │   │   ├── service/           ChatService → RagFacade if kbId present, else → AiChatService
│       │   │   └── dto/
│       │   ├── knowledge/
│       │   │   ├── controller/        POST/GET/DELETE /api/knowledge-base, POST upload documents, POST /process
│       │   │   ├── service/           document ingestion, chunking, embedding pipeline
│       │   │   ├── dao/               entity + mapper
│       │   │   ├── dto/
│       │   │   └── config/
│       │   ├── rag/
│       │   │   ├── api/               RagFacade, RagQueryService (orchestrates rewrite → retrieve → rerank → generate)
│       │   │   ├── query/             rewrite, merge, context assembly
│       │   │   ├── parser/            DocumentParserFactory, Markdown/Html/Python parsers
│       │   │   ├── eval/              rule-based eval + RAGAS evaluation
│       │   │   └── config/            RAG configuration
│       │   ├── ragtrace/
│       │   │   ├── controller/        GET /api/rag/traces, /traces/{traceId}
│       │   │   ├── service/           trace recording for rewrite → retrieve → rerank → generate
│       │   │   ├── dao/               entity + mapper
│       │   │   └── dto/
│       │   ├── observability/
│       │   │   ├── controller/        GET /api/rag/observability/metrics, /alerts, /summary
│       │   │   └── service/           metrics, alerts, cost estimation
│       │   ├── feedback/              FeedbackController, FeedbackService
│       │   └── infrastructure/
│       │       ├── embedding/controller/  POST /embedding
│       │       ├── vector/            pgvector client
│       │       ├── reranker/          DashScopeRerankerAdapter
│       │       ├── storage/           local filesystem adapter
│       │       ├── cache/             Redis cache adapter
│       │       ├── mq/                RocketMQ consumer/listener
│       │       ├── memory/            chat memory persistence
│       │       └── ratelimit/         rate limiter
│       ├── main/resources/
│       │   ├── application.yml        all config (datasource, AI providers, RAG params, MQ, observability)
│       │   ├── META-INF/spring.factories  registers DotenvEnvironmentPostProcessor
│       │   └── rag-eval/sample-template.json  default eval dataset
│       └── test/java/com/agenticrag/
│           ├── AgenticragApplicationTests           smoke test (@SpringBootTest)
│           ├── rag/parser/DocumentParserStructureTests   parser + chunking unit tests
│           └── infrastructure/reranker/DashScopeRerankerAdapterTests  URL normalization
│
├── infra-ai/                         SHARED LIBRARY (no main class)
│   ├── pom.xml                       spring-ai-starter-model-openai, pdfbox, poi, commonmark, onnx
│   └── src/main/java/com/agenticrag/infra/ai/
│       ├── api/
│       │   ├── chat/                 AiChatFacade, DefaultAiChatFacade, ChatRequest/Response
│       │   └── embedding/            AiEmbeddingFacade, DefaultAiEmbeddingFacade, EmbeddingRequest/Response
│       ├── config/                   AiInfraAutoConfiguration, AiChatProperties, EmbeddingProperties, AiProviderProperties, RagProperties, AiObservabilityProperties, AiChatMemoryConfig
│       ├── service/                  AiChatService, AiEmbeddingService, AiProviderRouter, OpenAiModelFactory, OpenAiCompatibleModelFactory
│       ├── port/
│       │   ├── embedding/            KnowledgeEmbeddingPort
│       │   ├── memory/               ConversationMemoryPort
│       │   ├── reranker/             RerankerPort
│       │   ├── storage/              DocumentStoragePort
│       │   └── vector/               VectorIndexPort
│       ├── memory/                   DatabaseChatMemory
│       ├── model/                    AiChatScene, AiRuntimeContext, AiRuntimeOptions, AiEnhancement, OpenAiRuntimeOptions
│       └── observability/            TokenCostEstimator
│
├── framework/                        SHARED LIBRARY (no main class)
│   ├── pom.xml                       spring-boot-starter-data-redis, rocketmq-spring-boot-starter
│   └── src/main/java/com/agenticrag/framework/infrastructure/
│       ├── cache/                    CachePort, RedisCacheAdapter
│       ├── mq/                       EventPublisherPort, RocketMqEventPublisherAdapter, MqEvent
│       ├── ratelimit/                RateLimiterPort, RedisRateLimiterAdapter
│       └── config/                   RedisConfig, RocketMqConfig
│
├── frontend/                         React 19 + Vite + TypeScript + Tailwind CSS 4
│   ├── package.json                  scripts: dev, build (tsc -b && vite build), lint, preview
│   ├── vite.config.ts                proxies /ai, /chat, /user, /api → localhost:8080
│   ├── tsconfig.app.json / tsconfig.json / tsconfig.node.json
│   ├── eslint.config.js
│   ├── tailwind.config.js
│   └── src/
│       ├── main.tsx                  app entrypoint
│       ├── App.tsx                   router + layout
│       ├── api/                      chat.ts, knowledge.ts, eval.ts, trace.ts, observability.ts, feedback.ts
│       ├── components/
│       │   ├── chat/                 ChatComposer, ChatContent, ChatHeader, ChatSidebar, CitationPanel, types
│       │   ├── auth/                 AuthCard
│       │   ├── KnowledgeBaseView.tsx
│       │   ├── EvalView.tsx
│       │   ├── ObservabilityView.tsx
│       │   └── ...
│       ├── pages/                    Login.tsx, Register.tsx
│       └── utils/                    auth.ts (JWT token management)
│
├── doc-parser-service/               standalone Python FastAPI doc parser (port :8000)
│   ├── pyproject.toml                fastapi, pymupdf, onnxruntime, opencv, huggingface-hub
│   ├── download_models.py            downloads ONNX deepdoc models
│   ├── app/main.py                   FastAPI app, registers /api/parse + /api/health
│   ├── app/config.py                 Settings (model dir, upload dir, auto-download flags)
│   ├── app/api/
│   │   ├── parse.py                  POST /api/parse (pdf/docx/pptx/xlsx)
│   │   └── health.py                 GET /api/health
│   ├── app/core/
│   │   ├── parser_factory.py         routes file types to processors
│   │   ├── base_parser.py            abstract parser interface
│   │   └── task_manager.py           background task orchestration
│   ├── app/engines/
│   │   ├── deepdoc_engine.py         deep-learning layout analysis
│   │   ├── layout_engine.py          page layout detection
│   │   ├── ocr_engine.py             OCR via PaddleOCR
│   │   └── table_engine.py           table extraction
│   ├── app/processors/
│   │   ├── pdf_processor.py          PDF parsing (pymupdf + deepdoc)
│   │   ├── word_processor.py         .docx parsing
│   │   ├── excel_processor.py        .xlsx parsing
│   │   ├── ppt_processor.py          .pptx parsing
│   │   └── image_processor.py        image OCR
│   └── app/utils/
│       └── model_downloader.py       downloads ONNX models from HuggingFace
│
├── resources/databases/
│   ├── scheme.sql                    full schema: users, knowledge_base, document, vector_store, chat_session, trace, eval tables + pgvector extension
│   └── init.sql                      inserts default admin user (admin/admin)
│
├── scripts/
│   ├── run-rag-eval.ps1              PowerShell: POST /api/rag/evals/run, saves to artifacts/rag-eval/
│   ├── compare-rag-eval.ps1          PowerShell: GET /api/rag/evals/compare
│   ├── eval/
│   │   ├── run_rag_eval.py           Python: POST /api/eval/ragas/run → artifacts/ragas-eval/
│   │   ├── quick_eval.py             quick one-shot RAGAS eval
│   │   ├── compare_rag_eval.py        compare two eval runs
│   │   ├── convert_dureader.py       convert DuReader dataset → eval format
│   │   ├── build_real_eval_dataset.py   normalize real eval dataset doc names
│   │   ├── export_dureader_corpus.py   export DuReader corpus as txt files for KB ingestion
│   │   ├── samples_example.json      example eval dataset
│   │   └── README.md
│   └── pdf_pipeline/main.py          Python PDF pipeline fallback
│
├── uploads/                          gitignored; local file storage for uploaded documents
├── artifacts/                        gitignored; eval report output (rag-eval/, ragas-eval/)
└── docs/
    ├── chat.png & upload.png         screenshots
    ├── DEEPDOC_DESIGN.md
    └── DOCUMENT_PARSER_OPTIMIZATION_PLAN.md
```

## Developer commands

### Backend
```powershell
.\mvnw.cmd spring-boot:run -pl bootstrap          # start dev server on :8080
.\mvnw.cmd test                                    # all tests
.\mvnw.cmd test -pl bootstrap                      # bootstrap tests only
```
Uses Maven wrapper (`mvnw.cmd`). Without it, Maven 3.9+ required.

### Frontend
```powershell
cd frontend
npm install
npm run dev                  # Vite dev server on :5173
npm run build                # tsc -b && vite build (typecheck + bundle)
npm run lint                 # ESLint
```

### Python doc-parser service (Python 3.13+)
```powershell
cd doc-parser-service
pip install -e ".[dev]"
uvicorn app.main:app --reload   # FastAPI on :8000
```
Models auto-download on startup (or `python download_models.py`).

### RAG Evaluation
```powershell
.\scripts\run-rag-eval.ps1 -BaseUrl http://localhost:8080 -Dataset sample-template -KbId <id> -Username admin -Password admin
.\scripts\compare-rag-eval.ps1 -BaseUrl http://localhost:8080 -BaseRunId <a> -TargetRunId <b> -Username admin -Password admin
```
Or via Python: `python scripts/eval/run_rag_eval.py --kb-id <id> --samples <file> --username admin --password admin`

## Prerequisites

| Tool | Version |
|------|---------|
| JDK | 17 |
| Maven | 3.9+ (or use `mvnw.cmd`) |
| Node.js | 20+ |
| PostgreSQL | 14+ with `CREATE EXTENSION IF NOT EXISTS vector` |
| Redis | any |
| Python | 3.13+ (only for doc-parser-service) |

## Setup

1. Create PostgreSQL database and run `resources/databases/scheme.sql` then `resources/databases/init.sql`
2. Copy `.env` to project root (auto-loaded by `DotenvEnvironmentPostProcessor`):
   - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for PostgreSQL
   - `BASE_URL` + `API_KEY` for OpenAI-compatible chat
   - `EMBEDDING_*` for embedding provider (default: DashScope/Qwen)
   - `JWT_SECRET` for auth tokens
3. `.\mvnw.cmd spring-boot:run -pl bootstrap`

Default admin login: `admin` / `admin`.

## Key architecture

- **RAG orchestration is manual** (not Spring AI Advisor). The pipeline: rewrite → embed → vector+keyword search → merge → rerank → build context → generate → trace.
- **Spring AI is used only as model-calling layer** (`ChatClient`, `MessageChatMemoryAdvisor`, OpenAI-compatible).
- **Async ingestion** via RocketMQ (document upload → parse → chunk → embed → store vectors).
- **Document parsers**: Java-based for pdf/docx/md/txt (PDFBox, POI, CommonMark). Python-based fallback via doc-parser-service for deep-learning-enhanced parsing.
- **Maven local repo** redirected to `.m2repo/` (set in `.mvn-local-settings.xml` — not the default `~/.m2/`).
- **`bootstrap`** is the only runnable module (has `@SpringBootApplication`). It depends on `infra-ai` and `framework`.

## Tests

- 3 test classes, all in `bootstrap`
- `AgenticragApplicationTests` — context load smoke test
- `DocumentParserStructureTests` — parser + chunking (pure unit, no Spring context)
- `DashScopeRerankerAdapterTests` — URL normalization (pure unit)
- Run: `.\mvnw.cmd test -pl bootstrap`

## Testing quirks

- `$ { }` patterns in README are Vite interpolation placeholders, not env vars — do not edit them.
- `.env` is gitignored alongside `.m2repo/` and `artifacts/`.
- RocketMQ is required for ingestion; disable via `RAG_INGESTION_TOPIC` if testing without async ingestion.
- No Docker Compose, no Dockerfiles exist.
- Python models (ONNX) are large and not committed — auto-downloaded on first start.
- Eval output goes to `artifacts/rag-eval/` (PowerShell) or `artifacts/ragas-eval/` (Python).
