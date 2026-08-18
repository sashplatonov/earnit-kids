
(function () {
  const cfg = window.EARNIT_CONFIG || {};
  const url = String(cfg.telegramMiniAppUrl || "");
  const configured = url && url !== "#" && !/REPLACE_WITH_/i.test(url);

  document.querySelectorAll("[data-miniapp-link]").forEach((link) => {
    if (configured) {
      link.href = url;
    } else {
      link.href = "#";
      link.setAttribute("aria-disabled", "true");
      link.title = "Ссылка на Telegram Mini App пока не настроена";
      link.addEventListener("click", (event) => {
        event.preventDefault();
        alert("Укажите реальную ссылку Telegram Mini App в config.js");
      });
    }
  });

  const tabs = document.querySelector(".tabs");
  const activeTab = tabs && tabs.querySelector(".tab.active");
  if (tabs && activeTab && window.matchMedia("(max-width: 640px)").matches) {
    requestAnimationFrame(() => {
      const target = activeTab.offsetLeft - (tabs.clientWidth - activeTab.offsetWidth) / 2;
      tabs.scrollTo({ left: Math.max(0, target), behavior: "auto" });
    });
  }

  document.querySelectorAll("[data-carousel]").forEach((carousel) => {
    const track = carousel.querySelector(".carousel-track");
    const slides = Array.from(carousel.querySelectorAll(".carousel-slide"));
    const dots = Array.from(carousel.querySelectorAll(".carousel-dot"));
    const prev = carousel.querySelector(".carousel-prev");
    const next = carousel.querySelector(".carousel-next");
    const status = carousel.querySelector(".carousel-status");
    const labels = slides.map((slide) => slide.querySelector("figcaption b")?.textContent || "Экран");
    let index = 0;
    let startX = null;

    const render = () => {
      track.style.transform = `translateX(-${index * 100}%)`;
      dots.forEach((dot, i) => {
        dot.classList.toggle("is-active", i === index);
        if (i === index) dot.setAttribute("aria-current", "true");
        else dot.removeAttribute("aria-current");
      });
      if (status) status.textContent = `Экран ${index + 1} из ${slides.length}: ${labels[index]}`;
    };

    const move = (delta) => {
      index = (index + delta + slides.length) % slides.length;
      render();
    };

    prev?.addEventListener("click", () => move(-1));
    next?.addEventListener("click", () => move(1));
    dots.forEach((dot, i) => dot.addEventListener("click", () => { index = i; render(); }));

    carousel.tabIndex = 0;
    carousel.addEventListener("keydown", (event) => {
      if (event.key === "ArrowLeft") { event.preventDefault(); move(-1); }
      if (event.key === "ArrowRight") { event.preventDefault(); move(1); }
      if (event.key === "Home") { event.preventDefault(); index = 0; render(); }
      if (event.key === "End") { event.preventDefault(); index = slides.length - 1; render(); }
    });

    carousel.addEventListener("pointerdown", (event) => { startX = event.clientX; });
    carousel.addEventListener("pointerup", (event) => {
      if (startX === null) return;
      const delta = event.clientX - startX;
      if (Math.abs(delta) > 45) move(delta < 0 ? 1 : -1);
      startX = null;
    });
    carousel.addEventListener("pointercancel", () => { startX = null; });

    render();
  });
})();
