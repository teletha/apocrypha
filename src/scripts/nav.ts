// Nav animation
window.addEventListener("DOMContentLoaded", () => {
  const headerWaves = document.getElementById("header-waves");
  const nav = document.getElementById("nav");
  let lastScrollTop = 0;

  if (!headerWaves || !nav) return;

  // 监听尺寸变化
  window.addEventListener("resize", () => {
    // 当屏幕尺寸小于 1024px 时，显示导航栏
    if (window.innerWidth <= 1024) {
      nav.classList.remove("nav-hidden");
    }
  });

  // 监听滚动事件
  window.addEventListener("scroll", () => {
    // 当屏幕尺寸小于 1024px 时，不执行以下代码
    if (window.innerWidth < 1024) return;

    const headerWavesRect = headerWaves.getBoundingClientRect();
    const scrollTop = window.scrollY || document.documentElement.scrollTop;

    if (headerWavesRect.top <= 0) {
      if (scrollTop > lastScrollTop) {
        // 页面向下滚动
        nav.classList.add("nav-hidden");
      } else {
        // 页面向上滚动
        nav.classList.remove("nav-hidden");
      }
    } else {
      // header-waves 没有达到或超出屏幕顶端
      nav.classList.remove("nav-hidden");
    }

    lastScrollTop = scrollTop <= 0 ? 0 : scrollTop; // For Mobile or negative scrolling
  });
});
