'use strict';

/**
 * Access JWT хранится только в оперативной памяти вкладки.
 * Refresh-токен передаётся браузером через защищённый HttpOnly cookie.
 */
const state = {
    accessToken: null,
    user: null,
    customers: [],
    orders: { page: 0, size: 25, totalPages: 0, totalElements: 0, query: '', sort: 'created,desc', content: [] },
    selectedOrderIds: new Set(),
    activeOrderForNotes: null,
    activeNotes: [],
    refreshPromise: null
};

const $ = (id) => document.getElementById(id);
const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
}[ch]));

const roles = () => new Set((state.user?.roles || []).map(String));
const isAdmin = () => roles().has('ADMIN');
const canEdit = () => roles().has('PHARMACIST') || isAdmin();

function roleLabel(value) {
    return ({
        ADMIN: 'Администратор',
        PHARMACIST: 'Фармацевт-диспетчер',
        VIEWER: 'Наблюдатель (курьер)'
    })[value] || value;
}

function statusLabel(value) {
    return ({
        CREATED: 'Создан',
        CONFIRMED: 'Подтверждён',
        ASSEMBLING: 'Собирается в аптеке',
        IN_DELIVERY: 'В доставке у курьера',
        DELIVERED: 'Доставлен',
        CANCELLED: 'Отменён'
    })[value] || value;
}

function paymentLabel(value) {
    return ({
        CASH: 'Наличными курьеру',
        CARD_ON_DELIVERY: 'Картой курьеру',
        CARD_ONLINE: 'Картой онлайн',
        SBP: 'СБП'
    })[value] || value;
}

function priorityLabel(value) {
    return ({
        NOT_APPLICABLE: 'Не применяется',
        LOW: 'Низкий',
        MEDIUM: 'Средний',
        HIGH: 'Высокий',
        CRITICAL: 'Критический'
    })[value] || value;
}

function fieldLabel(value) {
    return ({
        orderNumber: 'номер заказа',
        customerId: 'клиент',
        medicationSummary: 'состав заказа',
        deliveryAddress: 'адрес доставки',
        contactPhone: 'контактный телефон',
        totalAmount: 'сумма заказа',
        status: 'статус',
        paymentMethod: 'способ оплаты',
        prescriptionRequired: 'требуется рецепт',
        prescriptionNumber: 'номер рецепта',
        deliveryNotes: 'примечание к доставке',
        tags: 'теги',
        code: 'код клиента',
        fullName: 'ФИО клиента',
        phone: 'телефон',
        email: 'email',
        defaultAddress: 'адрес по умолчанию',
        version: 'версия'
    })[value] || value;
}

function lowerFirst(value) {
    const text = String(value ?? '');
    return text ? text.charAt(0).toLocaleLowerCase('ru-RU') + text.slice(1) : text;
}

function normalizeAfterColon(value) {
    return String(value ?? '').replace(/:\s*([А-ЯЁA-Z])/g,
        (_, letter) => `: ${letter.toLocaleLowerCase('ru-RU')}`);
}

function formatRussianPhone(value) {
    let digits = String(value ?? '').replace(/\D/g, '');
    if (digits.startsWith('8')) digits = `7${digits.slice(1)}`;
    if (digits.startsWith('7')) digits = digits.slice(1);
    digits = digits.slice(0, 10);

    let result = '+7-';
    if (!digits.length) return result;
    result += digits.slice(0, 3);
    if (digits.length > 3) result += `-${digits.slice(3, 6)}`;
    if (digits.length > 6) result += `-${digits.slice(6, 8)}`;
    if (digits.length > 8) result += `-${digits.slice(8, 10)}`;
    return result;
}

function bindPhoneMask(input) {
    if (!input) return;
    input.addEventListener('focus', () => {
        if (!input.value) input.value = '+7-';
    });
    input.addEventListener('keydown', event => {
        const allowedKeys = new Set([
            'Backspace', 'Delete', 'ArrowLeft', 'ArrowRight',
            'Home', 'End', 'Tab', 'Enter'
        ]);
        if (event.ctrlKey || event.metaKey || allowedKeys.has(event.key) || /^\d$/.test(event.key)) {
            return;
        }
        event.preventDefault();
    });
    input.addEventListener('input', () => {
        input.value = formatRussianPhone(input.value);
    });
    input.addEventListener('blur', () => {
        if (input.value === '+7-') input.value = '';
    });
}

function toast(message, error = false) {
    const node = $('toast');
    node.textContent = message;
    node.className = `toast show${error ? ' error' : ''}`;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.className = 'toast', 3800);
}

function showFormError(form, message) {
    const box = form.querySelector('[data-form-error]');
    if (!box) {
        toast(message, true);
        return;
    }
    box.textContent = message;
    box.hidden = false;
}

function clearFormError(form) {
    const box = form.querySelector('[data-form-error]');
    if (!box) return;
    box.textContent = '';
    box.hidden = true;
}

async function parseError(response) {
    try {
        const body = await response.json();
        const fields = body.validationErrors
            ? Object.entries(body.validationErrors)
                .map(([key, value]) => `${fieldLabel(key)}: ${lowerFirst(value)}`)
                .join('; ')
            : '';
        const message = `${body.message || response.statusText}${fields ? ` — ${fields}` : ''}${body.correlationId ? ` [${body.correlationId}]` : ''}`;
        return normalizeAfterColon(message);
    } catch (_) {
        return `${response.status} ${response.statusText}`;
    }
}

async function api(path, options = {}, retry = true) {
    const headers = new Headers(options.headers || {});
    if (state.accessToken) headers.set('Authorization', `Bearer ${state.accessToken}`);
    if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }
    const response = await fetch(`/api${path}`, { ...options, headers, credentials: 'include' });
    if (response.status === 401 && retry) {
        const refreshed = await refreshAccessToken();
        if (refreshed) return api(path, options, false);
    }
    if (!response.ok) throw new Error(await parseError(response));
    if (response.status === 204) return null;
    const type = response.headers.get('content-type') || '';
    return type.includes('application/json') ? response.json() : response;
}

