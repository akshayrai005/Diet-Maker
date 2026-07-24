import type { WeeklyReport, FoodEntry } from './report';

const esc = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const n = (x: number) => Math.round(x).toLocaleString();
const MEAL_ORDER = ['wakeup', 'breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner', 'bedtime'];
const slot = (s: string) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);
const clamp = (x: number, lo = 0, hi = 100) => Math.max(lo, Math.min(hi, x));

function fmtDate(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`);
  if (isNaN(d.getTime())) return iso;
  const wd = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getUTCDay()];
  const mo = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][d.getUTCMonth()];
  return `${wd} ${d.getUTCDate()} ${mo}`;
}

interface Extras {
  score: number;
  scoreWord: string;
  proteinAvg: number;
  waterAvgL: number;
  insights: Array<{ kind: 'good' | 'warn' | 'water'; text: string }>;
}

/** Deterministic overall score + insights from the report data. Pure presentation logic. */
function derive(r: WeeklyReport, spanDays: number): Extras {
  const target = r.targets?.dailyKcal ?? 0;
  const pTarget = r.targets?.proteinG ?? 0;
  const wTarget = r.targets?.waterMl ?? 0;
  const logged = r.days.length;
  const proteinAvg = logged ? r.days.reduce((s, d) => s + d.proteinG, 0) / logged : 0;
  const waterAvg = r.waterByDay.length ? r.waterByDay.reduce((s, w) => s + w.ml, 0) / r.waterByDay.length : 0;

  // Sub-scores (0..1)
  const cal = target > 0 && r.avgKcal != null ? clamp(100 - Math.abs(r.avgKcal - target) / target * 180, 0, 100) / 100 : 0.6;
  const prot = pTarget > 0 ? clamp((proteinAvg / pTarget) * 100, 0, 100) / 100 : 0.6;
  const hyd = wTarget > 0 ? clamp((waterAvg / wTarget) * 100, 0, 100) / 100 : 0.6;
  const consistency = clamp((logged / Math.min(spanDays, 7)) * 100, 0, 100) / 100;
  const score = Math.round((cal * 0.3 + prot * 0.3 + hyd * 0.2 + consistency * 0.2) * 100);
  const scoreWord = score >= 85 ? 'Excellent' : score >= 70 ? 'Good' : score >= 55 ? 'Fair' : 'Needs work';

  const insights: Extras['insights'] = [];
  if (pTarget > 0 && proteinAvg >= pTarget * 0.95) insights.push({ kind: 'good', text: `Protein is on point — averaging <b>${Math.round(proteinAvg)} g/day</b> against a ${Math.round(pTarget)} g goal. That preserves muscle while you lose fat.` });
  else if (pTarget > 0) insights.push({ kind: 'warn', text: `Protein is a bit low — averaging <b>${Math.round(proteinAvg)} g/day</b> vs a ${Math.round(pTarget)} g goal. Add dal, curd or eggs.` });
  if (wTarget > 0 && waterAvg < wTarget * 0.8) insights.push({ kind: 'water', text: `Hydration is below target — <b>${(waterAvg / 1000).toFixed(1)} L/day</b> vs ${(wTarget / 1000).toFixed(1)} L. Front-load water earlier in the day.` });
  else if (wTarget > 0) insights.push({ kind: 'good', text: `Good hydration — averaging <b>${(waterAvg / 1000).toFixed(1)} L/day</b>.` });
  if (r.weightDeltaKg != null && r.weightDeltaKg < 0) insights.push({ kind: 'good', text: `Weight is trending down <b>${Math.abs(r.weightDeltaKg)} kg</b> — a steady, safe pace.` });
  const overDays = target > 0 ? r.days.filter((d) => d.kcal > target * 1.15).length : 0;
  if (overDays > 0) insights.push({ kind: 'warn', text: `You went well over target on <b>${overDays} day${overDays > 1 ? 's' : ''}</b>. Pre-planning those days protects your deficit.` });

  return { score, scoreWord, proteinAvg, waterAvgL: waterAvg / 1000, insights };
}

function ring(score: number, color: string, size = 132): string {
  const r = 52, c = 2 * Math.PI * r;
  const off = c * (1 - clamp(score) / 100);
  return `<div class="ring" style="width:${size}px;height:${size}px"><svg viewBox="0 0 120 120">
    <circle cx="60" cy="60" r="${r}" fill="none" stroke="var(--surface-2)" stroke-width="12"/>
    <circle cx="60" cy="60" r="${r}" fill="none" stroke="${color}" stroke-width="12" stroke-linecap="round"
      stroke-dasharray="${c.toFixed(1)}" stroke-dashoffset="${off.toFixed(1)}" transform="rotate(-90 60 60)"/></svg>
    <div class="val"><div><div class="big num">${score}</div><div class="cap">/ 100</div></div></div></div>`;
}

function bar(pct: number, color: string): string {
  return `<div class="bar"><i style="width:${clamp(pct)}%;background:${color}"></i></div>`;
}

/** Compact kcal label, e.g. 1720 → "1.7k", 640 → "640". */
function kLabel(v: number): string {
  return v >= 1000 ? `${(v / 1000).toFixed(1).replace(/\.0$/, '')}k` : String(Math.round(v));
}

function trendChart(r: WeeklyReport): string {
  const days = r.days.slice(-14);
  if (!days.length) return '';
  const target = r.targets?.dailyKcal ?? 0;
  const max = Math.max(target, ...days.map((d) => d.kcal)) * 1.12 || 1;
  const W = 300, chartH = 104, topPad = 16, botPad = 18;
  const H = topPad + chartH + botPad;
  const step = W / days.length;
  const bw = Math.min(24, step - 7);
  const parts: string[] = [];

  // Target reference line + its value.
  if (target > 0) {
    const ty = topPad + chartH - (target / max) * chartH;
    parts.push(`<line x1="0" y1="${ty.toFixed(1)}" x2="${W}" y2="${ty.toFixed(1)}" stroke="var(--accent)" stroke-width="1" stroke-dasharray="4 4" opacity=".55"/>`);
    parts.push(`<text x="${W - 2}" y="${(ty - 3).toFixed(1)}" text-anchor="end" style="font-family:var(--mono);font-size:8px;fill:var(--accent)">target ${n(target)}</text>`);
  }

  days.forEach((d, i) => {
    const bh = Math.max(2, (d.kcal / max) * chartH);
    const cx = i * step + step / 2;
    const x = cx - bw / 2;
    const y = topPad + chartH - bh;
    const col = target > 0 && d.kcal > target * 1.15 ? 'var(--crit)' : target > 0 && d.kcal < target * 0.7 ? 'var(--warn)' : 'var(--good)';
    parts.push(`<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${bw.toFixed(1)}" height="${bh.toFixed(1)}" rx="3" fill="${col}"/>`);
    // value above the bar
    parts.push(`<text x="${cx.toFixed(1)}" y="${(y - 4).toFixed(1)}" text-anchor="middle" style="font-family:var(--mono);font-size:8px;fill:var(--muted)">${kLabel(d.kcal)}</text>`);
    // date below the axis (DD)
    parts.push(`<text x="${cx.toFixed(1)}" y="${(H - 5).toFixed(1)}" text-anchor="middle" style="font-family:var(--mono);font-size:8px;fill:var(--faint)">${d.date.slice(-2)}</text>`);
  });

  return `<svg viewBox="0 0 ${W} ${H}">${parts.join('')}</svg>`;
}

