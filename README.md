# Commit Diff Analyzer

A local Spring Boot app that fetches diffs between two Bitbucket Server commits and uses Claude AI to classify them as **Functional** or **Non-functional**.

## Requirements

- Java 17+
- An Anthropic API key → https://console.anthropic.com

## Setup

### 1. Add your Anthropic API key

Open `src/main/resources/application.properties` and replace:

```
anthropic.api.key=YOUR_ANTHROPIC_API_KEY_HERE
```

with your actual key.

### 2. (Optional) Set your default Bitbucket Server URL

```
bitbucket.default.host=https://open-bitbucket.nrao.edu
```

This pre-fills the host field in the UI.

## Run

```bash
./gradlew bootRun
```

Then open your browser at: **http://localhost:8080**

## Usage

1. Enter your Bitbucket Server base URL (e.g. `https://open-bitbucket.nrao.edu`)
2. Enter the **Project key** (e.g. `CASA`) and **Repository slug** (e.g. `cartavis`)
3. Paste in the two commit hashes you want to compare
4. Click **Analyze commits**

The app will:
- Fetch the diff directly from your Bitbucket Server (no CORS issue since it runs locally)
- Send the diff to Claude AI
- Return a **Functional / Non-functional** verdict with confidence level and explanation

## Build a JAR

```bash
./gradlew bootJar
java -jar build/libs/commit-analyzer-1.0.0.jar
```

## Project Structure

```
src/main/java/com/qatools/commitanalyzer/
├── CommitAnalyzerApplication.java   # Entry point
├── controller/
│   └── AnalyzerController.java      # REST endpoints
├── service/
│   ├── BitbucketService.java        # Fetches diffs from Bitbucket Server API
│   └── ClaudeService.java           # Calls Anthropic API for classification
└── model/
    ├── AnalyzeRequest.java
    └── AnalysisResult.java

src/main/resources/
├── application.properties           # Config (API key, Bitbucket URL, port)
└── static/index.html                # Frontend UI
```
