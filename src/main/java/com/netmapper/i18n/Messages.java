package com.netmapper.i18n;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Utility class for internationalization (i18n).
 * Provides access to localized strings from resource bundles.
 */
public class Messages {
    private static final String BUNDLE_NAME = "messages";
    private static ResourceBundle bundle;

    static {
        // Load bundle based on system locale
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault());
    }

    /**
     * Get a localized string by key.
     * 
     * @param key The resource key
     * @return The localized string, or the key itself if not found
     */
    public static String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return key; // Fallback to key if not found
        }
    }

    /**
     * Get a formatted localized string with arguments.
     * 
     * @param key  The resource key
     * @param args Arguments to format into the string
     * @return The formatted localized string
     */
    public static String getString(String key, Object... args) {
        try {
            return String.format(bundle.getString(key), args);
        } catch (Exception e) {
            return key; // Fallback to key if not found
        }
    }

    /**
     * Set a specific locale (useful for testing or manual language switching).
     * 
     * @param locale The locale to use
     */
    public static void setLocale(Locale locale) {
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
    }
}
