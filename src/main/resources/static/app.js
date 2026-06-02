const tg = window.Telegram?.WebApp;
if (tg) {
  tg.ready();
  tg.expand();
}

const state = {
  grade: "",
  q: "",
  selected: null,
  selectedButton: null,
};

const gradesEl = document.querySelector("#grades");
const recipesEl = document.querySelector("#recipes");
const recipeCountEl = document.querySelector("#recipeCount");
const searchEl = document.querySelector("#search");
const countEl = document.querySelector("#count");
const treeEl = document.querySelector("#tree");
const treeTitleEl = document.querySelector("#treeTitle");

async function api(path) {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function gradeLabel(grade) {
  return grade || "NG";
}

async function loadGrades() {
  const grades = await api("/api/craft/grades");
  gradesEl.innerHTML = "";
  const all = document.createElement("button");
  all.className = "grade active";
  all.textContent = "All";
  all.addEventListener("click", () => selectGrade("", all));
  gradesEl.append(all);

  for (const group of grades) {
    const button = document.createElement("button");
    button.className = "grade";
    button.textContent = gradeLabel(group.grade);
    button.title = `${group.count} recipes`;
    button.addEventListener("click", () => selectGrade(group.grade, button));
    gradesEl.append(button);
  }
}

function selectGrade(grade, button) {
  state.grade = grade;
  document.querySelectorAll(".grade").forEach(el => el.classList.remove("active"));
  button.classList.add("active");
  loadRecipes();
}

let searchTimer = null;
searchEl.addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    state.q = searchEl.value;
    loadRecipes();
  }, 180);
});

countEl.addEventListener("input", () => {
  if (state.selected) loadTree(state.selected);
});

async function loadRecipes() {
  const params = new URLSearchParams({ grade: state.grade, q: state.q });
  const recipes = await api(`/api/craft/recipes?${params}`);
  recipeCountEl.textContent = recipes.length;
  recipesEl.innerHTML = "";
  for (const card of recipes) {
    const button = document.createElement("button");
    button.className = "recipe-card";
    button.innerHTML = `
      <img class="icon" src="${escapeHtml(card.item.icon)}" alt="">
      <span>
        <span class="name">${escapeHtml(card.item.name)}</span>
        <span class="meta">${escapeHtml(card.item.typeMain || "Item")} ${escapeHtml(card.item.typeSub || "")} · ${card.recipe.successRate}% · lvl ${card.recipe.level}</span>
      </span>
      <span class="badge">${escapeHtml(gradeLabel(card.item.grade))}</span>
    `;
    button.addEventListener("click", () => {
      if (state.selectedButton) state.selectedButton.classList.remove("active");
      state.selectedButton = button;
      button.classList.add("active");
      state.selected = card.item.id;
      treeTitleEl.textContent = card.item.name;
      loadTree(card.item.id);
    });
    recipesEl.append(button);
  }
}

async function loadTree(itemId) {
  const count = Math.max(1, Number(countEl.value || 1));
  const tree = await api(`/api/craft/tree/${itemId}?count=${count}`);
  treeEl.className = "tree";
  treeEl.innerHTML = renderNode(tree, true);
}

function renderNode(node, root = false) {
  const leaf = !node.craftable;
  const meta = leaf
    ? `<span class="meta leaf-label">base material</span>`
    : `<span class="meta">${node.recipe.successRate}% · MP ${node.recipe.mpConsume} · recipe #${node.recipe.id}</span>`;
  const children = node.materials?.map(child => renderNode(child)).join("") ?? "";
  return `
    <div class="node ${root ? "root" : ""} ${leaf ? "leaf" : ""}">
      <div class="node-card">
        <img class="icon" src="${escapeHtml(node.item.icon)}" alt="">
        <span>
          <span class="name">${escapeHtml(node.item.name)}</span>
          ${meta}
        </span>
        <span class="count">x${node.count}</span>
      </div>
      ${children}
    </div>
  `;
}

loadGrades()
  .then(loadRecipes)
  .catch(error => {
    treeEl.className = "tree empty";
    treeEl.textContent = `Не удалось загрузить данные: ${error.message}`;
  });
