function toTwoDigits(num) {
  return num < 10 ? '0'+num : num+'';
}

function dateInNiceFormat(date) {
  return date.getFullYear() + '-' + toTwoDigits(date.getMonth()+1) + '-' +
    toTwoDigits(date.getDate()) + ' ' + toTwoDigits(date.getHours()) + ':' +
    toTwoDigits(date.getMinutes()) + ':' + toTwoDigits(date.getSeconds());
}