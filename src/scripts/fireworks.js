import anime from "animejs/lib/anime.es.js";

const canvasEl = document.createElement("canvas");
canvasEl.style.cssText =
  "position:fixed;top:0;left:0;pointer-events:none;z-index:9999999";
document.body.appendChild(canvasEl);

const ctx = canvasEl.getContext("2d");
const numberOfParticules = 30;
let pointerX = 0;
let pointerY = 0;
const tap = "click";
const COLORS = [
  "rgba(255,182,185,.9)",
  "rgba(250,227,217,.9)",
  "rgba(187,222,214,.9)",
  "rgba(138,198,209,.9)",
];

function setCanvasSize() {
  canvasEl.width = window.innerWidth * 2;
  canvasEl.height = window.innerHeight * 2;
  canvasEl.style.width = `${window.innerWidth}px`;
  canvasEl.style.height = `${window.innerHeight}px`;
  canvasEl.getContext("2d").scale(2, 2);
}

function updateCoords(e) {
  pointerX = e.clientX || e.touches?.[0].clientX;
  pointerY = e.clientY || e.touches?.[0].clientY;
}

function setParticuleDirection(p) {
  const angle = (anime.random(0, 360) * Math.PI) / 180;
  const value = anime.random(50, 180);
  const radius = [-1, 1][anime.random(0, 1)] * value;
  return {
    x: p.x + radius * Math.cos(angle),
    y: p.y + radius * Math.sin(angle),
  };
}

function createParticule(x, y) {
  const p = {};
  p.x = x;
  p.y = y;
  p.color = COLORS[anime.random(0, COLORS.length - 1)];
  p.radius = anime.random(16, 32);
  p.endPos = setParticuleDirection(p);
  p.draw = () => {
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.radius, 0, 2 * Math.PI, true);
    ctx.fillStyle = p.color;
    ctx.fill();
  };
  return p;
}

function createCircle(x, y) {
  const p = {};
  p.x = x;
  p.y = y;
  p.color = "#FFF";
  p.radius = 0.1;
  p.alpha = 0.5;
  p.lineWidth = 6;
  p.draw = () => {
    ctx.globalAlpha = p.alpha;
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.radius, 0, 2 * Math.PI, true);
    ctx.lineWidth = p.lineWidth;
    ctx.strokeStyle = p.color;
    ctx.stroke();
    ctx.globalAlpha = 1;
  };
  return p;
}

function renderParticule(anim) {
  for (let i = 0; i < anim.animatables.length; i++) {
    anim.animatables[i].target.draw();
  }
}

function animateParticules(x, y) {
  const circle = createCircle(x, y);
  const particules = [];
  for (let i = 0; i < numberOfParticules; i++) {
    particules.push(createParticule(x, y));
  }
  anime
    .timeline()
    .add({
      targets: particules,
      x: (p) => p.endPos.x,
      y: (p) => p.endPos.y,
      radius: 0.1,
      duration: anime.random(1200, 1800),
      easing: "easeOutExpo",
      update: renderParticule,
    })
    .add(
      {
        targets: circle,
        radius: anime.random(80, 160),
        lineWidth: 0,
        alpha: {
          value: 0,
          easing: "linear",
          duration: anime.random(600, 800),
        },
        duration: anime.random(1200, 1800),
        easing: "easeOutExpo",
        update: renderParticule,
      },
      0,
    );
}

const render = anime({
  duration: Number.POSITIVE_INFINITY,
  update: () => {
    ctx.clearRect(0, 0, canvasEl.width, canvasEl.height);
  },
});

document.addEventListener(
  tap,
  (e) => {
    render.play();
    updateCoords(e);
    animateParticules(pointerX, pointerY);
  },
  false,
);

setCanvasSize();
window.addEventListener("resize", setCanvasSize, false);