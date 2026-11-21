/**
 * ---------------------------------------------------------------------------
 * widget-loader.js - loads the All in One Accessibility widget on public pages
 * ---------------------------------------------------------------------------
 * The widget component (aioa/components/widget) prints
 *   <div data-aioa-widget data-aioa-src="https://...widget-minify.js?..." data-aioa-delay="3000" hidden></div>
 * This script adds Skynet's widget script (id "aioa-adawidget") once, after the delay, so the
 * widget never blocks page rendering. The same as Skynet's install snippet, without inline script
 * (keeps a strict Content Security Policy possible).
 */
(function (window, document) {
  'use strict';

  var SCRIPT_ID = 'aioa-adawidget';

  function load(src) {
    if (!src || document.getElementById(SCRIPT_ID) || !/^https:\/\//i.test(src)) {
      return;
    }
    var script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = src;
    script.defer = true;
    document.getElementsByTagName('head')[0].appendChild(script);
  }

  /**
   * True inside the AEM page editor: the page is shown in the editor's content frame, next to the
   * authoring UI (Granite.author). The widget is not loaded there, so it never covers the editor.
   * Pages opened directly on author, in "view as published" and on publish load it as usual.
   */
  function inPageEditor() {
    try {
      if (window.self === window.top) {
        return false;
      }
      var parent = window.parent;
      return Boolean((parent.Granite && parent.Granite.author) || (window.frameElement && window.frameElement.id === 'ContentFrame'));
    } catch (e) {
      return false; // parent on another origin: not the AEM editor
    }
  }

  function start() {
    var marker = document.querySelector('[data-aioa-widget]');
    if (!marker || marker.getAttribute('data-aioa-started') === 'true' || inPageEditor()) {
      return;
    }
    marker.setAttribute('data-aioa-started', 'true');
    var delay = parseInt(marker.getAttribute('data-aioa-delay'), 10);
    window.setTimeout(function () {
      load(marker.getAttribute('data-aioa-src'));
    }, isNaN(delay) || delay < 0 ? 3000 : delay);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})(window, document);
