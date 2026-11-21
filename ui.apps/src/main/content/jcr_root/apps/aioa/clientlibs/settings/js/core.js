/**
 * ---------------------------------------------------------------------------
 * core.js - shared namespace, configuration, state and helpers
 * ---------------------------------------------------------------------------
 * Loaded first. Every other script works through window.AIOA:
 *
 *   AIOA.config    fixed values (icon folder, API URLs, default settings)
 *   AIOA.boot      what the platform sent: domain, name, email (any may be empty)
 *   AIOA.state     values that change while the page is open
 *   AIOA.util      DOM and validation helpers
 *   AIOA.storage   localStorage that never throws
 *   AIOA.api       requests to the server route and the Skynet API
 *
 * AEM changes (marked "AEM:"):
 *   - route calls send AEM's CSRF token (CSRF-Token header, from /libs/granite/csrf/token.json)
 *     and _charset_=utf-8, so Sling decodes the form fields as UTF-8;
 *   - the page's own hostname is not used as the website (on AEM that is the author host),
 *     unless the boot data sets hostFallback: true.
 */
(function (window, document) {
  'use strict';

  const AIOA = window.AIOA = window.AIOA || {};

  /* ---------- Boot data (printed by the page layout in #aioa-boot) ---------- */

  let boot = {};
  try {
    const bootEl = document.getElementById('aioa-boot');
    boot = JSON.parse(bootEl ? bootEl.textContent : '{}') || {};
  } catch (e) {
    boot = {};
  }

  /* ---------- Configuration ---------- */

  AIOA.config = {
    iconBase: boot.iconBase || 'assets/images/icons/', // bundled icons (assets/images/icons)
    defaultColor: boot.defaultColor || '420083',
    routeUrl: boot.routeUrl || 'route.php', // server route, from the layout ("route.php" in PHP, "route" in Python / Node.js)
    apiUpdateUrl: boot.apiUpdateUrl || 'https://ada.skynettechnologies.us/api/widget-setting-update-platform',
    geoApiUrl: boot.geoApiUrl || 'https://ipapi.co/json', // EU check (in_eu), only before a website is registered
    csrfTokenUrl: boot.csrfTokenUrl || '',                // AEM: Granite CSRF token servlet ('' = no token)
    hostFallback: boot.hostFallback !== false,            // AEM: false, the author host is not the website

    // Used until the settings of a registered website are loaded
    defaults: {
      widget_position: 'bottom_right',
      widget_color_code: '#' + (boot.defaultColor || '420083'),
      widget_icon_type: 'aioa-icon-type-1',
      widget_icon_size: 'aioa-default-icon',
      widget_size: 0
    }
  };

  // Website and admin details sent by the platform (empty on headless platforms).
  // A new website is registered with them; see app.js for the fallbacks.
  AIOA.boot = {
    domain: boot.domain || '',
    name: boot.name || '',
    email: boot.email || '',
    company: boot.company || '', // AEM: ?company= or the OSGi "Company name" (AEM profiles have no company field)
    // AEM: websites saved in this console that "Remove website" may take off the list
    removableDomains: Array.isArray(boot.removableDomains) ? boot.removableDomains : []
  };

  /* ---------- Helpers ---------- */

  const util = AIOA.util = {
    // Website host: a domain name or "localhost", never an IP address (same rules as the server's isValidDomain)
    DOMAIN_RE: /^(?:localhost|(?=.{3,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63})$/,

    /** First element that matches a CSS selector */
    $(selector) {
      return document.querySelector(selector);
    },

    /** Every element that matches a CSS selector, as an array */
    $$(selector) {
      return Array.from(document.querySelectorAll(selector));
    },

    /** "https://www.Example.com/shop" -> "www.example.com" (same rules as the server's normalizeDomain) */
    normalizeDomain(value) {
      return String(value || '').trim().toLowerCase()
        .replace(/^[a-z][a-z0-9+.-]*:\/\//, '') // scheme
        .replace(/[/?#].*$/, '')               // path, query, hash
        .replace(/:\d+$/, '')                  // port
        .replace(/^\.+|\.+$/g, '');
    },

    /** "#abc" / "aabbcc" -> "AABBCC", or null when the value is not a hex color */
    parseHex(value) {
      let hex = String(value || '').trim().replace(/^#/, '');
      if (/^[0-9a-f]{3}$/i.test(hex)) {
        hex = hex.split('').map((c) => c + c).join('');
      }
      return /^[0-9a-f]{6}$/i.test(hex) ? hex.toUpperCase() : null;
    },

    /** Value of the checked radio button in a group, or '' */
    checkedValue(name) {
      const input = document.querySelector('input[name="' + name + '"]:checked');
      return input ? input.value : '';
    },

    /** Check the radio button with the given value (ignored when it doesn't exist) */
    checkRadio(name, value) {
      const input = document.querySelector('input[name="' + name + '"][value="' + CSS.escape(String(value)) + '"]');
      if (input) {
        input.checked = true;
      }
    },

    /**
     * Saved widget position -> grid value. Older saves use the spelling
     * "middel_left" / "middel_right"; the grid uses "middle_left" / "middle_right".
     */
    positionValue(value) {
      return String(value || '').replace(/^middel_/, 'middle_');
    },

    /** Whole number from a field or API value ("40", 40.7, "0.500" -> 40, 40, 0); fallback when empty or not a number */
    wholeNumber(value, fallback) {
      if (value === null || value === undefined || String(value).trim() === '') {
        return fallback;
      }
      const number = Math.trunc(Number(value));
      return Number.isFinite(number) ? number : fallback;
    },

    /**
     * Whole numbers only in a number field: ".", ",", "e", "+" and "-" can't be typed, and a
     * pasted or dropped value such as "0.500" becomes a whole number (0). With max, the value
     * is also kept between 0 and max while typing.
     */
    wholeNumberField(input, max) {
      input.addEventListener('keydown', (e) => {
        if (!e.ctrlKey && !e.metaKey && ['.', ',', 'e', 'E', '+', '-'].includes(e.key)) {
          e.preventDefault();
        }
      });
      const normalize = () => {
        let number = util.wholeNumber(input.value, null);
        if (number === null) {
          return; // empty
        }
        if (max !== undefined) {
          number = Math.min(Math.max(number, 0), max);
        }
        if (String(number) !== input.value) {
          input.value = number;
        }
      };
      input.addEventListener('input', normalize);
      input.addEventListener('change', normalize);
    },

    /**
     * Number fields that must be filled in before saving, such as an exact position
     * ("0" is a value, an empty field is not). Returns { check, reset }:
     *   check()  marks each empty field (red border, aria-invalid), puts message in the
     *            error element under them and returns the first empty field, or null
     *   reset()  clears the marks and the message
     * Once the message is shown, the marks follow what the admin types.
     */
    requiredFields(inputs, error, message) {
      const mark = (input, empty) => {
        input.setAttribute('aria-invalid', empty ? 'true' : 'false');
        const box = document.querySelector('[data-for="' + input.id + '"]');
        if (box) {
          box.classList.toggle('is-invalid', empty);
        }
      };
      // The error element stays empty while hidden, so screen readers never read a stale message
      const show = (text) => {
        error.textContent = text;
        error.hidden = !text;
      };
      const check = () => {
        const empty = inputs.filter((input) => input.value.trim() === '');
        inputs.forEach((input) => mark(input, empty.includes(input)));
        show(empty.length ? message : '');
        return empty[0] || null;
      };
      const reset = () => {
        inputs.forEach((input) => mark(input, false));
        show('');
      };
      inputs.forEach((input) => {
        input.addEventListener('input', () => {
          if (!error.hidden) {
            check();
          }
        });
      });
      return { check: check, reset: reset };
    }
  };

  /* ---------- Storage ---------- */

  AIOA.storage = {
    get(key) {
      try { return window.localStorage.getItem(key) || ''; } catch (e) { return ''; }
    },
    set(key, value) {
      try { window.localStorage.setItem(key, value); } catch (e) { /* storage blocked */ }
    },
    remove(key) {
      try { window.localStorage.removeItem(key); } catch (e) { /* storage blocked */ }
    }
  };

  /* ---------- State ---------- */

  // Website: the platform's `url`, otherwise this page's own hostname (a domain or localhost, not an IP address).
  // An invalid `url` never falls back to the hostname (on a platform's admin page that is not the website).
  // AEM: no hostname fallback (hostFallback is false in the boot data), the website comes from ?url= or the OSGi configuration
  const domain = util.normalizeDomain(AIOA.boot.domain || (AIOA.config.hostFallback ? window.location.hostname : ''));
  const activeDomain = util.DOMAIN_RE.test(domain) ? domain : '';

  AIOA.state = {
    activeDomain: activeDomain, // website whose settings are shown ('' = none found)
    isPaid: false,              // true for a paid plan: Paid Features and Accessibility Score are unlocked
    customPosition: 0,          // 1 = exact offsets instead of the preset positions
    customSize: 0               // 1 = exact icon size instead of the preset sizes
  };

  /* ---------- API ---------- */

  /**
   * POST form fields and resolve with the parsed JSON (or the raw text).
   * null / undefined values are sent as empty strings.
   */
  function post(url, data, headers) {
    const body = new URLSearchParams();
    Object.keys(data).forEach((key) => {
      const value = data[key];
      body.append(key, value === null || value === undefined ? '' : value);
    });

    const options = { method: 'POST', body: body };
    if (headers) {
      options.headers = headers;
    }
    return fetch(url, options).then((res) => res.text().then((text) => {
      if (!res.ok) {
        throw new Error('Request failed (' + res.status + ')');
      }
      try { return JSON.parse(text); } catch (e) { return text; }
    }));
  }

  /**
   * AEM: a fresh CSRF token for a POST to AEM (tokens expire after a few minutes, and the route
   * is called only a handful of times per page). Resolves with '' when there is no token servlet.
   * The token is only ever sent to AEM itself, never to the Skynet API.
   */
  function csrfToken() {
    if (!AIOA.config.csrfTokenUrl) {
      return Promise.resolve('');
    }
    return fetch(AIOA.config.csrfTokenUrl, { credentials: 'same-origin', cache: 'no-store' })
      .then((res) => (res.ok ? res.json() : {}))
      .then((json) => (json && json.token) || '')
      .catch(() => '');
  }

  AIOA.api = {
    post: post,

    /** POST <routeUrl>?action=<action>: the AEM route servlet (actions: docs/API.md) */
    route(action, data) {
      const fields = Object.assign({ _charset_: 'utf-8' }, data || {});
      return csrfToken().then((token) => post(
        AIOA.config.routeUrl + '?action=' + encodeURIComponent(action),
        fields,
        token ? { 'CSRF-Token': token } : null
      ));
    }
  };
})(window, document);
