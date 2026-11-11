# 🤖 JobBot — AI-Powered Job Application Assistant

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?style=flat&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=flat&logo=mysql&logoColor=white)
![AI](https://img.shields.io/badge/AI-Groq%20%7C%20Llama%203.3-FF6B6B?style=flat)
![Cost](https://img.shields.io/badge/Cost-$0.00-brightgreen?style=flat)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat)

> **A personal-use Spring Boot app** that automates job discovery and application prep — completely free. Fetches jobs from 5 public APIs, scores each one against your resume using AI, and drafts tailored cover letters for high-match jobs. Never auto-submits.

---

## 🖥️ Project Preview

| Dashboard | Job Detail | Cover Letter |
|---|---|---|
| Jobs table with AI score badges | Per-job info with match reasoning | AI-drafted cover letter with edit + copy |

> **Features:** Resume upload → AI scoring → cover letter draft → CSV export · Daily 9am auto-scan

---

## ✨ Features

- **🔍 Multi-Source Job Fetching** — Greenhouse, Lever, Ashby, Workable, RemoteOK (all free public APIs, no scraping)
- **🧠 AI Resume Matching** — scores each job 0–100 against your resume using Groq (free cloud) or Ollama (free local)
- **✍️ Cover Letter Generation** — drafts tailored cover letters for jobs above match threshold (default: 70%)
- **📄 PDF Resume Support** — upload PDF or plain text resume via dashboard; uses Apache PDFBox
- **📅 Daily Auto-Scan** — scheduled job scan every morning at 9am
- **📊 CSV Export** — export all applications to CSV
- **📧 Email Notifications** — optional Gmail alerts when high-match jobs are found
- **📖 Swagger UI** — full interactive API docs at `/swagger-ui.html`
- **🛡️ Anti-Hallucination** — AI prompts explicitly forbid fabricating experience or skills
- **💰 $0 Cost** — Groq free tier (no credit card) or Ollama locally

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | MySQL 8 (Docker) / H2 in-memory (demo) |
| ORM | Spring Data JPA / Hibernate |
| AI | Groq API — Llama 3.3-70B (free) or Ollama (local) |
| PDF Parsing | Apache PDFBox 3 |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven 3.9 |
| UI | Vanilla JS single-page dashboard (served by Spring Boot) |

---

## 🚀 Run Locally

### Option A — With Docker (Full MySQL, recommended)

#### Prerequisites
- Java 21 · Docker Desktop · Maven 3.9

```bash
# 1. Clone
git clone https://github.com/vinayviratwork/JOB-BOT.git
cd JOB-BOT

# 2. Get FREE Groq key → https://console.groq.com/
# 3. Configure
cp .env.example .env
# Edit .env: set GROQ_API_KEY=gsk_...

# 4. Run (auto-installs everything)
.\install.ps1
```
Open → `http://localhost:8080`

---

### Option B — Without Docker (H2 in-memory, demo)

```bash
# No Docker needed — uses H2 in-memory database
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```
Open → `http://localhost:8080`

---

## 🤖 AI Provider Options (Both Free)

### Groq (Recommended — free cloud)
```env
AI_PROVIDER=groq
GROQ_API_KEY=gsk_xxxxxxxxxxxx
```
- 30 req/min, 14,400 req/day free
- Best model: `llama-3.3-70b-versatile`

### Ollama (100% local, offline)
```env
AI_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2
```

---

## 📡 API Reference

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/jobs/scan-now` | Trigger full fetch + score + draft pipeline |
| GET | `/api/jobs` | All discovered jobs |
| GET | `/api/jobs/matches?minScore=70` | High-match jobs |
| POST | `/api/jobs/{id}/generate-application` | Generate cover letter |
| POST | `/api/jobs/{id}/rescore` | Re-score against current resume |
| GET | `/api/applications` | All applications |
| GET | `/api/applications/{id}` | Single application |
| PATCH | `/api/applications/{id}/status` | Update status |
| PATCH | `/api/applications/{id}/cover-letter` | Edit cover letter |
| PATCH | `/api/applications/{id}/notes` | Edit notes |
| GET | `/api/applications/export.csv` | Export CSV |
| POST | `/api/resume/upload` | Upload PDF or txt |
| GET | `/api/resume` | Resume status |
| GET | `/api/info/ai` | AI provider info + email status |
| GET | `/swagger-ui.html` | Interactive API docs |

---

## 📁 Project Structure

```
JOB-BOT/
├── src/main/java/com/vinay/jobbot/
│   ├── JobBotApplication.java
│   ├── config/                           # RestClient, Scheduling
│   ├── controller/                       # 4 REST controllers
│   ├── dto/                              # DTOs for 5 job board APIs
│   ├── entity/                           # JobListing, Application, Status
│   ├── repository/                       # Spring Data JPA
│   ├── scheduler/                        # Daily 9am scan
│   └── service/
│       ├── AiClientService.java          # Groq + Ollama
│       ├── MatchScoringService.java      # AI resume scoring
│       ├── CoverLetterService.java       # AI cover letter generation
│       ├── ApplicationWorkflowService.java  # Pipeline orchestrator
│       ├── JobFetchService.java          # Greenhouse + orchestrator
│       ├── LeverFetchService.java
│       ├── AshbyFetchService.java
│       ├── WorkableFetchService.java
│       ├── RemoteOKFetchService.java
│       ├── ResumeStorageService.java     # PDF + txt handling
│       └── EmailNotificationService.java
├── src/main/resources/
│   ├── application.yml                   # Main config
│   ├── application-h2.yml                # H2 demo profile
│   ├── my-resume.txt                     # Resume placeholder
│   ├── target-companies.txt              # Job boards to scan
│   └── static/index.html                 # Dashboard UI
├── docker-compose.yml
├── install.ps1                           # Windows auto-setup
├── .env.example
└── pom.xml
```

---

## 🛡️ Design Guardrails

1. **No scraping** — only official public APIs
2. **Never auto-submits** — `APPLIED` status is manual-only
3. **No fabrication** — AI prompts explicitly forbid inventing experience
4. **Secrets via env** — API keys always from environment variables
5. **Rate limited** — 1 req/sec between job board calls
6. **Personal use only** — not a mass application bot

---

## 💰 Cost

| Item | Cost |
|---|---|
| Groq AI API | FREE (free tier) |
| Ollama | FREE (local) |
| All job board APIs | FREE (public APIs) |
| **Total** | **$0.00** |

---

## 📄 License

MIT © [Vinay](https://github.com/vinayviratwork)
