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

const levelGroup = document.getElementById("level-group");
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

const themeToggleBtn = document.getElementById("theme-toggle");
const themeToggleIcon = document.getElementById("theme-toggle-icon");
const themeToggleLabel = document.getElementById("theme-toggle-label");

// ---------------------------------------------------------------------
// State
// ---------------------------------------------------------------------

let mode = "text"; // "text" | "image"
let selectedFile = null;
let isSubmitting = false;

// ---------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------

function applyTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  const isDark = theme === "dark";
  if (themeToggleIcon) {
    themeToggleIcon.innerHTML = isDark
      ? '<svg class="theme-svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>'
      : '<svg class="theme-svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>';
  }
  if (themeToggleLabel) {
    themeToggleLabel.textContent = isDark ? "Dark" : "Light";
  }
  try {
    localStorage.setItem("clarify_theme", theme);
  } catch {
    // Ignore storage errors if disabled
  }
}

function initTheme() {
  let theme = "light";
  try {
    const saved = localStorage.getItem("clarify_theme");
    if (saved === "dark" || saved === "light") {
      theme = saved;
    }
    // No OS preference fallback: new users always start in Light Mode
  } catch {
    // Fallback to light
  }
  applyTheme(theme);
}

function toggleTheme() {
  const current = document.documentElement.getAttribute("data-theme") || "light";
  const next = current === "dark" ? "light" : "dark";
  applyTheme(next);
}

const LEVEL_CHECK_ICON =
  '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';

// Native radio inputs inside labels: arrow-key navigation, focus handling and
// screen-reader semantics come from the browser. The default level is the
// one marked in CONFIG, so the user never has to choose.
function renderLevelOptions() {
  CONFIG.SIMPLIFICATION_LEVELS.forEach((level) => {
    const option = document.createElement("label");
    option.className = "level-option";

    const input = document.createElement("input");
    input.type = "radio";
    input.name = "simplification-level";
    input.value = level.value;
    input.className = "level-option__input";
    input.checked = level.value === CONFIG.DEFAULT_SIMPLIFICATION_LEVEL;

    const card = document.createElement("span");
    card.className = "level-option__card";

    const top = document.createElement("span");
    top.className = "level-option__top";

    const name = document.createElement("span");
    name.className = "level-option__name";
    name.textContent = level.label;

    const check = document.createElement("span");
    check.className = "level-option__check";
    check.setAttribute("aria-hidden", "true");
    check.innerHTML = LEVEL_CHECK_ICON;

    const description = document.createElement("span");
    description.className = "level-option__desc";
    description.textContent = level.description;

    top.append(name, check);
    card.append(top, description);
    option.append(input, card);
    levelGroup.appendChild(option);
  });
}

function getSelectedLevel() {
  const checked = levelGroup.querySelector("input[name='simplification-level']:checked");
  return checked ? checked.value : CONFIG.DEFAULT_SIMPLIFICATION_LEVEL;
}

function init() {
  initTheme();
  renderLevelOptions();
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
  charCounter.textContent = `${length.toLocaleString("en-US")} / ${max.toLocaleString("en-US")}`;
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
      simplificationLevel: getSelectedLevel(),
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
  submitLabel.textContent = value ? "Zora is reading your document..." : "Explain this document";
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
  const text = `${resultExplanation.textContent}\n\nNext steps:\n${steps}`;

  try {
    await navigator.clipboard.writeText(text);
    copyFeedback.classList.remove("copy-feedback--hidden");
    setTimeout(() => copyFeedback.classList.add("copy-feedback--hidden"), 2000);
  } catch {
    // Clipboard API unavailable (e.g. insecure context): fail silently,
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

  if (themeToggleBtn) {
    themeToggleBtn.addEventListener("click", toggleTheme);
  }
}

init();