function mealBreakdown(entries: FoodEntry[]): string {
  const byMeal = new Map<string, FoodEntry[]>();
  for (const e of entries) { const a = byMeal.get(e.mealSlot) ?? []; a.push(e); byMeal.set(e.mealSlot, a); }
  const slots = [...byMeal.keys()].sort((a, b) => MEAL_ORDER.indexOf(a) - MEAL_ORDER.indexOf(b));
  if (!slots.length) return '';
  const icon: Record<string, string> = { breakfast: '🌅', lunch: '🍽️', dinner: '🌙', eveningsnack: '☕', midmorning: '🥤', wakeup: '🌄', bedtime: '🛏️' };
  return slots.map((s) => {
    const items = byMeal.get(s)!;
    const kcal = items.reduce((x, e) => x + e.kcal, 0);
    const prot = items.reduce((x, e) => x + e.proteinG, 0);
    // unique food names with counts
    const names = new Map<string, number>();
    for (const e of items) names.set(e.name, (names.get(e.name) ?? 0) + 1);
    const chips = [...names.entries()].slice(0, 8).map(([nm, c]) => `<span class="chip">${esc(nm)}${c > 1 ? ` <b>×${c}</b>` : ''}</span>`).join('');
    return `<div class="card meal"><div class="mh"><span class="mn">${icon[s] ?? '🍴'} ${slot(s)}</span><span class="mk num">${n(kcal)} kcal · ${Math.round(prot)} g P</span></div><div class="chips">${chips}</div></div>`;
  }).join('');
}

