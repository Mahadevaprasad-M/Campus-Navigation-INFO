/* map.js — draws the campus map as an SVG inside #map-container.
 * Exposes window.MapRenderer with:
 *   init(container), renderLocations(list), highlightPath(ids, start, end),
 *   clearPath(), onNodeClick(fn), getLocation(id)
 */
(function () {
  "use strict";

  // ---- Constants ----
  const SVG_NS     = "http://www.w3.org/2000/svg";
  const MAP_WIDTH  = 800;
  const MAP_HEIGHT = 600;
  const NODE_RADIUS  = 9;
  const LABEL_OFFSET = 22;
  const ARROW_SIZE   = 8;
  const ROAD_MAX_DISTANCE = 170;   // decorative roads only

  // ---- State ----
  const state = {
    container:   null,
    svg:         null,
    layerRoads:  null,
    layerNodes:  null,
    layerLabels: null,
    layerPath:   null,
    locations:   {},     // id -> location
    nodeGroups:  {},     // id -> <g> element
    onNodeClick: null
  };

  // ---- Public API ----
  const MapRenderer = {

    init(container) {
      if (!container) {
        console.error("[MapRenderer] No container element passed to init()");
        return;
      }
      state.container = container;

      // Remove the loading placeholder.
      const placeholder = container.querySelector(".map-placeholder");
      if (placeholder) placeholder.parentNode.removeChild(placeholder);

      // Root <svg>
      const svg = document.createElementNS(SVG_NS, "svg");
      svg.setAttribute("viewBox", `0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`);
      svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
      svg.setAttribute("role", "img");
      svg.setAttribute("aria-label", "Campus map");

      // Background rectangle.
      const bg = document.createElementNS(SVG_NS, "rect");
      bg.setAttribute("x", 0);
      bg.setAttribute("y", 0);
      bg.setAttribute("width", MAP_WIDTH);
      bg.setAttribute("height", MAP_HEIGHT);
      bg.setAttribute("class", "map-bg");
      svg.appendChild(bg);

      // Layer groups — order = painting order.
      state.layerRoads  = createGroup("roads");
      state.layerNodes  = createGroup("nodes");
      state.layerLabels = createGroup("labels");
      state.layerPath   = createGroup("path");

      svg.appendChild(state.layerRoads);
      svg.appendChild(state.layerNodes);
      svg.appendChild(state.layerLabels);
      svg.appendChild(state.layerPath);

      container.appendChild(svg);
      state.svg = svg;
    },

    renderLocations(locations) {
      if (!state.svg) {
        console.error("[MapRenderer] Call init() before renderLocations()");
        return;
      }
      if (!locations || locations.length === 0) return;

      // Clear previous drawing (path layer left alone).
      clearChildren(state.layerRoads);
      clearChildren(state.layerNodes);
      clearChildren(state.layerLabels);
      state.nodeGroups = {};
      state.locations  = {};

      // Index by id for O(1) lookup.
      for (const loc of locations) {
        state.locations[loc.id] = loc;
      }

      // Decorative roads first (under the nodes).
      drawRoads(locations);

      // Nodes.
      for (const loc of locations) {
        const group = createNodeGroup(loc);
        state.layerNodes.appendChild(group);
        state.nodeGroups[loc.id] = group;
      }

      // Labels on their own layer, above everything else.
      for (const loc of locations) {
        state.layerLabels.appendChild(createLabel(loc));
      }
    },

    highlightPath(nodeIds, startId, endId) {
      if (!state.svg) return;
      if (!nodeIds || nodeIds.length === 0) return;

      this.clearPath();

      // Collect coordinates of every node in order.
      const points = [];
      for (const id of nodeIds) {
        const loc = state.locations[id];
        if (loc) points.push({ x: loc.x, y: loc.y });
      }
      if (points.length < 2) return;

      // Main polyline.
      const polyline = document.createElementNS(SVG_NS, "polyline");
      polyline.setAttribute("points", points.map(p => `${p.x},${p.y}`).join(" "));
      polyline.setAttribute("class", "map-path");
      state.layerPath.appendChild(polyline);

      // One arrow per segment.
      for (let i = 0; i < points.length - 1; i++) {
        drawArrow(points[i], points[i + 1]);
      }

      // Reset all node classes, then mark the ones on the path.
      for (const g of Object.values(state.nodeGroups)) {
        g.classList.remove("start", "end", "on-path");
      }
      for (const id of nodeIds) {
        if (state.nodeGroups[id]) state.nodeGroups[id].classList.add("on-path");
      }
      if (state.nodeGroups[startId]) state.nodeGroups[startId].classList.add("start");
      if (state.nodeGroups[endId])   state.nodeGroups[endId].classList.add("end");
    },

    clearPath() {
      if (!state.layerPath) return;
      clearChildren(state.layerPath);

      for (const g of Object.values(state.nodeGroups)) {
        g.classList.remove("start", "end", "on-path");
      }
    },

    onNodeClick(fn) {
      state.onNodeClick = (typeof fn === "function") ? fn : null;
    },

    getLocation(id) {
      return state.locations[id] || null;
    }
  };

  // ---- Internal helpers ----

  function createGroup(name) {
    const g = document.createElementNS(SVG_NS, "g");
    g.setAttribute("data-layer", name);
    return g;
  }

  function clearChildren(element) {
    while (element.firstChild) element.removeChild(element.firstChild);
  }

  function createNodeGroup(loc) {
    const g = document.createElementNS(SVG_NS, "g");
    g.setAttribute("class", "node type-" + (loc.type || "unknown"));
    g.setAttribute("data-id", loc.id);
    g.setAttribute("transform", `translate(${loc.x} ${loc.y})`);
    g.setAttribute("role", "button");
    g.setAttribute("aria-label", loc.name || loc.id);

    // Invisible hit target — easier to tap on mobile.
    const hit = document.createElementNS(SVG_NS, "circle");
    hit.setAttribute("r", NODE_RADIUS + 8);
    hit.setAttribute("fill", "transparent");
    hit.style.cursor = "pointer";
    g.appendChild(hit);

    // Visible circle.
    const circle = document.createElementNS(SVG_NS, "circle");
    circle.setAttribute("r", NODE_RADIUS);
    circle.setAttribute("class", "map-node type-" + (loc.type || "unknown"));
    g.appendChild(circle);

    // Click handler reports the id to app.js.
    g.addEventListener("click", () => {
      if (state.onNodeClick) state.onNodeClick(loc.id);
    });

    return g;
  }

  function createLabel(loc) {
    const text = document.createElementNS(SVG_NS, "text");
    text.setAttribute("x", loc.x);
    text.setAttribute("y", loc.y + LABEL_OFFSET);
    text.setAttribute("class", "map-label");
    text.setAttribute("text-anchor", "middle");
    text.textContent = shorten(loc.name || loc.id, 18);
    return text;
  }

  function drawRoads(locations) {
    for (let i = 0; i < locations.length; i++) {
      for (let j = i + 1; j < locations.length; j++) {
        const a = locations[i];
        const b = locations[j];

        const dx = a.x - b.x;
        const dy = a.y - b.y;
        if (Math.sqrt(dx * dx + dy * dy) > ROAD_MAX_DISTANCE) continue;

        const line = document.createElementNS(SVG_NS, "line");
        line.setAttribute("x1", a.x);
        line.setAttribute("y1", a.y);
        line.setAttribute("x2", b.x);
        line.setAttribute("y2", b.y);
        line.setAttribute("class", "map-road");
        state.layerRoads.appendChild(line);
      }
    }
  }

  function drawArrow(from, to) {
    const midX = (from.x + to.x) / 2;
    const midY = (from.y + to.y) / 2;
    const angleDeg = Math.atan2(to.y - from.y, to.x - from.x) * 180 / Math.PI;

    const arrow = document.createElementNS(SVG_NS, "polygon");
    arrow.setAttribute("class", "map-arrow");

    const side = ARROW_SIZE * 1.4;
    const half = ARROW_SIZE * 0.7;
    arrow.setAttribute("points", `0,0 ${-side},${half} ${-side},${-half}`);
    arrow.setAttribute("transform", `translate(${midX} ${midY}) rotate(${angleDeg})`);

    state.layerPath.appendChild(arrow);
  }

  function shorten(text, maxLength) {
    if (!text) return "";
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength - 1) + "…";
  }

  // ---- Export ----
  window.MapRenderer = MapRenderer;

})();