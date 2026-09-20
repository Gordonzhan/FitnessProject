const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const SIZE = 81;
const SCALE = 4;
const W = SIZE * SCALE;

function rgba(hex) {
  return [
    parseInt(hex.slice(1, 3), 16),
    parseInt(hex.slice(3, 5), 16),
    parseInt(hex.slice(5, 7), 16),
    255
  ];
}

function canvas() {
  return new Uint8Array(W * W * 4);
}

function dot(buffer, x, y, radius, color) {
  const left = Math.max(0, Math.floor(x - radius));
  const right = Math.min(W - 1, Math.ceil(x + radius));
  const top = Math.max(0, Math.floor(y - radius));
  const bottom = Math.min(W - 1, Math.ceil(y + radius));
  const r2 = radius * radius;
  for (let py = top; py <= bottom; py += 1) {
    for (let px = left; px <= right; px += 1) {
      const dx = px + 0.5 - x;
      const dy = py + 0.5 - y;
      if (dx * dx + dy * dy <= r2) {
        const offset = (py * W + px) * 4;
        buffer.set(color, offset);
      }
    }
  }
}

function line(buffer, x1, y1, x2, y2, width, color) {
  x1 *= SCALE;
  y1 *= SCALE;
  x2 *= SCALE;
  y2 *= SCALE;
  const radius = width * SCALE / 2;
  const distance = Math.hypot(x2 - x1, y2 - y1);
  const steps = Math.max(1, Math.ceil(distance / 1.5));
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    dot(buffer, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, radius, color);
  }
}

function circle(buffer, cx, cy, radius, width, color) {
  const steps = Math.ceil(2 * Math.PI * radius * SCALE);
  let previous = null;
  for (let i = 0; i <= steps; i += 1) {
    const angle = (i / steps) * Math.PI * 2;
    const point = [cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius];
    if (previous) line(buffer, previous[0], previous[1], point[0], point[1], width, color);
    previous = point;
  }
}

function downsample(buffer) {
  const output = Buffer.alloc(SIZE * SIZE * 4);
  for (let y = 0; y < SIZE; y += 1) {
    for (let x = 0; x < SIZE; x += 1) {
      const sum = [0, 0, 0, 0];
      for (let sy = 0; sy < SCALE; sy += 1) {
        for (let sx = 0; sx < SCALE; sx += 1) {
          const offset = (((y * SCALE + sy) * W) + x * SCALE + sx) * 4;
          for (let c = 0; c < 4; c += 1) sum[c] += buffer[offset + c];
        }
      }
      const target = (y * SIZE + x) * 4;
      for (let c = 0; c < 4; c += 1) output[target + c] = Math.round(sum[c] / (SCALE * SCALE));
    }
  }
  return output;
}

function crc32(data) {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuffer = Buffer.from(type);
  const body = Buffer.concat([typeBuffer, data]);
  const result = Buffer.alloc(data.length + 12);
  result.writeUInt32BE(data.length, 0);
  body.copy(result, 4);
  result.writeUInt32BE(crc32(body), data.length + 8);
  return result;
}

function encode(buffer) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(SIZE, 0);
  header.writeUInt32BE(SIZE, 4);
  header[8] = 8;
  header[9] = 6;
  const pixels = downsample(buffer);
  const rows = Buffer.alloc((SIZE * 4 + 1) * SIZE);
  for (let y = 0; y < SIZE; y += 1) {
    const rowOffset = y * (SIZE * 4 + 1);
    rows[rowOffset] = 0;
    pixels.copy(rows, rowOffset + 1, y * SIZE * 4, (y + 1) * SIZE * 4);
  }
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header),
    chunk('IDAT', zlib.deflateSync(rows, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

function drawRecipe(color) {
  const b = canvas();
  circle(b, 40.5, 40.5, 24, 5, color);
  line(b, 27, 43, 54, 43, 5, color);
  line(b, 27, 43, 33, 49, 5, color);
  line(b, 33, 49, 40.5, 52, 5, color);
  line(b, 40.5, 52, 48, 49, 5, color);
  line(b, 48, 49, 54, 43, 5, color);
  line(b, 41, 42, 47, 34, 4, color);
  line(b, 47, 34, 55, 28, 4, color);
  line(b, 55, 28, 53, 35, 4, color);
  line(b, 53, 35, 41, 42, 4, color);
  return b;
}

function drawWorkout(color) {
  const b = canvas();
  line(b, 20, 35, 20, 46, 6, color);
  line(b, 27, 31, 27, 50, 6, color);
  line(b, 27, 40.5, 54, 40.5, 6, color);
  line(b, 54, 31, 54, 50, 6, color);
  line(b, 61, 35, 61, 46, 6, color);
  return b;
}

function drawCompare(color) {
  const b = canvas();
  line(b, 22, 56, 22, 45, 6, color);
  line(b, 34, 56, 34, 35, 6, color);
  line(b, 46, 56, 46, 25, 6, color);
  line(b, 58, 56, 58, 39, 6, color);
  line(b, 20, 62, 61, 62, 4, color);
  return b;
}

function drawProfile(color) {
  const b = canvas();
  circle(b, 40.5, 31, 10, 5, color);
  line(b, 23, 59, 25, 53, 5, color);
  line(b, 25, 53, 31, 48, 5, color);
  line(b, 31, 48, 40.5, 45, 5, color);
  line(b, 40.5, 45, 50, 48, 5, color);
  line(b, 50, 48, 56, 53, 5, color);
  line(b, 56, 53, 58, 59, 5, color);
  return b;
}

const outputDirectory = path.resolve(__dirname, '..', 'images');
const icons = {
  recipe: drawRecipe,
  workout: drawWorkout,
  compare: drawCompare,
  profile: drawProfile
};

for (const [name, draw] of Object.entries(icons)) {
  fs.writeFileSync(path.join(outputDirectory, `tab-${name}.png`), encode(draw(rgba('#98A39B'))));
  fs.writeFileSync(path.join(outputDirectory, `tab-${name}-active.png`), encode(draw(rgba('#16A765'))));
}
