/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.appenders;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.encoder.EncoderBase;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class JsonLayout extends EncoderBase<ILoggingEvent> {

    private static final Pattern SEP_PATTERN = Pattern.compile("(?:\\p{Space}*?[,;]\\p{Space}*)+");
    private static final Pattern PAIR_SEP_PATTERN = Pattern.compile("(?:\\p{Space}*?[:=]\\p{Space}*)+");

    private static final char[] HEX_CHARS =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private enum ExceptionField {
        CLASS("class"),
        MESSAGE("message"),
        STACKTRACE("stacktrace"),
        CAUSE("cause"),
        SUPPRESSED("suppressed"),
        ;

        private final String val;

        ExceptionField(String val) {
            this.val = val;
        }
    }

    private enum Field {
        EXCEPTION("exception"),
        LEVEL("level"),
        LOGGER("logger"),
        MESSAGE("message"),
        MDC("mdc"),
        HOST("host"),
        TAGS("tags"),
        TIMESTAMP("@timestamp"),
        THREAD("thread"),
        VERSION("@version");

        private final String val;

        Field(String exception) {
            val = exception;
        }

        public static Field fromValue(String val) {
            for (Field field : values()) {
                if (field.val.equals(val)) {
                    return field;
                }
            }
            throw new IllegalArgumentException(
                String.format("Unsupported value [%s]. Expecting one of %s.", val, Arrays.toString(values())));
        }
    }

    private static final String VERSION = "1";

    private String tagsVal;
    private String fieldsVal;
    private String includedFields;
    private String excludedFields;

    private final Map<String, String> fields = new HashMap<>();
    private final Set<Field> renderedFields = EnumSet.allOf(Field.class);
    private final ThreadLocal<DateFormat> dateFormat = new ThreadLocal<DateFormat>(){
        @Override
        protected DateFormat initialValue() {
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat;
        }
    };
    private String[] tags;
    private String hostName = "unknown";

    public byte[] headerBytes() {
        return new byte[0];
    }

    public byte[] footerBytes() {
        return new byte[0];
    }

    public byte[] encode(final ILoggingEvent event) {
        final StringBuilder buf = new StringBuilder(32 * 1024);
        buf.append('{');

        boolean hasPrevField = false;
        if (renderedFields.contains(Field.EXCEPTION)) {
            hasPrevField = appendException(buf, event);
        }

        if (hasPrevField) {
            buf.append(',');
        }
        hasPrevField = appendFields(buf, event);

        if (renderedFields.contains(Field.LEVEL)) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, Field.LEVEL.val, event.getLevel().toString());
            hasPrevField = true;
        }

        //No support for Field.LOCATION

        if (renderedFields.contains(Field.LOGGER)) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, Field.LOGGER.val, event.getLoggerName());
            hasPrevField = true;
        }

        if (renderedFields.contains(Field.MESSAGE)) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, Field.MESSAGE.val, event.getFormattedMessage());
            hasPrevField = true;
        }

        if (renderedFields.contains(Field.MDC)) {
            if (hasPrevField) {
                buf.append(',');
            }
            hasPrevField = appendMDC(buf, event);
        }

        //No support for Field.NDC

        if (renderedFields.contains(Field.HOST)) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, Field.HOST.val, hostName);
            hasPrevField = true;
        }

        //No support for Field.PATH

        if (renderedFields.contains(Field.TAGS)) {
            if (hasPrevField) {
                buf.append(',');
            }
            hasPrevField = appendTags(buf, event);
        }

        if (renderedFields.contains(Field.TIMESTAMP)) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, Field.TIMESTAMP.val, dateFormat.get().format(new Date(event.getTimeStamp())));
            hasPrevField = true;
        }

        if (renderedFields.contains(Field.THREAD)) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, Field.THREAD.val, event.getThreadName());
            hasPrevField = true;
        }

        if (renderedFields.contains(Field.VERSION)) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, Field.VERSION.val, VERSION);
        }

        buf.append("}\n");

        try {
            return buf.toString().getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            return "{error:\"failed to encode answer to UTF-8\"}\n".getBytes();
        }
    }

    @SuppressWarnings("UnusedParameters")
    private boolean appendFields(StringBuilder buf, ILoggingEvent event) {
        if (fields.isEmpty()) {
            return false;
        }

        for (Iterator<Map.Entry<String, String>> iter = fields.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<String, String> entry = iter.next();
            appendField(buf, entry.getKey(), entry.getValue());
            if (iter.hasNext()) {
                buf.append(',');
            }
        }

        return true;
    }

    @SuppressWarnings("UnusedParameters")
    private boolean appendTags(StringBuilder builder, ILoggingEvent event) {
        if (tags == null || tags.length == 0) {
            return false;
        }

        appendQuotedName(builder, Field.TAGS.val);
        builder.append(":[");
        for (int i = 0, len = tags.length; i < len; i++) {
            appendQuotedValue(builder, tags[i]);
            if (i != len - 1) {
                builder.append(',');
            }
        }
        builder.append(']');

        return true;
    }

    private boolean appendMDC(StringBuilder buf, ILoggingEvent event) {
        Map<?, ?> entries = event.getMDCPropertyMap();
        if (entries.isEmpty()) {
            return false;
        }

        appendQuotedName(buf, Field.MDC.val);
        buf.append(":{");

        for (Iterator<? extends Map.Entry<?, ?>> iter = entries.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<?, ?> entry = iter.next();
            appendField(buf, entry.getKey(), entry.getValue());
            if (iter.hasNext()) {
                buf.append(',');
            }
        }
        buf.append('}');

        return true;
    }

    private boolean appendException(StringBuilder buf, ILoggingEvent event) {
        IThrowableProxy throwableInfo = event.getThrowableProxy();
        if (throwableInfo == null) {
            return false;
        }

        appendQuotedName(buf, Field.EXCEPTION.val);
        buf.append(':');
        appendException(buf, throwableInfo);
        return true;
    }

    private void appendException(StringBuilder buf, IThrowableProxy throwableInfo) {
        buf.append("{");

        boolean hasPrevField = false;

        String message = throwableInfo.getMessage();
        if (message != null) {
            appendField(buf, ExceptionField.MESSAGE.val, message);
            hasPrevField = true;
        }

        String className = throwableInfo.getClassName();
        if (className != null) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendField(buf, ExceptionField.CLASS.val, className);
            hasPrevField = true;
        }

        StackTraceElementProxy[] stackTrace = throwableInfo.getStackTraceElementProxyArray();
        if (stackTrace != null && stackTrace.length != 0) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendQuotedName(buf, ExceptionField.STACKTRACE.val);
            buf.append(":\"");
            for (int i = 0, len = stackTrace.length; i < len; i++) {
                appendValue(buf, stackTrace[i].getSTEAsString());
                if (i != len - 1) {
                    appendChar(buf, '\n');
                }
            }
            buf.append('\"');
        }

        IThrowableProxy cause = throwableInfo.getCause();
        if (cause != null) {
            if (hasPrevField) {
                buf.append(',');
            }
            appendQuotedName(buf, ExceptionField.CAUSE.val);
            buf.append(':');
            appendException(buf, cause);
            hasPrevField = true;
        }

        IThrowableProxy[] suppressed = throwableInfo.getSuppressed();
        if (suppressed != null && suppressed.length > 0) {
            if (hasPrevField) {
                buf.append(',');
            }

            boolean isFirst = true;
            appendQuotedName(buf, ExceptionField.SUPPRESSED.val);
            buf.append(":[");
            for (IThrowableProxy info : suppressed) {
                if (info == null) continue;

                if (isFirst) {
                    isFirst = false;
                } else {
                    buf.append(',');
                }
                appendException(buf, info);
            }
            buf.append("]");
        }

        buf.append('}');
    }

    @Override
    public void start() {
        if (includedFields != null) {
            String[] included = SEP_PATTERN.split(includedFields);
            for (String val : included) {
                renderedFields.add(Field.fromValue(val));
            }
        }
        if (excludedFields != null) {
            String[] excluded = SEP_PATTERN.split(excludedFields);
            for (String val : excluded) {
                renderedFields.remove(Field.fromValue(val));
            }
        }
        if (tagsVal != null) {
            tags = SEP_PATTERN.split(tagsVal);
        }
        if (fieldsVal != null) {
            String[] fields = SEP_PATTERN.split(fieldsVal);
            for (String fieldVal : fields) {
                String[] field = PAIR_SEP_PATTERN.split(fieldVal);
                this.fields.put(field[0], field[1]);
            }
        }
        if (hostName == null) {
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                hostName = "localhost";
            }
        }
        super.start();
    }

    private void appendQuotedName(StringBuilder out, Object name) {
        out.append('\"');
        appendValue(out, String.valueOf(name));
        out.append('\"');
    }

    private void appendQuotedValue(StringBuilder out, Object val) {
        out.append('\"');
        appendValue(out, String.valueOf(val));
        out.append('\"');
    }

    private void appendValue(StringBuilder out, String val) {
        for (int i = 0, len = val.length(); i < len; i++) {
            appendChar(out, val.charAt(i));
        }
    }

    private void appendField(StringBuilder out, Object name, Object val) {
        appendQuotedName(out, name);
        out.append(':');
        appendQuotedValue(out, val);
    }

    private void appendChar(StringBuilder out, char ch) {
        switch (ch) {
            case '"':
                out.append("\\\"");
                break;
            case '\\':
                out.append("\\\\");
                break;
            case '/':
                out.append("\\/");
                break;
            case '\b':
                out.append("\\b");
                break;
            case '\f':
                out.append("\\f");
                break;
            case '\n':
                out.append("\\n");
                break;
            case '\r':
                out.append("\\r");
                break;
            case '\t':
                out.append("\\t");
                break;
            default:
                if ((ch <= '\u001F') || ('\u007F' <= ch && ch <= '\u009F') || ('\u2000' <= ch && ch <= '\u20FF')) {
                    out.append("\\u")
                        .append(HEX_CHARS[ch >> 12 & 0x000F])
                        .append(HEX_CHARS[ch >> 8 & 0x000F])
                        .append(HEX_CHARS[ch >> 4 & 0x000F])
                        .append(HEX_CHARS[ch & 0x000F]);
                } else {
                    out.append(ch);
                }
                break;
        }
    }

    public void setTags(String tags) {
        this.tagsVal = tags;
    }

    public void setFields(String fields) {
        this.fieldsVal = fields;
    }

    public void setIncludedFields(String includedFields) {
        this.includedFields = includedFields;
    }

    public void setExcludedFields(String excludedFields) {
        this.excludedFields = excludedFields;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }
}
