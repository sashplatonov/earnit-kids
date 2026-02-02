export const state = {
    isAdmin: false,
    role: 'child',
    balance: 0,
    tasks: [],
    shopItems: [],
    history: [],
    requests: [],
    isPinSet: false,
    familyId: null,
    familyName: '',
    monthlyLimit: 10000,
    baseData: { tasks: [], products: [] }
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
