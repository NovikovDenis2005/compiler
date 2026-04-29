// Массивы и объекты
let arr = [10, 20, 30, 40];
console.log("len:", arr.length);
console.log("arr[0]=", arr[0], "arr[3]=", arr[3]);

arr[1] = 999;
console.log("after arr[1]=999:", arr);

let user = { name: "Anna", age: 21, city: "Moscow" };
console.log(user.name, "from", user.city, "age:", user.age);

user.age = user.age + 1;
console.log("next year:", user.age);

// Доступ через []
let key = "city";
console.log("user[key] =", user[key]);
