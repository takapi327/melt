document.addEventListener('DOMContentLoaded', () => {
  const card = document.querySelector('.demo-card');
  if (!card) return;

  const display = card.querySelector('.counter-display');
  const doubled = card.querySelector('.counter-doubled b');
  const [btnDec, btnInc, btnReset] = card.querySelectorAll('.counter-buttons button');

  let count = 0;

  function update() {
    display.textContent = count;
    doubled.textContent = count * 2;
  }

  btnDec.addEventListener('click', () => { count--; update(); });
  btnInc.addEventListener('click', () => { count++; update(); });
  btnReset.addEventListener('click', () => { count = 0; update(); });
});
