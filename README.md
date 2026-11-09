# JobBot — AI-Powered Job Application Assistant (100% FREE)

A personal-use Spring Boot application that:
- Fetches jobs from Greenhouse, Lever, Ashby, Workable, RemoteOK (all free public APIs)
- Scores each job against your resume using FREE AI (Groq or Ollama)
- Drafts tailored cover letters for high-match jobs
- Never auto-submits — all submission is a manual human step

**COST: $0.00** — uses Groq free tier (no credit card) or local Ollama.

---

## Quick Start

### Step 1 — Install prerequisites
- Java 21: https://adoptium.net/
- Docker Desktop: https://www.docker.com/products/docker-desktop/
- Maven 3.9+: https://maven.apache.org/

### Step 2 — Get FREE Groq API key
1. Go to https://console.groq.com/
2. Sign up for FREE (no credit card needed)
3. Click "API Keys" -> "Create API Key"
4. Copy the key (starts with gsk_...)

### Step 3 — Configure .env
```
cp .env.example .env
```
Edit .env and set:
```
AI_PROVIDER=groq
GROQ_API_KEY=gsk_your_real_key_here
DB_PASSWORD=any_password_you_choose
```

### Step 4 — Add your resume
Paste your plain-text resume into:
```
src/main/resources/my-resume.txt
```
Or upload a PDF via the dashboard after starting the app.

### Step 5 — Add target companies
Edit src/main/resources/target-companies.txt:
```
greenhouse:stripe
lever:shopify
ashby:vercel
remoteok
```

### Step 6 — Run
```
docker compose up -d
.\install.ps1
```
Open: http://localhost:8080

---

## AI Provider Options (All FREE)

### Option 1: Groq (Recommended)
- Free cloud AI, no credit card
- 30 requests/min, 14,400/day
- Models: llama-3.3-70b-versatile (best), llama-3.1-8b-instant (fastest)
- Sign up: https://console.groq.com/

.env settings:
```
AI_PROVIDER=groq
GROQ_API_KEY=gsk_xxxxxxxxxxxx
```

### Option 2: Ollama (Local, 100% offline)
- Runs entirely on your PC
- No internet needed after setup
- Requires 8GB+ RAM
- Install: https://ollama.com/
- Then run: ollama pull llama3.2

.env settings:
```
AI_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2
```

---

## REST API

| Method | Path | Description |
|---|---|---|
| POST | /api/jobs/scan-now | Trigger fetch + score + draft |
| GET | /api/jobs | All discovered jobs |
| GET | /api/jobs/matches?minScore=70 | High-match jobs |
| POST | /api/jobs/{id}/generate-application | Generate cover letter |
| GET | /api/applications | All applications |
| PATCH | /api/applications/{id}/status | Update status |
| POST | /api/resume/upload | Upload PDF or txt resume |
| GET | /api/resume | Resume status |
| GET | /api/info/ai | AI provider info |
| GET | /swagger-ui.html | Full API docs |

---

## Cost Breakdown

| Item | Cost |
|---|---|
| Groq AI API | FREE (free tier) |
| Ollama | FREE (local) |
| Docker / MySQL | FREE |
| Java / Maven | FREE |
| All job board APIs | FREE (public APIs) |
| **Total** | **$0.00** |

---

## Guardrails

1. No scraping LinkedIn, Indeed, Naukri, Monster — only official public APIs
2. Never auto-submits — APPLIED status set only by human action
3. Cover letters never fabricate experience — enforced in AI system prompt
4. API keys always from environment variables — never hardcoded
5. Rate limited: 1 request/second to job boards and AI API
6. Personal use only — not a mass application bot

---

## Stopping

```
# Stop app: Ctrl+C
docker compose down        # stop MySQL, keep data
docker compose down -v     # stop MySQL, delete all data
```
