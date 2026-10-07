const API_URL = "/api/todos";
const PAGE_SIZE = 8;

const state = {
    date: "",
    page: 0,
    last: true,
};

const elements = {
    form: document.querySelector("#todoForm"),
    title: document.querySelector("#title"),
    memo: document.querySelector("#memo"),
    todoDate: document.querySelector("#todoDate"),
    filterDate: document.querySelector("#filterDate"),
    list: document.querySelector("#todoList"),
    template: document.querySelector("#todoTemplate"),
    notice: document.querySelector("#notice"),
    health: document.querySelector("#healthBadge"),
    pageLabel: document.querySelector("#pageLabel"),
    previous: document.querySelector("#previousButton"),
    next: document.querySelector("#nextButton"),
};

const localDate = () => {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60_000;
    return new Date(now.getTime() - offset).toISOString().slice(0, 10);
};

const showNotice = (message = "", error = false) => {
    elements.notice.textContent = message;
    elements.notice.classList.toggle("error", error);
};

const request = async (url, options = {}) => {
    const response = await fetch(url, {
        headers: { "Content-Type": "application/json", ...options.headers },
        ...options,
    });

    const body = response.status === 204 ? null : await response.json();
    if (!response.ok) {
        const fieldMessage = body?.errors?.[0]?.reason;
        throw new Error(fieldMessage || body?.message || "요청을 처리하지 못했습니다.");
    }
    return body;
};

const loadTodos = async () => {
    showNotice("목록을 불러오는 중입니다.");
    const params = new URLSearchParams({ page: state.page, size: PAGE_SIZE });
    if (state.date) params.set("date", state.date);

    try {
        const response = await request(`${API_URL}?${params}`);
        renderTodos(response.data);
        showNotice(`${response.data.totalElements}개의 할 일이 있습니다.`);
    } catch (error) {
        elements.list.innerHTML = "";
        showNotice(error.message, true);
    }
};

const renderTodos = (page) => {
    elements.list.innerHTML = "";

    if (page.content.length === 0) {
        elements.list.innerHTML = '<div class="empty-state">아직 등록된 할 일이 없습니다.</div>';
    }

    page.content.forEach((todo) => {
        const fragment = elements.template.content.cloneNode(true);
        const item = fragment.querySelector(".todo-item");
        item.classList.toggle("completed", todo.completed);
        fragment.querySelector(".todo-title").textContent = todo.title;
        fragment.querySelector(".todo-memo").textContent = todo.memo || "";
        fragment.querySelector(".todo-date").textContent = todo.todoDate;
        fragment.querySelector(".complete-button").addEventListener("click", () => changeCompletion(todo));
        fragment.querySelector(".edit-button").addEventListener("click", () => editTodo(todo));
        fragment.querySelector(".delete-button").addEventListener("click", () => deleteTodo(todo));
        elements.list.appendChild(fragment);
    });

    state.last = page.last;
    elements.pageLabel.textContent = `${page.page + 1} / ${Math.max(page.totalPages, 1)}`;
    elements.previous.disabled = page.first;
    elements.next.disabled = page.last;
};

const createTodo = async (event) => {
    event.preventDefault();
    try {
        await request(API_URL, {
            method: "POST",
            body: JSON.stringify({
                title: elements.title.value.trim(),
                memo: elements.memo.value.trim() || null,
                todoDate: elements.todoDate.value,
            }),
        });
        elements.form.reset();
        elements.todoDate.value = localDate();
        state.page = 0;
        showNotice("할 일을 추가했습니다.");
        await loadTodos();
    } catch (error) {
        showNotice(error.message, true);
    }
};

const changeCompletion = async (todo) => {
    try {
        await request(`${API_URL}/${todo.id}/completion`, {
            method: "PATCH",
            body: JSON.stringify({ completed: !todo.completed }),
        });
        await loadTodos();
    } catch (error) {
        showNotice(error.message, true);
    }
};

const editTodo = async (todo) => {
    const title = window.prompt("수정할 제목을 입력하세요.", todo.title);
    if (title === null) return;
    if (!title.trim()) {
        showNotice("제목은 비워둘 수 없습니다.", true);
        return;
    }

    try {
        await request(`${API_URL}/${todo.id}`, {
            method: "PATCH",
            body: JSON.stringify({ title: title.trim() }),
        });
        await loadTodos();
    } catch (error) {
        showNotice(error.message, true);
    }
};

const deleteTodo = async (todo) => {
    if (!window.confirm(`“${todo.title}”을 삭제할까요?`)) return;
    try {
        await request(`${API_URL}/${todo.id}`, { method: "DELETE" });
        await loadTodos();
    } catch (error) {
        showNotice(error.message, true);
    }
};

const checkHealth = async () => {
    try {
        const response = await fetch("/actuator/health");
        const data = await response.json();
        const up = response.ok && data.status === "UP";
        elements.health.className = `health-badge ${up ? "health-up" : "health-down"}`;
        elements.health.lastElementChild.textContent = up ? "서버 정상" : "서버 점검 필요";
    } catch {
        elements.health.className = "health-badge health-down";
        elements.health.lastElementChild.textContent = "서버 연결 실패";
    }
};

elements.form.addEventListener("submit", createTodo);
elements.filterDate.addEventListener("change", (event) => {
    state.date = event.target.value;
    state.page = 0;
    loadTodos();
});
document.querySelector("#todayButton").addEventListener("click", () => {
    state.date = localDate();
    state.page = 0;
    elements.filterDate.value = state.date;
    loadTodos();
});
document.querySelector("#allButton").addEventListener("click", () => {
    state.date = "";
    state.page = 0;
    elements.filterDate.value = "";
    loadTodos();
});
elements.previous.addEventListener("click", () => {
    if (state.page > 0) {
        state.page -= 1;
        loadTodos();
    }
});
elements.next.addEventListener("click", () => {
    if (!state.last) {
        state.page += 1;
        loadTodos();
    }
});

const today = localDate();
elements.todoDate.value = today;
document.querySelector("#todayLabel").textContent = new Intl.DateTimeFormat("ko-KR", {
    month: "long", day: "numeric", weekday: "short",
}).format(new Date(`${today}T00:00:00`));

checkHealth();
loadTodos();
