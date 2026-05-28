// Удаление неиспользуемых let-переменных.

let used = 10;
let unusedNumber = 42;            // удаляется
let unusedString = "lorem ipsum"; // удаляется
let unusedComputed = 2 + 3 * 4;   // удаляется

function inc(x) { return x + 1; }

// init с вызовом функции — не удаляется даже без чтения: вызов имеет побочные эффекты.
let keptBecauseCall = inc(used);

console.log("used =", used);
console.log("keptBecauseCall =", keptBecauseCall);
