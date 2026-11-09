# 🤖 JobBot — AI-Powered Job Application Assistant

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?style=flat&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=flat&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![AI](https://img.shields.io/badge/AI-Groq%20%7C%20Ollama-FF6B6B?style=flat)
![Cost](https://img.shields.io/badge/Cost-$0.00-brightgreen?style=flat)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat)

A personal-use Spring Boot application that automates job discovery and application preparation — **completely free**. Fetches jobs from public APIs, scores each one against your resume using AI, and drafts tailored cover letters for high-match jobs. Never auto-submits — all submissions are a manual human step.

---

## ✨ Features

- **Multi-Source Job Fetching** — Greenhouse, Lever, Ashby, Workable, RemoteOK (all free public APIs, no scraping)
- **AI Resume Matching** — scores each job against your resume using Groq (free cloud) or Ollama (free local)
- **Cover Letter Generation** — drafts tailored cover letters for jobs above your match threshold (default: 70%)
- **PDF Resume Support** — upload PDF or plain text resume via dashboard
- **Daily Auto-Scan** — scheduled job scan every morning at 9am (configurable)
- **CSV Export** — export all applications to CSV
- **Email Notifications** — optional Gmail alerts when high-match jobs are found
- **Swagger UI** — full interactive API docs at `/swagger-ui.html`
- **$0 Cost** — uses Groq free tier (no credit card) or Ollama locally

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | MySQL 8 (via Docker) |
| ORM | Spring Data JPA / Hibernate |
| AI | Groq API (free) or Ollama (local) |
| PDF Parsing | Apache PDFBox |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven 3.9 |

---

## 🚀 Getting Started

### Prerequisites

- Java 21 → [Download](https://adoptium.net/)
- Docker Desktop → [Download](https://www.docker.com/products/docker-desktop/)
- Maven 3.9+ → [Download](https://maven.apache.org/)

### Step 1 — Get a FREE Groq API Key

1. Go to [https://console.groq.com/](https://console.groq.com/)
2. Sign up (no credit card needed)
3. Create an API Key (starts with `gsk_...`)

### Step 2 — Configure `.env`

```bash
cp .env.example .env
```

Edit `.env`:
```env
AI_PROVIDER=groq
GROQ_API_KEY=gsk_your_key_here
DB_PASSWORD=any_password_you_choose
```

### Step 3 — Add Your Resume

Paste plain-text resume into:
```
src/main/resources/my-resume.txt
```
Or upload PDF via the dashboard after starting the app.

### Step 4 — Add Target Companies

Edit `src/main/resources/target-companies.txt`:
```
greenhouse:stripe
lever:shopify
ashby:vercel
remoteok
```

### Step 5 — Run

```bash
docker compose up -d
.\install.ps1
```

Open → `http://localhost:8080`
Swagger UI → `http://localhost:8080/swagger-ui.html`

---

## 🤖 AI Provider Options (Both Free)

### Option A: Groq (Recommended — free cloud)
```env
AI_PROVIDER=groq
GROQ_API_KEY=gsk_xxxxxxxxxxxx
```
- 30 req/min, 14,400 req/day free
- Models: `llama-3.3-70b-versatile` (best), `llama-3.1-8b-instant` (fastest)

### Option B: Ollama (100% local, no internet needed)
```env
AI_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2
```
- Install [Ollama](https://ollama.com/) then run: `ollama pull llama3.2`
- Requires 8GB+ RAM

---

## 📡 API Reference

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/jobs/scan-now` | Trigger fetch + score + draft |
| GET | `/api/jobs` | All discovered jobs |
| GET | `/api/jobs/matches?minScore=70` | High-match jobs |
| POST | `/api/jobs/{id}/generate-application` | Generate cover letter |
| GET | `/api/applications` | All applications |
| PATCH | `/api/applications/{id}/status` | Update application status |
| GET | `/api/applications/export/csv` | Export all applications as CSV |
| POST | `/api/resume/upload` | Upload PDF or txt resume |
| GET | `/api/resume` | Resume status |
| GET | `/api/info/ai` | Current AI provider info |
| GET | `/swagger-ui.html` | Full interactive API docs |

---

## 📁 Project Structure

```
jobbot/
├── src/main/java/com/vinay/jobbot/
│   ├── JobBotApplication.java
│   ├── config/           # RestClient, Scheduling config
│   ├── controller/       # REST controllers (Jobs, Applications, Resume, AI Info)
│   ├── dto/              # DTOs for Greenhouse, Lever, Ashby, Workable, RemoteOK
│   ├── entity/           # JPA entities (JobListing, Application, ApplicationStatus)
│   ├── repository/       # Spring Data JPA repositories
│   ├── scheduler/        # Daily 9am job scan scheduler
│   └── service/
│       ├── AiClientService.java          # Groq + Ollama integration
│       ├── JobFetchService.java          # Greenhouse fetcher + orchestrator
│       ├── LeverFetchService.java
│       ├── AshbyFetchService.java
│       ├── WorkableFetchService.java
│       ├── RemoteOKFetchService.java
│       ├── MatchScoringService.java      # AI resume scoring
│       ├── CoverLetterService.java       # AI cover letter generation
│       ├── ApplicationWorkflowService.java
│       ├── ResumeStorageService.java     # PDF + txt resume handling
│       └── EmailNotificationService.java
├── src/main/resources/
│   ├── application.yml
│   ├── my-resume.txt          # Your resume (plain text)
│   ├── target-companies.txt   # Job boards to scan
│   └── static/index.html      # Dashboard UI
├── docker-compose.yml         # MySQL + app
├── install.ps1                # Windows setup script
├── .env.example
└── pom.xml
```

---

## 🛡️ Guardrails

1. **No scraping** — only official public APIs (Greenhouse, Lever, Ashby, Workable, RemoteOK)
2. **Never auto-submits** — `APPLIED` status only set by human action
3. **No fabrication** — AI system prompts explicitly prohibit inventing experience
4. **Secrets via env** — API keys always from environment variables, never hardcoded
5. **Rate limited** — 1 request/second to job boards and AI API
6. **Personal use only** — not a mass application bot

---

## 💰 Cost Breakdown

| Item | Cost |
|---|---|
| Groq AI API | FREE (free tier) |
| Ollama | FREE (local) |
| Docker / MySQL | FREE |
| Java / Maven | FREE |
| All job board APIs | FREE (public APIs) |
| **Total** | **$0.00** |

---

## 🛑 Stopping

```bash
# Stop app: Ctrl+C
docker compose down        # stop MySQL, keep data
docker compose down -v     # stop MySQL, delete all data
```

---

## 📄 License

MIT © [Vinay](https://github.com/vinayviratwork)
