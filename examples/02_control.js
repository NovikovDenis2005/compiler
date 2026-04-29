// if / while / for / break / continue
let i = 0;
while (i < 5) {
    if (i == 3) {
        i = i + 1;
        continue;
    }
    console.log("while", i);
    i = i + 1;
}

for (let k = 0; k < 10; k = k + 1) {
    if (k > 4) {
        break;
    }
    console.log("for", k);
}

let n = 7;
if (n mod 2 == 0) {
    console.log(n, "четное");
} else {
    console.log(n, "нечетное");
}
