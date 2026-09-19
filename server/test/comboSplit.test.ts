import { describe, it, expect } from 'vitest';
import { generateWeeklyWorkout } from '../src/modules/exercise/workoutGenerator';

const MAP = { 0: 'Back & Biceps', 1: 'Legs & Abs', 2: 'Arms & Core', 3: 'Rest', 4: 'Chest & Triceps', 5: 'Legs & Abs', 6: 'Shoulders & Triceps' };
const START = new Date(Date.UTC(2026, 8, 13)); // a Sunday

function week(level: 'beginner' | 'intermediate' | 'advanced' = 'intermediate') {
  return generateWeeklyWorkout('muscular', 'gym', {
    split: 'body_part', fitnessLevel: level, startDate: START, today: START, dayFocusOverride: MAP,
  });
}
const groups = (d: { exercises: { muscleGroup?: string }[] }) => d.exercises.map((e) => e.muscleGroup);
const count = (d: { exercises: { muscleGroup?: string }[] }, g: string) => groups(d).filter((x) => x === g).length;

describe('user split: back+biceps / chest+triceps / shoulders+triceps / arms+core / legs+abs', () => {
  const plan = week();
  const byLabel = (label: string) => plan.days.find((d) => d.focus === label)!;

  it('lays the week out exactly as requested, rest on Wednesday', () => {
    expect(plan.days.map((d) => (d.rest ? 'Rest' : d.focus))).toEqual([
      'Back & Biceps', 'Legs & Abs', 'Arms & Core', 'Rest', 'Chest & Triceps', 'Legs & Abs', 'Shoulders & Triceps',
    ]);
  });

  it('chest is Thursday, shoulders Saturday - one day between them', () => {
    const idx = (f: string) => plan.days.findIndex((d) => d.focus === f);
    expect(plan.days[idx('Chest & Triceps')]!.date).toBeDefined();
    expect(idx('Shoulders & Triceps') - idx('Chest & Triceps')).toBe(2);
    expect(plan.days[5]!.focus).not.toBe('Chest & Triceps');
    expect(plan.days[5]!.focus).not.toBe('Shoulders & Triceps');
  });

  it('back day has 3 biceps exercises', () => {
    const d = byLabel('Back & Biceps');
    expect(count(d, 'biceps')).toBe(3);
    expect(count(d, 'back')).toBe(5);
  });

  it('chest day has 3 triceps exercises', () => {
    const d = byLabel('Chest & Triceps');
    expect(count(d, 'triceps')).toBe(3);
    expect(count(d, 'chest')).toBe(5);
  });

  it('shoulder day has 3 triceps exercises, all different from chest day', () => {
    const chest = byLabel('Chest & Triceps');
    const sh = byLabel('Shoulders & Triceps');
    expect(count(sh, 'triceps')).toBe(3);
    const chestTri = new Set(chest.exercises.filter((e) => e.muscleGroup === 'triceps').map((e) => e.name));
    const shTri = sh.exercises.filter((e) => e.muscleGroup === 'triceps').map((e) => e.name);
    for (const n of shTri) expect(chestTri.has(n)).toBe(false);
    expect(count(sh, 'shoulders') + count(sh, 'traps')).toBe(5);
  });

  it('arms day mixes biceps, triceps and core', () => {
    const d = byLabel('Arms & Core');
    expect(count(d, 'biceps')).toBe(3);
    expect(count(d, 'triceps')).toBe(3);
    expect(count(d, 'core')).toBe(2);
  });

  it('arms day uses biceps/triceps variations not already used on back, chest or shoulder day', () => {
    const arms = byLabel('Arms & Core');
    const used = new Set([...byLabel('Back & Biceps').exercises, ...byLabel('Chest & Triceps').exercises, ...byLabel('Shoulders & Triceps').exercises].map((e) => e.name));
    const armLifts = arms.exercises.filter((e) => e.muscleGroup === 'biceps' || e.muscleGroup === 'triceps');
    expect(armLifts.length).toBe(6);
    for (const e of armLifts) expect(used.has(e.name), e.name).toBe(false);
  });

  it('legs days have legs + abs, and the two leg days differ', () => {
    const legs = plan.days.filter((d) => d.focus === 'Legs & Abs');
    expect(legs.length).toBe(2);
    expect(legs[0]!.exercises.map((e) => e.name).join('|')).not.toBe(legs[1]!.exercises.map((e) => e.name).join('|'));
    for (const d of legs) expect((d.core?.length ?? 0)).toBeGreaterThanOrEqual(1);
  });

  it('no duplicate exercise within a day; combined days are 8 lifts (5 main + 3 accessory), legs 6', () => {
    for (const d of plan.days.filter((x) => !x.rest)) {
      const names = d.exercises.map((e) => e.name);
      expect(new Set(names).size, d.focus).toBe(names.length);
      expect(names.length, d.focus).toBe(d.focus === 'Legs & Abs' ? 6 : 8);
    }
  });

  it('beginner and advanced levels still produce every day with sensible sizes', () => {
    for (const lvl of ['beginner', 'advanced'] as const) {
      const p = week(lvl);
      for (const d of p.days.filter((x) => !x.rest)) {
        const combined = d.focus !== 'Legs & Abs';
        // combined days: 5 main + 3 (advanced 6 + 3, Arms & Core always 8); legs follow the level budget
        const exact = combined ? (lvl === 'advanced' && d.focus !== 'Arms & Core' ? 9 : 8) : { beginner: 5, advanced: 8 }[lvl];
        expect(d.exercises.length, `${lvl}/${d.focus}`).toBe(exact);
      }
    }
  });

  it('prints the week', () => {
    for (const d of plan.days) {
      // eslint-disable-next-line no-console
      console.log(`${(d.date ?? '').slice(5)} ${String(d.focus).padEnd(20)} ${d.rest ? '' : d.exercises.map((e) => `${e.name}[${e.muscleGroup}]`).join(', ')}`);
    }
  });
});

