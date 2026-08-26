const pages = new Map([
  ["/", "/"], ["/how.html", "/how.html"], ["/tasks.html", "/tasks.html"],
  ["/rewards.html", "/rewards.html"], ["/parents.html", "/parents.html"], ["/faq.html", "/faq.html"],
]);

export function publicLanguageHref(pathname, locale, origin = window.location.origin) {
  const englishPath = pathname.startsWith("/ru/") ? pathname.slice(3) || "/" : pathname;
  const path = pages.get(englishPath);
  if (!path || !["en", "ru"].includes(locale)) return null;
  return new URL(locale === "ru" ? `/ru${path}` : path, origin).href;
}
