const fs = require('fs');
const path = require('path');

const DATA_FILE = path.join(__dirname, '../data.json');

const DEFAULT_DATA = {
    admin_pin: "000000",
    child_pin: "000000",
    balance: 0,
    tasks: [],
    shop: [],
    history: []
};

// Load data from file
function loadData() {
    try {
        if (fs.existsSync(DATA_FILE)) {
            const content = fs.readFileSync(DATA_FILE, 'utf8');
            return { ...DEFAULT_DATA, ...JSON.parse(content) };
        }
    } catch (err) {
        console.error('Error loading data:', err.message);
    }
    return { ...DEFAULT_DATA };
}

// Save data to file
function saveData(data) {
    try {
        fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2), 'utf8');
        return true;
    } catch (err) {
        console.error('Error saving data:', err.message);
        return false;
    }
}

module.exports = {
    loadData,
    saveData
};
