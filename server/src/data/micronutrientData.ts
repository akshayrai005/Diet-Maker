import type { Micronutrients } from '../calc/micronutrients';

/**
 * MEASURED micronutrients per 100 g of the food AS SEEDED (cooked/prepared where the seed is), keyed
 * by the seed food `id`. Values are from ICMR-NIN "Indian Food Composition Tables 2017/2020" and
 * USDA FoodData Central, rounded to sensible precision. Only nutrients a food is a meaningful source
 * of are filled; anything omitted stays NULL (unknown) - never 0, so it can never become a false
 * deficiency. Cooked-dish values are diluted for water/oil vs the raw ingredient.
 *
 * Sources: ICMR-NIN IFCT 2017; USDA FDC (fdc.nal.usda.gov). Educational, not lab-personalised.
 */
export const MICRONUTRIENTS_PER_100G: Record<string, Partial<Micronutrients>> = {
  // ---- legumes / dals (cooked, diluted) ----
  'dal-tadka': { ironMg: 1.5, folateMcg: 60, magnesiumMg: 25, potassiumMg: 260, zincMg: 1.0, phosphorusMg: 75, vitaminB1Mg: 0.1, manganeseMg: 0.4 },
  rajma: { ironMg: 2.2, folateMcg: 130, magnesiumMg: 45, potassiumMg: 405, zincMg: 1.0, phosphorusMg: 140, manganeseMg: 0.4, copperMcg: 210 },
  chole: { ironMg: 2.9, folateMcg: 172, magnesiumMg: 48, potassiumMg: 290, zincMg: 1.5, phosphorusMg: 168, manganeseMg: 1.0, copperMcg: 350, vitaminB6Mg: 0.14 },
  'roasted-chana': { ironMg: 4.9, folateMcg: 172, magnesiumMg: 79, potassiumMg: 291, zincMg: 2.8, phosphorusMg: 168, manganeseMg: 1.7, copperMcg: 660, vitaminB6Mg: 0.3 },
  'sprouts-salad': { ironMg: 1.5, folateMcg: 100, vitaminCMg: 15, magnesiumMg: 40, potassiumMg: 150, zincMg: 0.8 },
  'moong-chilla': { ironMg: 2.5, folateMcg: 110, magnesiumMg: 50, potassiumMg: 300, zincMg: 1.4, phosphorusMg: 130 },
  'besan-chilla': { ironMg: 2.4, folateMcg: 110, magnesiumMg: 60, potassiumMg: 320, zincMg: 1.3, phosphorusMg: 130, copperMcg: 400 },
  sambar: { ironMg: 1.1, folateMcg: 40, magnesiumMg: 22, potassiumMg: 220, vitaminCMg: 6, zincMg: 0.6 },

  // ---- grains ----
  roti: { ironMg: 1.5, magnesiumMg: 40, potassiumMg: 120, zincMg: 1.0, phosphorusMg: 100, vitaminB3Mg: 2.0, manganeseMg: 1.5, seleniumMcg: 25 },
  'bajra-roti': { ironMg: 3.0, magnesiumMg: 76, potassiumMg: 130, zincMg: 1.5, phosphorusMg: 145, manganeseMg: 1.2 },
  'brown-rice': { ironMg: 0.5, magnesiumMg: 43, potassiumMg: 43, zincMg: 0.6, phosphorusMg: 83, manganeseMg: 0.9, vitaminB3Mg: 1.5, seleniumMcg: 10 },
  'white-rice': { ironMg: 0.2, magnesiumMg: 12, potassiumMg: 35, zincMg: 0.5, phosphorusMg: 43, manganeseMg: 0.5 },
  poha: { ironMg: 1.2, magnesiumMg: 20, potassiumMg: 30, phosphorusMg: 40 },
  'oats-porridge': { ironMg: 0.9, magnesiumMg: 26, potassiumMg: 40, zincMg: 0.9, phosphorusMg: 77, manganeseMg: 0.6, vitaminB1Mg: 0.1 },
  quinoa: { ironMg: 1.5, magnesiumMg: 64, potassiumMg: 172, zincMg: 1.1, phosphorusMg: 152, manganeseMg: 0.6, folateMcg: 42 },

  // ---- greens / veg / fruit ----
  palak: { ironMg: 3.6, calciumMg: 136, magnesiumMg: 79, potassiumMg: 466, vitaminAMcg: 469, vitaminCMg: 9.8, vitaminKMcg: 493, folateMcg: 146, vitaminEMg: 2.0 },
  'mixed-veg-sabzi': { ironMg: 1.0, calciumMg: 40, magnesiumMg: 20, potassiumMg: 250, vitaminAMcg: 200, vitaminCMg: 20, vitaminKMcg: 60, folateMcg: 40 },
  banana: { potassiumMg: 358, vitaminB6Mg: 0.37, vitaminCMg: 8.7, magnesiumMg: 27, manganeseMg: 0.27 },
  apple: { potassiumMg: 107, vitaminCMg: 4.6 },

  // ---- dairy / egg ----
  'warm-milk': { calciumMg: 120, phosphorusMg: 90, potassiumMg: 150, vitaminB12Mcg: 0.4, vitaminB2Mg: 0.18, vitaminAMcg: 46, iodineMcg: 15 },
  'turmeric-milk': { calciumMg: 115, phosphorusMg: 88, potassiumMg: 145, vitaminB12Mcg: 0.4, vitaminB2Mg: 0.17, iodineMcg: 14 },
  curd: { calciumMg: 120, phosphorusMg: 95, potassiumMg: 155, vitaminB12Mcg: 0.4, vitaminB2Mg: 0.14, zincMg: 0.6, iodineMcg: 15 },
  buttermilk: { calciumMg: 116, phosphorusMg: 90, potassiumMg: 150, vitaminB12Mcg: 0.4, vitaminB2Mg: 0.15 },
  paneer: { calciumMg: 480, phosphorusMg: 340, vitaminB12Mcg: 0.9, zincMg: 1.6, vitaminAMcg: 120, vitaminB2Mg: 0.2 },
  'boiled-egg': { ironMg: 1.2, vitaminB12Mcg: 1.1, vitaminDMcg: 2.0, vitaminAMcg: 149, seleniumMcg: 30, vitaminB2Mg: 0.5, phosphorusMg: 198, zincMg: 1.3, folateMcg: 44, iodineMcg: 25 },
  'egg-curry': { ironMg: 1.0, vitaminB12Mcg: 0.7, vitaminDMcg: 1.2, vitaminAMcg: 110, seleniumMcg: 20, phosphorusMg: 140, zincMg: 0.9 },

  // ---- non-veg ----
  'chicken-breast': { ironMg: 1.0, vitaminB12Mcg: 0.3, vitaminB3Mg: 13, vitaminB6Mg: 0.6, phosphorusMg: 220, zincMg: 1.0, seleniumMcg: 27, potassiumMg: 256 },
  'fish-curry': { vitaminB12Mcg: 1.2, vitaminDMcg: 2.0, seleniumMcg: 30, phosphorusMg: 200, iodineMcg: 30, potassiumMg: 300, vitaminB3Mg: 3.0 },

  // ---- nuts / seeds / soy ----
  'soaked-almonds': { calciumMg: 269, ironMg: 3.7, magnesiumMg: 270, potassiumMg: 733, zincMg: 3.1, phosphorusMg: 481, vitaminEMg: 25.6, vitaminB2Mg: 1.1, manganeseMg: 2.2, copperMcg: 1000 },
  peanuts: { ironMg: 2.3, magnesiumMg: 168, potassiumMg: 705, zincMg: 3.3, phosphorusMg: 376, vitaminB3Mg: 12, vitaminEMg: 8.3, folateMcg: 240, copperMcg: 1140, manganeseMg: 1.9, vitaminB6Mg: 0.3 },
  tofu: { calciumMg: 350, ironMg: 2.7, magnesiumMg: 60, potassiumMg: 120, zincMg: 1.6, phosphorusMg: 190, copperMcg: 190, manganeseMg: 0.6, seleniumMcg: 9 },

  // ---- Phase 5 seed expansion ----
  'ragi-porridge': { calciumMg: 220, ironMg: 1.8, magnesiumMg: 60, potassiumMg: 120, phosphorusMg: 130, manganeseMg: 1.2 },
  'moong-dal': { ironMg: 1.4, folateMcg: 80, magnesiumMg: 40, potassiumMg: 260, zincMg: 0.9, phosphorusMg: 90 },
  'masoor-dal': { ironMg: 3.3, folateMcg: 180, magnesiumMg: 36, potassiumMg: 310, zincMg: 1.3, phosphorusMg: 180, copperMcg: 250 },
  'sweet-potato': { potassiumMg: 337, vitaminAMcg: 709, vitaminCMg: 12, manganeseMg: 0.4, vitaminB6Mg: 0.2 },
  papaya: { vitaminCMg: 60, folateMcg: 37, potassiumMg: 182, vitaminAMcg: 47 },
  'chicken-curry': { ironMg: 1.2, vitaminB12Mcg: 0.3, vitaminB3Mg: 8, vitaminB6Mg: 0.4, phosphorusMg: 180, zincMg: 1.2, seleniumMcg: 20 },
  'palak-paneer': { calciumMg: 300, ironMg: 2.5, magnesiumMg: 55, potassiumMg: 320, vitaminAMcg: 300, vitaminKMcg: 250, vitaminB12Mcg: 0.5 },

  // ---- Final coverage pass (remaining catalog foods; ICMR-NIN 2020 / USDA FDC, per 100 g) ----
  'aloo-paratha': { ironMg: 1.6, calciumMg: 25, magnesiumMg: 30, potassiumMg: 200, zincMg: 0.8, phosphorusMg: 90, vitaminCMg: 8, folateMcg: 20, vitaminAMcg: 5, vitaminB3Mg: 1.5, vitaminB6Mg: 0.2, manganeseMg: 0.8, seleniumMcg: 15 },
  'chicken-tikka': { ironMg: 1.0, calciumMg: 15, magnesiumMg: 25, potassiumMg: 250, zincMg: 1.5, phosphorusMg: 200, vitaminB12Mcg: 0.3, vitaminB3Mg: 9, vitaminB6Mg: 0.5, seleniumMcg: 22, vitaminAMcg: 10 },
  'coconut-water': { ironMg: 0.3, calciumMg: 24, magnesiumMg: 25, potassiumMg: 250, vitaminCMg: 2.4 },
  'cucumber-salad': { ironMg: 0.4, calciumMg: 16, magnesiumMg: 12, potassiumMg: 180, vitaminCMg: 12, folateMcg: 20, vitaminAMcg: 40, vitaminKMcg: 8 },
  daliya: { ironMg: 1.4, calciumMg: 20, magnesiumMg: 35, potassiumMg: 130, zincMg: 0.9, phosphorusMg: 90, folateMcg: 20, vitaminAMcg: 30, vitaminB3Mg: 1.5, manganeseMg: 1.0 },
  dhokla: { ironMg: 1.8, calciumMg: 25, magnesiumMg: 45, potassiumMg: 200, zincMg: 1.0, phosphorusMg: 110, folateMcg: 60, vitaminAMcg: 5 },
  'egg-white-omelette': { ironMg: 0.1, calciumMg: 7, potassiumMg: 160, phosphorusMg: 15, seleniumMcg: 20, vitaminB2Mg: 0.44, folateMcg: 4 },
  'greek-yogurt': { calciumMg: 110, phosphorusMg: 135, potassiumMg: 141, vitaminB12Mcg: 0.75, vitaminB2Mg: 0.28, zincMg: 0.5, iodineMcg: 30 },
  'green-tea': { potassiumMg: 8, manganeseMg: 0.18 },
  'grilled-salmon': { ironMg: 0.5, magnesiumMg: 29, potassiumMg: 384, phosphorusMg: 240, vitaminB12Mcg: 3.2, vitaminDMcg: 11, seleniumMcg: 36, vitaminB3Mg: 8, vitaminB6Mg: 0.9 },
  guava: { ironMg: 0.26, calciumMg: 18, magnesiumMg: 22, potassiumMg: 417, vitaminCMg: 228, folateMcg: 49, vitaminAMcg: 31, copperMcg: 230 },
  idli: { ironMg: 0.9, calciumMg: 15, magnesiumMg: 20, potassiumMg: 60, zincMg: 0.7, phosphorusMg: 60, folateMcg: 15 },
  'jeera-rice': { ironMg: 0.4, magnesiumMg: 13, potassiumMg: 40, zincMg: 0.5, phosphorusMg: 45, manganeseMg: 0.5 },
  kadhi: { ironMg: 1.0, calciumMg: 90, magnesiumMg: 25, potassiumMg: 150, phosphorusMg: 100, vitaminB12Mcg: 0.3, zincMg: 0.6, folateMcg: 25 },
  khichdi: { ironMg: 1.0, magnesiumMg: 25, potassiumMg: 120, zincMg: 0.8, phosphorusMg: 80, folateMcg: 30, vitaminAMcg: 10 },
  'masala-dosa': { ironMg: 1.2, calciumMg: 12, magnesiumMg: 22, potassiumMg: 180, zincMg: 0.6, phosphorusMg: 70, vitaminCMg: 6, folateMcg: 15, vitaminB3Mg: 1.2 },
  orange: { ironMg: 0.1, calciumMg: 40, magnesiumMg: 10, potassiumMg: 181, vitaminCMg: 53, folateMcg: 30, vitaminAMcg: 11, vitaminB1Mg: 0.087, copperMcg: 45 },
  pomegranate: { ironMg: 0.3, calciumMg: 10, magnesiumMg: 12, potassiumMg: 236, vitaminCMg: 10, folateMcg: 38, vitaminKMcg: 16, copperMcg: 158, manganeseMg: 0.12 },
  'prawn-masala': { ironMg: 0.5, calciumMg: 70, potassiumMg: 180, zincMg: 1.3, phosphorusMg: 200, vitaminB12Mcg: 1.1, seleniumMcg: 40, iodineMcg: 35, copperMcg: 300 },
  'rajma-chawal': { ironMg: 1.8, calciumMg: 30, folateMcg: 90, magnesiumMg: 40, potassiumMg: 300, zincMg: 0.9, phosphorusMg: 120, manganeseMg: 0.4 },
  upma: { ironMg: 1.2, calciumMg: 20, magnesiumMg: 25, potassiumMg: 120, zincMg: 0.6, phosphorusMg: 80, folateMcg: 20, vitaminAMcg: 30, vitaminB3Mg: 1.5 },
  'veg-pulao': { ironMg: 1.0, calciumMg: 25, magnesiumMg: 22, potassiumMg: 180, zincMg: 0.6, phosphorusMg: 70, vitaminAMcg: 150, vitaminCMg: 8, folateMcg: 25 },
  'vegetable-pulao': { ironMg: 1.0, calciumMg: 25, magnesiumMg: 22, potassiumMg: 180, zincMg: 0.6, phosphorusMg: 70, vitaminAMcg: 150, vitaminCMg: 8, folateMcg: 25 },
  walnuts: { ironMg: 2.9, calciumMg: 98, magnesiumMg: 158, potassiumMg: 441, zincMg: 3.1, phosphorusMg: 346, copperMcg: 1586, manganeseMg: 3.4, folateMcg: 98, vitaminB6Mg: 0.5, vitaminEMg: 0.7 },

  // ---- PG / hostel / no-cook staples ----
  'cold-milk': { calciumMg: 120, phosphorusMg: 95, potassiumMg: 150, vitaminB12Mcg: 0.4, vitaminB2Mg: 0.18, zincMg: 0.4, iodineMcg: 15 },
  sattu: { ironMg: 4.5, folateMcg: 160, magnesiumMg: 75, potassiumMg: 280, zincMg: 2.6, phosphorusMg: 160, copperMcg: 600, manganeseMg: 1.6 },
  muesli: { ironMg: 3.5, magnesiumMg: 80, potassiumMg: 280, zincMg: 2.0, phosphorusMg: 180, manganeseMg: 2.0, vitaminB1Mg: 0.3, calciumMg: 90 },
  'bread-pb': { ironMg: 2.0, magnesiumMg: 60, potassiumMg: 230, zincMg: 1.4, phosphorusMg: 130, vitaminB3Mg: 4.0, vitaminEMg: 3.0, manganeseMg: 1.2 },
  'soaked-oats': { ironMg: 2.3, magnesiumMg: 55, potassiumMg: 180, zincMg: 1.4, phosphorusMg: 140, manganeseMg: 1.8, vitaminB1Mg: 0.25 },
  'whey-shake': { calciumMg: 200, phosphorusMg: 130, potassiumMg: 180, magnesiumMg: 20, zincMg: 1.0, vitaminB12Mcg: 0.6 },
  'roasted-makhana': { magnesiumMg: 210, potassiumMg: 500, phosphorusMg: 280, calciumMg: 60, ironMg: 1.4, manganeseMg: 1.3 },
  'dry-fruits-mix': { ironMg: 3.0, magnesiumMg: 150, potassiumMg: 600, zincMg: 2.8, phosphorusMg: 320, copperMcg: 1100, manganeseMg: 2.2, vitaminEMg: 7.0, folateMcg: 60 },
};
