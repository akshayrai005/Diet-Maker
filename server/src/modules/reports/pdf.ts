import PDFDocument from 'pdfkit';
import type { WeeklyReport } from './report';
import type { ReportInsights } from './reportInsights';

// Palette
// Kaizen spectrum. The names are kept (GREEN = the main accent) so the layout code below is unchanged.
const SPECTRUM = ['#F57C00', '#E91E63', '#8E24AA', '#3949AB'];
const GREEN = '#8E24AA';
const GREEN_DEEP = '#6A1B9A';
const GREEN_TINT = '#F6EAF9';
const AMBER = '#E64A19';
const GREY = '#6B7280';
const INK = '#111827';
const ROW_ALT = '#F6F8F6';

const PAGE_LEFT = 50;
const PAGE_RIGHT = 545;
const CONTENT_W = PAGE_RIGHT - PAGE_LEFT; // 495
const PAGE_BOTTOM = 792;

const slotLabel = (s: string) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);
const MEAL_ORDER = ['wakeup', 'breakfast', 'midmorning', 'lunch', 'eveningsnack', 'dinner', 'bedtime'];

/** "2026-07-24" -> "Thu, 24 Jul 2026". */
function formatDate(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`);
  if (isNaN(d.getTime())) return iso;
  const wd = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getUTCDay()];
  const mo = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][d.getUTCMonth()];
  return `${wd}, ${d.getUTCDate()} ${mo} ${d.getUTCFullYear()}`;
}

/** Renders a WeeklyReport to a colourful, well-organised PDF Buffer (pdfkit, pure JS). */
export function renderReportPdf(r: WeeklyReport, insights?: ReportInsights, label = 'This week'): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    sectionIdx = 0;
    const doc = new PDFDocument({ size: 'A4', margin: 50 });
    const chunks: Buffer[] = [];
    doc.on('data', (c: Buffer) => chunks.push(c));
    doc.on('end', () => resolve(Buffer.concat(chunks)));
    doc.on('error', reject);

    const ensure = (need: number): boolean => {
      if (doc.y + need > PAGE_BOTTOM) {
        doc.addPage();
        return true;
      }
      return false;
    };

    // ---- Header band ----
    const grad = doc.linearGradient(0, 0, doc.page.width, 0);
    SPECTRUM.forEach((c, i) => grad.stop(i / (SPECTRUM.length - 1), c));
    doc.rect(0, 0, doc.page.width, 90).fill(grad);
    doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(24).text('Kaizen', PAGE_LEFT, 28);
    doc.font('Helvetica').fontSize(12).fillColor('#FFFFFF').text('Health & Fitness Report', PAGE_LEFT, 58);
    doc.fontSize(10).fillColor('#FFFFFF').text(
      `${r.name}   ·   ${new Date(r.generatedAt).toDateString()}`,
      PAGE_LEFT,
      74,
    );
    doc.y = 110;

    // ---- Summary stat cards ----
    sectionHeader(doc, `${label} at a glance`);
    const consumed = r.avgKcal ?? 0;
    const target = r.targets?.dailyKcal ?? 0;
    statCards(doc, [
      ['Avg calories/day', `${Math.round(consumed)}`, GREEN],
      ['Daily target', target ? `${Math.round(target)}` : '-', INK],
      ['Adherence', r.adherencePct != null ? `${r.adherencePct}%` : '-', r.adherencePct != null && r.adherencePct <= 110 ? GREEN : AMBER],
      ['BMI', r.bmi != null ? `${r.bmi}` : '-', INK],
    ]);

    doc.font('Helvetica').fontSize(10).fillColor(GREY);
    const wLine: string[] = [];
    if (r.latestWeightKg != null) wLine.push(`Latest weight: ${r.latestWeightKg} kg`);
    if (r.weightDeltaKg != null) wLine.push(`Change: ${r.weightDeltaKg > 0 ? '+' : ''}${r.weightDeltaKg} kg`);
    if (wLine.length) doc.text(wLine.join('    ·    '), PAGE_LEFT, doc.y);
    doc.moveDown(1);

    // ---- Coach read-out (AI) ----
    if (insights) {
      ensure(120);
      sectionHeader(doc, `Coach read-out - ${label}`);
      doc.font('Helvetica-Bold').fontSize(11).fillColor(INK).text(insights.headline, PAGE_LEFT, doc.y, { width: CONTENT_W });
      doc.moveDown(0.4);
      const block = (title: string, items: string[]) => {
        if (!items.length) return;
        ensure(40 + items.length * 14);
        doc.font('Helvetica-Bold').fontSize(10).fillColor(GREEN_DEEP).text(title, PAGE_LEFT, doc.y);
        doc.font('Helvetica').fontSize(10).fillColor(INK);
        items.forEach((t) => doc.text(`•  ${t}`, PAGE_LEFT + 6, doc.y + 2, { width: CONTENT_W - 6 }));
        doc.moveDown(0.5);
      };
      block('Eating', insights.eat);
      block('Drinking', insights.drink);
      block('Training', insights.exercise);
      block('Do this next week', insights.nextWeek);
      doc.moveDown(0.5);
    }

    // ---- Daily calories vs target ----
    ensure(140);
    sectionHeader(doc, 'Daily calories vs target');
    tableHeader(doc, ['Date', 'Calories', 'Protein (g)', 'vs Target'], [150, 110, 110, 125]);
    if (r.days.length === 0) {
      emptyRow(doc, 'No entries logged yet.');
    } else {
      r.days.forEach((d, i) => {
        ensure(22);
        const status = target > 0 ? (d.kcal <= target * 1.1 ? 'On target' : 'Over') : '-';
        const statusColor = status === 'On target' ? GREEN : status === 'Over' ? AMBER : GREY;
        row(doc, [d.date, String(d.kcal), String(d.proteinG), status], [150, 110, 110, 125], i, [INK, INK, INK, statusColor]);
      });
    }
    doc.moveDown(0.6);

    // Totals + body maximum (TDEE).
    statCards(doc, [
      ['Total intake (7d)', `${r.totalIntakeKcal}`, GREEN],
      ['Total target (7d)', r.totalTargetKcal != null ? `${r.totalTargetKcal}` : '-', INK],
      ['Body max/day (TDEE)', r.maintenanceKcal != null ? `${Math.round(r.maintenanceKcal)}` : '-', AMBER],
    ]);

    // AI energy-balance prediction.
    if (r.prediction) predictionBox(doc, r.prediction);
    doc.moveDown(1);

    // ---- What you ate - ONE fully-gridded table (Date / Meal / Item / Qty / kcal) ----
    if (r.entries.length) {
      ensure(120);
      sectionHeader(doc, 'What you ate (last 7 days)');
      const widths = [95, 90, 190, 55, 65];
      // Newest date first; within a date, ordered breakfast → dinner.
      const sorted = r.entries.slice().sort(
        (a, b) => b.date.localeCompare(a.date) || MEAL_ORDER.indexOf(a.mealSlot) - MEAL_ORDER.indexOf(b.mealSlot),
      );
      gridHeader(doc, ['Date', 'Meal', 'Item', 'Qty', 'kcal'], widths);
      let prevDate = '';
      sorted.forEach((e, i) => {
        if (ensure(20)) {
          gridHeader(doc, ['Date', 'Meal', 'Item', 'Qty', 'kcal'], widths);
          prevDate = '';
        }
        // Only print the date on its first row (merged look), but keep every cell bordered.
        const dateCell = e.date === prevDate ? '' : formatDate(e.date);
        prevDate = e.date;
        gridRow(doc, [dateCell, slotLabel(e.mealSlot), e.name, `${e.grams} g`, String(e.kcal)], widths, i, [GREY, GREEN_DEEP, INK, INK, INK]);
      });
      doc.moveDown(1);
    }

    // ---- Training ----
    if (r.exercise && r.exercise.totalSets) {
      const ex = r.exercise;
      ensure(150);
      sectionHeader(doc, 'Training');
      statCards(doc, [
        ['Workouts', `${ex.sessions}`, GREEN],
        ['Active days', `${ex.activeDays}`, INK],
        ['Sets', `${ex.totalSets}`, INK],
        ['Kcal burned', `${ex.kcalBurned}`, AMBER],
      ]);
      doc.moveDown(0.5);
      if (ex.byMuscle.length) {
        tableHeader(doc, ['Muscle group', 'Sets'], [300, 195]);
        ex.byMuscle.forEach((m, i) => {
          ensure(22);
          row(doc, [m.group, String(m.sets)], [300, 195], i, [INK, GREEN]);
        });
      }
      if (ex.top.length) {
        doc.moveDown(0.6);
        ensure(60);
        tableHeader(doc, ['Most done', 'Sets', 'Best weight'], [280, 90, 125]);
        ex.top.forEach((t, i) => {
          ensure(22);
          row(doc, [t.name, String(t.sets), t.bestKg != null ? `${t.bestKg} kg` : '-'], [280, 90, 125], i, [INK, GREEN, GREY]);
        });
      }
      doc.moveDown(1);
    }

    // ---- Water intake ----
    if (r.waterByDay.length) {
      ensure(120);
      sectionHeader(doc, 'Water intake');
      tableHeader(doc, ['Date', 'Water (ml)', 'Glasses (~250ml)'], [180, 150, 165]);
      r.waterByDay.forEach((w, i) => {
        ensure(22);
        row(doc, [w.date, String(w.ml), `${Math.round(w.ml / 250)}`], [180, 150, 165], i, [GREY, '#0EA5E9', INK]);
      });
      doc.moveDown(1);
    }

    // ---- All-time record ----
    if (r.allTime) {
      ensure(110);
      sectionHeader(doc, 'All-time record');
      const a = r.allTime;
      statCards(doc, [
        ['Days logged', `${a.daysLogged}`, GREEN],
        ['Total calories', `${a.totalKcal.toLocaleString()}`, INK],
        ['Avg/day', `${a.avgDailyKcal}`, INK],
        ['Total water', `${(a.totalWaterMl / 1000).toFixed(1)} L`, '#0EA5E9'],
      ]);
      doc.moveDown(1);
    }

    // ---- Footer ----
    ensure(40);
    doc.moveDown(0.5);
    doc.font('Helvetica-Oblique').fontSize(9).fillColor(GREY).text(r.disclaimer, PAGE_LEFT, doc.y, { width: CONTENT_W, align: 'center' });

    doc.end();
  });
}

// ---------------------------------------------------------------------------
// Drawing helpers
// ---------------------------------------------------------------------------

let sectionIdx = 0;
function sectionHeader(doc: PDFKit.PDFDocument, title: string) {
  const y = doc.y;
  const c = SPECTRUM[sectionIdx++ % SPECTRUM.length]!;
  doc.rect(PAGE_LEFT, y, 5, 16).fill(c);
  doc.fillColor(c).font('Helvetica-Bold').fontSize(14).text(title, PAGE_LEFT + 12, y);
  doc.moveDown(0.6);
  doc.fillColor(INK);
}

function statCards(doc: PDFKit.PDFDocument, cards: Array<[string, string, string]>) {
  const gap = 10;
  const w = (CONTENT_W - gap * (cards.length - 1)) / cards.length;
  const y = doc.y;
  const h = 54;
  cards.forEach(([label, value, color], i) => {
    const x = PAGE_LEFT + i * (w + gap);
    const accent = SPECTRUM[i % SPECTRUM.length]!;
    doc.roundedRect(x, y, w, h, 8).fill(GREEN_TINT);
    doc.roundedRect(x, y, w, 5, 2).fill(accent);
    doc.fillColor(color).font('Helvetica-Bold').fontSize(18).text(value, x, y + 10, { width: w, align: 'center' });
    doc.fillColor(GREY).font('Helvetica').fontSize(8).text(label.toUpperCase(), x, y + 34, { width: w, align: 'center' });
  });
  doc.y = y + h + 6;
  doc.fillColor(INK);
}

const GRID_BORDER = '#C9CFD6';

/** Draws the cell borders (verticals + bottom) for one fully-gridded row. */
function cellBorders(doc: PDFKit.PDFDocument, y: number, h: number, widths: number[]) {
  doc.save();
  doc.lineWidth(0.5).strokeColor(GRID_BORDER);
  let x = PAGE_LEFT;
  for (let i = 0; i <= widths.length; i++) {
    doc.moveTo(x, y).lineTo(x, y + h).stroke();
    if (i < widths.length) x += widths[i]!;
  }
  doc.moveTo(PAGE_LEFT, y + h).lineTo(x, y + h).stroke();
  doc.restore();
}

/** Green header row of a fully-gridded table. */
function gridHeader(doc: PDFKit.PDFDocument, cols: string[], widths: number[]) {
  const y = doc.y;
  const h = 20;
  const totalW = widths.reduce((a, b) => a + b, 0);
  doc.rect(PAGE_LEFT, y, totalW, h).fill(GREEN);
  let x = PAGE_LEFT + 5;
  doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(9);
  cols.forEach((c, i) => {
    doc.text(c.toUpperCase(), x, y + 6, { width: widths[i]! - 8, ellipsis: true });
    x += widths[i]!;
  });
  cellBorders(doc, y, h, widths);
  doc.y = y + h;
  doc.fillColor(INK);
}

/** One fully-gridded (all-border) data row. */
function gridRow(doc: PDFKit.PDFDocument, cells: string[], widths: number[], index: number, colors: string[]) {
  const y = doc.y;
  const h = 18;
  const totalW = widths.reduce((a, b) => a + b, 0);
  if (index % 2 === 1) doc.rect(PAGE_LEFT, y, totalW, h).fill(ROW_ALT);
  let x = PAGE_LEFT + 5;
  doc.font('Helvetica').fontSize(8.5);
  cells.forEach((c, i) => {
    doc.fillColor(colors[i] ?? INK).text(c, x, y + 5, { width: widths[i]! - 8, ellipsis: true });
    x += widths[i]!;
  });
  cellBorders(doc, y, h, widths);
  doc.y = y + h;
  doc.fillColor(INK);
}

/** Highlighted next-month weight-prediction panel. */
function predictionBox(doc: PDFKit.PDFDocument, p: import('./report').Prediction) {
  const y = doc.y;
  const h = 62;
  doc.roundedRect(PAGE_LEFT, y, CONTENT_W, h, 8).fill('#FFF7ED');
  doc.fillColor(AMBER).font('Helvetica-Bold').fontSize(11).text('AI weight prediction  ·  next month', PAGE_LEFT + 12, y + 10);
  const weight = p.projectedWeightKg != null ? `${p.projectedWeightKg} kg` : '-';
  const delta = p.projectedMonthlyDeltaKg;
  const deltaStr = `${delta > 0 ? '+' : ''}${delta} kg/month`;
  const deltaColor = delta < 0 ? GREEN : delta > 0 ? AMBER : GREY;
  doc.fillColor(INK).font('Helvetica-Bold').fontSize(17).text(weight, PAGE_LEFT + 12, y + 26, { continued: true });
  doc.font('Helvetica').fontSize(10).fillColor(deltaColor).text(`   ${deltaStr}   ·   balance ${p.dailyBalanceKcal > 0 ? '+' : ''}${p.dailyBalanceKcal} kcal/day`);
  doc.font('Helvetica').fontSize(8.5).fillColor(GREY).text(p.note, PAGE_LEFT + 12, y + 48, { width: CONTENT_W - 24 });
  doc.y = y + h + 6;
  doc.fillColor(INK);
}

function tableHeader(doc: PDFKit.PDFDocument, cols: string[], widths: number[]) {
  const y = doc.y;
  doc.rect(PAGE_LEFT, y, CONTENT_W, 20).fill(GREEN);
  let x = PAGE_LEFT + 6;
  doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(9);
  cols.forEach((c, i) => {
    doc.text(c.toUpperCase(), x, y + 6, { width: widths[i]! - 8, ellipsis: true });
    x += widths[i]!;
  });
  doc.y = y + 20;
  doc.fillColor(INK);
}

function row(
  doc: PDFKit.PDFDocument,
  cells: string[],
  widths: number[],
  index: number,
  colors: string[],
) {
  const y = doc.y;
  const h = 20;
  if (index % 2 === 1) doc.rect(PAGE_LEFT, y, CONTENT_W, h).fill(ROW_ALT);
  let x = PAGE_LEFT + 6;
  doc.font('Helvetica').fontSize(9);
  cells.forEach((c, i) => {
    doc.fillColor(colors[i] ?? INK).text(c, x, y + 6, { width: widths[i]! - 8, ellipsis: true });
    x += widths[i]!;
  });
  doc.y = y + h;
  doc.fillColor(INK);
}

function emptyRow(doc: PDFKit.PDFDocument, text: string) {
  const y = doc.y;
  doc.font('Helvetica-Oblique').fontSize(9).fillColor(GREY).text(text, PAGE_LEFT + 6, y + 6);
  doc.y = y + 20;
  doc.fillColor(INK);
}
