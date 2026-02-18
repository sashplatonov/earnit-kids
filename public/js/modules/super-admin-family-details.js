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

export function renderFamilyDetails(familyData) {
    const modalTitle = document.getElementById('modal-title');
    const modalBody = document.getElementById('modal-body');

    modalTitle.textContent = familyData.familyInfo.name;
    modalBody.innerHTML = `
        <div class="detail-grid">
            <div class="detail-item">
                <strong>ID семьи</strong>
                <div>#${familyData.familyId}</div>
            </div>
            <div class="detail-item">
                <strong>Дата создания</strong>
                <div>${new Date(familyData.familyInfo.created_at).toLocaleString('ru-RU')}</div>
            </div>
            <div class="detail-item">
                <strong>Email</strong>
                <div>${familyData.familyInfo.email || '-'}</div>
            </div>
            <div class="detail-item">
                <strong>Баланс</strong>
                <div>${familyData.data.balance} 🪙</div>
            </div>
            <div class="detail-item">
                <strong>Лимит (мес)</strong>
                <div>${familyData.familyInfo.monthly_limit || 10000}</div>
            </div>
            <div class="detail-item" style="grid-column: span 2">
                <strong>Дети (${familyData.familyInfo.children.length})</strong>
                <div style="margin-top:0.5rem; display:flex; flex-direction:column; gap:0.5rem;">
                    ${renderChildren(familyData.familyInfo.children)}
                </div>
            </div>
        </div>

        <h3>📋 Задания (${familyData.data.tasks.length})</h3>
        <table>
            <thead>
                <tr>
                    <th>Название</th>
                    <th>Награда</th>
                </tr>
            </thead>
            <tbody>${renderTasks(familyData.data.tasks)}</tbody>
        </table>

        <h3>🏪 Магазин (${familyData.data.shop.length})</h3>
        <table>
            <thead>
                <tr>
                    <th>Название</th>
                    <th>Цена</th>
                </tr>
            </thead>
            <tbody>${renderShopItems(familyData.data.shop)}</tbody>
        </table>

        <h3>📜 История (${familyData.data.history.length})</h3>
        ${renderRecentHistory(familyData.data.history)}
    `;
}
