package org.ebookdroid.common.settings.books;

import org.ebookdroid.common.settings.definitions.AppPreferences;
import org.ebookdroid.common.settings.types.DocumentViewMode;
import org.ebookdroid.common.settings.types.PageAlign;
import org.ebookdroid.common.settings.types.RotationType;
import org.ebookdroid.core.curl.PageAnimationType;

import org.emdev.utils.enums.EnumUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class BookSettingsTemplate {

    public String name;
    public boolean cropPages;
    public RotationType rotation;
    public DocumentViewMode viewMode;
    public PageAlign pageAlign;
    public PageAnimationType animationType;
    public boolean splitPages;
    public boolean splitRTL;
    public boolean nightMode;
    public boolean positiveImagesInNightMode;
    public boolean tint;
    public int tintColor;
    public int contrast;
    public int gamma;
    public int exposure;
    public boolean autoLevels;
    public boolean rtl;

    public BookSettingsTemplate(final String name, final BookSettings bs) {
        this.name = name;
        this.cropPages = bs.cropPages;
        this.rotation = bs.rotation;
        this.viewMode = bs.viewMode;
        this.pageAlign = bs.pageAlign;
        this.animationType = bs.animationType;
        this.splitPages = bs.splitPages;
        this.splitRTL = bs.splitRTL;
        this.nightMode = bs.nightMode;
        this.positiveImagesInNightMode = bs.positiveImagesInNightMode;
        this.tint = bs.tint;
        this.tintColor = bs.tintColor;
        this.contrast = bs.contrast;
        this.gamma = bs.gamma;
        this.exposure = bs.exposure;
        this.autoLevels = bs.autoLevels;
        this.rtl = bs.rtl;
    }

    BookSettingsTemplate(final JSONObject obj) throws JSONException {
        this.name = obj.getString("name");
        this.cropPages = obj.getBoolean("cropPages");
        this.rotation = EnumUtils.getByName(RotationType.class, obj, "rotation", RotationType.UNSPECIFIED);
        this.viewMode = EnumUtils.getByName(DocumentViewMode.class, obj, "viewMode", DocumentViewMode.VERTICALL_SCROLL);
        this.pageAlign = EnumUtils.getByName(PageAlign.class, obj, "pageAlign", PageAlign.AUTO);
        this.animationType = EnumUtils.getByName(PageAnimationType.class, obj, "animationType", PageAnimationType.NONE);
        this.splitPages = obj.getBoolean("splitPages");
        this.splitRTL = obj.optBoolean("splitRTL", false);
        this.nightMode = obj.getBoolean("nightMode");
        this.positiveImagesInNightMode = obj.optBoolean("positiveImagesInNightMode", false);
        this.tint = obj.optBoolean("tint", false);
        this.tintColor = obj.optInt("tintColor", 0);
        this.contrast = obj.optInt("contrast", AppPreferences.CONTRAST.defValue);
        this.gamma = obj.optInt("gamma", AppPreferences.GAMMA.defValue);
        this.exposure = obj.optInt("exposure", AppPreferences.EXPOSURE.defValue);
        this.autoLevels = obj.getBoolean("autoLevels");
        this.rtl = obj.optBoolean("rtl", false);
    }

    JSONObject toJSON() throws JSONException {
        final JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("cropPages", cropPages);
        obj.put("rotation", rotation != null ? rotation.name() : RotationType.UNSPECIFIED.name());
        obj.put("viewMode", viewMode != null ? viewMode.name() : DocumentViewMode.VERTICALL_SCROLL.name());
        obj.put("pageAlign", pageAlign != null ? pageAlign.name() : PageAlign.AUTO.name());
        obj.put("animationType", animationType != null ? animationType.name() : PageAnimationType.NONE.name());
        obj.put("splitPages", splitPages);
        obj.put("splitRTL", splitRTL);
        obj.put("nightMode", nightMode);
        obj.put("positiveImagesInNightMode", positiveImagesInNightMode);
        obj.put("tint", tint);
        obj.put("tintColor", tintColor);
        obj.put("contrast", contrast);
        obj.put("gamma", gamma);
        obj.put("exposure", exposure);
        obj.put("autoLevels", autoLevels);
        obj.put("rtl", rtl);
        return obj;
    }

    public void applyTo(final BookSettings bs) {
        bs.cropPages = this.cropPages;
        bs.rotation = this.rotation;
        bs.viewMode = this.viewMode;
        bs.pageAlign = this.pageAlign;
        bs.animationType = this.animationType;
        bs.splitPages = this.splitPages;
        bs.splitRTL = this.splitRTL;
        bs.nightMode = this.nightMode;
        bs.positiveImagesInNightMode = this.positiveImagesInNightMode;
        bs.tint = this.tint;
        bs.tintColor = this.tintColor;
        bs.contrast = this.contrast;
        bs.gamma = this.gamma;
        bs.exposure = this.exposure;
        bs.autoLevels = this.autoLevels;
        bs.rtl = this.rtl;
        bs.lastChanged = System.currentTimeMillis();
    }
}
