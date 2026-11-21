/**
 * ---------------------------------------------------------------------------
 * setup.js - "Your website" step and "Add website" (AEM)
 * ---------------------------------------------------------------------------
 * The console needs to know which website it manages. When no website is known yet (no ?url=,
 * no OSGi "Site domains", nothing saved), app.js shows this step instead of an error:
 *
 *   1. The domain field is pre-filled with what AEM already knows (Sling mappings, Externalizer
 *      publish domain); the other suggestions are one click away.
 *   2. Continue saves the domain in AEM (route action saveDomain), then opens the console for it
 *      (?url=<domain>). app.js then registers the free widget as for any new website.
 *
 * "Add website" in the header opens the same step for one more website; Cancel goes back.
 */
(function (AIOA, window) {
  'use strict';

  const { $, $$ } = AIOA.util;
  const form = $('#setup-form');
  if (!form) {
    return;
  }

  const input = $('#setup-domain');
  const field = $('[data-for="setup-domain"]');
  const error = $('#setup-error');
  const submit = $('#setup-submit');
  const cancel = $('#setup-cancel');
  const addButton = $('#add-website');
  const firstValue = input.value;
  let returnView = null;

  // "Add website" / "Remove website" are switched off for now: the website comes from the OSGi
  // configuration or is detected automatically. Set to true to show both buttons again.
  const MANAGE_WEBSITES = false;

  /** Error under the field ('' hides it) */
  function setError(message) {
    error.textContent = message;
    error.hidden = !message;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    field.classList.toggle('is-invalid', Boolean(message));
  }

  AIOA.setup = {
    /**
     * Show the step. adding = true for "Add website": other title, empty field, Cancel goes back
     * to the settings. adding = false when the console has no website yet.
     */
    show(adding) {
      returnView = adding ? 'settings_block' : null;
      $('#setup-title').textContent = adding ? 'Add a website' : 'Which website is this for?';
      input.value = adding ? '' : firstValue;
      cancel.hidden = !adding;
      setError('');
      AIOA.ui.setView('setup_block');
      input.focus();
    }
  };

  // A suggestion fills the field
  $$('[data-setup-domain]').forEach((button) => {
    button.addEventListener('click', () => {
      input.value = button.dataset.setupDomain;
      setError('');
      input.focus();
    });
  });

  input.addEventListener('input', () => {
    if (!error.hidden) {
      setError('');
    }
  });

  cancel.addEventListener('click', () => {
    setError('');
    AIOA.ui.setView(returnView || 'settings_block');
    if (addButton) {
      addButton.focus();
    }
  });

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const domain = AIOA.util.normalizeDomain(input.value);
    if (!AIOA.util.DOMAIN_RE.test(domain)) {
      setError('Enter a valid domain, for example www.example.com.');
      input.focus();
      return;
    }

    AIOA.ui.setBusy(submit, true, 'Saving…');
    AIOA.api.route('saveDomain', { url: domain })
      .then((result) => {
        if (!result || typeof result !== 'object' || result.error || !result.domain) {
          throw new Error((result && result.error) || 'The website could not be saved. Please try again.');
        }
        // Open the console for the saved website (it registers the free widget if needed)
        const next = new URL(window.location.href);
        next.searchParams.set('url', result.domain);
        window.location.assign(next.toString());
      })
      .catch((err) => {
        AIOA.ui.setBusy(submit, false);
        setError(err instanceof TypeError ? 'Please check your connection and try again.' : err.message);
        input.focus();
      });
  });

  // "Add website" is available once a website is shown
  if (addButton) {
    addButton.hidden = !MANAGE_WEBSITES || !AIOA.state.activeDomain;
    addButton.addEventListener('click', () => AIOA.setup.show(true));
  }

  /* ---------- Remove website ---------- */

  // Only websites saved in this console (not the OSGi "Site domains") can be removed here
  const removeButton = $('#remove-website');
  const dialog = $('#remove-dialog');
  const activeDomain = AIOA.state.activeDomain;
  if (!MANAGE_WEBSITES || !removeButton || !dialog || !activeDomain || !AIOA.boot.removableDomains.includes(activeDomain)) {
    return;
  }

  const confirmButton = $('#remove-confirm');
  const removeError = $('#remove-error');
  removeButton.hidden = false;

  function setRemoveError(message) {
    removeError.textContent = message;
    removeError.hidden = !message;
  }

  removeButton.addEventListener('click', () => {
    $('#remove-domain').textContent = activeDomain;
    $('#remove-unsaved').hidden = !$('#savebar').classList.contains('is-dirty');
    setRemoveError('');
    dialog.showModal();
    $('#remove-cancel').focus(); // the safe choice first
  });

  $('#remove-cancel').addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => removeButton.focus());

  confirmButton.addEventListener('click', () => {
    AIOA.ui.setBusy(confirmButton, true, 'Removing…');
    AIOA.api.route('removeDomain', { url: activeDomain })
      .then((result) => {
        if (!result || typeof result !== 'object' || result.error || !result.removed) {
          throw new Error((result && result.error) || 'The website could not be removed. Please try again.');
        }
        // Open the console again without ?url=: the next website, or the "Your website" step when none is left
        const next = new URL(window.location.href);
        next.searchParams.delete('url');
        window.location.assign(next.toString());
      })
      .catch((err) => {
        AIOA.ui.setBusy(confirmButton, false);
        setRemoveError(err instanceof TypeError ? 'Please check your connection and try again.' : err.message);
      });
  });
})(window.AIOA, window);