async function refreshAccessToken() {
    if (!state.refreshPromise) {
        state.refreshPromise = fetch('/api/auth/refresh', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: '{}'
        }).then(async response => {
            if (!response.ok) return false;
            const result = await response.json();
            state.accessToken = result.accessToken;
            state.user = result.user;
            return true;
        }).finally(() => state.refreshPromise = null);
    }
    return state.refreshPromise;
}

async function boot() {
    bindEvents();
    if (await refreshAccessToken()) {
        await enterApplication();
    } else {
        showLogin();
    }
}

function showLogin() {
    $('app-view').hidden = true;
    $('login-view').hidden = false;
    state.accessToken = null;
    state.user = null;
}

async function enterApplication() {
    $('login-view').hidden = true;
    $('app-view').hidden = false;
    applyRoleVisibility();
    $('current-user').innerHTML = `<strong>${escapeHtml(state.user.displayName)}</strong><br>${escapeHtml(state.user.email)}<br>${escapeHtml(state.user.roles.map(roleLabel).join(', '))}`;

    if (state.user.passwordChangeRequired) {
        toast('Необходимо сменить временный пароль после создания учётной записи', true);
        const changed = await openPasswordDialog(true);
        if (!changed) return;
    }

    try {
        await Promise.all([loadCustomers(), loadDashboard()]);
    } catch (error) {
        console.error('[Medicine Delivery] Initial data loading failed', error);
        toast(`Вход выполнен, но часть данных не загрузилась: ${error.message}`, true);
    }
}

function applyRoleVisibility() {
    document.querySelectorAll('.admin-only').forEach(node => node.hidden = !isAdmin());
    document.querySelectorAll('.pharmacist-only').forEach(node => node.hidden = !canEdit());
}

function switchTab(name) {
    document.querySelectorAll('.tab').forEach(tab => tab.classList.toggle('active', tab.id === `tab-${name}`));
    document.querySelectorAll('.nav-item').forEach(button => button.classList.toggle('active', button.dataset.tab === name));
    const titles = {
        dashboard: ['Аналитика', 'Сводные показатели службы доставки медикаментов'],
        orders: ['Заказы лекарств', 'Умный поиск, фильтрация, рецептурный контроль и массовые операции'],
        customers: ['Клиенты аптеки', 'Справочник получателей доставки и адресов по умолчанию'],
        users: ['Пользователи', 'Управление сотрудниками, ролями и временными паролями'],
        audit: ['Журнал аудита', 'Прослеживаемость входов и изменений с Correlation ID']
    };
    [$('page-title').textContent, $('page-subtitle').textContent] = titles[name];
    if (name === 'orders') loadOrders();
    if (name === 'customers') renderCustomers();
    if (name === 'users' && isAdmin()) loadUsers();
    if (name === 'audit' && isAdmin()) loadAudit();
    if (name === 'dashboard') loadDashboard();
}

function formatMoney(value) {
    if (value === null || value === undefined) return '—';
    return new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value)) + ' ₽';
}

async function loadDashboard() {
    try {
        const data = await api('/analytics/summary');
        const metrics = [
            ['Всего заказов', data.total],
            ['Активные', data.active],
            ['В архиве', data.archived],
            ['По рецепту', data.prescriptionRequiredCount],
            ['Средний чек', formatMoney(data.averageAmount)],
            ['Выручка доставленных', formatMoney(data.deliveredRevenue)]
        ];
        $('metric-grid').innerHTML = metrics.map(([name, value]) =>
            `<article class="metric card"><span class="muted">${escapeHtml(name)}</span><strong>${escapeHtml(value)}</strong></article>`
        ).join('');
        renderBars('chart-customers', data.byCustomer);
        renderBars('chart-status', data.byStatus);
        renderBars('chart-priority', data.byPriority);
    } catch (error) {
        toast(error.message, true);
    }
}

function renderBars(id, values) {
    const max = Math.max(1, ...(values || []).map(item => item.count));
    const displayName = value => {
        if (id === 'chart-status') return statusLabel(value);
        if (id === 'chart-priority') return priorityLabel(value);
        return value;
    };
    $(id).innerHTML = (values && values.length)
        ? values.map(item => `<div class="bar-row"><span>${escapeHtml(displayName(item.name))}</span><progress value="${item.count}" max="${max}"></progress><strong>${item.count}</strong></div>`).join('')
        : '<p class="muted">Нет данных</p>';
}

async function loadCustomers() {
    try {
        state.customers = await api('/customers?activeOnly=false');
        renderCustomers();
    } catch (error) {
        toast(error.message, true);
    }
}

function renderCustomers() {
    const body = $('customers-body');
    if (!body) return;
    body.innerHTML = state.customers.map(c => `<tr>
        <td><strong>${escapeHtml(c.code)}</strong></td>
        <td>${escapeHtml(c.fullName)}</td>
        <td>${escapeHtml(c.phone)}</td>
        <td>${escapeHtml(c.email || '—')}</td>
        <td>${escapeHtml(c.defaultAddress)}</td>
        <td><span class="badge ${c.active ? 'success' : ''}">${c.active ? 'Да' : 'Отключён'}</span></td>
        <td class="admin-only"><div class="actions">
            <button class="secondary" data-customer-edit="${c.id}">Изменить</button>
            <button class="secondary" data-customer-toggle="${c.id}">${c.active ? 'Отключить' : 'Включить'}</button>
        </div></td>
    </tr>`).join('') || '<tr><td colspan="7" class="muted">Справочник клиентов пуст</td></tr>';
    applyRoleVisibility();
}

