import { CONFIG } from "./config.js";
import { translate } from "./api.js";
import { validateText, validateImage, formatBytes } from "./validation.js";

// ---------------------------------------------------------------------
// Elements
// ---------------------------------------------------------------------

const tabText = document.getElementById("tab-text");
const tabImage = document.getElementById("tab-image");
const panelText = document.getElementById("panel-text");
const panelImage = document.getElementById("panel-image");

const textInput = document.getElementById("text-input");
const charCounter = document.getElementById("char-counter");

const dropzone = document.getElementById("dropzone");
const fileInput = document.getElementById("file-input");
const dropzoneEmpty = document.getElementById("dropzone-empty");
const dropzoneFile = document.getElementById("dropzone-file");
const fileNameEl = document.getElementById("file-name");
const fileSizeEl = document.getElementById("file-size");
const removeFileBtn = document.getElementById("remove-file");

const languageSelect = document.getElementById("language-select");
const formError = document.getElementById("form-error");

const submitBtn = document.getElementById("submit-btn");
const submitLabel = document.getElementById("submit-label");
const submitSpinner = document.getElementById("submit-spinner");

const formCard = document.querySelector(".card");
const resultCard = document.getElementById("result-card");
const resultExplanation = document.getElementById("result-explanation");
const resultSteps = document.getElementById("result-steps");
const copyBtn = document.getElementById("copy-btn");
const copyFeedback = document.getElementById("copy-feedback");

const errorCard = document.getElementById("error-card");
const errorMessage = document.getElementById("error-message");
const retryBtn = document.getElementById("retry-btn");

// ---------------------------------------------------------------------
// State
// ---------------------------------------------------------------------

let mode = "text"; // "text" | "image"
let selectedFile = null;
let isSubmitting = false;

// ---------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------

function init() {
  CONFIG.LANGUAGES.forEach((lang) => {
    const option = document.createElement("option");
    option.value = lang.value;
    option.textContent = lang.label;
    languageSelect.appendChild(option);
  });
  languageSelect.value = CONFIG.DEFAULT_LANGUAGE;

  updateCharCounter();
  updateSubmitState();
  bindEvents();
}

// ---------------------------------------------------------------------
// Mode switching
// ---------------------------------------------------------------------

function setMode(nextMode) {
  mode = nextMode;
  const isText = mode === "text";

  tabText.classList.toggle("tab--active", isText);
  tabText.setAttribute("aria-selected", String(isText));
  tabImage.classList.toggle("tab--active", !isText);
  tabImage.setAttribute("aria-selected", String(!isText));

  panelText.classList.toggle("panel--hidden", !isText);
  panelImage.classList.toggle("panel--hidden", isText);

  clearFormError();
  updateSubmitState();
}

// ---------------------------------------------------------------------
// Text panel
// ---------------------------------------------------------------------

function updateCharCounter() {
  const length = textInput.value.length;
  const max = CONFIG.MAX_TEXT_LENGTH;
  charCounter.textContent = `${length.toLocaleString("pt-BR")} / ${max.toLocaleString("pt-BR")}`;
  charCounter.classList.toggle("is-over", length > max);
}

// ---------------------------------------------------------------------
// Image panel
// ---------------------------------------------------------------------

function setSelectedFile(file) {
  selectedFile = file;

  if (!file) {
    dropzoneEmpty.classList.remove("dropzone__file--hidden");
    dropzoneFile.classList.add("dropzone__file--hidden");
    fileInput.value = "";
  } else {
    dropzoneEmpty.classList.add("dropzone__file--hidden");
    dropzoneFile.classList.remove("dropzone__file--hidden");
    fileNameEl.textContent = file.name;
    fileSizeEl.textContent = formatBytes(file.size);
  }

  clearFormError();
  updateSubmitState();
}

function handleFileChosen(file) {
  const error = validateImage(file);
  if (error) {
    setSelectedFile(null);
    showFormError(error);
    return;
  }
  setSelectedFile(file);
}

// ---------------------------------------------------------------------
// Validation / submit state
// ---------------------------------------------------------------------

