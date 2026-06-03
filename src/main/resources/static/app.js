const tg = window.Telegram?.WebApp;
const isTelegram = Boolean(tg?.initData);
const hasBackButton = Boolean(isTelegram && tg?.BackButton && tg.isVersionAtLeast?.("6.1"));
const hasHaptics = Boolean(isTelegram && tg?.HapticFeedback && tg.isVersionAtLeast?.("6.1"));
const hasMainButton = Boolean(isTelegram && tg?.MainButton);

if (tg) {
  tg.ready();
  tg.expand();
  applyTelegramTheme();
}

if (!isTelegram) {
  document.body.classList.add("browser-fallback");
}

const categories = [
  { id: "weapon", label: "Оружие" },
  { id: "armor", label: "Броня" },
  { id: "jewelry", label: "Бижутерия" },
];

const state = {
  view: "catalog",
  category: "weapon",
  grade: "",
  q: "",
  selected: null,
  selectedCard: null,
  selectedTree: null,
  recipes: [],
  collapsed: new Set(),
  inventory: loadJson("l2craft.inventory", {}),
  bookmarks: loadJson("l2craft.bookmarks", []),
  bookmarkMode: false,
  mainButtonHandler: null,
};

const els = {
  catalogView: document.querySelector("#catalogView"),
  detailView: document.querySelector("#detailView"),
  categories: document.querySelector("#categories"),
  grades: document.querySelector("#grades"),
  recipes: document.querySelector("#recipes"),
  recipeCount: document.querySelector("#recipeCount"),
  recipeListTitle: document.querySelector("#recipeListTitle"),
  search: document.querySelector("#search"),
  count: document.querySelector("#count"),
  tree: document.querySelector("#tree"),
  treeTitle: document.querySelector("#treeTitle"),
  treeMeta: document.querySelector("#treeMeta"),
  selectedIcon: document.querySelector("#selectedIcon"),
  bookmarkToggle: document.querySelector("#bookmarkToggle"),
  bookmarkView: document.querySelector("#bookmarkView"),
  shortageList: document.querySelector("#shortageList"),
  ledgerMeta: document.querySelector("#ledgerMeta"),
  sendMissing: document.querySelector("#sendMissing"),
  fallbackBack: document.querySelector("#fallbackBack"),
};

async function api(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

function loadJson(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key)) ?? fallback;
  } catch {
    return fallback;
  }
}

function saveJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function applyTelegramTheme() {
  const theme = tg?.themeParams ?? {};
  const root = document.documentElement;
  for (const [key, value] of Object.entries(theme)) {
    if (!value) continue;
    const cssName = `--tg-theme-${key.replaceAll("_", "-")}`;
    root.style.setProperty(cssName, value);
  }
}

function haptic(type = "selection") {
  if (!hasHaptics) return;
  try {
    if (type === "success") tg?.HapticFeedback?.notificationOccurred("success");
    else if (type === "error") tg?.HapticFeedback?.notificationOccurred("error");
    else if (type === "light") tg?.HapticFeedback?.impactOccurred("light");
    else tg?.HapticFeedback?.selectionChanged();
  } catch {
    // Telegram haptics are best-effort only.
  }
}

function gradeLabel(grade) {
  return grade || "NG";
}

function initControls() {
  els.categories.innerHTML = categories.map(category => `
    <button class="category ${category.id === state.category ? "active" : ""}" data-category="${category.id}" type="button">
      ${category.label}
    </button>
  `).join("");

  els.categories.addEventListener("click", event => {
    const button = event.target.closest("[data-category]");
    if (!button || button.dataset.category === state.category) return;
    state.category = button.dataset.category;
    state.grade = "";
    state.bookmarkMode = false;
    state.selectedCard = null;
    state.selected = null;
    state.selectedTree = null;
    haptic();
    syncBookmarkMode();
    loadGrades().then(loadRecipes);
  });

  els.bookmarkView.addEventListener("click", () => {
    state.bookmarkMode = !state.bookmarkMode;
    haptic("light");
    syncBookmarkMode();
    showCatalog();
    renderRecipes();
  });

  els.bookmarkToggle.addEventListener("click", () => {
    if (!state.selectedCard) return;
    toggleBookmark(state.selectedCard);
  });

  let searchTimer = null;
  els.search.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.q = els.search.value.trim();
      loadRecipes();
    }, 160);
  });

  els.count.addEventListener("input", () => {
    if (state.selected) loadTree(state.selected);
  });

  els.tree.addEventListener("click", event => {
    const toggle = event.target.closest("[data-collapse]");
    if (!toggle) return;
    const key = toggle.dataset.collapse;
    state.collapsed.has(key) ? state.collapsed.delete(key) : state.collapsed.add(key);
    haptic();
    renderTree();
  });

  els.tree.addEventListener("input", event => {
    const input = event.target.closest("[data-stock]");
    if (!input) return;
    const id = input.dataset.stock;
    const value = Math.max(0, Number(input.value || 0));
    if (value > 0) state.inventory[id] = value;
    else delete state.inventory[id];
    saveJson("l2craft.inventory", state.inventory);
    renderShortages();
  });

  els.sendMissing.addEventListener("click", sendMissingToTelegram);
  els.fallbackBack.addEventListener("click", showCatalog);

  if (hasBackButton) {
    tg.BackButton.onClick(showCatalog);
  }
}

