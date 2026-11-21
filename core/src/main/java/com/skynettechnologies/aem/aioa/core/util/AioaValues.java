package com.skynettechnologies.aem.aioa.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validation and loose value conversions, ported 1:1 from app/skynet-widget.js and app/helpers.js.
 * API values may be numbers, numeric strings, booleans or null, so every read goes through these.
 */
public final class AioaValues {

    /** Website host: a domain name such as "example.com", or "localhost". Same rule as the browser's AIOA.util.DOMAIN_RE. */
    private static final Pattern DOMAIN_RE = Pattern.compile(
            "^(?:localhost|(?=.{3,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63})$");

    /** An email address the registration accepts (the same cases as PHP's FILTER_VALIDATE_EMAIL). */
    private static final Pattern EMAIL_RE = Pattern.compile(
            "^(?=.{1,254}$)(?=[^@]{1,64}@)[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+(?![0-9]+$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");

    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\{.*\\}$");

    private AioaValues() {
    }

    /* ---------- Domains, emails, request values ---------- */

    /** "https://www.Example.com/shop" -> "www.example.com" */
    public static String normalizeDomain(String value) {
        String domain = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        domain = domain.replaceFirst("^[a-z][a-z0-9+.-]*://", ""); // scheme
        domain = domain.replaceFirst("[/?#].*$", "");               // path, query, hash
        domain = domain.replaceFirst(":\\d+$", "");                 // port
        return domain.replaceAll("^\\.+|\\.+$", "");
    }

    /** True for a domain name or "localhost". IP addresses are not accepted. */
    public static boolean isValidDomain(String domain) {
        return domain != null && DOMAIN_RE.matcher(domain).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_RE.matcher(email).matches();
    }

    /**
     * Clean one value of the query string the platform sent (helpers.js aioaRequest):
     * a raw placeholder such as "{email}" counts as missing, other braces are dropped.
     */
    public static String cleanRequestValue(String value) {
        if (value == null) {
            return "";
        }
        if (PLACEHOLDER.matcher(value.trim()).matches()) {
            return "";
        }
        return value.replaceAll("[{}]", "").trim();
    }

    /** Last value of a multi-valued parameter ("a=1&a=2" gives "2"), '' when missing. */
    public static String lastValue(String[] values) {
        return values == null || values.length == 0 || values[values.length - 1] == null ? "" : values[values.length - 1];
    }

    /** "top_left" -> "top-left", "aioa-extra-small-icon" -> "extra-small" */
    public static String slug(String value) {
        return value.replace("aioa-", "").replace("-icon", "").replace("_", "-");
    }

    public static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** JSON for a script block: <, >, & and ' escaped (helpers.js jsonForScript). */
    public static String jsonForScript(String json) {
        return json.replace("<", "\\u003C").replace(">", "\\u003E").replace("&", "\\u0026").replace("'", "\\u0027");
    }

    /* ---------- Loose conversions (JS semantics) ---------- */

    public static double toFloat(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) ? number : 0;
        }
        if (value instanceof String) {
            Matcher match = LEADING_NUMBER.matcher((String) value);
            if (match.find()) {
                try {
                    return Double.parseDouble(match.group().trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public static long toInt(Object value) {
        double number = toFloat(value);
        return Double.isFinite(number) ? (long) number : 0; // (long) truncates toward zero, like Math.trunc
    }

    /** String(value) as JavaScript would print it; '' for null, false, objects and arrays. */
    public static String toStr(Object value) {
        if (value == null || Boolean.FALSE.equals(value) || value instanceof Map || value instanceof Collection) {
            return "";
        }
        if (Boolean.TRUE.equals(value)) {
            return "1";
        }
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            return numberToString(((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    public static boolean isObject(Object value) {
        return value instanceof Map;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    public static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    /** Empty like PHP's empty(): missing, null, false, 0, "", "0", [] or {} */
    public static boolean isEmpty(Object value) {
        if (value == null || Boolean.FALSE.equals(value) || "".equals(value) || "0".equals(value)) {
            return true;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0;
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        return false;
    }

    /** Round half away from zero, like PHP's round(). */
    public static double phpRound(double value, int precision) {
        BigDecimal shifted = new BigDecimal(Double.toString(Math.abs(value))).round(new java.math.MathContext(15));
        double result = shifted.setScale(precision, RoundingMode.HALF_UP).doubleValue();
        return value < 0 ? -result : result;
    }

    /** A JSON-friendly number: 86.36 stays a double, 12.0 becomes 12 (as JavaScript prints it). */
    public static Number jsonNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return (long) value;
        }
        return value;
    }

    private static String numberToString(double value) {
        if (!Double.isFinite(value)) {
            return Double.isNaN(value) ? "NaN" : (value > 0 ? "Infinity" : "-Infinity");
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e21) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
