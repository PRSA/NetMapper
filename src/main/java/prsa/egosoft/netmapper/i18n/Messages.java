package prsa.egosoft.netmapper.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for internationalization (i18n). Provides access to localized
 * strings from resource bundles with dynamic switching.
 */
public class Messages
{
    private static final String BUNDLE_NAME = "messages";
    private static ResourceBundle bundle;
    private static Locale currentLocale;
    private static String[] availableLanguages =
    {"Español", "English", "中文 (简体)"};
    private static Locale[] availableLocales =
    {new Locale("es"), Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE};
    public static final int defaultLanguageIndex = 0;
    
    private static final List<Runnable> localeListeners = new ArrayList<>();
    
    private Messages()
    {
        // Private constructor for utility class
    }
    
    static
    {
        // Initialize with default locale, falling back to Spanish if not
        // in available locales
        initLocale(Locale.getDefault());
    }
    
    private static void initLocale(Locale locale)
    {
        for(Locale l : availableLocales)
        {
            if(l.equals(locale))
            {
                currentLocale = l;
                break;
            }
        }
        if(currentLocale != locale)
        {
            // Default and explicit Spanish
            currentLocale = availableLocales[defaultLanguageIndex];
        }
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
    }
    
    /**
     * Get a localized string by key.
     * 
     * @param key The resource key
     * @return The localized string, or the key itself if not found
     */
    public static String getString(String key)
    {
        try
        {
            return bundle.getString(key);
        }
        catch(Exception e)
        {
            return key; // Fallback to key if not found
        }
    }
    
    /**
     * Get a formatted localized string with arguments using MessageFormat.
     * 
     * @param key  The resource key
     * @param args Arguments to format into the string
     * @return The formatted localized string
     */
    public static String getString(String key, Object... args)
    {
        try
        {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        }
        catch(Exception e)
        {
            return key; // Fallback to key if not found
        }
    }
    
    /**
     * Set a specific locale and notify listeners.
     * 
     * @param locale The locale to use
     */
    public static void setLocale(Locale locale)
    {
        initLocale(locale);
        notifyListeners();
    }
    
    /**
     * Get current locale.
     */
    public static Locale getLocale()
    {
        return currentLocale;
    }
    
    /**
     * Get available languages.
     */
    public static String[] getAvailableLanguages()
    {
        return availableLanguages;
    }
    
    /**
     * Get available locales.
     */
    public static Locale[] getAvailableLocales()
    {
        return availableLocales;
    }
    
    /**
     * Register a listener to be notified when the locale changes.
     */
    public static void addLocaleListener(Runnable listener)
    {
        localeListeners.add(listener);
    }
    
    private static void notifyListeners()
    {
        for(Runnable listener : localeListeners)
        {
            listener.run();
        }
    }
}
