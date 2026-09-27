import { CONFIG } from "./config.js";

/**
 * Each validator returns null when valid, or a user-friendly error string.
 * These mirror backend rules so the user gets instant feedback, but the
 * backend remains the source of truth (it validates again).
 */

export function validateText(text) {
  if (!text || !text.trim()) {
    return "Cole ou digite o texto que deseja traduzir.";
  }
  if (text.length > CONFIG.MAX_TEXT_LENGTH) {
    return `Texto muito longo (${text.length.toLocaleString("pt-BR")}/${CONFIG.MAX_TEXT_LENGTH.toLocaleString("pt-BR")} caracteres).`;
  }
  return null;
}

export function validateImage(file) {
  if (!file) {
    return "Selecione uma imagem para enviar.";
  }
  if (!CONFIG.ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return "Tipo de arquivo não suportado. Envie uma imagem JPEG ou PNG.";
  }
  if (file.size > CONFIG.MAX_IMAGE_SIZE_BYTES) {
    return `Imagem muito grande (${formatBytes(file.size)}). O limite é ${formatBytes(CONFIG.MAX_IMAGE_SIZE_BYTES)}.`;
  }
  return null;
}

export function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}
