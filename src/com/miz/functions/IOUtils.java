package com.miz.functions;

import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Iterator;

import com.miz.mizuu.BuildConfig;

public class IOUtils {

	public static final String IF_NONE_MATCH = "If-None-Match";
	public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
	public static final String E_TAG = "ETag";
	public static final String LAST_MODIFIED = "Last-Modified";

	public static final String CHARSET_UTF8 = "UTF-8";

	private static final String TAG = IOUtils.class.getSimpleName();
	
    private static final String REGEX_INPUT_BOUNDARY_BEGINNING = "\\A";

	public static String convertStreamToString(InputStream is) {
		java.util.Scanner s = null;
		try {
		    s = new java.util.Scanner(is).useDelimiter(REGEX_INPUT_BOUNDARY_BEGINNING);
		    return s.hasNext() ? s.next() : "";
		} finally {
			close(s);
		}
	}

	public static void disconnect(HttpURLConnection connection) {
		if (connection != null) {
			try {
				connection.disconnect();
			} catch (Exception e) {
				IOUtils.error(TAG, "Could not close stream", e);
			}
		}
	}

	public static void close(Cursor cursor) {
		if (cursor != null) {
			cursor.close();
		}
	}

	public static void close(Closeable stream) {
		closeStream(stream);
	}
	public static void closeStream(Closeable stream) {
		if (stream != null) {
			try {
				if(stream instanceof Flushable){
					try {
						((Flushable)stream).flush();
					} catch (IOException e) {
						IOUtils.error(TAG, "Could not flush stream", e);
					}
				}
				stream.close();
			} catch (IOException e) {
				IOUtils.error(TAG, "Could not close stream", e);
			}
		}
	}

	public static void closeFileDescriptor(ParcelFileDescriptor fd) {
		if (fd != null) {
			try {
				fd.close();
			} catch (IOException e) {
				IOUtils.error(TAG, "Could not file descriptor", e);
			}
		}
	}

	public static void copyStream(InputStream is, OutputStream os) {
		final int buffer_size = 1024;
		try {
			byte[] bytes = new byte[buffer_size];
			for (;;) {
				int count = is.read(bytes, 0, buffer_size);
				if (count == -1)
					break;
				os.write(bytes, 0, count);
			}
		} catch (Exception ex) {
		}
	}

	/**
	 * Join a iterable Object with character as glue
	 * 
	 * @param iterable
	 * @param glue
	 * @return Returns a string containing a string representation of all the
	 *         iterable elements in the same order, with the glue character
	 *         between each element.
	 */
	public static String joinToString(Iterable<? extends Object> iterable,
			Character glue) {
		final StringBuilder sb = new StringBuilder();
		final Iterator<? extends Object> iterator = iterable.iterator();
		if (iterator.hasNext()) {
			sb.append(iterator.next());
		}
		while (iterator.hasNext()) {
			sb.append(glue);
			sb.append(iterator.next());
		}
		return sb.toString();
	}

