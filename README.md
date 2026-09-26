# Bureaucracy Translator

Making bureaucratic language easier to understand !

Bureaucratic and legal documents can be difficult to understand even when you know the language. 

They are often filled with formal terms, long sentences, and information that is hard to turn into a simple answer: “What does this actually mean, and what do I need to do?”

Bureaucracy Translator is a hackathon project built to make that process simpler.

You can paste the text of a document or upload an image. The application extracts the content when necessary and uses AI to explain it in plain language, highlight the important information, and show the next steps in a clear way.


## Status

• 🚧 **Early development**

The project skeleton is complete and the Spring Boot application builds and passes its tests.

Current status:

* [x] Spring Boot project setup
* [x] Java 21 configuration
* [x] Gradle Wrapper
* [x] Initial package structure
* [x] Basic application test
* [ ] Gemini integration
* [ ] `/api/translate` endpoint
* [ ] OCR integration
* [ ] Frontend
* [ ] Language selection
* [ ] Deployment

## Planned Architecture

```text
User
  │
  ├── Paste document text
  │
  └── Upload document image
          │
          ▼
      Spring Boot API
          │
          ├── OCR.space
          │
          ▼
       Gemini API
          │
          ▼
   Structured explanation
          │
          ├── Plain-language explanation
          └── Next steps
```

External API calls will be handled by the backend so API keys are never exposed in the browser.

## Tech Stack

* **Java 21**
* **Spring Boot 3**
* **Gradle**
* **Google Gemini API** for document explanation
* **OCR.space** for text extraction from images
* **HTML / CSS / JavaScript** for the frontend
* **Render** for deployment

## Requirements

* JDK 21
* Git

Gradle does not need to be installed globally. The project uses the **Gradle Wrapper**.

Verify Java:

```bash
java -version
```

## Running Locally

Clone the repository and enter the project directory:

```bash
git clone <repository-url>
cd bureaucracy-translator
```

Run the application with the Gradle Wrapper:

### • Windows

```powershell
.\gradlew.bat bootRun
```

### • macOS / Linux

```bash
./gradlew bootRun
```

The application starts on:

```text
http://localhost:8080
```

To run the test suite:

### • Windows

```powershell
.\gradlew.bat test
```

### • macOS / Linux

```bash
./gradlew test
```

## Package Structure

```text
src/main/java/com/bureaucracytranslator/
├── controller/   → REST API endpoints
├── service/      → Application and business logic
├── client/       → External API integrations
├── dto/          → Request and response objects
├── config/       → Configuration and Spring beans
└── exception/    → Exception handling
```

## Environment Variables

The following environment variables will be required once the external integrations are implemented:

```text
GEMINI_API_KEY
GEMINI_MODEL
OCR_SPACE_API_KEY
```

`GEMINI_MODEL` will be configurable so the application can switch models without changing application code.

## API

The main API endpoint planned for the MVP is:

```text
POST /api/translate
```

It will accept either document text or an image and return a structured response containing:

```json
{
  "explanation": "Plain-language explanation of the document.",
  "nextSteps": [
    "First action the user should consider.",
    "Second action the user should consider."
  ],
  "disclaimer": "This is not legal advice."
}
```

## Project Principles

### • Privacy

Document contents and AI responses should not be written to application logs.

### • Reliability

The system should explain only what is supported by the provided document and avoid inventing deadlines, amounts, obligations, or clauses.

### • Simplicity

The output should use short sentences and plain language instead of unnecessary legal terminology.

### • Transparency

When the input is unclear, incomplete, or affected by poor OCR, the system should say so instead of guessing.

## Project Goal

A complicated document should not become a barrier to understanding what is happening in your own life.

Bureaucracy Translator was created to make bureaucratic and legal information clearer and more accessible, helping people understand what a document says, why it matters, and what they may need to do next.

Because understanding important information should not require a law degree.

<br>

### Built as a **hackathon project** <3
