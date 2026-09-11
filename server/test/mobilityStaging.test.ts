import { describe, it, expect } from 'vitest';
import { determineMovementStage } from '../src/modules/exercise/mobilityStaging';

describe('determineMovementStage', () => {
  it('is always "full" when no mobility limitation is reported, regardless of weight', () => {
    expect(determineMovementStage({ reducedMobility: false, heightCm: 160, currentWeightKg: 95 }).stage).toBe('full');
  });

  it('the mom scenario: reducedMobility + high BMI -> diet_first, with a concrete resume weight', () => {
    // 160cm, 95kg -> BMI ~37.1
    const r = determineMovementStage({ reducedMobility: true, heightCm: 160, currentWeightKg: 95 });
    expect(r.stage).toBe('diet_first');
    expect(r.bmi).toBeGreaterThan(30);
    expect(r.resumeAroundWeightKg).toBeDefined();
    // BMI 30 at 160cm ~ 76.8kg
    expect(r.resumeAroundWeightKg!).toBeCloseTo(76.8, 0);
  });

  it('mid-range BMI with reducedMobility -> light_movement, not full or diet_first', () => {
    // 170cm, 80kg -> BMI ~27.7
    const r = determineMovementStage({ reducedMobility: true, heightCm: 170, currentWeightKg: 80 });
    expect(r.stage).toBe('light_movement');
  });

  it('reducedMobility but healthy-range BMI -> full (standard training still reasonable)', () => {
    // 170cm, 65kg -> BMI ~22.5
    const r = determineMovementStage({ reducedMobility: true, heightCm: 170, currentWeightKg: 65 });
    expect(r.stage).toBe('full');
  });

  it('generalises across different reported problems the same way - it is BMI-driven, not diagnosis-specific', () => {
    // Two different people, same height/weight, different implied "problem" (only the flag matters).
    const a = determineMovementStage({ reducedMobility: true, heightCm: 165, currentWeightKg: 90 });
    const b = determineMovementStage({ reducedMobility: true, heightCm: 165, currentWeightKg: 90 });
    expect(a.stage).toBe(b.stage);
    expect(a.bmi).toBe(b.bmi);
  });
});
