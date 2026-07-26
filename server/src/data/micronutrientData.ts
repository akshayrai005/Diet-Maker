import type { Micronutrients } from '../calc/micronutrients';

/**
 * MEASURED micronutrients per 100 g of the food AS SEEDED (cooked/prepared where the seed is), keyed
 * by the seed food `id`. Values are from ICMR-NIN "Indian Food Composition Tables 2017/2020" and
 * USDA FoodData Central, rounded to sensible precision. Only nutrients a food is a meaningful source
 * of are filled; anything omitted stays NULL (unknown) — never 0, so it can never become a false
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
};
