CREATE TABLE catalog_item_translations (
    catalog_item_id BIGINT NOT NULL,
    locale_code VARCHAR(16) NOT NULL,
    title VARCHAR(500) NOT NULL,
    comment TEXT,
    group_name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_catalog_item_translations PRIMARY KEY (catalog_item_id, locale_code),
    CONSTRAINT fk_catalog_item_translations_item FOREIGN KEY (catalog_item_id) REFERENCES catalog_items (id),
    CONSTRAINT ck_catalog_item_translations_locale_non_blank CHECK (TRIM(locale_code) <> ''),
    CONSTRAINT ck_catalog_item_translations_title_non_blank CHECK (TRIM(title) <> ''),
    CONSTRAINT ck_catalog_item_translations_group_non_blank CHECK (TRIM(group_name) <> '')
);

CREATE INDEX idx_catalog_item_translations_item_locale
    ON catalog_item_translations (catalog_item_id, locale_code);

INSERT INTO catalog_item_translations (catalog_item_id, locale_code, title, comment, group_name)
SELECT id, 'ru', title_ru, NULLIF(comment_ru, ''), group_name_ru
FROM catalog_items;

INSERT INTO catalog_item_translations (catalog_item_id, locale_code, title, comment, group_name)
SELECT id,
       'en',
       CASE
           WHEN title_ru LIKE '%Умыться, одеться%' THEN '🌅 Wash, get dressed, and fix your hair'
           WHEN title_ru LIKE '%Почистить зубы%' THEN '🪥 Brush your teeth in the morning and evening'
           WHEN title_ru LIKE '%Заправить кровать%' THEN '🛏️ Make your bed in the morning'
           WHEN title_ru LIKE '%Собрать вещи на завтра%' THEN '🎒 Pack what you need for tomorrow'
           WHEN title_ru LIKE '%Проверить школьный рюкзак%' THEN '🎒 Check your school backpack'
           WHEN title_ru LIKE '%Подготовить всё для школы%' THEN '🎒 Prepare everything you need for school tomorrow'
           WHEN title_ru LIKE '%Убрать свою одежду%' OR title_ru LIKE '%Разобрать одежду%' THEN '👕 Put your clothes away'
           WHEN title_ru LIKE '%Убрать игрушки%' THEN '🧸 Put your toys away after playing'
           WHEN title_ru LIKE '%Убрать свой стол%' THEN '🧽 Tidy your desk or workspace'
           WHEN title_ru LIKE '%Убрать за собой посуду%' THEN '🍽️ Clear your dishes after eating'
           WHEN title_ru LIKE '%Полить одно домашнее растение%' THEN '🌱 Water one houseplant'
           WHEN title_ru LIKE '%Почитать книгу 10 минут%' THEN '📖 Read a book for 10 minutes'
           WHEN title_ru LIKE '%Почитать книгу 20 минут%' THEN '📖 Read a book for 20 minutes'
           WHEN title_ru LIKE '%Почитать книгу 25 минут%' THEN '📖 Read a book for 25 minutes'
           WHEN title_ru LIKE '%Повторить буквы или слова%' THEN '🔤 Practise letters or words for 10 minutes'
           WHEN title_ru LIKE '%Позаниматься математикой%' THEN '🔢 Practise maths for a few minutes'
           WHEN title_ru LIKE '%Написать 5 аккуратных строк%' THEN '✍️ Write five neat lines'
           WHEN title_ru LIKE '%по-сербски%' THEN '🇷🇸 Learn a few words in Serbian'
           WHEN title_ru LIKE '%Порисовать или сделать поделку%' THEN '🎨 Draw or make something for 15 minutes'
           WHEN title_ru LIKE '%Сделать зарядку%' OR title_ru LIKE '%Сделать тренировку%' THEN '🧘 Exercise or stretch for a few minutes'
           WHEN title_ru LIKE '%Погулять%' THEN '🚶 Go outside and move actively'
           WHEN title_ru LIKE '%Выпить%' AND title_ru LIKE '%воды%' THEN '💧 Drink enough water today'
           WHEN title_ru LIKE '%Сказать словами%' THEN '🗣️ Say what you feel and what you want'
           WHEN title_ru LIKE '%Попросить прощения%' OR title_ru LIKE '%Признать ошибку%' THEN '🪄 Admit a mistake and help make it right'
           WHEN title_ru LIKE '%Решить спор%' OR title_ru LIKE '%Договориться о спорном%' THEN '🤝 Resolve a disagreement without insults'
           WHEN title_ru LIKE '%Провести 30 минут без телефона%' THEN '📵 Spend 30 minutes without your phone or tablet'
           WHEN title_ru LIKE '%Провести 1 час без телефона%' THEN '📵 Spend an hour without your phone or social media'
           WHEN title_ru LIKE '%Помогать по дому%' THEN '⏱️ Help around the house for a while'
           WHEN title_ru LIKE '%Вынести мусор%' THEN '🗑️ Take out the rubbish'
           WHEN title_ru LIKE '%Прочитать короткую историю%' OR title_ru LIKE '%Прочитать главу%' THEN '📚 Read a chapter and tell someone what happened'
           WHEN title_ru LIKE '%Разобрать одну небольшую зону%' OR title_ru LIKE '%Разобрать одну зону%' THEN '🧺 Organise one small area of your things'
           WHEN title_ru LIKE '%Пропылесосить одну комнату%' THEN '🧹 Vacuum one room'
           WHEN title_ru LIKE '%Спланировать домашние задания%' THEN '📝 Plan your homework and mark what is done'
           WHEN title_ru LIKE '%Записать свои расходы%' THEN '💳 Record what you spent today'
           WHEN title_ru LIKE '%Приготовить простой завтрак%' THEN '🍳 Make a simple breakfast or snack'
           WHEN item_type = 'task' THEN '✅ Complete a useful ' || LOWER(group_name_en) || ' activity'
           WHEN title_ru LIKE '%Выбрать настольную игру%' THEN '🎲 Choose a board game for family time'
           WHEN title_ru LIKE '%Поиграть с мамой%' THEN '🧸 Play with a parent for a while'
           WHEN title_ru LIKE '%крепость из подушек%' THEN '🏰 Build a pillow fort and play'
           WHEN title_ru LIKE '%десерт%' THEN '🧁 Make a simple dessert with a parent'
           WHEN title_ru LIKE '%маршрут прогулки%' OR title_ru LIKE '%место для прогулки%' THEN '🚶 Choose the route or place for a family walk'
           WHEN title_ru LIKE '%ужин%' THEN '🍽️ Choose dinner from the agreed options'
           WHEN title_ru LIKE '%мультфильм%' OR title_ru LIKE '%фильм%' THEN '🎬 Choose a family film for the evening'
           WHEN title_ru LIKE '%музыку%' THEN '🎵 Choose the music for the car or home'
           WHEN title_ru LIKE '%ванну%' THEN '🛁 Enjoy a bubble bath with toys'
           WHEN title_ru LIKE '%мороженое%' THEN '🍦 Have one ice cream'
           WHEN title_ru LIKE '%небольшой десерт%' THEN '🧁 Choose a small dessert'
           WHEN title_ru LIKE '%детскую площадку%' THEN '🛝 Visit a new playground'
           WHEN title_ru LIKE '%игровую зону%' THEN '🎡 Visit a play area or amusement park'
           WHEN title_ru LIKE '%боулинг%' THEN '🎳 Go bowling with the family'
           WHEN title_ru LIKE '%кино%' THEN '🎬 Go to the cinema'
           WHEN title_ru LIKE '%бассейн%' THEN '🏊 Go swimming'
           WHEN title_ru LIKE '%новую книгу%' OR title_ru LIKE '%книгу или комикс%' THEN '📚 Choose a new book or comic'
           WHEN title_ru LIKE '%набор для творчества%' THEN '🧩 Get a small craft kit or puzzle'
           WHEN title_ru LIKE '%маленькую игрушку%' THEN '🧸 Get a small toy'
           WHEN title_ru LIKE '%ягоды или фрукты%' THEN '🍓 Choose your favourite fruit or berries'
           WHEN title_ru LIKE '%семейный мини-пикник%' THEN '🎈 Have a mini family picnic'
           WHEN title_ru LIKE '%кафе%' THEN '☕ Go to a café with a parent and talk together'
           WHEN title_ru LIKE '%дополнительных минут игры%' THEN '🎮 Get extra time to play'
           WHEN title_ru LIKE '%экранного времени%' THEN '📱 Get extra screen time'
           WHEN title_ru LIKE '%Лечь спать%' THEN '🛌 Go to bed later on a weekend'
           WHEN title_ru LIKE '%Пригласить друга%' THEN '👫 Invite a friend over or for a walk'
           WHEN title_ru LIKE '%доставку%' THEN '🍕 Choose a family takeaway from the agreed options'
           WHEN title_ru LIKE '%аксессуар%' THEN '🎧 Buy an affordable hobby or tech accessory'
           WHEN title_ru LIKE '%онлайн-заказ%' THEN '📦 Place one small online order within the budget'
           WHEN title_ru LIKE '%кафе или фастфуд%' THEN '🍔 Choose a café or fast-food place for a family outing'
           WHEN title_ru LIKE '%концерт%' THEN '🎟️ Go to a concert, exhibition, or event you enjoy'
           WHEN title_ru LIKE '%желанную вещь%' THEN '🎧 Buy one much-wanted item'
           WHEN title_ru LIKE '%самостоятельную покупку%' THEN '🎯 Get a budget for a small independent purchase'
           ELSE '⭐ Enjoy a ' || LOWER(group_name_en) || ' family reward'
       END,
       NULLIF(comment_en, ''),
       group_name_en
FROM catalog_items;
