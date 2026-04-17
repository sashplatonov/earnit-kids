/** @file Super Admin Family Details frontend UI module */
function renderChildren(children) {
    return children.map((child) => `
        <div style="display:flex; gap:0.5rem; align-items:center; background: #f9fafb; padding: 0.5rem; border-radius: 6px;">
            <span style="font-weight:bold; min-width: 80px;">${child.name}</span>
            <span style="font-size:0.8rem; color:#666">Bal: ${child.balance}</span>
            <div style="flex:1; display:flex; gap:0.3rem;">
                <input type="text" readonly value="${window.location.origin}/login-child/${child.token}" style="flex:1; font-size:0.8rem; border:1px solid #ddd; padding:2px 4px; border-radius:4px;">
                <button class="view-btn" onclick="copyMagicLink('${child.token}')">Copy</button>
                <button class="block-btn" style="background:#f59e0b; padding:2px 6px;" onclick="regenerateToken(null, ${child.id})">Refresh</button>
            </div>
        </div>
    `).join('');
}

function renderTasks(tasks) {
    return tasks.map((task) => `
        <tr>
            <td>${task.name}</td>
            <td>${task.coins} 🪙</td>
        </tr>
    `).join('');
}

function renderShopItems(items) {
    return items.map((item) => {
        const freqText = item.frequency ? `<br><small>${item.frequency.limit}/${item.frequency.period}</small>` : '';
        const limitText = item.money_limit ? `<br><small>Limit: ${item.money_limit}🪙</small>` : '';
        return `
            <tr>
                <td>${item.name}${freqText}${limitText}</td>
                <td>${item.price} 🪙</td>
            </tr>
        `;
    }).join('');
}

function renderRecentHistory(history) {
    if (history.length === 0) {
        return '<p>Нет записей</p>';
    }

    return `
        <table>
            <tbody>
                ${history.slice(-10).reverse().map((item) => `
                    <tr>
                        <td>${new Date(item.timestamp).toLocaleString('ru-RU')}</td>
                        <td>${item.action}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;
}

function renderFamilyGrid(familyData) {
    const { created_at, email, monthly_limit, children } = familyData.familyInfo;
    const { balance } = familyData.data;

    return `
        <div class="detail-grid">
            <div class="detail-item"><strong>ID семьи</strong><div>#${familyData.familyId}</div></div>
            <div class="detail-item"><strong>Дата создания</strong><div>${new Date(created_at).toLocaleString('ru-RU')}</div></div>
            <div class="detail-item"><strong>Email</strong><div>${email || '-'}</div></div>
            <div class="detail-item"><strong>Баланс</strong><div>${balance} 🪙</div></div>
            <div class="detail-item"><strong>Лимит (мес)</strong><div>${monthly_limit || 10000}</div></div>
            <div class="detail-item" style="grid-column: span 2">
                <strong>Дети (${children.length})</strong>
                <div style="margin-top:0.5rem; display:flex; flex-direction:column; gap:0.5rem;">
                    ${renderChildren(children)}
                </div>
            </div>
        </div>
    `;
}

function renderDetailsSection(title, dataArray, renderFn) {
    return `
        <h3>${title} (${dataArray.length})</h3>
        <table>
            <thead><tr><th>Название</th><th>${title.includes('Задания') ? 'Награда' : 'Цена'}</th></tr></thead>
            <tbody>${renderFn(dataArray)}</tbody>
        </table>
    `;
}

export function renderFamilyDetails(familyData) {
    const modalTitle = document.getElementById('modal-title');
    const modalBody = document.getElementById('modal-body');

    modalTitle.textContent = familyData.familyInfo.email || `Family ${familyData.familyId}`;
    modalBody.innerHTML = `
        ${renderFamilyGrid(familyData)}
        ${renderDetailsSection('📋 Задания', familyData.data.tasks, renderTasks)}
        ${renderDetailsSection('🏪 Магазин', familyData.data.shop, renderShopItems)}
        <h3>📜 История (${familyData.data.history.length})</h3>
        ${renderRecentHistory(familyData.data.history)}
    `;
}
