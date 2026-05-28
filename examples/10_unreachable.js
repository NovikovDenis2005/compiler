// Удаление недостижимого кода после return / break / continue.

function early(n) {
    return n * 2;
    console.log("сюда не дойдём");   // должно быть выброшено
    let z = 99;                      // и это тоже
}

let i = 0;
while (i < 10) {
    if (i == 3) {
        break;
        console.log("после break");  // выбрасывается
    }
    i = i + 1;
    continue;
    console.log("после continue");   // выбрасывается
}

console.log("early(5) =", early(5));
console.log("i =", i);
