/* Campus Navigation — app.js
 * Reads the current location from the URL, fetches locations,
 * finds routes, shows steps, speaks them, and toggles EN/KN.
 * Depends on map.js (uses window.MapRenderer).
 */
(function () {
  "use strict";

  // ---- Config ----
  // API_BASE comes from index.html via window.API_BASE.
  // Strip trailing slashes so we never produce "//api/...".
  let API_BASE = (window.API_BASE || "").replace(/\/+$/, "");

  // The two gates we consider as exits for the emergency button.
  const EXIT_NODES = ["entrance", "gate2"];

  // ---- State ----
  const state = {
    currentLocationId: null,   // where the user is standing
    destinationId:     null,   // what they picked
    allLocations:      [],     // array from /api/locations
    locationsById:     {},     // id -> location
    lastPath:          null,   // last /api/path response
    lang:              "en"    // "en" or "kn"
  };

  // ---- Translations ----
  const STRINGS = {
    en: {
      youAreHere:       "You are here",
      chooseDest:       "Where do you want to go?",
      orPick:           "Or pick from the full list",
      findRoute:        "Find Route",
      emergency:        "Emergency — Nearest Exit",
      directions:       "Directions",
      distance:         "Distance",
      walkTime:         "Walk time",
      newRoute:         "New Route",
      swap:             "Reverse",
      searchPlaceholder:"Search a place…",
      selectDest:       "Select a destination…",
      pickFirst:        "Please pick a destination first.",
      noPath:           "No walking route exists between these locations.",
      loadFail:         "Could not load locations. Is the backend running?",
      routeFail:        "Could not fetch the route. Try again.",
      unknownLoc:       "Unknown location — showing the main entrance instead.",
      arrivedAt:        "You have arrived at",
      alreadyThere:     "You are already there."
    },
    kn: {
      youAreHere:       "ನೀವು ಇಲ್ಲಿದ್ದೀರಿ",
      chooseDest:       "ನೀವು ಎಲ್ಲಿಗೆ ಹೋಗಬೇಕು?",
      orPick:           "ಅಥವಾ ಪೂರ್ಣ ಪಟ್ಟಿಯಿಂದ ಆಯ್ಕೆಮಾಡಿ",
      findRoute:        "ದಾರಿ ತೋರಿಸಿ",
      emergency:        "ತುರ್ತು — ಹತ್ತಿರದ ನಿರ್ಗಮನ",
      directions:       "ದಾರಿ ಸೂಚನೆಗಳು",
      distance:         "ದೂರ",
      walkTime:         "ನಡಿಗೆ ಸಮಯ",
      newRoute:         "ಹೊಸ ದಾರಿ",
      swap:             "ತಿರುಗಿಸಿ",
      searchPlaceholder:"ಸ್ಥಳ ಹುಡುಕಿ…",
      selectDest:       "ಗಮ್ಯಸ್ಥಾನ ಆಯ್ಕೆಮಾಡಿ…",
      pickFirst:        "ದಯವಿಟ್ಟು ಮೊದಲು ಗಮ್ಯಸ್ಥಾನ ಆಯ್ಕೆಮಾಡಿ.",
      noPath:           "ಈ ಸ್ಥಳಗಳ ನಡುವೆ ನಡಿಗೆ ದಾರಿ ಇಲ್ಲ.",
      loadFail:         "ಸ್ಥಳಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ. ಸರ್ವರ್ ಚಾಲನೆಯಲ್ಲಿದೆಯೇ?",
      routeFail:        "ದಾರಿ ಪಡೆಯಲಾಗಲಿಲ್ಲ. ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.",
      unknownLoc:       "ಅಪರಿಚಿತ ಸ್ಥಳ — ಮುಖ್ಯ ಪ್ರವೇಶದ್ವಾರ ತೋರಿಸಲಾಗುತ್ತಿದೆ.",
      arrivedAt:        "ನೀವು ತಲುಪಿದ್ದೀರಿ",
      alreadyThere:     "ನೀವು ಈಗಾಗಲೇ ಅಲ್ಲಿದ್ದೀರಿ."
    }
  };

  // Look up a key in the current language, fallback to English.
  function t(key) {
    return (STRINGS[state.lang] && STRINGS[state.lang][key])
        || STRINGS.en[key]
        || key;
  }

  // ---- DOM refs (filled in start()) ----
  const els = {};

  // ---- Toast timer ----
  let toastTimer = null;

  // ---- Start ----
  function start() {

    // Grab elements.
    els.currentName    = document.getElementById("current-name");
    els.currentHours   = document.getElementById("current-hours");
    els.search         = document.getElementById("dest-search");
    els.searchClear    = document.getElementById("search-clear");
    els.suggestions    = document.getElementById("dest-suggestions");
    els.select         = document.getElementById("dest-select");
    els.findBtn        = document.getElementById("find-route-btn");
    els.emergencyBtn   = document.getElementById("emergency-btn");
    els.mapContainer   = document.getElementById("map-container");
    els.dirCard        = document.getElementById("directions-card");
    els.speakBtn       = document.getElementById("speak-btn");
    els.summaryDist    = document.getElementById("summary-distance");
    els.summaryTime    = document.getElementById("summary-time");
    els.stepsList      = document.getElementById("steps-list");
    els.resetBtn       = document.getElementById("reset-btn");
    els.swapBtn        = document.getElementById("swap-btn");
    els.loading        = document.getElementById("loading-overlay");
    els.errorToast     = document.getElementById("error-toast");
    els.errorText      = document.getElementById("error-text");
    els.errorClose     = document.getElementById("error-close");
    els.langBtn        = document.getElementById("lang-toggle");
    els.langLabel      = document.getElementById("lang-label");

    // Init the SVG map.
    if (window.MapRenderer && els.mapContainer) {
      window.MapRenderer.init(els.mapContainer);
      window.MapRenderer.onNodeClick(handleMapNodeClick);
    }

    attachEvents();

    state.currentLocationId = readLocationFromUrl();
    loadLocations();
  }

  // ---- Event listeners ----
  function attachEvents() {

    if (els.search) {
      els.search.addEventListener("input", handleSearchInput);
      els.search.addEventListener("keydown", handleSearchKey);
    }
    if (els.searchClear)  els.searchClear.addEventListener("click", clearSearch);
    if (els.select)       els.select.addEventListener("change", handleSelectChange);
    if (els.findBtn)      els.findBtn.addEventListener("click", findRoute);
    if (els.emergencyBtn) els.emergencyBtn.addEventListener("click", emergencyRoute);
    if (els.resetBtn)     els.resetBtn.addEventListener("click", resetRoute);
    if (els.swapBtn)      els.swapBtn.addEventListener("click", swapRoute);
    if (els.speakBtn)     els.speakBtn.addEventListener("click", speakDirections);
    if (els.errorClose)   els.errorClose.addEventListener("click", hideToast);
    if (els.langBtn)      els.langBtn.addEventListener("click", toggleLanguage);

    // Hide suggestions when clicking anywhere else.
    document.addEventListener("click", evt => {
      if (!els.suggestions || els.suggestions.hidden) return;
      if (els.suggestions.contains(evt.target)) return;
      if (els.search && els.search.contains(evt.target)) return;
      hideSuggestions();
    });
  }

  // ---- Read current location from URL ----
  // Supports: /loc/entrance, /loc/entrance/, ?loc=library, ?from=canteen.
  // Cleans the id: decode → strip whitespace → lowercase.
  function readLocationFromUrl() {

    let rawId = null;

    // Try /loc/<id> in the path first.
    const path = window.location.pathname;
    const marker = "/loc/";
    const idx = path.indexOf(marker);

    if (idx >= 0) {
      rawId = path.substring(idx + marker.length);
      rawId = rawId.replace(/\/+$/, "");        // strip trailing slashes
    }

    // Fallback: ?loc=, then ?from=.
    if (!rawId) {
      const query = window.location.search;
      rawId = getQueryParam(query, "loc") || getQueryParam(query, "from");
    }

    if (!rawId) return null;

    // Clean up.
    let cleaned = rawId;
    try {
      cleaned = decodeURIComponent(cleaned);
    } catch (e) {
      // ignore — we will clean it anyway
    }
    cleaned = cleaned.replace(/\s+/g, "").toLowerCase();

    return cleaned.length === 0 ? null : cleaned;
  }

  // Tiny query-string parser (kept simple, no URLSearchParams).
  function getQueryParam(query, name) {
    if (!query) return null;

    const search = name + "=";
    const parts = query.substring(1).split("&");

    for (const part of parts) {
      if (part.indexOf(search) === 0) {
        return decodeURIComponent(part.substring(search.length));
      }
    }
    return null;
  }

  // ---- Fetch locations ----
  function loadLocations() {

    const url = API_BASE + "/api/locations";

    fetch(url)
      .then(res => {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.json();
      })
      .then(list => {
        state.allLocations = list;
        state.locationsById = {};
        for (const loc of list) {
          state.locationsById[loc.id] = loc;
        }

        // Unknown id? Fall back to entrance.
        if (!state.currentLocationId || !state.locationsById[state.currentLocationId]) {
          if (state.currentLocationId) showToast(t("unknownLoc"));
          state.currentLocationId = "entrance";
        }

        if (window.MapRenderer) window.MapRenderer.renderLocations(list);
        fillDropdown(list);
        updateCurrentLocationUI();
      })
      .catch(err => {
        console.error("[app] loadLocations failed:", err);
        showToast(t("loadFail"));
      });
  }

  // ---- Fill the dropdown ----
  function fillDropdown(list) {

    if (!els.select) return;

    // Remove everything except the placeholder (first option).
    while (els.select.options.length > 1) {
      els.select.remove(1);
    }

    for (const loc of list) {
      if (loc.id === state.currentLocationId) continue;   // you can't walk to yourself

      const option = document.createElement("option");
      option.value = loc.id;
      option.textContent = loc.name + "  ·  " + typeLabel(loc.type);
      els.select.appendChild(option);
    }
  }

  // ---- Update the "You are here" card ----
  function updateCurrentLocationUI() {

    const loc = state.locationsById[state.currentLocationId];
    if (!loc || !els.currentName) return;

    els.currentName.textContent = loc.name;

    if (els.currentHours) {
      if (loc.hours) {
        els.currentHours.hidden = false;
        els.currentHours.textContent = "🕐 " + loc.hours;
      } else {
        els.currentHours.hidden = true;
      }
    }
  }

  // ---- Find route (the main action) ----
  function findRoute() {

    if (!state.destinationId) {
      showToast(t("pickFirst"));
      return;
    }
    if (state.destinationId === state.currentLocationId) {
      showToast(t("alreadyThere"));
      return;
    }

    showLoading(true);

    const url = API_BASE
      + "/api/path?from=" + encodeURIComponent(state.currentLocationId)
      + "&to="   + encodeURIComponent(state.destinationId);

    fetch(url)
      .then(res => {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.json();
      })
      .then(data => {

        if (data.error === "no_path") {
          showToast(t("noPath"));
          return;
        }

        state.lastPath = data;
        renderDirections(data);

        if (window.MapRenderer) {
          window.MapRenderer.highlightPath(data.path, data.from, data.to);
        }
      })
      .catch(err => {
        console.error("[app] findRoute failed:", err);
        showToast(t("routeFail"));
      })
      .then(() => showLoading(false));       // always runs
  }

  // ---- Render the directions card ----
  function renderDirections(data) {

    if (!data || !data.steps) return;

    els.summaryDist.textContent = data.totalDistance + " m";
    els.summaryTime.textContent = data.estimatedTimeMinutes + " min";

    // Clear old steps.
    els.stepsList.innerHTML = "";

    for (const step of data.steps) {

      const li = document.createElement("li");
      li.className = "step" + (step.direction === "arrived" ? " arrived" : "");

      // Numbered circle.
      const circle = document.createElement("div");
      circle.className = "step-num dir-" + (step.direction || "straight");
      circle.textContent = step.step;

      // Text and meta line.
      const body = document.createElement("div");
      body.className = "step-body";

      const text = document.createElement("p");
      text.className = "step-text";
      text.textContent = step.instruction;

      const meta = document.createElement("p");
      meta.className = "step-meta";
      if (step.distance > 0) {
        meta.textContent = step.distance + " m · " + (step.direction || "straight");
      } else {
        meta.textContent = step.nodeName || "";
      }

      body.appendChild(text);
      body.appendChild(meta);

      li.appendChild(circle);
      li.appendChild(body);
      els.stepsList.appendChild(li);
    }

    els.dirCard.hidden = false;
    els.dirCard.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  // ---- Emergency: pick the nearest gate by map coordinates ----
  function emergencyRoute() {

    const current = state.locationsById[state.currentLocationId];
    if (!current) return;

    let bestId = null;
    let bestDistance = Infinity;

    for (const gateId of EXIT_NODES) {
      const gate = state.locationsById[gateId];
      if (!gate) continue;

      const dx = gate.x - current.x;
      const dy = gate.y - current.y;
      const distance = Math.sqrt(dx * dx + dy * dy);

      if (distance < bestDistance) {
        bestDistance = distance;
        bestId = gateId;
      }
    }

    if (bestId) {
      state.destinationId = bestId;
      if (els.select) els.select.value = bestId;
      findRoute();
    }
  }

  // ---- Reset / swap ----
  function resetRoute() {

    state.destinationId = null;

    if (els.select)       els.select.value = "";
    if (els.search)       els.search.value = "";
    if (els.searchClear)  els.searchClear.hidden = true;
    if (els.dirCard)      els.dirCard.hidden = true;
    if (els.stepsList)    els.stepsList.innerHTML = "";

    hideSuggestions();

    if (window.MapRenderer) window.MapRenderer.clearPath();
  }

  function swapRoute() {

    if (!state.lastPath) return;

    const oldFrom = state.lastPath.from;
    const oldTo   = state.lastPath.to;

    // Walk backwards: the destination becomes the new start.
    state.currentLocationId = oldTo;
    state.destinationId     = oldFrom;

    const loc = state.locationsById[oldTo];
    if (els.currentName && loc) els.currentName.textContent = loc.name;

    // Repopulate the dropdown because the current location changed.
    fillDropdown(state.allLocations);

    if (els.select) els.select.value = oldFrom;
    if (els.search) {
      const dest = state.locationsById[oldFrom];
      els.search.value = dest ? dest.name : "";
    }

    findRoute();
  }

  // ---- Dropdown and search handlers ----
  function handleSelectChange(evt) {

    state.destinationId = evt.target.value;

    if (els.findBtn) els.findBtn.disabled = !state.destinationId;

    if (els.search && state.destinationId) {
      const loc = state.locationsById[state.destinationId];
      if (loc) els.search.value = loc.name;
    }
    hideSuggestions();
  }

  function handleSearchInput(evt) {

    const query = evt.target.value.trim().toLowerCase();

    if (els.searchClear) els.searchClear.hidden = (query.length === 0);

    if (query.length < 1) {
      hideSuggestions();
      return;
    }

    const matches = [];

    for (const loc of state.allLocations) {
      if (loc.id === state.currentLocationId) continue;

      const nameLower = (loc.name || "").toLowerCase();
      const idLower   = (loc.id   || "").toLowerCase();

      if (nameLower.indexOf(query) >= 0 || idLower.indexOf(query) >= 0) {
        matches.push(loc);
      }
      if (matches.length >= 8) break;
    }

    showSuggestions(matches);
  }

  function handleSearchKey(evt) {
    if (evt.key === "Enter") {
      const first = els.suggestions.querySelector("li");
      if (first) first.click();
    } else if (evt.key === "Escape") {
      hideSuggestions();
    }
  }

  function showSuggestions(items) {

    if (!els.suggestions) return;

    els.suggestions.innerHTML = "";

    if (items.length === 0) {
      els.suggestions.hidden = true;
      return;
    }

    for (const loc of items) {

      const li = document.createElement("li");

      const name = document.createElement("span");
      name.className = "sug-name";
      name.textContent = loc.name;

      const type = document.createElement("span");
      type.className = "sug-type";
      type.textContent = typeLabel(loc.type);

      li.appendChild(name);
      li.appendChild(type);

      // Click handler captures loc safely (no var-in-loop bug).
      li.onclick = () => {
        state.destinationId = loc.id;
        if (els.select)  els.select.value = loc.id;
        if (els.search)  els.search.value = loc.name;
        if (els.findBtn) els.findBtn.disabled = false;
        hideSuggestions();
      };

      els.suggestions.appendChild(li);
    }

    els.suggestions.hidden = false;
  }

  function hideSuggestions() {
    if (els.suggestions) {
      els.suggestions.hidden = true;
      els.suggestions.innerHTML = "";
    }
  }

  function clearSearch() {
    if (els.search)      els.search.value = "";
    if (els.searchClear) els.searchClear.hidden = true;
    hideSuggestions();
  }

  // ---- Map node click ----
  function handleMapNodeClick(nodeId) {

    if (!nodeId) return;
    if (nodeId === state.currentLocationId) return;

    state.destinationId = nodeId;

    if (els.select)  els.select.value = nodeId;
    if (els.findBtn) els.findBtn.disabled = false;

    const loc = state.locationsById[nodeId];
    if (els.search && loc) els.search.value = loc.name;
  }

  // ---- Speak directions (Web Speech API) ----
  function speakDirections() {

    if (!state.lastPath || !state.lastPath.steps) return;

    if (!("speechSynthesis" in window)) {
      showToast("Voice guidance is not supported in this browser.");
      return;
    }

    window.speechSynthesis.cancel();

    // Join every step into one long sentence.
    const text = state.lastPath.steps
      .map(s => s.instruction)
      .join(". ");

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate  = 0.95;
    utterance.pitch = 1.0;

    // Try to pick a voice that matches the current language.
    const wantLang = (state.lang === "kn") ? "kn" : "en";
    const voices = window.speechSynthesis.getVoices();
    for (const voice of voices) {
      if (voice.lang && voice.lang.toLowerCase().indexOf(wantLang) === 0) {
        utterance.voice = voice;
        break;
      }
    }

    if (els.speakBtn) els.speakBtn.classList.add("speaking");
    utterance.onend = () => {
      if (els.speakBtn) els.speakBtn.classList.remove("speaking");
    };

    window.speechSynthesis.speak(utterance);
  }

  // ---- Language toggle ----
  function toggleLanguage() {

    state.lang = (state.lang === "en") ? "kn" : "en";

    // Search placeholder.
    if (els.search) els.search.placeholder = t("searchPlaceholder");

    // Dropdown placeholder (first option).
    if (els.select && els.select.options.length > 0) {
      els.select.options[0].textContent = t("selectDest");
    }

    // Find Route button label.
    if (els.findBtn) {
      const label = els.findBtn.querySelector("span");
      if (label) label.textContent = t("findRoute");
    }

    // Emergency button label.
    if (els.emergencyBtn) {
      const emLabel = els.emergencyBtn.querySelector("span");
      if (emLabel) emLabel.textContent = t("emergency");
    }

    // Section headings — "Where do you want to go?" in either language.
    for (const h of document.querySelectorAll(".section-title")) {
      const text = h.textContent;
      if (text.indexOf("Where do you") >= 0 || text.indexOf("ಎಲ್ಲಿಗೆ") >= 0) {
        h.textContent = t("chooseDest");
      }
    }

    // "You are here" label.
    const label = document.querySelector(".current-label");
    if (label) label.textContent = t("youAreHere");

    // Toggle face: "ಕ" in English mode, "EN" in Kannada mode.
    if (els.langLabel) {
      els.langLabel.textContent = (state.lang === "en") ? "ಕ" : "EN";
    }
  }

  // ---- Helpers ----

  // Convert a raw type like "building" into a short UI label.
  function typeLabel(type) {
    if (!type) return "place";

    switch (type) {
      case "gate":     return "gate";
      case "junction": return "path";
      case "building": return "building";
      case "lab":      return "lab";
      case "office":   return "office";
      case "facility": return "facility";
      default:         return type;
    }
  }

  // Show or hide the loading overlay.
  function showLoading(on) {
    if (els.loading) els.loading.hidden = !on;
  }

  // Show the red toast for 4 seconds.
  function showToast(message) {
    if (!els.errorToast || !els.errorText) return;

    els.errorText.textContent = message;
    els.errorToast.hidden = false;

    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(hideToast, 4000);
  }

  function hideToast() {
    if (els.errorToast) els.errorToast.hidden = true;
  }

  // ---- Kick off ----
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }

})();