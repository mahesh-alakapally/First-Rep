export type ThemeChoice = "light" | "dark" | "system";

export const THEME_KEY = "firstrep-theme";

export function readThemeChoice(): ThemeChoice {
  const stored = localStorage.getItem(THEME_KEY);
  return stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
}

export function resolveTheme(choice: ThemeChoice, prefersDark: boolean): "light" | "dark" {
  if (choice === "system") {
    return prefersDark ? "dark" : "light";
  }
  return choice;
}

export function applyTheme(choice: ThemeChoice) {
  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  document.documentElement.dataset.theme = resolveTheme(choice, prefersDark);
  localStorage.setItem(THEME_KEY, choice);
}