async function loadOrders(page = state.orders.page) {
    try {
        const q = encodeURIComponent(state.orders.query);
        const sort = encodeURIComponent(state.orders.sort);
        const result = await api(`/orders?q=${q}&page=${page}&size=${state.orders.size}&sort=${sort}`);
        state.orders = { ...state.orders, ...result };
        state.selectedOrderIds.clear();
        renderOrders();
    } catch (error) {
        toast(error.message, true);
    }
}

function renderOrders() {
    $('orders-body').innerHTML = state.orders.content.map(order => `<tr class="${order.archived ? 'archived' : ''}">
        <td class="pharmacist-only"><input type="checkbox" data-order-select="${order.id}"></td>
        <td>
            <strong>${escapeHtml(order.orderNumber)}</strong><br>
            <small>${escapeHtml(order.medicationSummary)}</small>
        </td>
        <td>
            <strong>${escapeHtml(order.customerName || 'Без клиента')}</strong>
            ${order.customerCode ? `<span class="badge">${escapeHtml(order.customerCode)}</span>` : ''}<br>
            <small class="muted">${escapeHtml(order.deliveryAddress)} · ${escapeHtml(order.contactPhone)}</small>
        </td>
        <td><span class="badge status-${order.status}">${escapeHtml(statusLabel(order.status))}</span></td>
        <td><small>${escapeHtml(paymentLabel(order.paymentMethod))}</small></td>
        <td><strong>${escapeHtml(formatMoney(order.totalAmount))}</strong></td>
        <td>${order.prescriptionRequired
            ? `<span class="badge warning" title="${escapeHtml(order.prescriptionNumber || '')}">По рецепту (${escapeHtml(order.prescriptionNumber || '—')})</span>`
            : 'Без рецепта'}</td>
        <td><span class="badge priority-${order.priorityLevel}">${escapeHtml(priorityLabel(order.priorityLevel))}</span></td>
        <td>${(order.tags || []).map(tag => `<span class="badge">${escapeHtml(tag)}</span>`).join(' ') || '—'}</td>
        <td><div class="actions">
            <button class="secondary" data-order-view="${order.id}">Карточка</button>
            ${canEdit() ? `
                <button class="secondary" data-order-edit="${order.id}">Изменить</button>
                <button class="secondary" data-order-notes="${order.id}">Заметки</button>
                <button class="danger-outline" data-order-archive="${order.id}">${order.archived ? 'Восстановить' : 'В архив'}</button>
            ` : ''}
            ${isAdmin() && order.archived ? `<button class="danger-outline" data-order-delete-perm="${order.id}" title="Окончательное удаление">Удалить навсегда</button>` : ''}
        </div></td>
    </tr>`).join('') || '<tr><td colspan="10" class="muted">Заказы по заданному фильтру не найдены</td></tr>';

    $('orders-page').textContent = `Страница ${state.orders.page + 1} из ${Math.max(1, state.orders.totalPages)} · всего записей: ${state.orders.totalElements}`;
    $('orders-prev').disabled = state.orders.first;
    $('orders-next').disabled = state.orders.last;
    $('select-all-orders').checked = false;
    applyRoleVisibility();
}

function statusOptions(selected = 'CREATED') {
    return ['CREATED', 'CONFIRMED', 'ASSEMBLING', 'IN_DELIVERY', 'DELIVERED', 'CANCELLED']
        .map(v => `<option value="${v}" ${v === selected ? 'selected' : ''}>${statusLabel(v)}</option>`).join('');
}

function paymentOptions(selected = 'CARD_ONLINE') {
    return ['CARD_ONLINE', 'SBP', 'CARD_ON_DELIVERY', 'CASH']
        .map(v => `<option value="${v}" ${v === selected ? 'selected' : ''}>${paymentLabel(v)}</option>`).join('');
}

