# All in One Accessibility for Adobe Experience Manager

AEM port of the All in One Accessibility settings page (Node.js / Express version) plus a
front-end widget component for published pages.

## Modules

| Module | Contents |
|---|---|
| `core` | OSGi bundle: `SkynetWidgetServiceImpl` (port of `app/skynet-widget.js`), `AioaRouteServlet` (port of `POST /route`), Sling Models `SettingsPageModel` and `WidgetModel`, OSGi config `AioaConfiguration` (port of `app/config.js`) |
| `ui.apps` | `/apps/aioa/components/settings-page` (HTL port of `views/`), `/apps/aioa/components/widget`, clientlibs (`assets/` unchanged except AEM notes in `core.js` / `app.js`), console resource `/apps/aioa/content/settings`, Tools menu entry |
| `ui.apps.structure` | Repository roots for package validation |
| `ui.config` | OSGi configuration (`SkynetWidgetServiceImpl.cfg.json`) |
| `all` | Single deployable package (bundle + ui.apps + ui.config) |

## Build and install (local AEM SDK)

Requirements: Java 21, Maven 3.6.3+, AEM as a Cloud Service SDK running on `localhost:4502` (author) and `localhost:4503` (publish).

```bash
# Update <aem.sdk.api> in pom.xml to the latest SDK release first
mvn clean install -PautoInstallSinglePackage          # author
mvn clean install -PautoInstallSinglePackagePublish   # publish
```

Cloud Manager: commit to your Cloud Manager Git repository and run the pipeline (`.cloudmanager/java-version` selects Java 21).

## Configure

OSGi configuration **All in One Accessibility - Settings**
(`ui.config/.../SkynetWidgetServiceImpl.cfg.json`, or `/system/console/configMgr` on a local SDK):

| Property | Meaning |
|---|---|
| `siteDomains` | **Optional.** Public domain(s) of your website(s), e.g. `www.example.com` (on Cloud Service: the `AIOA_SITE_DOMAIN` environment variable). Without it, the console uses the domain AEM already knows (see "Use"). Entries here come first; more than one shows a website switcher. |
| `allowedGroups` | Groups that may open the console and call its route (default `administrators`; user `admin` always allowed). Members also need read access to `/apps/aioa`. |
| `platform` | Platform name sent when a free widget is registered (default `AEM`). |
| `companyName` | **Optional.** Company sent when a free widget is registered and pre-filled in the "Upgrade to Paid Plan" link (AEM user profiles have no company field). On Cloud Service: the `AIOA_COMPANY_NAME` environment variable. |
| `upgradeUrl` | "Upgrade to Paid Plan" destination; the console adds `website`, `email`, `company`, `first_name`, `last_name`. |
| `widgetEnabled`, `widgetScriptUrl`, `widgetLoadDelay` | Front-end widget (`widgetEnabled` is the master switch). |
| `autoInjectEnabled` | Add the widget to site pages automatically (default on), see "Widget on the site". |
| `autoInjectPaths`, `autoInjectExcludedPaths` | Pages below the include paths (default `/content`) get the widget automatically, except below the excluded paths (default `/content/dam`, `/content/experience-fragments`, `/content/cq:tags`). |
| API URLs, timeout, scan language, default color | Same as the Node.js `AIOA_*` constants. |

## Use

1. **Settings console (author):** Tools > All in One Accessibility > Widget Settings, or
   `http://localhost:4502/apps/aioa/content/settings.html` (optional `?url=www.example.com`). The console is
   always shown in the light theme (the dark / light toggle is hidden; `js/theme.js` `THEME_SWITCHING`).

   **Website domain, found automatically.** The console uses, in this order: `?url=`, the OSGi `siteDomains`
   (`AIOA_SITE_DOMAIN`), a website saved earlier in the console, and otherwise the domain AEM already knows: a
   Sling mapping host (`/etc/map`) or the Externalizer `publish` domain. AEM's technical `*.adobeaemcloud.com`
   domains are never used on their own; when nothing else is known, the console asks once ("Which website is this
   for?") and saves the answer in `/conf/aioa/settings` (property `siteDomains`, written by the service user
   `aioa-domain-store` created by repoinit). A new website is then registered for the free widget automatically,
   exactly like the Node.js page.

   **Add website / Remove website** are built in but switched off for now (`js/setup.js` `MANAGE_WEBSITES`).

   **Upgrade to Paid Plan** opens the trial page pre-filled with `website`, `email`, `company`, `first_name` and
   `last_name`: the website, the signed-in user's AEM profile (`?username=` / `?email=` / `?company=` win) and
   `companyName`. Without an email, `no-reply@<website>` is sent (as for the registration).
2. **Widget on the site: automatic.** Nothing to do after installing the package: `WidgetAutoInjectFilter`
   adds the widget (the same markup as the widget component) before `</body>` of every page below
   `autoInjectPaths`: on publish, and on author for pages opened directly, in Preview mode and "View as
   Published". Inside the page editor the markup is present but `widget-loader.js` does not load the widget, so it
   never covers the authoring UI. Nothing is added in design mode, for excluded paths, error pages and non-HTML
   answers, and on pages that already contain the widget. Narrow it to your sites with
   `autoInjectPaths` (e.g. `/content/mysite`), or switch it off with `autoInjectEnabled`. Flush the Dispatcher
   cache after changing these settings, since cached pages keep what they were rendered with.
   
   **Manual alternative** (with `autoInjectEnabled` off, or for full control per page): include the component
   once in your page component, e.g. in `customfooterlibs.html` of a Core Components based page:
   ```html
   <sly data-sly-resource="${'aioa-widget' @ resourceType='aioa/components/widget', decorationTagName=''}"/>
   ```
   or allow the component group "All in One Accessibility" in your template policy and drop the
   component on a page / footer experience fragment.
3. **Check it:** open a page on publish (`localhost:4503/content/...html`) or directly on author
   (`localhost:4502/content/...html`). The widget loads after ~3 seconds. It does not load inside the page editor.

## Differences from the Node.js version

- Website: `?url=`, the OSGi `siteDomains`, a website saved in the console, or the domain AEM knows (Sling mappings,
  Externalizer); never `window.location.hostname` (on AEM that is the author host).
- Light theme only (no dark / light toggle); `?theme=` is not used.
- Route: `POST /apps/aioa/content/settings.route.json?action=<name>` (same actions and answers),
  protected by group membership and AEM's CSRF filter (the page sends the `CSRF-Token` header).
- Admin name / email default to the signed-in AEM user's profile (`?username=` / `?email=` still win).
- Saving still goes from the browser straight to `widget-setting-update-platform`, as before.

## Before going to production

- Outbound HTTPS from AEM to `ada.skynettechnologies.us`, `freeada.skynettechnologies.com`,
  `skynetaccessibilityscan.com` (Cloud Service: allowed by default; with advanced networking the JVM proxy is used).
- Admin browsers: allow `ipapi.co` and the Skynet domains in any `connect-src` CSP.
- Publish / CDN CSP: allow `https://www.skynettechnologies.com` (widget script) and the domains the widget calls.
- Dispatcher: keep `/apps` blocked on publish (the console is author-only).
- Never log `check-website` responses (they hold sign-in links and `api_key`).
