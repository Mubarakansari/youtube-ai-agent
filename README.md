# 🤖 YouTube AI Monitoring Agent (Spring Boot)

Automatically fetches the **Top 10 AI-related YouTube videos from India** in the last 48 hours, ranked by highest view count, and sends a beautiful HTML email digest every day at **6:00 AM IST**.

Built with **Java 17 + Spring Boot 3.2**

---

## 📁 Project Structure

```
youtube-ai-agent-java/
├── pom.xml                                      ← Maven dependencies
├── .env.example                                 ← Environment variable template
├── .gitignore
├── .github/workflows/
│   └── daily.yml                                ← GitHub Actions (6 AM IST)
└── src/main/
    ├── java/com/agent/
    │   ├── AgentApplication.java                ← Main entry point
    │   ├── model/
    │   │   └── VideoResult.java                 ← Data model
    │   └── service/
    │       ├── YouTubeService.java              ← API + filtering + ranking
    │       └── EmailService.java                ← HTML email sender
    └── resources/
        └── application.properties               ← Configuration
```

---

## ✅ Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.8+ |
| Git | any |
| IntelliJ IDEA / VS Code | any |
| Google Cloud account | for YouTube API |
| Gmail account | for sending email |

### Check installations:
```bash
java -version
mvn -version
```

---

## 🔑 Step 1 — Get YouTube API Key

1. Go to [console.cloud.google.com](https://console.cloud.google.com/)
2. Create a new project → name it `youtube-ai-agent`
3. Go to **APIs & Services → Library**
4. Search **"YouTube Data API v3"** → Enable it
5. Go to **APIs & Services → Credentials**
6. Click **+ Create Credentials → API Key**
7. Copy the key

---

## 🔑 Step 2 — Get Gmail App Password

> ⚠️ Your regular Gmail password will NOT work.

1. Go to [myaccount.google.com](https://myaccount.google.com/)
2. **Security → 2-Step Verification** (enable if not done)
3. **Security → App passwords**
4. Select app: **Mail** | device: **Windows Computer**
5. Click **Generate** → copy the 16-character password

---

## ⚙️ Step 3 — Configure `application.properties`

Open `src/main/resources/application.properties` and set your credentials as default values:

```properties
spring.main.web-application-type=none

youtube.api.key=${YOUTUBE_API_KEY:YOUR_API_KEY_HERE}

spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${GMAIL_USER:your_gmail@gmail.com}
spring.mail.password=${GMAIL_PASS:your app password here}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

recipient.email=${RECIPIENT_EMAIL:your_gmail@gmail.com}
```

> ⚠️ **Add `application.properties` to `.gitignore`** before pushing to GitHub — it contains real credentials!

---

## 📦 Step 4 — Build the Project

```bash
cd d:\Mubark\youtube-ai-agent-java
mvn clean package -DskipTests
```

This creates `target/youtube-ai-agent-1.0.0.jar`

---

## ▶️ Step 5 — Run Locally

### Option A — IntelliJ IDEA (Recommended)

1. Open the project in IntelliJ
2. Click **Edit Configurations** (top-right dropdown)
3. Select `AgentApplication`
4. Add **Environment Variables**:
   - `YOUTUBE_API_KEY` = your key
   - `GMAIL_USER` = your gmail
   - `GMAIL_PASS` = your app password
   - `RECIPIENT_EMAIL` = your gmail
5. Click ▶️ Run

### Option B — Command Line

```bash
# Set env vars first
set YOUTUBE_API_KEY=your_key_here
set GMAIL_USER=your_gmail@gmail.com
set GMAIL_PASS=your app password
set RECIPIENT_EMAIL=your_gmail@gmail.com

# Then run
java -jar target/youtube-ai-agent-1.0.0.jar
```

**Expected output:**
```
🚀 YouTube AI Monitoring Agent started...
🔑 API Key loaded: AIzaSyCg...
🕐 48-hour cutoff (UTC): 2026-04-18T00:30:00Z
📍 Region: India (IN) | Language: Hindi + English
🔍 Searching 12 queries × up to 3 pages...
  ✅ Query 'AI agent tutorial' page 1: 50 results
  ...
📦 Total raw IDs collected: 1100+
🎬 After duration filter (≥8min): ...
🧠 After relevance filter: ...
🏆 Top 10 videos selected.
📧 Email sent successfully!
```

---

## 🧠 How Filtering Works

| Filter | Logic |
|--------|-------|
| **Region** | `regionCode=IN` — India only |
| **Pagination** | 3 pages per query (up to 150 results/query) |
| **48h Window** | Manual date check on `publishedAt` field |
| **Duration** | ≥ 8 minutes (excludes YouTube Shorts) |
| **Semantic Score** | Must score ≥ 4 (AI term + dev-intent term) |
| **Top 10** | Sorted by `viewCount` descending |

---

## 🚀 Step 6 — Deploy to GitHub Actions

### 6a. Push to GitHub

```bash
git init
git add .
git commit -m "feat: Spring Boot YouTube AI agent"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/youtube-ai-agent-java.git
git push -u origin main
```

> **Important:** Make sure `application.properties` is in `.gitignore` so credentials aren't exposed.

### 6b. Add GitHub Secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Value |
|-------------|-------|
| `YOUTUBE_API_KEY` | your YouTube API key |
| `GMAIL_USER` | your Gmail address |
| `GMAIL_PASS` | your Gmail App Password |

### 6c. Test Manually

Repo → **Actions tab → "YouTube AI Daily Digest (Spring Boot)" → Run workflow**

---

## ⏰ Schedule

Runs automatically at **6:00 AM IST** (00:30 UTC) daily:

```yaml
cron: '30 0 * * *'
```

---

## 🔧 Troubleshooting

| Problem | Solution |
|---------|----------|
| `No videos found` | Run again — check for `❌ YouTube API error` in logs |
| `[403] quotaExceeded` | Daily API quota hit — resets at midnight PST |
| `[403] keyInvalid` | API key wrong or YouTube API v3 not enabled |
| `Email not sent` | Verify App Password and 2FA is enabled |
| `BUILD FAILURE` | Ensure Java 17+ and Maven 3.8+ are installed |

---

## 📜 License

MIT