function orderForm(order = null, readOnly = false) {
    const customerOptions = [
        '<option value="">— Без привязки к справочнику —</option>',
        ...state.customers
            .filter(c => c.active || c.id === order?.customerId)
            .map(c => `<option value="${c.id}" ${c.id === order?.customerId ? 'selected' : ''}>${escapeHtml(c.code)} — ${escapeHtml(c.fullName)}</option>`)
    ].join('');

    const metadataBlock = order ? `
        <div class="span-2 muted" style="font-size:.82rem; border-top:1px solid var(--line); padding-top:.55rem;">
            UUID: <code>${escapeHtml(order.id)}</code> ·
            Приоритет: <strong>${escapeHtml(priorityLabel(order.priorityLevel))}</strong> ·
            Версия (optimistic lock): <strong>${order.version}</strong> ·
            Создан: ${formatDate(order.createdAt)} ·
            Изменён: ${formatDate(order.updatedAt)}
            ${order.deliveredAt ? ` · Доставлен: ${formatDate(order.deliveredAt)}` : ''}
        </div>` : '';

    return `<div class="dialog-head">
        <h3>${order ? (readOnly ? 'Карточка заказа доставки лекарств' : 'Редактирование заказа') : 'Новый заказ доставки лекарств'}</h3>
        <button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button>
    </div>
    <div class="form-grid">
        <label>Номер заказа (внешний ID)
            <input name="orderNumber" maxlength="100" placeholder="Авто (ORD-YYYYMMDD-XXXX)" value="${escapeHtml(order?.orderNumber || '')}" ${readOnly ? 'disabled' : ''}>
        </label>
        <label>Клиент аптеки (справочник)
            <select name="customerId" id="order-customer-select" ${readOnly ? 'disabled' : ''}>${customerOptions}</select>
        </label>
        <label class="span-2">Состав заказа (лекарственные препараты и дозировки)
            <input name="medicationSummary" required maxlength="500" placeholder="Например: Амоксиклав 875+125 мг №14, Линекс Форте №14" value="${escapeHtml(order?.medicationSummary || '')}" ${readOnly ? 'disabled' : ''}>
        </label>
        <label class="span-2">Адрес доставки
            <input name="deliveryAddress" id="order-address-input" required maxlength="255" placeholder="г. Москва, пр-т Вернадского, д. 78, кв. 45" value="${escapeHtml(order?.deliveryAddress || '')}" ${readOnly ? 'disabled' : ''}>
        </label>
        <label>Контактный телефон получателя
            <input name="contactPhone" id="order-phone-input" type="tel" inputmode="numeric" required maxlength="16" pattern="\+7-[0-9]{3}-[0-9]{3}-[0-9]{2}-[0-9]{2}" title="Формат: +7-999-999-99-99" placeholder="+7-999-999-99-99" value="${escapeHtml(order?.contactPhone ? formatRussianPhone(order.contactPhone) : '')}" ${readOnly ? 'disabled' : ''}>
        </label>
        <label>Сумма заказа (₽)
            <input name="totalAmount" type="number" required min="0.01" max="1000000" step="0.01" inputmode="decimal" placeholder="1500.00" value="${order?.totalAmount ?? ''}" ${readOnly ? 'disabled' : ''}>
        </label>
        <label>Статус заказа
            <select name="status" ${readOnly ? 'disabled' : ''}>${statusOptions(order?.status)}</select>
        </label>
        <label>Способ оплаты
            <select name="paymentMethod" ${readOnly ? 'disabled' : ''}>${paymentOptions(order?.paymentMethod)}</select>
        </label>
        <label class="inline">
            <input name="prescriptionRequired" id="order-rx-checkbox" type="checkbox" ${order?.prescriptionRequired ? 'checked' : ''} ${readOnly ? 'disabled' : ''}>
            Требуется рецепт врача
        </label>
        <label>Номер рецепта (обязателен при рецептурном отпуске)
            <input name="prescriptionNumber" id="order-rx-number" maxlength="60" placeholder="RX-77-2026-00123" value="${escapeHtml(order?.prescriptionNumber || '')}" ${readOnly ? 'disabled' : ''}>
        </label>
        <label class="span-2">Примечание к доставке / инструкции курьеру
            <input name="deliveryNotes" maxlength="1000" placeholder="Код домофона, температурный режим, интервал доставки" value="${escapeHtml(order?.deliveryNotes || '')}" ${readOnly ? 'disabled' : ''}>
        </label>
        <label class="span-2">Теги заказа (через запятую)
            <input name="tags" maxlength="1000" placeholder="срочно, термолабильный, рецепт" value="${escapeHtml((order?.tags || []).join(', '))}" ${readOnly ? 'disabled' : ''}>
        </label>
        ${metadataBlock}
    </div>
    ${readOnly ? '' : '<div class="form-message error" data-form-error hidden></div><button class="primary" type="submit">Сохранить заказ</button>'}`;
}

async function openOrder(id = null, readOnly = false) {
    const order = id ? await api(`/orders/${id}`) : null;
    const form = $('entity-form');
    form.innerHTML = orderForm(order, readOnly);

    const phoneInput = form.elements.contactPhone;
    if (phoneInput && !readOnly) bindPhoneMask(phoneInput);

    const customerSelect = $('order-customer-select');
    if (customerSelect && !readOnly) {
        customerSelect.addEventListener('change', () => {
            const selected = state.customers.find(c => c.id === customerSelect.value);
            if (selected) {
                if (!form.elements.deliveryAddress.value.trim()) {
                    form.elements.deliveryAddress.value = selected.defaultAddress;
                }
                if (!form.elements.contactPhone.value.trim() || form.elements.contactPhone.value === '+7-') {
                    form.elements.contactPhone.value = formatRussianPhone(selected.phone);
                }
            }
        });
    }

    form.onsubmit = readOnly ? null : async event => {
        event.preventDefault();
        clearFormError(form);
        if (!form.reportValidity()) return;

        const data = new FormData(form);
        const rxRequired = data.get('prescriptionRequired') === 'on';
        const rxNumber = empty(data.get('prescriptionNumber'));
        if (rxRequired && !rxNumber) {
            showFormError(form, 'Для рецептурного заказа необходимо указать номер рецепта');
            return;
        }

        const payload = {
            orderNumber: empty(data.get('orderNumber')),
            customerId: empty(data.get('customerId')),
            medicationSummary: data.get('medicationSummary'),
            deliveryAddress: data.get('deliveryAddress'),
            contactPhone: data.get('contactPhone'),
            totalAmount: Number(data.get('totalAmount')),
            status: data.get('status'),
            paymentMethod: data.get('paymentMethod'),
            prescriptionRequired: rxRequired,
            prescriptionNumber: rxRequired ? rxNumber : null,
            deliveryNotes: empty(data.get('deliveryNotes')),
            tags: String(data.get('tags') || '').split(',').map(v => v.trim()).filter(Boolean),
            version: order?.version ?? null
        };
        try {
            await api(id ? `/orders/${id}` : '/orders', {
                method: id ? 'PUT' : 'POST',
                body: JSON.stringify(payload)
            });
            $('entity-dialog').close();
            toast(id ? 'Заказ обновлён' : 'Заказ создан');
            await Promise.all([loadOrders(), loadDashboard()]);
        } catch (error) {
            showFormError(form, error.message);
        }
    };
    $('entity-dialog').showModal();
}

async function archiveOrder(id) {
    const order = state.orders.content.find(item => item.id === id);
    const actionText = order?.archived ? 'восстановить заказ из архива' : 'переместить заказ в архив';
    if (!confirm(`Подтвердите действие: ${actionText} (${order?.orderNumber || id})?`)) return;
    try {
        await api(order.archived ? `/orders/${id}/restore` : `/orders/${id}`, {
            method: order.archived ? 'POST' : 'DELETE'
        });
        toast(order.archived ? 'Заказ восстановлен из архива' : 'Заказ перемещён в архив');
        await Promise.all([loadOrders(), loadDashboard()]);
    } catch (error) {
        toast(error.message, true);
    }
}