function currentValidationError() {
  if (mode === "text") {
    return validateText(textInput.value);
  }
  return validateImage(selectedFile);
}

function updateSubmitState() {
  submitBtn.disabled = isSubmitting || Boolean(currentValidationError());
}

function showFormError(message) {
  formError.textContent = message;
  formError.classList.remove("form-error--hidden");
}

function clearFormError() {
  formError.classList.add("form-error--hidden");
  formError.textContent = "";
}

// ---------------------------------------------------------------------
// Submit
// ---------------------------------------------------------------------

async function handleSubmit() {
  const error = currentValidationError();
  if (error) {
    showFormError(error);
    return;
  }

  clearFormError();
  setSubmitting(true);
  hideResult();
  hideError();

  try {
    const payload = await translate({
      text: mode === "text" ? textInput.value : null,
      imageFile: mode === "image" ? selectedFile : null,
      targetLanguage: languageSelect.value,
    });
    showResult(payload);
  } catch (err) {
    showError(err.message);
  } finally {
    setSubmitting(false);
  }
}

function setSubmitting(value) {
  isSubmitting = value;
  submitLabel.textContent = value ? "Traduzindo..." : "Traduzir";
  submitSpinner.classList.toggle("spinner--hidden", !value);
  updateSubmitState();
}

// ---------------------------------------------------------------------
// Result / error rendering
// ---------------------------------------------------------------------

function showResult({ explanation, nextSteps }) {
  resultExplanation.textContent = explanation;
  resultSteps.innerHTML = "";
  nextSteps.forEach((step) => {
    const li = document.createElement("li");
    li.textContent = step;
    resultSteps.appendChild(li);
  });
  copyFeedback.classList.add("copy-feedback--hidden");
  resultCard.classList.remove("result-card--hidden");
  resultCard.scrollIntoView({ behavior: "smooth", block: "start" });
}

function hideResult() {
  resultCard.classList.add("result-card--hidden");
}

function showError(message) {
  errorMessage.textContent = message;
  errorCard.classList.remove("error-card--hidden");
  errorCard.scrollIntoView({ behavior: "smooth", block: "start" });
}

function hideError() {
  errorCard.classList.add("error-card--hidden");
}

async function copyResult() {
  const steps = Array.from(resultSteps.children)
    .map((li, i) => `${i + 1}. ${li.textContent}`)
    .join("\n");
  const text = `${resultExplanation.textContent}\n\nPróximos passos:\n${steps}`;

  try {
    await navigator.clipboard.writeText(text);
    copyFeedback.classList.remove("copy-feedback--hidden");
    setTimeout(() => copyFeedback.classList.add("copy-feedback--hidden"), 2000);
  } catch {
    // Clipboard API unavailable (e.g. insecure context) — fail silently,
    // the text is still fully visible/selectable for manual copy.
  }
}

// ---------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------

function bindEvents() {
  tabText.addEventListener("click", () => setMode("text"));
  tabImage.addEventListener("click", () => setMode("image"));

  textInput.addEventListener("input", () => {
    updateCharCounter();
    clearFormError();
    updateSubmitState();
  });

  dropzone.addEventListener("click", () => fileInput.click());
  dropzone.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      fileInput.click();
    }
  });

  fileInput.addEventListener("change", () => {
    const file = fileInput.files && fileInput.files[0];
    if (file) handleFileChosen(file);
  });

  removeFileBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    setSelectedFile(null);
  });

  ["dragenter", "dragover"].forEach((evt) => {
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.add("is-dragover");
    });
  });

  ["dragleave", "drop"].forEach((evt) => {
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.remove("is-dragover");
    });
  });

  dropzone.addEventListener("drop", (e) => {
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) handleFileChosen(file);
  });

  languageSelect.addEventListener("change", clearFormError);

  submitBtn.addEventListener("click", handleSubmit);
  retryBtn.addEventListener("click", () => {
    hideError();
    formCard.scrollIntoView({ behavior: "smooth", block: "start" });
  });
  copyBtn.addEventListener("click", copyResult);
}

init();