function dayCards(r: WeeklyReport): string {
  const target = r.targets?.dailyKcal ?? 0, pT = r.targets?.proteinG ?? 0, wT = r.targets?.waterMl ?? 2500;
  const water = new Map(r.waterByDay.map((w) => [w.date, w.ml]));
  const entriesByDate = new Map<string, FoodEntry[]>();
  for (const e of r.entries) { const a = entriesByDate.get(e.date) ?? []; a.push(e); entriesByDate.set(e.date, a); }
  const days = [...r.days].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 21);
  return days.map((d, i) => {
    const w = water.get(d.date) ?? 0;
    const meals = (entriesByDate.get(d.date) ?? []).slice().sort((a, b) => MEAL_ORDER.indexOf(a.mealSlot) - MEAL_ORDER.indexOf(b.mealSlot));
    const overish = target > 0 && d.kcal > target * 1.15;
    const badge = overish ? 'crit' : target > 0 && d.kcal >= target * 0.7 && d.proteinG >= pT * 0.7 ? 'good' : 'warn';
    const sc = clamp(Math.round(((target > 0 ? clamp(100 - Math.abs(d.kcal - target) / target * 180) : 60) * .5 + (pT > 0 ? clamp(d.proteinG / pT * 100) : 60) * .3 + clamp(w / wT * 100) * .2)));
    const mealRows = meals.length
      ? meals.map((m) => `<div class="dmeal"><div><span class="dm-n">${slot(m.mealSlot)}</span> <span class="dm-f">${esc(m.name)}</span></div><span class="dm-k num">${n(m.kcal)}</span></div>`).join('')
      : '<div class="dm-f" style="padding:6px 0">No items logged.</div>';
    return `<details class="day"${i < 2 ? ' open' : ''}>
      <summary>
        <div class="score-badge ${badge}">${sc}</div>
        <div class="day-date">${fmtDate(d.date)}</div>
        <div class="day-quick"><span>🔥 <b class="num">${n(d.kcal)}</b></span><span>🥩 <b class="num">${Math.round(d.proteinG)}g</b></span><span>💧 <b class="num">${(w / 1000).toFixed(1)}L</b></span></div>
        <span class="chev">▾</span>
      </summary>
      <div class="day-body">
        <div class="day-metrics">
          <div class="dm"><div class="k">Calories</div><div class="v num">${n(d.kcal)}</div>${bar(target > 0 ? d.kcal / target * 100 : 0, overish ? 'var(--crit)' : 'var(--good)')}</div>
          <div class="dm"><div class="k">Protein</div><div class="v num">${Math.round(d.proteinG)} g</div>${bar(pT > 0 ? d.proteinG / pT * 100 : 0, 'var(--good)')}</div>
          <div class="dm"><div class="k">Water</div><div class="v num">${(w / 1000).toFixed(1)} L</div>${bar(w / wT * 100, 'var(--water)')}</div>
        </div>
        <div class="day-meals">${mealRows}</div>
      </div>
    </details>`;
  }).join('');
}

