# Bureaucracy Translator

Making bureaucratic language easier to understand!

Bureaucratic and legal documents can be difficult to understand even when you know the language.

They are often filled with formal terms, long sentences, and information that is hard to turn into a simple answer: “What does this actually mean, and what do I need to do?”

Bureaucracy Translator is a hackathon project built to make that process simpler.

You can paste the text of a document or upload an image. The application extracts the content when necessary and uses AI to explain it in plain language, highlight the important information, and show the next steps in a clear way.

## Status

🚧 **Early development**

The core backend and document-processing pipeline are now working.

Current status:

* [x] Spring Boot project setup
* [x] Java 21 configuration
* [x] Gradle Wrapper
* [x] Initial package structure
* [x] Gemini integration
* [x] `/api/translate` endpoint
* [x] OCR integration
* [x] Text document translation
* [x] Image document translation
* [x] Structured AI response
* [x] Input validation and error handling
* [x] Automated tests
* [ ] Frontend
* [ ] Language selection
* [ ] Deployment

The current backend test suite passes successfully.

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
          ├── Text ──────────────┐
          │                      │
          └── Image              │
                │                │
                ▼                │
            OCR.space            │
                │                │
                └───────┬────────┘
                        ▼
                   Gemini API
                        │
                        ▼
               Structured response
                        │
                        ├── Plain-language explanation
                        └── Next steps
```

External API calls are handled by the backend so API keys are never exposed in the browser.

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

Before starting the application, configure the required environment variables.

### Windows PowerShell

```powershell
$env:GEMINI_API_KEY="your-gemini-api-key"
$env:GEMINI_MODEL="gemini-3.5-flash-lite"
$env:OCR_SPACE_API_KEY="your-ocr-space-api-key"
```

Run the application:

```powershell
.\gradlew.bat bootRun
```

### macOS / Linux

```bash
export GEMINI_API_KEY="your-gemini-api-key"
export GEMINI_MODEL="gemini-3.5-flash-lite"
export OCR_SPACE_API_KEY="your-ocr-space-api-key"

./gradlew bootRun
```

The application starts on:

```text
http://localhost:8080
```

## Running Tests

The project uses the Gradle Wrapper, so Gradle does not need to be installed globally.

### Windows

```powershell
$env:GEMINI_API_KEY="dummy"
$env:OCR_SPACE_API_KEY="dummy"
.\gradlew.bat clean test
```

### macOS / Linux

```bash
GEMINI_API_KEY=dummy OCR_SPACE_API_KEY=dummy ./gradlew clean test
```

The test suite covers the translation service and REST controller.

## API

The main API endpoint is:

```text
POST /api/translate
```

It accepts either document text or an image.

### Text input

The endpoint can receive document text together with the target language.

### Image input

An image can be uploaded as multipart form data:

```bash
curl -X POST http://localhost:8080/api/translate \
  -F "image=@document.jpg" \
  -F "targetLanguage=original"
```

The backend sends the image to OCR.space, extracts the document text, and then passes the extracted text through the same translation pipeline used for text input.

The response contains a structured explanation and next steps:

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

The application uses the following environment variables:

```text
GEMINI_API_KEY
GEMINI_MODEL
OCR_SPACE_API_KEY
```

`GEMINI_MODEL` is configurable so the application can switch Gemini models without changing application code.

API keys should never be committed to the repository.

## Project Principles

### • Privacy

Document contents and AI responses should not be written to application logs.

<br>

_Built as a **hackathon project** <3_
