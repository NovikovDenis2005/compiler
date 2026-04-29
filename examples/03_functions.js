// Функции, рекурсия, замыкание
function fact(n) {
    if (n <= 1) {
        return 1;
    }
    return n * fact(n - 1);
}

function greet(name) {
    return "Hi, " + name + "!";
}

console.log("5! =", fact(5));
console.log("10! =", fact(10));
console.log(greet("world"));

// Замыкание: counter возвращает функцию-инкрементор
function makeCounter() {
    let c = 0;
    function inc() {
        c = c + 1;
        return c;
    }
    return inc;
}

let cnt = makeCounter();
console.log(cnt(), cnt(), cnt());