/** Renders a full premium HTML report from the report data. Self-contained, print/PDF friendly. */
export interface ReportAnalysis {
  rating: { overall: number; grade: string; pillars: { label: string; score: number }[]; biggestLever: string };
  paragraphs: string[];
}

/** Grade → accent colour for the analysis ring. */
function gradeColor(grade: string): string {
  const g = grade.toUpperCase();
  if (g === 'A' || g === 'B') return 'var(--good)';
  if (g === 'C') return 'var(--warn)';
  return 'var(--crit)';
}

function analysisSection(a: ReportAnalysis): string {
  const bars = a.rating.pillars
    .map((p) => {
      const w = Math.max(0, Math.min(100, Math.round(p.score)));
      return `<div style="margin:6px 0"><div style="display:flex;justify-content:space-between;font-size:12px"><span>${esc(p.label)}</span><b>${w}</b></div><div style="height:8px;background:var(--surface-2);border-radius:4px;overflow:hidden"><div style="height:8px;width:${w}%;background:var(--accent);border-radius:4px"></div></div></div>`;
    })
    .join('');
  const body = a.paragraphs.map((t) => `<p style="margin:6px 0">${esc(t)}</p>`).join('');
  return `<section><div class="sh"><h2>Your Analysis</h2><span class="hint">coach read-out</span></div>
<div class="hero"><div class="card score-card">${ring(a.rating.overall, gradeColor(a.rating.grade))}<div class="verdict">Grade ${esc(a.rating.grade)}</div></div>
<div class="card" style="flex:1;align-content:start">${bars}</div></div>
<div class="card" style="margin-top:12px;font-size:13.5px">${body}</div></section>`;
}

