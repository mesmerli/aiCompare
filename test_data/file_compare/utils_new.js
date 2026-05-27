export function formatDate(date, separator = '-') {
    const d = new Date(date);
    if (isNaN(d.getTime())) {
        throw new Error('Invalid date instance provided');
    }
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const year = d.getFullYear();

    return `${year}${separator}${month}${separator}${day}`;
}

export function calculateAverage(numbers = []) {
    if (!Array.isArray(numbers) || numbers.length === 0) {
        return 0;
    }
    const sum = numbers.reduce((acc, curr) => acc + curr, 0);
    return sum / numbers.length;
}

export function findMax(numbers = []) {
    if (!Array.isArray(numbers) || numbers.length === 0) {
        return null;
    }
    return Math.max(...numbers);
}
