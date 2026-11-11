package dhp.thl.tpl.ndv.xposed;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Optional Zalo module (LSPosed / NPatch) that makes ZSticker's "Animated GIF" mode animate.
 *
 * ZSticker shares a still first-frame sticker (type 3) and passes the hosted animation URL in the
 * "zsticker_animated_url" intent extra. Zalo's photo-sticker message has an optional "webp" object
 * that its chat row plays; this hook fills it with that URL.
 *
 * It only touches shares that carry the extra, only inside com.zing.zalo, and only sticker messages.
 * Class and field names below belong to the Zalo build this was developed against; if Zalo changes
 * them the hooks log and do nothing, and ZSticker keeps working with vanilla GIFs.
 */
public class ZaloHook implements IXposedHookLoadPackage {
    private static final String TAG = "ZSticker";
    private static final String ZALO_PACKAGE = "com.zing.zalo";
    private static final String EXTRA_ANIMATED_URL = "zsticker_animated_url";
    private static final long PENDING_TTL_MS = 2 * 60 * 1000L;

    private static volatile String pendingUrl;
    private static volatile long pendingAt;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!ZALO_PACKAGE.equals(lp.packageName)) return;
        hookShareIntent();
        hookPhotoSticker(lp.classLoader);
    }

    private static boolean hasPending() {
        return pendingUrl != null && System.currentTimeMillis() - pendingAt < PENDING_TTL_MS;
    }

    /** Remembers the animation URL carried by the share intent that opens Zalo's share screen. */
    private static void hookShareIntent() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Intent intent = ((Activity) param.thisObject).getIntent();
                        String url = intent == null ? null : intent.getStringExtra(EXTRA_ANIMATED_URL);
                        if (url != null && url.startsWith("https://")) {
                            pendingUrl = url;
                            pendingAt = System.currentTimeMillis();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "share intent hook", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "cannot hook Activity.onCreate", t);
        }
    }

    /** Fills the photo sticker's animated "webp" slot while the message is created and serialized. */
    private static void hookPhotoSticker(ClassLoader cl) {
        try {
            // Created before the chat row binds, so the row sees the animation immediately.
            XposedHelpers.findAndHookConstructor("f20.p0", cl,
                    int.class, int.class, String.class, String.class, String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!hasPending()) return;
                            try {
                                inject(param.thisObject, "");
                            } catch (Throwable t) {
                                Log.w(TAG, "inject (ctor)", t);
                            }
                        }
                    });

            // At send time the real thumbnail URL is known; also drop the field for non-sticker photos.
            XposedHelpers.findAndHookMethod("f20.p0", cl, "l", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!hasPending()) return;
                    try {
                        Object message = param.thisObject;
                        if (!getBoolean(message, "q")) {
                            setField(message, "t", null);
                            setField(message, "x", null);
                            return;
                        }
                        inject(message, String.valueOf(getField(superclassField(message, "c"), message)));
                    } catch (Throwable t) {
                        Log.w(TAG, "inject (send)", t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (getBoolean(param.thisObject, "q")) pendingUrl = null;
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "cannot hook photo sticker (unsupported Zalo version?)", t);
        }
    }

    private static void inject(Object message, String thumb) throws Exception {
        String url = pendingUrl;
        if (url == null) return;
        JSONObject webp = new JSONObject();
        webp.put("url", url);
        webp.put("thumb", thumb == null || "null".equals(thumb) ? "" : thumb);
        webp.put("width", getInt(message, "l"));
        webp.put("height", getInt(message, "m"));

        setField(message, "t", webp.toString());
        Class<?> animation = Class.forName("androidx.media3.transformer.t1", true, message.getClass().getClassLoader());
        setField(message, "x", animation.getConstructor(int.class, JSONObject.class).newInstance(2, webp));
    }

    private static Field superclassField(Object o, String name) throws NoSuchFieldException {
        for (Class<?> c = o.getClass().getSuperclass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object getField(Field f, Object o) throws IllegalAccessException {
        return f.get(o);
    }

    private static Field own(Object o, String name) throws NoSuchFieldException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void setField(Object o, String name, Object value) throws Exception {
        own(o, name).set(o, value);
    }

    private static boolean getBoolean(Object o, String name) throws Exception {
        return own(o, name).getBoolean(o);
    }

    private static int getInt(Object o, String name) throws Exception {
        return own(o, name).getInt(o);
    }
}
