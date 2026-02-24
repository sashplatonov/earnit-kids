const { loadFamilies, loadFamilyData } = require('../services/familyService');
const { loadBaseData } = require('../services/baseDataService');
const { createLogger } = require('./logger');
const logger = createLogger('statsLogger');

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
        logger.info({
            families: familyIds.length,
            tasks: tasksCount,
            products: productsCount,
            catalogTasks: catalog.tasks.length,
            catalogProducts: catalog.products.length
        }, 'App startup statistics');
    } catch (err) {
        logger.error({ err: err.message }, 'Failed to generate startup statistics');
    }
}

module.exports = { logStartupStats };
