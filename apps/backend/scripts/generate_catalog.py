#!/usr/bin/env python3
"""Generate the ready-catalog section for baseData.json from catalog-seed-reference.json.

Transforms the age-bucketed seed reference into the CAT-008 catalog template
schema (id, title, comment, coins/price, groupKey, groupName,
semanticGraphicKey, frequencyLimit, frequencyPeriod, minAge, maxAge,
difficulty, tags, active, sortOrder).
"""
import json
import re
import sys

SEED = "catalog-seed-reference.json"
OUT = "apps/backend/src/main/resources/baseData.json"

TASK_GROUP_KEYS = {
    "Утро и вечер": "morning",
    "Учёба": "study",
    "Дом и порядок": "home",
    "Самостоятельность": "independence",
    "Движение и здоровье": "health",
    "Общение и эмоции": "emotions",
    "Полезные привычки": "habits",
    "Творчество": "creativity",
}

REWARD_GROUP_KEYS = {
    "Время с семьёй": "family",
    "Выбор и привилегии": "privileges",
    "Творчество и игры": "creativity",
    "Маленькие радости": "joys",
    "Прогулки и развлечения": "outings",
    "Покупки": "purchases",
    "Большие цели": "biggoals",
}

GROUP_GRAPHIC = {
    "morning": "sunrise",
    "study": "book",
    "home": "home",
    "independence": "box",
    "health": "dumbbell",
    "emotions": "heart",
    "habits": "sparkles",
    "creativity": "palette",
    "family": "users",
    "privileges": "star",
    "joys": "iceCream",
    "outings": "treePine",
    "purchases": "gift",
    "biggoals": "trophy",
}

AGE_RANGES = {
    "6-8": (6, 8),
    "9-11": (9, 11),
    "12-14": (12, 14),
}

FREQ_RE = re.compile(r"(\d+)\s+раз(?:а)?\s+в\s+(день|неделю|месяц|год)(?:\s+(\d+)\s+месяца)?")


def parse_frequency(label):
    m = FREQ_RE.match(label.strip())
    if not m:
        return 1, "week", label
    limit = int(m.group(1))
    period = m.group(2)
    if period == "день":
        period = "day"
    elif period == "неделю":
        period = "week"
    elif period == "месяц":
        period = "month"
    elif period == "год":
        period = "year"
    return limit, period, label


def difficulty_for(amount, kind):
    if kind == "task":
        if amount <= 2:
            return "simple"
        if amount <= 3:
            return "normal"
        return "advanced"
    # reward
    if amount < 8:
        return "simple"
    if amount <= 15:
        return "normal"
    return "advanced"


def build(kind, group_keys, seed):
    items = []
    for age_bucket, bucket in seed.items():
        min_age, max_age = AGE_RANGES[age_bucket]
        entries = bucket["tasks"] if kind == "task" else bucket["rewards"]
        for idx, entry in enumerate(entries, start=1):
            group_name = entry["groupName"]
            group_key = group_keys.get(group_name, "other")
            limit, period, _ = parse_frequency(entry["frequency"])
            amount = entry["coins"] if kind == "task" else entry["price"]
            item_id = f"c{kind[0]}-{age_bucket}-{idx}"
            item = {
                "id": item_id,
                "title": entry["title"],
                "comment": "",
                "groupKey": group_key,
                "groupName": group_name,
                "semanticGraphicKey": GROUP_GRAPHIC.get(group_key, "circleDot"),
                "frequencyLimit": limit,
                "frequencyPeriod": period,
                "minAge": min_age,
                "maxAge": max_age,
                "difficulty": difficulty_for(amount, kind),
                "tags": [group_key],
                "active": True,
                "sortOrder": idx,
            }
            if kind == "task":
                item["coins"] = amount
            else:
                item["price"] = amount
            items.append(item)
    return items


def main():
    with open(SEED, encoding="utf-8") as fh:
        seed = json.load(fh)

    catalog = {
        "tasks": build("task", TASK_GROUP_KEYS, seed),
        "rewards": build("reward", REWARD_GROUP_KEYS, seed),
    }

    with open(OUT, encoding="utf-8") as fh:
        base = json.load(fh)
    base["catalog"] = catalog

    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(base, fh, ensure_ascii=False, indent=4)
        fh.write("\n")

    print(f"tasks: {len(catalog['tasks'])}, rewards: {len(catalog['rewards'])}")


if __name__ == "__main__":
    sys.exit(main())
