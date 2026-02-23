export const state = {
    isAdmin: false,
    role: 'child',
    balance: 0,
    tasks: [],
    shopItems: [],
    history: [],
    requests: [],
    friends: [],
    childNickname: null,
    isPinSet: false,
    familyId: null,
    monthlyLimit: 10000,
    dailyCoinLimit: 0,
    baseData: { tasks: [], products: [] },
    children: [],
    currentChildId: null
};

// Simple event bus for state changes
const listeners = [];

export function subscribe(listener) {
    listeners.push(listener);
}

export function notify() {
    listeners.forEach(listener => listener(state));
}

export function setState(newState) {
    Object.assign(state, newState);
    notify();
}

export function updateBalance(amount) {
    state.balance += amount;
    notify();
}
