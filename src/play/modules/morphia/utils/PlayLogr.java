package play.modules.morphia.utils;

import play.Logger;
import play.modules.morphia.MorphiaPlugin;

import com.google.code.morphia.logging.Logr;

public class PlayLogr implements Logr {
    private static final long serialVersionUID = -3207944672312237465L;

    @Override
    public boolean isTraceEnabled() {
        return Logger.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        MorphiaPlugin.trace(msg);
    }

    @Override
    public void trace(String format, Object... arg) {
        MorphiaPlugin.trace(format, arg);
    }

    @Override
    public void trace(String msg, Throwable t) {
        MorphiaPlugin.trace("Exception captured: %s, \n%s", t, msg);
    }

    @Override
    public boolean isDebugEnabled() {
        return Logger.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        MorphiaPlugin.debug(msg);
    }

    @Override
    public void debug(String format, Object... arg) {
        MorphiaPlugin.debug(format, arg);
    }

    @Override
    public void debug(String msg, Throwable t) {
        MorphiaPlugin.debug(t, msg);
    }
    
    private Boolean isInfoEnabled_ = null;

    @Override
    public boolean isInfoEnabled() {
        if (null == isInfoEnabled_) {
            isInfoEnabled_ = Logger.isEnabledFor("INFO"); 
        }
        return isInfoEnabled_;
    }

    @Override
    public void info(String msg) {
        if (MorphiaPlugin.loggerRegistered()) MorphiaPlugin.info(msg);
    }

    @Override
    public void info(String format, Object... arg) {
        MorphiaPlugin.info(format, arg);
    }

    @Override
    public void info(String msg, Throwable t) {
        MorphiaPlugin.info(t, msg);
    }

    private Boolean isWarningEnabled_ = null;
    @Override
    public boolean isWarningEnabled() {
        if (null == isWarningEnabled_) {
            isWarningEnabled_ = Logger.isEnabledFor("WARN"); 
        }
        return isWarningEnabled_;
    }

    @Override
    public void warning(String msg) {
        MorphiaPlugin.warn(msg);
    }

    @Override
    public void warning(String format, Object... arg) {
        MorphiaPlugin.warn(format, arg);
    }

    @Override
    public void warning(String msg, Throwable t) {
        MorphiaPlugin.warn(t, msg);
    }

    private Boolean isErrorEnabled_ = null;
    @Override
    public boolean isErrorEnabled() {
        if (null == isErrorEnabled_) {
            isErrorEnabled_ = Logger.isEnabledFor("ERROR");
        }
        return isErrorEnabled_;
    }

    @Override
    public void error(String msg) {
        MorphiaPlugin.error(msg);
    }

    @Override
    public void error(String format, Object... arg) {
        MorphiaPlugin.error(format, arg);
    }

    @Override
    public void error(String msg, Throwable t) {
        MorphiaPlugin.error(t, msg);
    }
    }
