// Constant propagation через let — все идентификаторы ниже подставятся литералами,
// а получившиеся выражения свернутся обычной свёрткой.

let x = 10;
let y = 20;
let z = x + y;       // -> 30
let msg = "сумма = ";

console.log(msg, z);            // -> "сумма = " 30
console.log(x * 2, y - x);      // -> 20  10
console.log(x < y, x == 10);    // -> true true