async function permanentDeleteOrder(id) {
    const order = state.orders.content.find(item => item.id === id);
    if (!confirm(`Окончательно удалить заказ ${order?.orderNumber || id} из БД? Операция необратима.`)) return;
    try {
        await api(`/orders/${id}/permanent`, {
            method: 'DELETE',
            headers: { 'X-Confirm-Delete': 'PERMANENTLY_DELETE' }
        });
        toast('Заказ окончательно удалён');
        await Promise.all([loadOrders(), loadDashboard()]);
    } catch (error) {
        toast(error.message, true);
    }
}

async function openNotes(orderId) {
    state.activeOrderForNotes = orderId;
    const order = state.orders.content.find(o => o.id === orderId);
    $('notes-dialog-title').textContent = `Заметки к заказу ${order ? order.orderNumber : ''}`;
    resetNoteForm();
    await renderNotes();
    $('notes-dialog').showModal();
}

function resetNoteForm() {
    $('note-edit-id').value = '';
    $('note-edit-version').value = '';
    $('note-body').value = '';
    $('note-pinned').checked = false;
    $('note-submit-btn').textContent = 'Добавить заметку';
    $('note-cancel-edit').hidden = true;
}

async function renderNotes() {
    try {
        const list = await api(`/orders/${state.activeOrderForNotes}/notes`);
        state.activeNotes = list;
        $('notes-list').innerHTML = list.map(note => `<article class="note">
            <div class="note-meta">
                <span><strong>${escapeHtml(note.authorName)}</strong> · ${formatDate(note.createdAt)} (v${note.version})</span>
                <span>${note.pinned ? '📌 Закреплена' : ''}</span>
            </div>
            <p>${escapeHtml(note.body).replace(/\n/g, '<br>')}</p>
            <div class="actions">
                <button class="secondary" type="button" data-note-edit="${note.id}">Изменить</button>
                <button class="danger-outline" type="button" data-note-delete="${note.id}">Удалить</button>
            </div>
        </article>`).join('') || '<p class="muted">Служебных заметок к этому заказу пока нет</p>';
    } catch (error) {
        toast(error.message, true);
    }
}

function openBulkDialog() {
    if (!state.selectedOrderIds.size) {
        return toast('Отметьте галочками хотя бы один заказ в таблице', true);
    }
    const form = $('entity-form');
    form.innerHTML = `<div class="dialog-head">
        <h3>Массовая операция (${state.selectedOrderIds.size} шт.)</h3>
        <button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button>
    </div>
    <label>Действие
        <select name="action" id="bulk-action-select">
            <option value="SET_STATUS">Изменить статус заказов (SET_STATUS)</option>
            <option value="ADD_TAG">Добавить тег (ADD_TAG)</option>
            <option value="REMOVE_TAG">Удалить тег (REMOVE_TAG)</option>
            <option value="ARCHIVE">Переместить в архив (ARCHIVE)</option>
            <option value="RESTORE">Восстановить из архива (RESTORE)</option>
        </select>
    </label>
    <label id="bulk-status-wrap">Новый статус
        <select name="status">${statusOptions('CONFIRMED')}</select>
    </label>
    <label id="bulk-tag-wrap" hidden>Тег
        <input name="tag" maxlength="50" placeholder="Например: срочно">
    </label>
    <div class="form-message error" data-form-error hidden></div>
    <button class="primary" type="submit">Выполнить для выбранных (${state.selectedOrderIds.size})</button>`;

    const actionSelect = $('bulk-action-select');
    const statusWrap = $('bulk-status-wrap');
    const tagWrap = $('bulk-tag-wrap');
    actionSelect.addEventListener('change', () => {
        const val = actionSelect.value;
        statusWrap.hidden = val !== 'SET_STATUS';
        tagWrap.hidden = val !== 'ADD_TAG' && val !== 'REMOVE_TAG';
    });

    form.onsubmit = async event => {
        event.preventDefault();
        clearFormError(form);
        const data = new FormData(form);
        const action = data.get('action');
        const payload = {
            ids: [...state.selectedOrderIds],
            action,
            status: action === 'SET_STATUS' ? data.get('status') : null,
            tag: (action === 'ADD_TAG' || action === 'REMOVE_TAG') ? empty(data.get('tag')) : null
        };
        try {
            const result = await api('/orders/bulk', { method: 'POST', body: JSON.stringify(payload) });
            $('entity-dialog').close();
            toast(`Массовая операция завершена: успешно ${result.succeeded} из ${result.requested}, ошибок: ${result.failures.length}`);
            await Promise.all([loadOrders(), loadDashboard()]);
        } catch (error) {
            showFormError(form, error.message);
        }
    };
    $('entity-dialog').showModal();
}

