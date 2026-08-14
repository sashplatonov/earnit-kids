import { describe, expect, it } from 'vitest';
import { buildCsvTemplate, parseCsvImport } from '../../src/lib/services/csvImport';

describe('parseCsvImport', () => {
    it('parses task csv with comma separators', () => {
        const result = parseCsvImport('tasks', 'title,coins,groupName\nWash dishes,10,Home');

        expect(result.separator).toBe(',');
        expect(result.errors).toHaveLength(0);
        expect(result.normalizedRows).toHaveLength(1);
        expect(result.rows[0]?.normalized).toMatchObject({
            rowNumber: 2,
            title: 'Wash dishes',
            coins: 10,
            groupName: 'Home',
        });
    });

    it('detects semicolon separators and duplicate shop names', () => {
        const result = parseCsvImport('shop', 'name;price\nTablet time;50\nTablet time;60');

        expect(result.separator).toBe(';');
        expect(result.errors.some((error) => error.field === 'name')).toBe(true);
        expect(result.rows[1]?.errors.some((error) => error.message === 'duplicate name')).toBe(true);
    });

    it('keeps multiline quoted task comments inside the same csv record', () => {
        const result = parseCsvImport(
            'tasks',
            'title,coins,comment\n'
            + 'Task one,10,"line 1\nline 2"\n'
            + 'Task two,20,ok'
        );

        expect(result.errors).toHaveLength(0);
        expect(result.normalizedRows).toHaveLength(2);
        expect(result.normalizedRows[0]).toMatchObject({
            rowNumber: 2,
            title: 'Task one',
            coins: 10,
            comment: 'line 1\nline 2',
        });
        expect(result.normalizedRows[1]).toMatchObject({
            rowNumber: 4,
            title: 'Task two',
            coins: 20,
            comment: 'ok',
        });
    });

    it('drops unsupported legacy shop type values before submit payload generation', () => {
        const result = parseCsvImport(
            'shop',
            'name,price,groupName,comment,frequencyLimit,frequencyPeriod,moneyLimit,type,isActive\n'
            + 'Королева Настолки,2,Семья,Вечер вместе,2,week,,reward,true'
        );

        expect(result.errors).toHaveLength(0);
        expect(result.normalizedRows).toEqual([
            {
                rowNumber: 2,
                name: 'Королева Настолки',
                price: 2,
                groupName: 'Семья',
                comment: 'Вечер вместе',
                frequencyLimit: 2,
                frequencyPeriod: 'week',
                moneyLimit: null,
                type: null,
                icon: null,
                isActive: true,
            },
        ]);
    });

    it('rejects non-positive reward frequency limits', () => {
        const result = parseCsvImport(
            'shop',
            'name,price,frequencyLimit,frequencyPeriod\nTablet time,50,0,day'
        );

        expect(result.errors).toEqual([
            { row: 2, field: 'frequencyLimit', message: 'frequencyLimit must be positive' },
        ]);
    });

    it('accepts markdown formatting around shop headers and values and normalizes enum case', () => {
        const result = parseCsvImport(
            'shop',
            '"name","price","groupName","comment","frequencyLimit","frequencyPeriod","moneyLimit","type",**"isActive"**\n'
            + '"Reward","2","Family","A reward","1","DAY","","",**"true"**'
        );

        expect(result.errors).toHaveLength(0);
        expect(result.normalizedRows[0]).toMatchObject({
            name: 'Reward',
            price: 2,
            frequencyPeriod: 'day',
            isActive: true,
        });
    });

    it('recovers an unquoted comma inside a task comment', () => {
        const result = parseCsvImport(
            'tasks',
            'title,coins,groupName,comment,frequencyLimit,frequencyPeriod,moneyLimit,icon,isActive\n'
            + 'Task,5,Health,Did it in the morning, without reminders,1,DAY,,,true'
        );

        expect(result.errors).toHaveLength(0);
        expect(result.normalizedRows[0]).toMatchObject({
            comment: 'Did it in the morning, without reminders',
            frequencyLimit: 1,
            frequencyPeriod: 'day',
            isActive: true,
        });
    });

    it('builds a copyable csv template with header and sample row', () => {
        expect(buildCsvTemplate('tasks')).toBe(
            'title,coins,groupName,comment,frequencyLimit,frequencyPeriod,moneyLimit,icon,isActive\n'
            + 'Wash dishes,10,,,,,,,'
        );
        expect(buildCsvTemplate('shop')).toBe(
            'name,price,groupName,comment,frequencyLimit,frequencyPeriod,moneyLimit,type,icon,isActive\n'
            + 'Tablet time,50,,,,,,,,'
        );
    });
});