describe('combined split - robustness', () => {
  const LEVELS = ['beginner', 'intermediate', 'advanced'] as const;

  it('every training block (weeks 0-15) x every level keeps the rules', () => {
    for (let weeksSinceJoin = 0; weeksSinceJoin <= 15; weeksSinceJoin++) {
      for (const level of LEVELS) {
        const p = generateWeeklyWorkout('muscular', 'gym', {
          split: 'body_part', fitnessLevel: level, weeksSinceJoin, startDate: START, today: START, dayFocusOverride: MAP,
        });
        const tag = `week ${weeksSinceJoin}/${level}`;
        expect(p.days.map((d) => (d.rest ? 'Rest' : d.focus)), tag).toEqual([
          'Back & Biceps', 'Legs & Abs', 'Arms & Core', 'Rest', 'Chest & Triceps', 'Legs & Abs', 'Shoulders & Triceps',
        ]);
        const chest = p.days[4]!; const sh = p.days[6]!; const back = p.days[0]!;
        const tri = (d: typeof chest) => d.exercises.filter((e) => e.muscleGroup === 'triceps').map((e) => e.name);
        // user's rule at EVERY level: exactly 3 accessory lifts, 5 main (advanced 6)
        const check = (n: number, label: string) => expect(n, label).toBe(3);
        const mainWant = level === 'advanced' ? 6 : 5;
        expect(chest.exercises.filter((e) => e.muscleGroup === 'chest').length, `${tag} chest`).toBe(mainWant);
        expect(back.exercises.filter((e) => e.muscleGroup === 'back').length, `${tag} back`).toBe(mainWant);
        expect(sh.exercises.filter((e) => e.muscleGroup === 'shoulders' || e.muscleGroup === 'traps').length, `${tag} shoulders`).toBe(mainWant);
        check(tri(chest).length, `${tag} chest triceps`);
        check(tri(sh).length, `${tag} shoulder triceps`);
        for (const n of tri(sh)) expect(tri(chest), `${tag} overlap`).not.toContain(n);
        check(back.exercises.filter((e) => e.muscleGroup === 'biceps').length, `${tag} biceps`);
        for (const d of p.days.filter((x) => !x.rest)) {
          const isCombo = d.focus !== 'Legs & Abs';
          const size = isCombo ? (level === 'advanced' && d.focus !== 'Arms & Core' ? 9 : 8) : { beginner: 5, intermediate: 6, advanced: 8 }[level];
          expect(d.exercises.length, `${tag}/${d.focus} size`).toBe(size);
          expect(new Set(d.exercises.map((e) => e.name)).size, `${tag}/${d.focus} dupes`).toBe(d.exercises.length);
          expect((d.core?.length ?? 0) >= 1 || d.focus === 'Arms & Core', `${tag}/${d.focus} abs`).toBe(true);
          expect(d.warmup?.length ?? 0, `${tag}/${d.focus} warmup`).toBeGreaterThan(0);
          expect(d.cooldown?.length ?? 0, `${tag}/${d.focus} cooldown`).toBeGreaterThan(0);
          for (const e of d.exercises) {
            expect(e.sets, `${tag} sets`).toBeGreaterThanOrEqual(1);
            expect(e.sets).toBeLessThanOrEqual(6);
            expect(e.muscleGroup, `${tag}/${e.name} muscleGroup`).toBeTruthy();
            expect(e.cue, `${tag}/${e.name} cue`).toBeTruthy();
          }
        }
        if (level === 'beginner') for (const d of p.days) for (const e of d.exercises) expect(e.name.toLowerCase()).not.toMatch(/weighted|^deadlift$/);
        if (level !== 'advanced') for (const d of p.days) for (const e of d.exercises) expect(e.name.toLowerCase()).not.toContain('weighted');
      }
    }
  });

  it('is deterministic (same input -> identical plan)', () => {
    const a = JSON.stringify(week());
    const b = JSON.stringify(week());
    expect(a).toBe(b);
  });

  it('unknown focus name and empty map fall back to the normal rotation without crashing', () => {
    const bad = generateWeeklyWorkout('muscular', 'gym', { split: 'body_part', startDate: START, today: START, dayFocusOverride: { 0: 'Nonsense day', 1: '' } });
    expect(bad.days.filter((d) => !d.rest).length).toBeGreaterThan(0);
    for (const d of bad.days.filter((x) => !x.rest)) expect(d.exercises.length).toBeGreaterThan(0);
    const none = generateWeeklyWorkout('muscular', 'gym', { split: 'body_part', startDate: START, today: START, dayFocusOverride: {} });
    expect(none.days.some((d) => d.focus === 'Chest')).toBe(true);
  });

  it('old saved maps (classic focus names, e.g. "Chest") still work unchanged', () => {
    const old = generateWeeklyWorkout('muscular', 'gym', {
      split: 'body_part', startDate: START, today: START,
      dayFocusOverride: { 0: 'Chest', 1: 'Back', 2: 'Biceps & Forearms', 3: 'Rest', 4: 'Chest', 5: 'Legs & Abs', 6: 'Shoulders' },
    });
    expect(old.days.map((d) => (d.rest ? 'Rest' : d.focus))).toEqual(['Chest', 'Back', 'Biceps & Forearms', 'Rest', 'Chest', 'Legs & Abs', 'Shoulders']);
  });

  it('home location ignores gym-only combined days but still produces a full valid week', () => {
    const home = generateWeeklyWorkout('muscular', 'home', { split: 'body_part', startDate: START, today: START, dayFocusOverride: MAP });
    for (const d of home.days.filter((x) => !x.rest)) expect(d.exercises.length).toBeGreaterThan(0);
  });

  it('override only applies to body_part split (other splits are untouched)', () => {
    const ppl = generateWeeklyWorkout('muscular', 'gym', { split: 'push_pull_legs', startDate: START, today: START, dayFocusOverride: MAP });
    expect(ppl.days.some((d) => /&/.test(String(d.focus)) && /Biceps|Triceps/.test(String(d.focus)))).toBe(false);
  });

  it('the profile schema accepts the saved map and the parser drops invalid keys', async () => {
    const { parseDayFocusOverride } = await import('../src/modules/exercise/workoutGenerator');
    expect(parseDayFocusOverride({ '0': 'Back & Biceps', '7': 'x', 'a': 'y', '-1': 'z' })).toEqual({ 0: 'Back & Biceps' });
    expect(parseDayFocusOverride(undefined)).toBeUndefined();
    const { sensitiveSchema } = await import('../src/modules/profile/profile.schemas');
    const r = sensitiveSchema.safeParse({ sex: 'male', dob: '1998-04-01', currentWeightKg: 74, targetWeightKg: 69, bodyPartDayFocus: { '0': 'Back & Biceps' } });
    expect(r.success).toBe(true);
  });
});
