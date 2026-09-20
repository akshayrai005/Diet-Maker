import { Router } from 'express';
import { asyncHandler } from '../../lib/asyncHandler';
import { requireAuth, type AuthedRequest } from '../../middleware/auth';
import { getGrocery, getWeeklyReport, getGamification, getReportView, getReportData } from './reports.service';
import { getAnalysis } from './analysis.service';
import { reportToCsv } from './report';
import { renderReportPdf } from './pdf';
import { tzOffsetMin } from '../../lib/tz';

export const reportsRouter = Router();

// Coach-voice narrative analysis + explainable rating - GET /report/analysis.
reportsRouter.get(
  '/report/analysis',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const { narrative, rating } = await getAnalysis(req.user!.id, tzOffsetMin(req));
    res.json({ narrative, rating });
  }),
);

reportsRouter.get(
  '/grocery',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const grocery = await getGrocery(req.user!.id);
    res.json({ grocery });
  }),
);

// In-app HTML report - GET /report/view?range=weekly|monthly&count=N  → { html }.
reportsRouter.get(
  '/report/view',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const range = req.query.range === 'monthly' ? 'monthly' : 'weekly';
    const count = Number(req.query.count);
    const html = await getReportView(req.user!.id, range, Number.isFinite(count) ? count : 1);
    res.json({ html });
  }),
);

reportsRouter.get(
  '/gamification',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const gamification = await getGamification(req.user!.id);
    res.json({ gamification });
  }),
);

reportsRouter.get(
  '/reports/weekly.json',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const report = await getWeeklyReport(req.user!.id, new Date().toISOString());
    res.json({ report });
  }),
);

reportsRouter.get(
  '/reports/weekly.csv',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const report = await getWeeklyReport(req.user!.id, new Date().toISOString());
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', 'attachment; filename="kaizen-weekly.csv"');
    res.send(reportToCsv(report));
  }),
);

// Downloadable AI report - GET /reports/report.pdf?range=weekly|monthly  (also /reports/report.html).
reportsRouter.get(
  '/reports/report.pdf',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const range = req.query.range === 'monthly' ? 'monthly' : 'weekly';
    const { report, insights, label } = await getReportData(req.user!.id, range, 1);
    const pdf = await renderReportPdf(report, insights, label);
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="kaizen-${range}-report.pdf"`);
    res.send(pdf);
  }),
);

reportsRouter.get(
  '/reports/report.html',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const range = req.query.range === 'monthly' ? 'monthly' : 'weekly';
    const html = await getReportView(req.user!.id, range, 1);
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="kaizen-${range}-report.html"`);
    res.send(html);
  }),
);

reportsRouter.get(
  '/reports/weekly.pdf',
  requireAuth,
  asyncHandler(async (req: AuthedRequest, res) => {
    const report = await getWeeklyReport(req.user!.id, new Date().toISOString());
    const pdf = await renderReportPdf(report);
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', 'attachment; filename="kaizen-weekly.pdf"');
    res.send(pdf);
  }),
);