async function exportCsv() {
    try {
        const response = await api(`/orders/export?q=${encodeURIComponent(state.orders.query)}`);
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `medicine-orders-${new Date().toISOString().slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
    } catch (error) {
        toast(error.message, true);
    }
}

function customerForm(customer = null) {
    return `<div class="dialog-head">
        <h3>${customer ? 'Изменение клиента аптеки' : 'Новый клиент аптеки'}</h3>
        <button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button>
    </div>
    <label>Код клиента (карта лояльности)
        <input name="code" required maxlength="50" placeholder="CL-1006" value="${escapeHtml(customer?.code || '')}">
    </label>
    <label>ФИО клиента
        <input name="fullName" required minlength="2" maxlength="150" placeholder="Иванов Сергей Петрович" value="${escapeHtml(customer?.fullName || '')}">
    </label>
    <label>Телефон
        <input name="phone" type="tel" inputmode="numeric" required maxlength="16" pattern="\+7-[0-9]{3}-[0-9]{3}-[0-9]{2}-[0-9]{2}" placeholder="+7-999-999-99-99" value="${escapeHtml(customer?.phone ? formatRussianPhone(customer.phone) : '')}">
    </label>
    <label>Email
        <input name="email" type="email" maxlength="254" placeholder="client@example.org" value="${escapeHtml(customer?.email || '')}">
    </label>
    <label>Адрес доставки по умолчанию
        <input name="defaultAddress" required maxlength="255" placeholder="г. Москва, ул. Строителей, д. 10, кв. 15" value="${escapeHtml(customer?.defaultAddress || '')}">
    </label>
    <label class="inline"><input name="active" type="checkbox" ${customer?.active !== false ? 'checked' : ''}> Активен в справочнике</label>
    <div class="form-message error" data-form-error hidden></div>
    <button class="primary" type="submit">Сохранить клиента</button>`;
}

async function openCustomer(id = null) {
    const customer = id ? state.customers.find(c => c.id === id) : null;
    const form = $('entity-form');
    form.innerHTML = customerForm(customer);
    bindPhoneMask(form.elements.phone);

    form.onsubmit = async event => {
        event.preventDefault();
        clearFormError(form);
        if (!form.reportValidity()) return;
        const data = new FormData(form);
        const payload = {
            code: data.get('code'),
            fullName: data.get('fullName'),
            phone: data.get('phone'),
            email: empty(data.get('email')),
            defaultAddress: data.get('defaultAddress'),
            active: data.get('active') === 'on',
            version: customer?.version ?? null
        };
        try {
            await api(id ? `/customers/${id}` : '/customers', {
                method: id ? 'PUT' : 'POST',
                body: JSON.stringify(payload)
            });
            $('entity-dialog').close();
            toast('Клиент сохранён в справочнике');
            await loadCustomers();
        } catch (error) {
            showFormError(form, error.message);
        }
    };
    $('entity-dialog').showModal();
}

async function toggleCustomer(id) {
    const customer = state.customers.find(c => c.id === id);
    if (!customer) return;
    try {
        await api(`/customers/${id}/active?active=${!customer.active}&version=${customer.version}`, { method: 'PATCH' });
        toast(customer.active ? 'Клиент отключён' : 'Клиент включён');
        await loadCustomers();
    } catch (error) {
        toast(error.message, true);
    }
}

async function loadUsers() {
    try {
        const page = await api('/admin/users?page=0&size=100&sort=email,asc');
        $('users-body').innerHTML = page.content.map(user => `<tr>
            <td>${escapeHtml(user.email)}</td>
            <td>${escapeHtml(user.displayName)}</td>
            <td>${user.roles.map(r => `<span class="badge">${escapeHtml(roleLabel(r))}</span>`).join(' ')}</td>
            <td>${user.enabled ? '<span class="badge success">Да</span>' : '<span class="badge">Отключён</span>'}</td>
            <td>${user.passwordChangeRequired ? '<span class="badge warning">Требуется смена</span>' : 'Постоянный'}</td>
            <td><div class="actions">
                <button class="secondary" data-user-edit="${user.id}">Изменить</button>
                <button class="secondary" data-user-reset="${user.id}">Сбросить пароль</button>
            </div></td>
        </tr>`).join('');
        state.users = page.content;
    } catch (error) {
        toast(error.message, true);
    }
}

function userForm(user = null) {
    const checked = role => user?.roles.includes(role) || (!user && role === 'VIEWER');
    return `<div class="dialog-head">
        <h3>${user ? 'Изменение сотрудника' : 'Новый сотрудник'}</h3>
        <button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button>
    </div>
    <label>Email (логин)
        <input name="email" type="email" required maxlength="254" value="${escapeHtml(user?.email || '')}">
    </label>
    <label>ФИО и должность
        <input name="displayName" required maxlength="160" value="${escapeHtml(user?.displayName || '')}">
    </label>
    ${user ? '' : '<label>Временный пароль (мин. 12 символов, регистры, цифра, спецсимвол)<input name="temporaryPassword" type="password" required minlength="12" maxlength="128"></label>'}
    <fieldset><legend>Уровни доступа (роли)</legend>
        ${['VIEWER', 'PHARMACIST', 'ADMIN'].map(r => `<label class="inline"><input type="checkbox" name="roles" value="${r}" ${checked(r) ? 'checked' : ''}> ${escapeHtml(roleLabel(r))}</label>`).join('')}
    </fieldset>
    ${user ? `<label class="inline"><input type="checkbox" name="enabled" ${user.enabled ? 'checked' : ''}> Учётная запись активна</label>` : ''}
    <div class="form-message error" data-form-error hidden></div>
    <button class="primary" type="submit">Сохранить</button>`;
}

async function openUser(id = null) {
    const user = id ? state.users.find(u => u.id === id) : null;
    const form = $('entity-form');
    form.innerHTML = userForm(user);
    form.onsubmit = async event => {
        event.preventDefault();
        clearFormError(form);
        const data = new FormData(form);
        const payload = {
            email: data.get('email'),
            displayName: data.get('displayName'),
            roles: data.getAll('roles'),
            ...(user
                ? { enabled: data.get('enabled') === 'on', version: user.version }
                : { temporaryPassword: data.get('temporaryPassword') })
        };
        try {
            await api(id ? `/admin/users/${id}` : '/admin/users', {
                method: id ? 'PUT' : 'POST',
                body: JSON.stringify(payload)
            });
            $('entity-dialog').close();
            toast('Учётная запись сохранена');
            await loadUsers();
        } catch (error) {
            showFormError(form, error.message);
        }
    };
    $('entity-dialog').showModal();
}

async function resetUserPassword(id) {
    const password = prompt('Введите новый временный пароль (не менее 12 символов, верхний/нижний регистр, цифра и спецсимвол):');
    if (!password) return;
    try {
        await api(`/admin/users/${id}/reset-password`, {
            method: 'POST',
            body: JSON.stringify({ temporaryPassword: password })
        });
        toast('Временный пароль установлен');
        await loadUsers();
    } catch (error) {
        toast(error.message, true);
    }
}

async function loadAudit() {
    try {
        const q = encodeURIComponent($('audit-search').value.trim());
        const page = await api(`/admin/audit?q=${q}&page=0&size=100&sort=occurred,desc`);
        $('audit-body').innerHTML = page.content.map(item => `<tr>
            <td>${formatDate(item.occurredAt)}</td>
            <td>${escapeHtml(item.actorEmail || 'system')}</td>
            <td><code>${escapeHtml(item.eventType)}</code></td>
            <td>${escapeHtml(item.entityType || '—')}</td>
            <td>${escapeHtml(item.summary)}</td>
            <td><code>${escapeHtml(item.correlationId || '—')}</code></td>
        </tr>`).join('') || '<tr><td colspan="6" class="muted">Записи аудита не найдены</td></tr>';
    } catch (error) {
        toast(error.message, true);
    }
}

function openPasswordDialog(required = false) {
    return new Promise(resolve => {
        const dialog = $('entity-dialog');
        const form = $('entity-form');

        const currentPasswordField = required
            ? ''
            : '<label>Текущий пароль<input name="current" type="password" required maxlength="128" autocomplete="current-password"></label>';

        form.innerHTML = `<div class="dialog-head">
            <h3>${required ? 'Обязательная смена временного пароля' : 'Смена пароля'}</h3>
            ${required ? '' : '<button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button>'}
        </div>
        ${currentPasswordField}
        <label>Новый пароль<input name="next" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
        <label>Повторите новый пароль<input name="confirm" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
        <p class="muted">Минимум 12 символов: заглавная и строчная буквы, цифра и специальный символ.</p>
        <div class="form-message error" data-form-error hidden></div>
        <button class="primary" type="submit">${required ? 'Установить постоянный пароль' : 'Изменить пароль'}</button>`;

        let completed = false;
        const onClose = () => {
            dialog.removeEventListener('close', onClose);
            if (!completed) resolve(false);
        };
        dialog.addEventListener('close', onClose);

        form.onsubmit = async event => {
            event.preventDefault();
            clearFormError(form);
            const data = new FormData(form);
            const next = String(data.get('next') || '');
            const confirmVal = String(data.get('confirm') || '');

            if (next !== confirmVal) {
                showFormError(form, 'Новые пароли не совпадают');
                return;
            }

            try {
                if (required) {
                    await api('/auth/change-initial-password', {
                        method: 'POST',
                        body: JSON.stringify({ newPassword: next })
                    });
                    const refreshed = await refreshAccessToken();
                    if (!refreshed) {
                        completed = true;
                        dialog.close();
                        showLogin();
                        toast('Пароль изменён. Войдите с новым паролем.', true);
                        resolve(false);
                        return;
                    }
                    state.user.passwordChangeRequired = false;
                    completed = true;
                    dialog.close();
                    toast('Постоянный пароль успешно установлен');
                    resolve(true);
                    return;
                }

                await api('/auth/change-password', {
                    method: 'POST',
                    body: JSON.stringify({
                        currentPassword: data.get('current'),
                        newPassword: next
                    })
                });
                completed = true;
                dialog.close();
                toast('Пароль изменён. Выполните вход заново.');
                resolve(true);
                await logout();
            } catch (error) {
                showFormError(form, error.message);
            }
        };
        dialog.showModal();
    });
}

async function logout() {
    try {
        await fetch('/api/auth/logout', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: '{}'
        });
    } catch (_) {}
    showLogin();
}

function bindEvents() {
    $('login-form').addEventListener('submit', async event => {
        event.preventDefault();
        const form = $('login-form');
        const button = form.querySelector('button[type="submit"]');
        const errorNode = $('login-error');

        errorNode.hidden = true;
        button.disabled = true;
        const originalCaption = button.textContent;
        button.textContent = 'Входим…';

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    email: $('login-email').value,
                    password: $('login-password').value,
                    clientType: 'WEB'
                })
            });

            if (!response.ok) {
                throw new Error(await parseError(response));
            }

            const result = await response.json();
            state.accessToken = result.accessToken;
            state.user = result.user;
            $('login-password').value = '';
            await enterApplication();
        } catch (error) {
            console.error('[Medicine Delivery] Login flow failed', error);
            showLogin();
            errorNode.textContent = error.message || 'Не удалось выполнить вход';
            errorNode.hidden = false;
        } finally {
            button.disabled = false;
            button.textContent = originalCaption;
        }
    });

    $('navigation').addEventListener('click', event => {
        const button = event.target.closest('[data-tab]');
        if (button && !button.hidden) switchTab(button.dataset.tab);
    });
    $('logout').addEventListener('click', logout);
    $('change-password').addEventListener('click', () => openPasswordDialog(false));

    $('search-orders').addEventListener('click', () => {
        state.orders.query = $('order-search').value.trim();
        state.orders.sort = $('order-sort').value;
        state.orders.page = 0;
        loadOrders(0);
    });
    $('order-search').addEventListener('keydown', event => {
        if (event.key === 'Enter') $('search-orders').click();
    });
    $('order-sort').addEventListener('change', () => $('search-orders').click());
    $('orders-prev').addEventListener('click', () => loadOrders(state.orders.page - 1));
    $('orders-next').addEventListener('click', () => loadOrders(state.orders.page + 1));

    $('add-order').addEventListener('click', () => openOrder());
    $('bulk-action').addEventListener('click', openBulkDialog);
    $('export-csv').addEventListener('click', exportCsv);
    $('import-csv').addEventListener('click', () => {
        $('csv-result').textContent = '';
        $('csv-apply').disabled = true;
        $('csv-dialog').showModal();
    });

    $('add-customer').addEventListener('click', () => openCustomer());
    $('add-user').addEventListener('click', () => openUser());
    $('search-audit').addEventListener('click', loadAudit);

    $('orders-body').addEventListener('click', event => handleOrderTableClick(event));
    $('orders-body').addEventListener('change', event => {
        const checkbox = event.target.closest('[data-order-select]');
        if (checkbox) {
            checkbox.checked
                ? state.selectedOrderIds.add(checkbox.dataset.orderSelect)
                : state.selectedOrderIds.delete(checkbox.dataset.orderSelect);
        }
    });
    $('select-all-orders').addEventListener('change', event => {
        document.querySelectorAll('[data-order-select]').forEach(cb => {
            cb.checked = event.target.checked;
            event.target.checked
                ? state.selectedOrderIds.add(cb.dataset.orderSelect)
                : state.selectedOrderIds.delete(cb.dataset.orderSelect);
        });
    });

    $('customers-body').addEventListener('click', event => {
        const edit = event.target.closest('[data-customer-edit]');
        const toggle = event.target.closest('[data-customer-toggle]');
        if (edit) openCustomer(edit.dataset.customerEdit);
        if (toggle) toggleCustomer(toggle.dataset.customerToggle);
    });

    $('users-body').addEventListener('click', event => {
        const edit = event.target.closest('[data-user-edit]');
        const reset = event.target.closest('[data-user-reset]');
        if (edit) openUser(edit.dataset.userEdit);
        if (reset) resetUserPassword(reset.dataset.userReset);
    });

    $('note-form').addEventListener('submit', async event => {
        event.preventDefault();
        const editId = $('note-edit-id').value;
        const editVersion = $('note-edit-version').value;
        const payload = {
            body: $('note-body').value,
            pinned: $('note-pinned').checked,
            version: editId ? Number(editVersion) : null
        };
        try {
            await api(
                editId
                    ? `/orders/${state.activeOrderForNotes}/notes/${editId}`
                    : `/orders/${state.activeOrderForNotes}/notes`,
                {
                    method: editId ? 'PUT' : 'POST',
                    body: JSON.stringify(payload)
                }
            );
            resetNoteForm();
            await renderNotes();
        } catch (error) {
            toast(error.message, true);
        }
    });

    $('note-cancel-edit').addEventListener('click', resetNoteForm);

    $('notes-list').addEventListener('click', async event => {
        const editBtn = event.target.closest('[data-note-edit]');
        if (editBtn) {
            const note = state.activeNotes.find(n => n.id === editBtn.dataset.noteEdit);
            if (note) {
                $('note-edit-id').value = note.id;
                $('note-edit-version').value = note.version;
                $('note-body').value = note.body;
                $('note-pinned').checked = note.pinned;
                $('note-submit-btn').textContent = 'Сохранить изменения';
                $('note-cancel-edit').hidden = false;
                $('note-body').focus();
            }
            return;
        }
        const deleteBtn = event.target.closest('[data-note-delete]');
        if (deleteBtn && confirm('Удалить служебную заметку?')) {
            try {
                await api(`/orders/${state.activeOrderForNotes}/notes/${deleteBtn.dataset.noteDelete}`, { method: 'DELETE' });
                if ($('note-edit-id').value === deleteBtn.dataset.noteDelete) resetNoteForm();
                await renderNotes();
            } catch (error) {
                toast(error.message, true);
            }
        }
    });

    $('csv-preview').addEventListener('click', () => runCsv(false));
    $('csv-apply').addEventListener('click', () => runCsv(true));
    document.body.addEventListener('click', event => {
        const close = event.target.closest('[data-close-dialog]');
        if (close) $(close.dataset.closeDialog).close();
    });
}

function handleOrderTableClick(event) {
    const view = event.target.closest('[data-order-view]');
    const edit = event.target.closest('[data-order-edit]');
    const notes = event.target.closest('[data-order-notes]');
    const archive = event.target.closest('[data-order-archive]');
    const permDelete = event.target.closest('[data-order-delete-perm]');
    if (view) openOrder(view.dataset.orderView, true);
    if (edit) openOrder(edit.dataset.orderEdit, false);
    if (notes) openNotes(notes.dataset.orderNotes);
    if (archive) archiveOrder(archive.dataset.orderArchive);
    if (permDelete) permanentDeleteOrder(permDelete.dataset.orderDeletePerm);
}

async function runCsv(apply) {
    const file = $('csv-file').files[0];
    if (!file) return toast('Выберите CSV-файл для проверки', true);
    const form = new FormData();
    form.append('file', file);
    try {
        const result = await api(`/orders/import/${apply ? 'apply' : 'preview'}?mode=${$('csv-mode').value}`, {
            method: 'POST',
            body: form
        });
        $('csv-result').textContent = JSON.stringify(result, null, 2);
        $('csv-apply').disabled = apply ? true : result.invalidRows > 0;
        if (apply) {
            toast(`Импорт выполнен: создано ${result.created}, обновлено ${result.updated}`);
            await Promise.all([loadOrders(), loadDashboard()]);
        } else {
            toast(`Проверка CSV: корректных строк ${result.validRows}, ошибок ${result.invalidRows}`);
        }
    } catch (error) {
        toast(error.message, true);
        $('csv-result').textContent = error.message;
    }
}

function formatDate(value) {
    return value
        ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
        : '—';
}

function empty(value) {
    const normalized = String(value ?? '').trim();
    return normalized || null;
}

boot().catch(error => {
    console.error('[Medicine Delivery] Boot failed', error);
    showLogin();
    toast(error.message || 'Не удалось инициализировать приложение', true);
});
