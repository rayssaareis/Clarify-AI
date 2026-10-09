/**
 * Single source of truth for limits and constants mirrored from the backend
 * (TranslateController / TranslateService). If a backend limit changes,
 * update it here only.
 */
export const CONFIG = {
  API_URL: "/api/translate",

  MAX_TEXT_LENGTH: 8000,

  MAX_IMAGE_SIZE_BYTES: 1 * 1024 * 1024, // 1 MB
  ALLOWED_IMAGE_TYPES: ["image/jpeg", "image/jpg", "image/png"],

  LANGUAGES: [
    { value: "original", label: "Original language" },
    { value: "en", label: "English" },
  ],
  DEFAULT_LANGUAGE: "original",

  // Values mirror the backend enum SimplificationLevel; labels and
  // descriptions are what the user sees in the selector.
  SIMPLIFICATION_LEVELS: [
    { value: "QUICK_SIMPLE", label: "Quick & Simple", description: "The essentials, in plain language." },
    { value: "CLEAR_DETAILED", label: "Clear & Detailed", description: "A clear explanation with the context you need." },
    { value: "IN_DEPTH", label: "In-Depth Explanation", description: "More context, terminology, and details." },
  ],
  DEFAULT_SIMPLIFICATION_LEVEL: "CLEAR_DETAILED",
};