async function loadGrades() {
  const params = new URLSearchParams({ category: state.category });
  const grades = await api(`/api/craft/grades?${params}`);
  els.grades.innerHTML = "";
  const all = document.createElement("button");
  all.className = `grade ${state.grade === "" ? "active" : ""}`;
  all.textContent = "All";
  all.type = "button";
  all.addEventListener("click", () => selectGrade(""));
  els.grades.append(all);

  for (const group of grades) {
    const button = document.createElement("button");
    button.className = `grade ${group.grade === state.grade ? "active" : ""}`;
    button.textContent = gradeLabel(group.grade);
    button.title = `${group.count} recipes`;
    button.type = "button";
    button.addEventListener("click", () => selectGrade(group.grade));
    els.grades.append(button);
  }
}

function selectGrade(grade) {
  if (state.grade === grade) return;
  state.grade = grade;
  haptic();
  loadRecipes();
  loadGrades();
}

async function loadRecipes() {
  const params = new URLSearchParams({ category: state.category, grade: state.grade, q: state.q });
  state.recipes = await api(`/api/craft/recipes?${params}`);
  renderRecipes();
}

function renderRecipes() {
  const source = state.bookmarkMode ? state.bookmarks : state.recipes;
  const visible = state.bookmarkMode
    ? source.filter(card => card.category === state.category)
    : source;
  els.recipeListTitle.textContent = state.bookmarkMode ? "Закладки" : "Рецепты";
  els.recipeCount.textContent = visible.length;
  els.recipes.innerHTML = "";

  if (state.bookmarkMode && visible.length === 0) {
    els.recipes.innerHTML = `<div class="bookmarked-note">В этой категории пока нет закладок.</div>`;
    return;
  }

  if (!state.bookmarkMode && visible.length === 0) {
    els.recipes.innerHTML = `<div class="bookmarked-note">Ничего не найдено.</div>`;
    return;
  }

  for (const card of visible) {
    const button = document.createElement("button");
    button.className = `recipe-card ${state.selected === card.item.id ? "active" : ""}`;
    button.type = "button";
    button.innerHTML = `
      <img class="icon" src="${escapeHtml(card.item.icon)}" alt="${escapeHtml(card.item.name)}">
      <span>
        <span class="name">${escapeHtml(card.item.name)}</span>
        <span class="meta">${escapeHtml(card.item.typeSub || card.item.typeMain)} · ${card.recipe.successRate}% · lvl ${card.recipe.level}</span>
      </span>
      <span class="badge">${escapeHtml(gradeLabel(card.item.grade))}</span>
    `;
    button.addEventListener("click", () => selectRecipe(card));
    els.recipes.append(button);
  }
}

function selectRecipe(card) {
  state.selectedCard = card;
  state.selected = card.item.id;
  state.collapsed.clear();
  els.treeTitle.textContent = card.item.name;
  els.treeMeta.textContent = `${gradeLabel(card.item.grade)} · ${card.item.typeSub || card.item.typeMain || "Item"}`;
  els.selectedIcon.src = card.item.icon;
  els.selectedIcon.alt = card.item.name;
  syncBookmarkButton();
  renderRecipes();
  showDetail();
  haptic("light");
  loadTree(card.item.id);
}