export function renderReportHtml(r: WeeklyReport, opts: { label: string; analysis?: ReportAnalysis }): string {
  const x = derive(r, 30);
  const target = r.targets?.dailyKcal ?? 0;
  const pT = r.targets?.proteinG ?? 0;
  const wT = r.targets?.waterMl ?? 2500;
  const p = r.prediction;
  const meals = mealBreakdown(r.entries);
  const insights = x.insights.map((i) => `<div class="ins ${i.kind}"><div class="ic">${i.kind === 'good' ? '✓' : i.kind === 'water' ? '◐' : '!'}</div><div class="t">${i.text}</div></div>`).join('');

  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light only"><title>NutriAI Report</title>
<style>
:root{color-scheme:light;--bg:#F3F8F5;--surface:#fff;--surface-2:#EEF3F0;--line:#E1EAE5;--ink:#0F1A16;--muted:#5C6B64;--faint:#8A968F;--accent:#0E7A46;--accent-weak:#E4F2EA;--good:#16A34A;--water:#2563EB;--warn:#D97706;--crit:#DC2626;--good-bg:#E7F5EC;--water-bg:#E6EEFC;--warn-bg:#FBF0E0;--crit-bg:#FBE9E9;--mono:ui-monospace,Menlo,Consolas,monospace;--sans:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:var(--sans);line-height:1.5;font-variant-numeric:tabular-nums;-webkit-font-smoothing:antialiased}
.wrap{max-width:820px;margin:0 auto;padding:20px 16px 60px}
.num{font-variant-numeric:tabular-nums}
.eyebrow,.k,.lab{font-family:var(--mono);letter-spacing:.1em;text-transform:uppercase;color:var(--faint)}
header{display:flex;justify-content:space-between;align-items:flex-start;gap:14px;padding-bottom:16px;border-bottom:1px solid var(--line);margin-bottom:20px}
.brand{display:flex;align-items:center;gap:9px}.logo{width:28px;height:28px;border-radius:8px;background:var(--accent);color:#fff;display:grid;place-items:center;font-weight:700}
h1{margin:0;font-size:19px;font-weight:680}.sub{color:var(--muted);font-size:12.5px;margin-top:3px}
section{margin:24px 0;break-inside:avoid}.sh{display:flex;align-items:baseline;gap:10px;margin:0 0 12px}.sh h2{margin:0;font-size:14px;font-weight:680}.sh .hint{margin-left:auto;font-size:11.5px;color:var(--faint)}
.card{background:var(--surface);border:1px solid var(--line);border-radius:14px;box-shadow:0 1px 2px rgba(16,32,24,.04)}.pad{padding:16px}
.hero{display:grid;grid-template-columns:180px 1fr;gap:14px}@media(max-width:600px){.hero{grid-template-columns:1fr}}
.score-card{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;padding:18px;text-align:center}
.ring{position:relative}.ring svg{width:100%;height:100%;display:block}.ring .val{position:absolute;inset:0;display:grid;place-items:center}.ring .big{font-size:34px;font-weight:720;line-height:1}.ring .cap{font-family:var(--mono);font-size:9px;color:var(--muted)}
.verdict{font-weight:640;font-size:13.5px}
.grid{display:grid;gap:10px}.g3{grid-template-columns:repeat(3,1fr)}@media(max-width:600px){.g3{grid-template-columns:repeat(2,1fr)}}
.metric{padding:13px}.metric .lab{font-size:9.5px;display:flex;gap:6px;align-items:center}.metric .v{font-size:22px;font-weight:680;margin:5px 0 1px}.metric .v small{font-size:12px;color:var(--muted);font-weight:500}.metric .foot{font-size:11.5px;color:var(--muted)}
.dot{width:7px;height:7px;border-radius:50%;display:inline-block}
.bar{height:6px;border-radius:5px;background:var(--surface-2);overflow:hidden;margin-top:8px}.bar>i{display:block;height:100%;border-radius:5px}
.mrow{display:flex;justify-content:space-between;font-size:13px}.mrow .r{color:var(--muted);font-size:12px}
.ai{background:linear-gradient(180deg,var(--accent-weak),transparent)}.ai .lab{font-size:10px;color:var(--accent);display:flex;gap:7px;align-items:center}.ai p{margin:9px 0 0;font-size:14px;line-height:1.55}.spark{width:6px;height:6px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 3px var(--accent-weak)}
.timeline{display:flex;flex-direction:column;gap:10px}
.day{background:var(--surface);border:1px solid var(--line);border-radius:14px;overflow:hidden}.day[open]{border-color:color-mix(in srgb,var(--accent) 35%,var(--line))}
.day>summary{list-style:none;cursor:pointer;display:grid;grid-template-columns:auto 1fr auto auto;gap:12px;align-items:center;padding:12px 14px}.day>summary::-webkit-details-marker{display:none}
.score-badge{width:40px;height:40px;border-radius:11px;display:grid;place-items:center;font-weight:720;font-size:15px}.score-badge.good{background:var(--good-bg);color:var(--good)}.score-badge.warn{background:var(--warn-bg);color:var(--warn)}.score-badge.crit{background:var(--crit-bg);color:var(--crit)}
.day-date{font-weight:640;font-size:14px}.day-quick{display:flex;gap:14px;font-size:12px;color:var(--muted)}.day-quick b{color:var(--ink)}@media(max-width:600px){.day-quick{display:none}}
.chev{color:var(--faint);font-size:11px;transition:transform .2s}.day[open] .chev{transform:rotate(180deg)}
.day-body{padding:0 14px 14px;border-top:1px solid var(--line)}
.day-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;padding:14px 0}
.dm{background:var(--surface-2);border-radius:10px;padding:10px}.dm .k{font-family:var(--mono);font-size:9px;color:var(--faint);letter-spacing:.06em;text-transform:uppercase}.dm .v{font-size:15px;font-weight:680;margin-top:3px}
.day-meals{display:flex;flex-direction:column}.dmeal{display:flex;justify-content:space-between;gap:10px;font-size:12.5px;padding:6px 0;border-top:1px dashed var(--line)}.dmeal:first-child{border-top:0}.dm-n{font-weight:600}.dm-f{color:var(--muted)}.dm-k{font-family:var(--mono);font-size:11px;color:var(--muted)}
.chart{padding:14px}.chart h3{margin:0 0 6px;font-size:13px;font-weight:640}svg{display:block;width:100%;height:auto}.axis{font-family:var(--mono);font-size:9px;fill:var(--faint)}
.meal{padding:14px}.mh{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:9px}.mn{font-weight:640;font-size:13.5px}.mk{font-family:var(--mono);font-size:11.5px;color:var(--muted)}
.chips{display:flex;flex-wrap:wrap;gap:6px}.chip{font-size:12px;background:var(--surface-2);border:1px solid var(--line);border-radius:8px;padding:3px 8px}.chip b{font-family:var(--mono);font-size:10px;color:var(--muted)}
.ins{display:flex;gap:11px;padding:12px 14px;border-radius:11px;border:1px solid var(--line);align-items:flex-start}.ins+.ins{margin-top:8px}.ins .ic{width:21px;height:21px;border-radius:6px;flex:0 0 21px;display:grid;place-items:center;font-size:11px;font-weight:700}
.ins.good{background:color-mix(in srgb,var(--good-bg) 45%,transparent)}.ins.good .ic{background:var(--good-bg);color:var(--good)}
.ins.warn{background:color-mix(in srgb,var(--warn-bg) 45%,transparent)}.ins.warn .ic{background:var(--warn-bg);color:var(--warn)}
.ins.water{background:color-mix(in srgb,var(--water-bg) 45%,transparent)}.ins.water .ic{background:var(--water-bg);color:var(--water)}
.ins .t{font-size:13px}
.pred{display:grid;grid-template-columns:1.1fr 1fr}@media(max-width:600px){.pred{grid-template-columns:1fr}}
.pred .l{padding:18px;border-right:1px solid var(--line)}@media(max-width:600px){.pred .l{border-right:0;border-bottom:1px solid var(--line)}}
.pred .rng{font-size:27px;font-weight:720}.pred .rng small{font-size:14px;color:var(--muted);font-weight:500}
.conf{display:inline-flex;gap:7px;align-items:center;margin-top:9px;font-size:12px;color:var(--muted)}.cb{display:inline-flex;gap:2px}.cb i{width:5px;height:12px;border-radius:2px;background:var(--line)}.cb i.on{background:var(--accent)}
.pred .r{padding:18px;font-size:12.5px}.assume{list-style:none;margin:0;padding:0}.assume li{display:flex;padding:4px 0;color:var(--muted)}.assume li b{color:var(--ink);font-family:var(--mono);font-size:12px;margin-left:auto}
.disc{font-size:11px;color:var(--faint);margin-top:10px}
footer{margin-top:34px;padding-top:16px;border-top:1px solid var(--line);color:var(--faint);font-size:11px;line-height:1.6}
@media print{body{background:#fff}.card,.day{box-shadow:none;break-inside:avoid}.day{}*{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head><body><div class="wrap">

<header><div><div class="brand"><div class="logo">N</div><h1>Health &amp; Nutrition Report</h1></div>
<div class="sub"><b>${esc(r.name)}</b> · ${esc(opts.label)} · generated ${new Date(r.generatedAt).toDateString()}</div></div></header>

${opts.analysis ? analysisSection(opts.analysis) : ''}

<section><div class="sh"><h2>How you're doing</h2><span class="hint">${esc(opts.label).toLowerCase()}</span></div>
<div class="hero"><div class="card score-card">${ring(x.score, 'var(--accent)')}<div class="verdict">${x.scoreWord}</div></div>
<div class="grid g3" style="align-content:start">
<div class="card metric"><div class="lab"><span class="dot" style="background:var(--accent)"></span>Calories</div><div class="v num">${r.avgKcal != null ? n(r.avgKcal) : '—'}<small>${target ? ' /' + n(target) : ''}</small></div><div class="foot">avg/day${r.adherencePct != null ? ' · ' + r.adherencePct + '% of target' : ''}</div></div>
<div class="card metric"><div class="lab"><span class="dot" style="background:var(--good)"></span>Protein</div><div class="v num">${Math.round(x.proteinAvg)}<small>${pT ? ' /' + Math.round(pT) + ' g' : ' g'}</small></div><div class="foot">avg/day</div></div>
<div class="card metric"><div class="lab"><span class="dot" style="background:var(--water)"></span>Water</div><div class="v num">${x.waterAvgL.toFixed(1)}<small>${wT ? ' /' + (wT / 1000).toFixed(1) + ' L' : ' L'}</small></div><div class="foot">avg/day</div></div>
<div class="card metric"><div class="lab"><span class="dot" style="background:var(--good)"></span>Weight</div><div class="v num">${r.latestWeightKg ?? '—'}<small> kg</small></div><div class="foot">${r.weightDeltaKg != null ? (r.weightDeltaKg > 0 ? '+' : '') + r.weightDeltaKg + ' kg' : 'no check-in yet'}</div></div>
<div class="card metric"><div class="lab"><span class="dot" style="background:var(--accent)"></span>BMI</div><div class="v num">${r.bmi ?? '—'}</div><div class="foot">Asian cut-off</div></div>
<div class="card metric"><div class="lab"><span class="dot" style="background:var(--warn)"></span>Days logged</div><div class="v num">${r.allTime?.daysLogged ?? r.days.length}</div><div class="foot">${r.days.length} in this period</div></div>
</div></div></section>

${insights ? `<section><div class="sh"><h2>AI insights</h2><span class="hint">act on these</span></div>${insights}</section>` : ''}

${r.days.length ? `<section><div class="sh"><h2>Calorie trend</h2><span class="hint">daily vs target</span></div><div class="card chart">${trendChart(r)}</div></section>` : ''}

${r.days.length ? `<section style="break-before:page"><div class="sh"><h2>Daily health timeline</h2><span class="hint">tap a day</span></div><div class="timeline">${dayCards(r)}</div></section>` : ''}

${meals ? `<section style="break-before:page"><div class="sh"><h2>Meal breakdown</h2><span class="hint">across this period</span></div><div class="grid" style="grid-template-columns:1fr 1fr;gap:10px">${meals}</div></section>` : ''}

${p ? `<section><div class="sh"><h2>AI trend analysis</h2><span class="hint">a range, not a promise</span></div>
<div class="card pred"><div class="l"><div class="eyebrow" style="font-size:10px">Estimated weight · 4 weeks</div>
<div class="rng num" style="margin-top:5px">${p.projectedWeightKg != null ? (p.projectedWeightKg - 0.6).toFixed(1) + '–' + (p.projectedWeightKg + 0.6).toFixed(1) : '—'}<small> kg</small></div>
<div class="conf"><span class="cb"><i class="${r.days.length >= 5 ? 'on' : ''}"></i><i class="${r.days.length >= 5 ? 'on' : ''}"></i><i></i></span> ${r.days.length >= 5 ? 'Medium' : 'Low'} confidence · ${r.days.length} days logged</div></div>
<div class="r"><div class="eyebrow" style="font-size:10px;margin-bottom:7px">Assumptions</div><ul class="assume">
<li>Daily balance <b>${p.dailyBalanceKcal > 0 ? '+' : ''}${p.dailyBalanceKcal} kcal</b></li>
<li>Maintenance (TDEE) <b>${n(p.maintenanceKcal)}</b></li>
<li>Avg intake <b>${n(p.avgDailyIntake)}</b></li>
<li>~7,700 kcal ≈ <b>1 kg</b></li></ul>
<p class="disc">${esc(p.note)} Estimate only — not a medical prediction.</p></div></div></section>` : ''}

<footer><b style="color:var(--muted)">Educational summary, not a medical diagnosis.</b> Generated by NutriAI from your logged data — designed to be shared with your doctor, dietitian or coach. Figures reflect the days you logged.</footer>

</div></body></html>`;
}
