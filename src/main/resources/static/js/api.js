import { CONFIG } from "./config.js";

/**
 * Thin service layer around POST /api/translate.
 * No API keys live here — the backend owns the Gemini/OCR credentials.
 *
 * Contract (matches TranslateController):
 *   multipart/form-data with exactly one of "text" / "image", plus
 *   "targetLanguage" ("original" | "en").
 *
 * Resolves with { explanation, nextSteps } on success.
 * Rejects with an Error whose .message is already user-friendly
 * (either the backend's ErrorResponse.error, or a generic fallback).
 */
export async function translate({ text, imageFile, targetLanguage }, { signal } = {}) {
  const formData = new FormData();

  if (imageFile) {
    formData.append("image", imageFile);
  } else {
    formData.append("text", text);
  }
  formData.append("targetLanguage", targetLanguage);

  let response;
  try {
    response = await fetch(CONFIG.API_URL, {
      method: "POST",
      body: formData,
      signal,
    });
  } catch (err) {
    if (err.name === "AbortError") throw err;
    throw new Error("Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.");
  }

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    // No/invalid JSON body — fall through to generic error below.
  }

  if (!response.ok) {
    const message = payload && payload.error
      ? payload.error
      : "Não foi possível processar o documento. Tente novamente.";
    throw new Error(message);
  }

  if (!payload || !payload.explanation || !Array.isArray(payload.nextSteps)) {
    throw new Error("Resposta inesperada do servidor. Tente novamente.");
  }

  return payload;
}
