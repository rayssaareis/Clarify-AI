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
};
