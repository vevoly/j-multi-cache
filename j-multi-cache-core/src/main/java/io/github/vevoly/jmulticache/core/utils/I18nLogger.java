package io.github.vevoly.jmulticache.core.utils;

import org.slf4j.Logger;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 一个用于支持国际化 (i18n) 日志输出的辅助类。
 * <p>
 * 此类封装了 {@link java.util.ResourceBundle} 和 {@link org.slf4j.Logger}，
 * 允许开发者在日志代码中使用语言无关的“键 (key)”，而不是硬编码的日志消息。
 * 它会根据当前的系统语言环境 (Locale)，自动从对应的 {@code .properties} 资源文件中加载消息模板，
 * 并将参数格式化后输出。
 * <p>
 * A helper class for supporting internationalized (i18n) log output.
 * This class wraps {@link java.util.ResourceBundle} and {@link org.slf4j.Logger},
 * allowing developers to use language-neutral keys in their logging code instead of hard-coded log messages.
 * It automatically loads message templates from the corresponding {@code .properties} resource file
 * based on the current system locale, formats them with arguments, and outputs the log.
 *
 * @author vevoly
 */
public class I18nLogger {

    private final Logger slf4jLogger;
    private final ResourceBundle resourceBundle;

    /**
     * 定义了国际化资源文件的基础名称。
     * <p>
     * Defines the base name for the internationalization resource files.
     */
    private static final String BUNDLE_BASE_NAME = "i18n.jmulticache_messages";

    /**
     * 构造一个新的 I18nLogger 实例。
     * <p>
     * Constructs a new I18nLogger instance.
     *
     * @param slf4jLogger 底层的 SLF4J Logger 实例，用于实际的日志输出。/ The underlying SLF4J Logger instance for actual log output.
     */
    public I18nLogger(Logger slf4jLogger) {
        this.slf4jLogger = slf4jLogger;
        ResourceBundle bundle = null;
        try {
            // 尝试使用当前线程的上下文类加载器和默认的 Locale 加载资源包。
            // Tries to load the resource bundle using the current thread's context class loader and the default Locale.
            bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.getDefault(), Thread.currentThread().getContextClassLoader());
        } catch (MissingResourceException e) {
            // 如果找不到资源文件，为了不影响程序运行，只记录一个警告。后续的日志将输出原始 Key。
            // If the resource file cannot be found, log a warning without affecting program execution. Subsequent logs will output the original key.
            slf4jLogger.warn("Could not find i18n resource bundle with base name '{}'. Internationalized logging will be disabled.", BUNDLE_BASE_NAME);
        }
        this.resourceBundle = bundle;
    }

    /**
     * 以 INFO 级别记录一条国际化日志。
     * <p>
     * Logs an internationalized message at the INFO level.
     *
     * @param key  资源文件中的消息键。/ The message key in the resource file.
     * @param args 用于格式化消息的可选参数。/ Optional arguments for formatting the message.
     */
    public void info(String key, Object... args) {
        if (slf4jLogger.isInfoEnabled()) {
            slf4jLogger.info(format(key, args));
        }
    }

    /**
     * 以 WARN 级别记录一条国际化日志。
     * <p>
     * Logs an internationalized message at the WARN level.
     *
     * @param key  资源文件中的消息键。/ The message key in the resource file.
     * @param args 用于格式化消息的可选参数。/ Optional arguments for formatting the message.
     */
    public void warn(String key, Object... args) {
        if (slf4jLogger.isWarnEnabled()) {
            slf4jLogger.warn(format(key, args));
        }
    }

    /**
     * 以 ERROR 级别记录一条带异常信息的国际化日志。
     * <p>
     * Logs an internationalized message with an exception at the ERROR level.
     *
     * @param key  资源文件中的消息键。/ The message key in the resource file.
     * @param t    要记录的异常。/ The exception to log.
     * @param args 用于格式化消息的可选参数。/ Optional arguments for formatting the message.
     */
    public void error(String key, Throwable t, Object... args) {
        if (slf4jLogger.isErrorEnabled()) {
            slf4jLogger.error(format(key, args), t);
        }
    }

    /**
     * 以 ERROR 级别记录一条国际化日志。
     * <p>
     * Logs an internationalized message at the ERROR level.
     *
     * @param key  资源文件中的消息键。/ The message key in the resource file.
     * @param args 用于格式化消息的可选参数。/ Optional arguments for formatting the message.
     */
    public void error(String key, Object... args) {
        if (slf4jLogger.isErrorEnabled()) {
            slf4jLogger.error(format(key, args));
        }
    }

    /**
     * 根据给定的键和参数，格式化最终的日志消息。
     * <p>
     * Formats the final log message based on the given key and arguments.
     *
     * @param key  消息键。/ The message key.
     * @param args 格式化参数。/ The format arguments.
     * @return 格式化后的日志字符串。/ The formatted log string.
     */
    private String format(String key, Object... args) {
        if (resourceBundle == null) {
            // 如果资源包未加载，返回一个清晰的调试信息，而不是让程序崩溃。
            // If the resource bundle is not loaded, return a clear debug message instead of crashing.
            return "[i18n disabled] " + key;
        }
        try {
            String pattern = resourceBundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            // 如果在资源文件中找不到对应的 key，返回一个包含 key 本身的错误信息，方便调试。
            // If the key is not found in the resource file, return an error message containing the key itself for easy debugging.
            return "!!! LOG KEY NOT FOUND: " + key + " !!!";
        } catch (Exception e) {
            // 其他格式化异常
            // Other formatting exceptions
            return "!!! LOG FORMATTING ERROR for key: " + key + " !!!";
        }
    }
}
