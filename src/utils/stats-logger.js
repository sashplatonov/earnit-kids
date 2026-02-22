const { loadFamilies, loadFamilyData } = require('../services/familyService');
const { loadBaseData } = require('../services/baseDataService');

async function logStartupStats() {
    try {
        const familiesData = await loadFamilies();
        const familyIds = Object.keys(familiesData.families);

        let tasksCount = 0;
        let productsCount = 0;

        for (const id of familyIds) {
            const data = await loadFamilyData(id);
            tasksCount += (data.tasks || []).length;
            productsCount += (data.shop || []).length;
        }

        const catalog = loadBaseData();

        console.log('-------------------------------------------');
        console.log('📊 APP STARTUP STATISTICS:');
        console.log(`🏠 Total Shops (Families): ${familyIds.length}`);
        console.log(`✅ Total tasks in all shops: ${tasksCount}`);
        console.log(`🎁 Total products in all shops: ${productsCount}`);
        console.log(`📚 Global Catalog: ${catalog.tasks.length} tasks, ${catalog.products.length} products`);
        console.log('-------------------------------------------');
    } catch (err) {
        console.error('Error generating startup stats:', err.message);
    }
}

module.exports = { logStartupStats };