function showCatalog() {
  state.view = "catalog";
  els.catalogView.classList.add("active");
  els.detailView.classList.remove("active");
  if (hasBackButton) tg.BackButton.hide();
  configureMainButton([]);
  window.scrollTo({ top: 0, behavior: "instant" });
}

function showDetail() {
  state.view = "detail";
  els.catalogView.classList.remove("active");
  els.detailView.classList.add("active");
  if (hasBackButton) tg.BackButton.show();
  window.scrollTo({ top: 0, behavior: "instant" });
  renderShortages();
}

async function loadTree(itemId) {
  const count = Math.max(1, Number(els.count.value || 1));
  state.selectedTree = await api(`/api/craft/tree/${itemId}?count=${count}`);
  renderTree();
}

function renderTree() {
  if (!state.selectedTree) {
    els.tree.className = "tree empty";
    els.tree.textContent = "Выберите предмет в каталоге";
    renderShortages();
    return;
  }
  els.tree.className = "tree";
  els.tree.innerHTML = renderNode(state.selectedTree, "root", true);
  renderShortages();
}

function renderNode(node, key, root = false) {
  const hasChildren = Boolean(node.craftable && node.materials?.length);
  const collapsed = state.collapsed.has(key);
  const meta = node.craftable
    ? `${node.recipe.successRate}% · MP ${node.recipe.mpConsume} · output x${node.recipe.productCount}`
    : "базовый ресурс";
  const stock = !root ? Number(state.inventory[node.item.id] || 0) : "";
  const children = hasChildren
    ? `<div class="node-children ${collapsed ? "collapsed" : ""}">
        ${node.materials.map((child, index) => renderNode(child, `${key}.${child.item.id}.${index}`)).join("")}
       </div>`
    : "";

  return `
    <div class="node-shell ${root ? "root" : ""} ${node.craftable ? "craftable" : "leaf"}">
      <div class="node-card">
        <img class="icon" src="${escapeHtml(node.item.icon)}" alt="${escapeHtml(node.item.name)}">
        <span>
          <span class="name">${escapeHtml(node.item.name)}</span>
          <span class="meta ${node.craftable ? "" : "leaf-label"}">${escapeHtml(meta)}</span>
        </span>
        <span class="count">x${formatCount(node.count)}</span>
        ${hasChildren ? `<button class="collapse-button" type="button" data-collapse="${escapeHtml(key)}" aria-label="${collapsed ? "Развернуть" : "Свернуть"}">${collapsed ? "+" : "−"}</button>` : `<span></span>`}
        ${root ? "" : `<input class="stock-input" data-stock="${node.item.id}" type="number" min="0" inputmode="numeric" placeholder="Есть у меня" value="${stock || ""}" aria-label="Есть ${escapeHtml(node.item.name)}">`}
      </div>
      ${children}
    </div>
  `;
}

function renderShortages() {
  if (!state.selectedTree) {
    els.shortageList.className = "shortage-list empty";
    els.shortageList.textContent = "Здесь появится список ресурсов.";
    els.ledgerMeta.textContent = "0";
    els.sendMissing.disabled = true;
    configureMainButton([]);
    return;
  }

  const shortages = collectShortages(state.selectedTree);
  els.ledgerMeta.textContent = formatCount(shortages.length);
  els.sendMissing.disabled = shortages.length === 0;

  if (shortages.length === 0) {
    els.shortageList.className = "shortage-list empty";
    els.shortageList.textContent = "Все ресурсы закрыты.";
    configureMainButton(shortages);
    return;
  }

  els.shortageList.className = "shortage-list";
  els.shortageList.innerHTML = shortages.map(row => `
    <div class="shortage-row">
      <img src="${escapeHtml(row.item.icon)}" alt="${escapeHtml(row.item.name)}">
      <span>
        <span class="name">${escapeHtml(row.item.name)}</span>
        <span class="meta">нужно ${formatCount(row.required)} · есть ${formatCount(row.have)}</span>
      </span>
      <span class="shortage-count">-${formatCount(row.missing)}</span>
    </div>
  `).join("");
  configureMainButton(shortages);
}

function collectShortages(root) {
  const result = new Map();
  const stock = new Map(Object.entries(state.inventory).map(([id, value]) => [Number(id), Number(value || 0)]));
  walkNeed(root, root.count, true, result, stock);
  return [...result.values()].sort((a, b) => b.missing - a.missing || a.item.name.localeCompare(b.item.name));
}