	/**
	 * Join a iterable Object with character as glue
	 * 
	 * @param iterable
	 * @param glue
	 * @return Returns a string containing a string representation of all the
	 *         iterable elements in the same order, with the glue character
	 *         between each element.
	 */
	public static String joinToString(long[] iterable, Character glue) {
		if (iterable.length == 0) {
			return null;
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(iterable[0]);
		for (int i = 1; i < iterable.length; i++) {
			sb.append(glue).append(iterable[i]);
		}
		return sb.toString();
	}

	static public class O extends Object {
		public String getPackageName() {
			return getClass().getEnclosingClass().getPackage().getName();
		}
	}

	public static String getParameterFromQuery(String query, String key) {
		return getParameterByQuery(query, key, 0,
				(query != null) ? query.length() : 0);
	}

	private static String getParameterByQuery(String query, String key,
			int start, int end) {
		if (end < 0) {
			end = query.length();
		}
		if (query == null || start >= query.length() || start > end) {
			return null;
		}
		if (query.charAt(start) == '?') {
			return getParameterByQuery(query, key, start + 1, end);
		}
		final String encodedKey = Uri.encode(key, null);
		do {
			int nextAmpersand = query.indexOf('&', start);
			int nextEnd = nextAmpersand != -1 ? nextAmpersand : end;

			int separator = query.indexOf('=', start);
			if (separator > nextEnd || separator == -1) {
				separator = nextEnd;
			}

			if (separator - start == encodedKey.length()
					&& query.regionMatches(start, encodedKey, 0,
							encodedKey.length())) {
				if (separator == nextEnd) {
					return "";
				} else {
					return Uri.decode(query.substring(separator + 1, nextEnd));
				}
			}
			if (nextAmpersand != -1) {
				start = nextAmpersand + 1;
			} else {
				break;
			}
		} while (true);
		return null;
	}

	public static void error(Object object, Throwable tr) {
		if (BuildConfig.DEBUG)
			Log.e(object.getClass().getSimpleName(), tr.getMessage(), tr);
	}

	public static void error(String tag, Throwable tr) {
		if (BuildConfig.DEBUG)
			Log.e(tag, tr.getMessage(), tr);
	}

	public static void error(Object object, String msg) {
		if (BuildConfig.DEBUG)
			Log.e(object.getClass().getSimpleName(), msg);
	}

	public static void error(String tag, String msg) {
		if (BuildConfig.DEBUG)
			Log.e(tag, msg);
	}

	public static void error(Object object, String msg, Throwable tr) {
		if (BuildConfig.DEBUG)
			Log.e(object.getClass().getSimpleName(), msg, tr);
	}

	public static void error(String tag, String msg, Throwable tr) {
		if (BuildConfig.DEBUG)
			Log.e(tag, msg, tr);
	}

	public static void warn(Object object, String msg) {
		if (BuildConfig.DEBUG)
			Log.w(object.getClass().getSimpleName(), msg);
	}

	public static void warn(String tag, String msg) {
		if (BuildConfig.DEBUG)
			Log.w(tag, msg);
	}

	public static void info(Object object, String msg) {
		if (BuildConfig.DEBUG)
			Log.i(object.getClass().getSimpleName(), msg);
	}

	public static void info(String tag, String msg) {
		if (BuildConfig.DEBUG)
			Log.i(tag, msg);
	}

	public static void verbose(Object object, String msg) {
		if (BuildConfig.DEBUG)
			Log.v(object.getClass().getSimpleName(), msg);
	}

	public static void verbose(String tag, String msg) {
		if (BuildConfig.DEBUG)
			Log.v(tag, msg);
	}

	public static void debug(Object object, String msg, Object dump,
			Object... objects) {
		if (BuildConfig.DEBUG) {
			StringBuilder sb = new StringBuilder();
			sb.append(msg).append(" : ")
					.append((dump == null) ? "null" : dump.toString());
			for (Object o : objects) {
				sb.append(", ");
				sb.append((o == null) ? "null" : o.toString());
			}
			Log.d(object.getClass().getSimpleName(), sb.toString());
		}
	}
	
	public static void debug(String tag, String msg, Object dump,
			Object... objects) {
		if (BuildConfig.DEBUG) {
			StringBuilder sb = new StringBuilder();
			sb.append(msg).append(" : ")
					.append((dump == null) ? "null" : dump.toString());
			for (Object o : objects) {
				sb.append(", ");
				sb.append((o == null) ? "null" : o.toString());
			}
			Log.d(tag, sb.toString());
		}
	}
	
	public static void debug(Object object, String msg, Object dump) {
		if (BuildConfig.DEBUG)
			Log.d(object.getClass().getSimpleName(), msg + " :"
					+ ((dump == null) ? "null" : dump.toString()));
	}

	public static void debug(String tag, String msg, Object dump) {
		if (BuildConfig.DEBUG)
			Log.d(tag, msg + " :" + ((dump == null) ? "null" : dump.toString()));
	}

	public static void debug(Object object, String msg) {
		if (BuildConfig.DEBUG)
			Log.d(object.getClass().getSimpleName(), msg);
	}

	public static void debug(String tag, String msg) {
		if (BuildConfig.DEBUG)
			Log.d(tag, msg);
	}
}
