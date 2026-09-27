# Bureaucracy Translator🪻️

**Making bureaucratic language easier to understand!** :)

Bureaucratic and legal documents can be difficult to understand even when you know the language.

They are often filled with formal terms, long sentences, and information that is hard to turn into a simple answer:

> **“What does this actually mean, and what do I need to do?”**

**Bureaucracy Translator** is a hackathon project built to make that process simpler.

You can paste the text of a document or upload an image. The application extracts the content when necessary and uses AI to explain it in plain language, highlight the important information, and show the next steps in a clear way.

---

## 🚧 Status

**Early development**

The core backend, document-processing pipeline, and initial frontend are working.

### Current progress

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
* [x] Automated tests added
* [x] Frontend
* [x] Language selection
* [x] Text flow manually tested through the frontend
* [ ] Full end-to-end test suite verified locally
* [ ] Deployment

---

## ✨ How It Works

The application supports two input methods:

### 📝 Text

Paste the text of a bureaucratic document and choose the desired output language.

### 📄 Image

Upload an image of a document. The backend sends it to OCR.space to extract the text before sending it through the same AI translation pipeline.

### Text Flow

```text
User enters bureaucratic text
        ↓
Frontend validates input
        ↓
POST /api/translate
        ↓
Spring Boot validates request
        ↓
Gemini processes the content
        ↓
Structured explanation + next steps
        ↓
Frontend displays the result
```

### Image Flow

```text
User uploads document image
        ↓
Frontend validates file
        ↓
POST /api/translate
        ↓
Spring Boot validates image
        ↓
OCR.space extracts the text
        ↓
Extracted text is sent to Gemini
        ↓
Structured explanation + next steps
        ↓
Frontend displays the result
```

---

## 🏗️ Architecture

```text
                         User
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
       Paste document             Upload image
              │                         │
              └────────────┬────────────┘
                           ▼
                  Static Frontend
                           │
                           ▼
                 Spring Boot REST API
                    │             │
                    │             │
                    ▼             ▼
               OCR.space      Gemini API
                    │             │
                    └──────┬──────┘
                           ▼
                  Structured response
                    │              │
                    ▼              ▼
              Explanation      Next steps
```

External API calls are handled by the backend, so API keys are never exposed in the browser.

---

## 🛠️ Tech Stack

* **Java 21**
* **Spring Boot 3**
* **Gradle**
* **Google Gemini API** for document explanation
* **OCR.space** for text extraction from images
* **HTML / CSS / JavaScript** for the frontend
* **Render** for deployment *(planned)*

---

## 🎨 Frontend

The project includes a static frontend served directly by Spring Boot.

### Features

* Paste bureaucratic text
* Upload documents as images
* Drag-and-drop image upload
* Client-side file validation
* Character counter
* Source language toggle
* Translation/explanation loading state
* Structured explanation and next steps
* Copy result button
* Responsive layout

### Structure

```text
src/main/resources/static/
├── index.html
├── css/
│   └── styles.css
└── js/
    ├── config.js
    ├── api.js
    ├── validation.js
    └── app.js
```

---

## 🔌 API

### `POST /api/translate`

The main endpoint accepts either document text or an image.

The request must contain **exactly one** of:

* `text`
* `image`

`targetLanguage` accepts:

* `original`
* `en`

### Limits

* Text: maximum 8,000 characters
* Image formats: JPEG / JPG / PNG
* Image size: maximum 1 MB

### Success Response

```json
{
  "explanation": "Plain-language explanation of the document.",
  "nextSteps": [
    "First action the user should consider.",
    "Second action the user should consider."
  ]
}
```

### Error Response

```json
{
  "error": "string"
}
```

### Image Request Example

```bash
curl -X POST http://localhost:8080/api/translate \
  -F "image=@document.jpg" \
  -F "targetLanguage=original"
```

For image requests, the backend sends the document to OCR.space, extracts the text, and then passes the extracted text through the same translation pipeline used for text input.

---

## 💻 Running Locally

### Requirements

* JDK 21
* Git

Gradle does not need to be installed globally. The project uses the **Gradle Wrapper**.

Verify Java:

```bash
java -version
```

### Clone the repository

```bash
git clone <repository-url>
cd bureaucracy-translator
```

### Configure environment variables

The application requires:

```text
GEMINI_API_KEY
GEMINI_MODEL
OCR_SPACE_API_KEY
```

#### Windows PowerShell

```powershell
$env:GEMINI_API_KEY="your-gemini-api-key"
$env:GEMINI_MODEL="gemini-3.5-flash-lite"
$env:OCR_SPACE_API_KEY="your-ocr-space-api-key"
```

#### macOS / Linux

```bash
export GEMINI_API_KEY="your-gemini-api-key"
export GEMINI_MODEL="gemini-3.5-flash-lite"
export OCR_SPACE_API_KEY="your-ocr-space-api-key"
```

Start the application:

#### Windows

```powershell
.\gradlew.bat bootRun
```

#### macOS / Linux

```bash
./gradlew bootRun
```

The application starts on:

```text
http://localhost:8080
```

---

## 🧪 Testing

The project includes automated tests covering the translation service and REST controller.

The text translation flow has also been manually tested through the frontend with a structured bureaucratic document.

> The full automated test suite should be verified locally before marking all tests as passing.

### Run tests

The project uses the Gradle Wrapper, so Gradle does not need to be installed globally.

#### Windows

```powershell
$env:GEMINI_API_KEY="dummy"
$env:OCR_SPACE_API_KEY="dummy"
.\gradlew.bat clean test
```

#### macOS / Linux

```bash
GEMINI_API_KEY=dummy OCR_SPACE_API_KEY=dummy ./gradlew clean test
```

---

## 📦 Package Structure

```text
src/main/java/com/bureaucracytranslator/
├── controller/   → REST API endpoints
├── service/      → Application and business logic
├── client/       → External API integrations
├── dto/          → Request and response objects
├── config/       → Configuration and Spring beans
└── exception/    → Exception handling
```

---

## 🔐 Environment Variables

The application uses the following environment variables:

```text
GEMINI_API_KEY
GEMINI_MODEL
OCR_SPACE_API_KEY
```

`GEMINI_MODEL` is configurable so the application can switch Gemini models without changing application code.

**API keys should never be committed to the repository.**

---

## 🔒 Security & Privacy

The application applies validation at both the frontend and backend layers.

* Input type and size validation
* Text length validation
* Image format validation
* API-level request validation
* API keys remain on the backend and are not exposed to the frontend
* No frontend framework or build process required

### Privacy

Document contents and AI responses should not be written to application logs.

---

## 🚀 Future Improvements

The project is still in early development. Planned improvements include:

* [ ] Full end-to-end testing
* [ ] Deployment
* [ ] Further UI/UX improvements
* [ ] Additional document and language support
* [ ] More robust document processing

---

*Built as a **hackathon project** <3*
