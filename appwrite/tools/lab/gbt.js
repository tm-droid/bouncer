/**
 * Gradient-boosted trees over histogram bins, in plain JavaScript.
 *
 * Exists to answer one question honestly: does a model that can form its own interactions beat the
 * logistic regression once the features include grounds and players? It is here rather than a
 * Python library so that the lab, the weekly trainer and the reader all run the same code and a
 * measured gain cannot come from one implementation and ship in another.
 *
 * One class (`classes: 1`) is binary log-loss on a single logit; three is softmax, one tree per
 * class per round.
 */

const DEFAULTS = Object.freeze({
  rounds: 400,
  learningRate: 0.08,
  maxDepth: 5,
  minChildRows: 200,
  lambda: 2,
  rowSample: 0.5,
  maxBins: 48,
  patience: 30,
  seed: 4242,
});

function lcg(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

/** Up to `maxBins - 1` cut points per feature, taken at quantiles of a sample of rows. */
export function fitBins(X, n, k, maxBins = DEFAULTS.maxBins, sampleRows = 60000, seed = 99) {
  const random = lcg(seed);
  const take = Math.min(n, sampleRows);
  const rows = new Int32Array(take);
  for (let i = 0; i < take; i++) rows[i] = take === n ? i : Math.floor(random() * n);
  const edges = [];
  const column = new Float32Array(take);
  for (let j = 0; j < k; j++) {
    for (let i = 0; i < take; i++) column[i] = X[rows[i] * k + j];
    const sorted = Float32Array.from(column).sort();
    const cuts = [];
    for (let b = 1; b < maxBins; b++) {
      const v = sorted[Math.min(take - 1, Math.floor((b * take) / maxBins))];
      if (v > sorted[0] && (cuts.length === 0 || v > cuts[cuts.length - 1])) cuts.push(v);
    }
    edges.push(Float32Array.from(cuts));
  }
  return edges;
}

/** Bin index of every value: the number of cut points at or below it. */
export function quantize(X, n, k, edges) {
  const Q = new Uint8Array(n * k);
  for (let j = 0; j < k; j++) {
    const cuts = edges[j];
    const m = cuts.length;
    for (let i = 0; i < n; i++) {
      const v = X[i * k + j];
      let lo = 0;
      let hi = m;
      while (lo < hi) {
        const mid = (lo + hi) >> 1;
        if (cuts[mid] <= v) lo = mid + 1;
        else hi = mid;
      }
      Q[i * k + j] = lo;
    }
  }
  return Q;
}

function growTree({ Q, k, bins, rows, grad, hess, edges, opts }) {
  const feature = [];
  const threshold = [];
  const splitBin = [];
  const left = [];
  const right = [];
  const value = [];
  const G = new Float64Array(k * bins);
  const H = new Float64Array(k * bins);
  const C = new Int32Array(k * bins);

  const newNode = () => {
    feature.push(-1);
    threshold.push(0);
    splitBin.push(0);
    left.push(-1);
    right.push(-1);
    value.push(0);
    return feature.length - 1;
  };

  const build = (node, start, end, depth) => {
    let sumG = 0;
    let sumH = 0;
    for (let i = start; i < end; i++) {
      sumG += grad[rows[i]];
      sumH += hess[rows[i]];
    }
    value[node] = (-sumG / (sumH + opts.lambda)) * opts.learningRate;
    const count = end - start;
    if (depth >= opts.maxDepth || count < 2 * opts.minChildRows) return;

    G.fill(0);
    H.fill(0);
    C.fill(0);
    for (let i = start; i < end; i++) {
      const r = rows[i];
      const g = grad[r];
      const h = hess[r];
      const off = r * k;
      for (let j = 0; j < k; j++) {
        const at = j * bins + Q[off + j];
        G[at] += g;
        H[at] += h;
        C[at] += 1;
      }
    }

    const parent = (sumG * sumG) / (sumH + opts.lambda);
    let bestGain = 1e-7;
    let bestFeature = -1;
    let bestBin = 0;
    for (let j = 0; j < k; j++) {
      const cuts = edges[j].length;
      let gl = 0;
      let hl = 0;
      let cl = 0;
      for (let b = 0; b < cuts; b++) {
        const at = j * bins + b;
        gl += G[at];
        hl += H[at];
        cl += C[at];
        if (cl < opts.minChildRows) continue;
        if (count - cl < opts.minChildRows) break;
        const gr = sumG - gl;
        const hr = sumH - hl;
        const gain = (gl * gl) / (hl + opts.lambda) + (gr * gr) / (hr + opts.lambda) - parent;
        if (gain > bestGain) {
          bestGain = gain;
          bestFeature = j;
          bestBin = b;
        }
      }
    }
    if (bestFeature < 0) return;

    let i = start;
    let j = end - 1;
    while (i <= j) {
      if (Q[rows[i] * k + bestFeature] <= bestBin) i++;
      else {
        const tmp = rows[i];
        rows[i] = rows[j];
        rows[j] = tmp;
        j--;
      }
    }
    feature[node] = bestFeature;
    splitBin[node] = bestBin;
    threshold[node] = edges[bestFeature][bestBin];
    const l = newNode();
    const r = newNode();
    left[node] = l;
    right[node] = r;
    build(l, start, i, depth + 1);
    build(r, i, end, depth + 1);
  };

  build(newNode(), 0, rows.length, 0);
  return {
    feature: Int16Array.from(feature),
    threshold: Float32Array.from(threshold),
    splitBin: Uint8Array.from(splitBin),
    left: Int32Array.from(left),
    right: Int32Array.from(right),
    value: Float32Array.from(value),
  };
}

function addTreeQuantized(tree, Q, n, k, out, stride, offset) {
  for (let i = 0; i < n; i++) {
    let node = 0;
    while (tree.feature[node] >= 0) {
      node = Q[i * k + tree.feature[node]] <= tree.splitBin[node] ? tree.left[node] : tree.right[node];
    }
    out[i * stride + offset] += tree.value[node];
  }
}

function meanLogLoss(logits, y, n, classes) {
  const eps = 1e-7;
  let loss = 0;
  if (classes === 1) {
    for (let i = 0; i < n; i++) {
      const p = Math.min(1 - eps, Math.max(eps, 1 / (1 + Math.exp(-logits[i]))));
      loss -= y[i] ? Math.log(p) : Math.log(1 - p);
    }
    return loss / Math.max(n, 1);
  }
  for (let i = 0; i < n; i++) {
    let max = -Infinity;
    for (let c = 0; c < classes; c++) max = Math.max(max, logits[i * classes + c]);
    let sum = 0;
    for (let c = 0; c < classes; c++) sum += Math.exp(logits[i * classes + c] - max);
    loss -= logits[i * classes + y[i]] - max - Math.log(sum);
  }
  return loss / Math.max(n, 1);
}

/**
 * Fit a boosted model on `train`, stopping when `valid` stops improving.
 *
 * `train` and `valid` are `{X: Float32Array, y: Uint8Array, length, numFeatures}` — the same shape
 * as `RowSink`, so both model classes are fed from one matrix.
 */
export function trainGbt(train, valid, { classes = 1, ...overrides } = {}) {
  const opts = { ...DEFAULTS, ...overrides };
  const n = train.length;
  const k = train.numFeatures;
  const edges = fitBins(train.X, n, k, opts.maxBins);
  const bins = opts.maxBins;
  const Q = quantize(train.X, n, k, edges);
  const QV = quantize(valid.X, valid.length, k, edges);
  const random = lcg(opts.seed);

  const base = new Float64Array(classes);
  if (classes === 1) {
    let pos = 0;
    for (let i = 0; i < n; i++) pos += train.y[i];
    const rate = Math.min(0.999, Math.max(0.001, pos / Math.max(n, 1)));
    base[0] = Math.log(rate / (1 - rate));
  } else {
    const counts = new Float64Array(classes);
    for (let i = 0; i < n; i++) counts[train.y[i]] += 1;
    for (let c = 0; c < classes; c++) base[c] = Math.log(Math.max(counts[c], 1) / Math.max(n, 1));
  }

  const logits = new Float64Array(n * classes);
  const validLogits = new Float64Array(valid.length * classes);
  for (let i = 0; i < n; i++) for (let c = 0; c < classes; c++) logits[i * classes + c] = base[c];
  for (let i = 0; i < valid.length; i++) for (let c = 0; c < classes; c++) validLogits[i * classes + c] = base[c];

  const grad = new Float64Array(n);
  const hess = new Float64Array(n);
  const probs = new Float64Array(classes);
  const trees = [];
  let bestLoss = Infinity;
  let bestRounds = 0;
  const sampleSize = Math.max(1, Math.floor(n * opts.rowSample));

  for (let round = 0; round < opts.rounds; round++) {
    const rowsForRound = new Int32Array(sampleSize);
    for (let i = 0; i < sampleSize; i++) rowsForRound[i] = Math.floor(random() * n);

    for (let c = 0; c < classes; c++) {
      if (classes === 1) {
        for (let i = 0; i < n; i++) {
          const p = 1 / (1 + Math.exp(-logits[i]));
          grad[i] = p - train.y[i];
          hess[i] = Math.max(p * (1 - p), 1e-6);
        }
      } else {
        for (let i = 0; i < n; i++) {
          let max = -Infinity;
          for (let d = 0; d < classes; d++) max = Math.max(max, logits[i * classes + d]);
          let sum = 0;
          for (let d = 0; d < classes; d++) {
            probs[d] = Math.exp(logits[i * classes + d] - max);
            sum += probs[d];
          }
          const p = probs[c] / sum;
          grad[i] = p - (train.y[i] === c ? 1 : 0);
          hess[i] = Math.max(p * (1 - p), 1e-6);
        }
      }
      const tree = growTree({ Q, k, bins, rows: Int32Array.from(rowsForRound), grad, hess, edges, opts });
      trees.push(tree);
      addTreeQuantized(tree, Q, n, k, logits, classes, c);
      addTreeQuantized(tree, QV, valid.length, k, validLogits, classes, c);
    }

    const loss = meanLogLoss(validLogits, valid.y, valid.length, classes);
    if (loss < bestLoss - 1e-5) {
      bestLoss = loss;
      bestRounds = round + 1;
    } else if (round + 1 - bestRounds >= opts.patience) {
      break;
    }
  }

  trees.length = bestRounds * classes;
  return {
    kind: "gbt",
    classes,
    base: Array.from(base),
    rounds: bestRounds,
    validLogLoss: bestLoss,
    trees: trees.map(({ feature, threshold, left, right, value }) => ({ feature, threshold, left, right, value })),
  };
}

/** Raw logits for one feature vector: one number for a binary model, one per class otherwise. */
export function gbtLogits(model, features) {
  const out = model.base.slice();
  for (let t = 0; t < model.trees.length; t++) {
    const tree = model.trees[t];
    let node = 0;
    // A value below the cut point goes left — the same rule `quantize` bins by.
    while (tree.feature[node] >= 0) {
      node = features[tree.feature[node]] < tree.threshold[node] ? tree.left[node] : tree.right[node];
    }
    out[t % model.classes] += tree.value[node];
  }
  return out;
}