function walkNeed(node, required, root, result, stock) {
  const available = root ? 0 : Number(stock.get(node.item.id) || 0);
  const used = Math.min(required, available);
  if (!root && used > 0) {
    stock.set(node.item.id, available - used);
  }
  const remaining = required - used;
  if (remaining <= 0) return;

  if (!node.craftable || !node.materials?.length) {
    const existing = result.get(node.item.id) ?? { item: node.item, required: 0, have: 0, missing: 0 };
    existing.required += required;
    existing.have += used;
    existing.missing += remaining;
    result.set(node.item.id, existing);
    return;
  }

  const factor = remaining / node.count;
  for (const child of node.materials) {
    walkNeed(child, child.count * factor, false, result, stock);
  }
}

function configureMainButton(shortages) {
  const visible = state.view === "detail" && shortages.length > 0 && isTelegram;
  if (!hasMainButton) return;

  if (state.mainButtonHandler) {
    tg.MainButton.offClick(state.mainButtonHandler);
    state.mainButtonHandler = null;
  }

  if (!visible) {
    tg.MainButton.hide();
    return;
  }

  state.mainButtonHandler = sendMissingToTelegram;
  tg.MainButton.setText("Отправить список ресурсов");
  tg.MainButton.enable();
  tg.MainButton.show();
  tg.MainButton.onClick(state.mainButtonHandler);
}

async function sendMissingToTelegram() {
  const shortages = state.selectedTree ? collectShortages(state.selectedTree) : [];
  const title = state.selectedCard?.item?.name ?? "Craft";
  const text = [`Недостающие ресурсы для ${title}:`, ...shortages.map(row => `- ${row.item.name}: ${formatCount(row.missing)}`)].join("\n");

  if (!isTelegram) {
    await navigator.clipboard?.writeText(text).catch(() => {});
    els.sendMissing.textContent = "Скопировано";
    setTimeout(() => els.sendMissing.textContent = "Отправить", 1400);
    return;
  }

  els.sendMissing.disabled = true;
  tg?.MainButton?.showProgress();
  tg?.MainButton?.disable();
  try {
    await api("/api/craft/telegram/missing", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ initData: tg.initData, text }),
    });
    haptic("success");
    els.sendMissing.textContent = "Отправлено";
  } catch {
    haptic("error");
    els.sendMissing.textContent = "Ошибка";
  } finally {
    tg?.MainButton?.hideProgress();
    tg?.MainButton?.enable();
    setTimeout(() => {
      els.sendMissing.textContent = "Отправить";
      renderShortages();
    }, 1400);
  }
}

function toggleBookmark(card) {
  const id = card.item.id;
  const exists = state.bookmarks.some(item => item.item.id === id);
  state.bookmarks = exists
    ? state.bookmarks.filter(item => item.item.id !== id)
    : [card, ...state.bookmarks].slice(0, 80);
  saveJson("l2craft.bookmarks", state.bookmarks);
  haptic("light");
  syncBookmarkButton();
  renderRecipes();
}

function syncBookmarkButton() {
  const hasSelection = Boolean(state.selectedCard);
  els.bookmarkToggle.disabled = !hasSelection;
  const active = hasSelection && state.bookmarks.some(item => item.item.id === state.selectedCard.item.id);
  els.bookmarkToggle.classList.toggle("active", active);
  els.bookmarkToggle.querySelector("span").textContent = active ? "★" : "☆";
  els.bookmarkToggle.setAttribute("aria-label", active ? "Удалить из закладок" : "Добавить в закладки");
}

function syncBookmarkMode() {
  els.bookmarkView.classList.toggle("active", state.bookmarkMode);
  els.bookmarkView.setAttribute("aria-pressed", String(state.bookmarkMode));
  els.bookmarkView.querySelector("span").textContent = state.bookmarkMode ? "★" : "☆";
}

function formatCount(value) {
  const rounded = Math.ceil(Number(value || 0));
  return new Intl.NumberFormat("ru-RU").format(rounded);
}

initControls();
syncBookmarkMode();
showCatalog();
loadGrades()
  .then(loadRecipes)
  .catch(error => {
    els.recipes.innerHTML = `<div class="bookmarked-note">Не удалось загрузить данные: ${escapeHtml(error.message)}</div>`;
  });
