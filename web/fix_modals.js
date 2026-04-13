const fs = require('fs');
let content = fs.readFileSync('views/components/modals.html', 'utf8');

content = content.replace(/class="modal"/g, 'class="modal" role="dialog" aria-modal="true"');
content = content.replace(/type="number"/g, 'type="number" inputmode="numeric"');

fs.writeFileSync('views/components/modals.html', content);
console.log('Modals updated');
