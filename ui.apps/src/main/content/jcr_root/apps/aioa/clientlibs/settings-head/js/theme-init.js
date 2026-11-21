/**
 * ---------------------------------------------------------------------------
 * theme-init.js - runs in <head>, before the first paint
 * ---------------------------------------------------------------------------
 * AEM: the console is always shown in the light theme, like the rest of the AEM UI. data-theme="light"
 * also overrides the system dark-mode setting (tokens.css only applies the dark colors without it).
 * The light / dark toggle is hidden (js/theme.js, THEME_SWITCHING); a choice saved earlier is ignored.
 */
(function () {
  'use strict';

  document.documentElement.setAttribute('data-theme', 'light');
})();
