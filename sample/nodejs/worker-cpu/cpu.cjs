"use strict";
module.exports = function cpu(limit) {
  let sum = 0;
  for (let value = 2; value <= limit; value += 1) {
    let prime = true;
    for (let factor = 2; factor * factor <= value; factor += 1) {
      if (value % factor === 0) { prime = false; break; }
    }
    if (prime) sum += value;
  }
  return sum;
};
