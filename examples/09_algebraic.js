// Алгебраические тождества.

// С известными переменными — сначала propagation, потом обычная свёртка.
let a = 6;
let b = 7;
console.log((a * b) + 0);     // -> 42
console.log(1 * (a * b));     // -> 42
console.log(0 * (a * b));     // -> 0
console.log((a * b) / 1);     // -> 42

// Параметр функции не пробрасывается — здесь срабатывает именно алгебра.
function check(n) {
    return !!(n > 0);         // !!(...) -> (...): внутри сравнение, статически булево
}
console.log(check(5));    // -> true
console.log(check(-3));   // -> false
console.log(check(0));    // -> false

function boolOps(n) {
    console.log((n > 0) && true);   // -> (n > 0)
    console.log((n > 0) || false);  // -> (n > 0)
    console.log((n > 0) && false);  // -> false
}
boolOps(5);
