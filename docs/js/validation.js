import { CONFIG } from "./config.js";

/**
 * Each validator returns null when valid, or a user-friendly error string.
 * These mirror backend rules so the user gets instant feedback, but the
 * backend remains the source of truth (it validates again).
 */

export function validateText(text) {
  if (!text || !text.trim()) {
    return "Paste or type the text you want explained.";
  }
  if (text.length > CONFIG.MAX_TEXT_LENGTH) {
    return `Text is too long (${text.length.toLocaleString("en-US")}/${CONFIG.MAX_TEXT_LENGTH.toLocaleString("en-US")} characters).`;
  }
  return null;
}

export function validateImage(file) {
  if (!file) {
    return "Select an image to upload.";
  }
  if (!CONFIG.ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return "Unsupported file type. Please upload a JPEG or PNG image.";
  }
  if (file.size > CONFIG.MAX_IMAGE_SIZE_BYTES) {
    return `Image is too large (${formatBytes(file.size)}). The limit is ${formatBytes(CONFIG.MAX_IMAGE_SIZE_BYTES)}.`;
  }
  return null;
}

export function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}
