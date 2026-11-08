package br.com.joserfjunior.timelinebackup;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads ONLY user-selected exports and location points collected by this app. */
final class TimelineStore {
    private static final int MAX_JSON_BYTES = 64 * 1024 * 1024;
    private static final String IMPORT = "google_import_2026.json";
    private static final String OWN = "own_location.jsonl";
    private static final String NOTES = "explicit_notes.jsonl";
    static final String OUTPUT = "export_2026.json";
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Object LOCK = new Object();
    private TimelineStore() {}

    static void addLocation(Context context, android.location.Location location) throws IOException, JSONException {
        JSONObject object = new JSONObject();
        object.put("timestamp", Instant.ofEpochMilli(location.getTime()).toString());
        object.put("latitude", location.getLatitude());
        object.put("longitude", location.getLongitude());
        object.put("accuracyMeters", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL);
        object.put("provider", location.getProvider());
        object.put("source", "app_location_with_explicit_consent");
        synchronized (LOCK) {
            try (FileOutputStream stream = context.openFileOutput(OWN, Context.MODE_APPEND)) {
                stream.write((object.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
        }
    }

    /** Only explicitly entered notes in this app; never a keyboard logger. */
    static void addNote(Context context, String note) throws IOException, JSONException {
        if (note == null || note.isBlank()) return;
        JSONObject object = new JSONObject();
        object.put("timestamp", Instant.now().toString());
        object.put("text", note.substring(0, Math.min(note.length(), 10000)));
        object.put("source", "voluntary_note_inside_app");
        synchronized (LOCK) {
            try (FileOutputStream stream = context.openFileOutput(NOTES, Context.MODE_APPEND)) {
                stream.write((object.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static JSONObject importUserSelected(Context context, Uri uri) throws Exception {
        byte[] bytes;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Não foi possível abrir o arquivo.");
            bytes = readLimited(in, MAX_JSON_BYTES);
        }
        String document = new String(bytes, StandardCharsets.UTF_8);
        // Some exports are ZIP archives with JSON inside.
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0x50 && (bytes[1] & 0xff) == 0x4b) {
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry; String json = null;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                        try {
                            String candidate = new String(readLimited(zis, MAX_JSON_BYTES), StandardCharsets.UTF_8);
                            if (candidate.trim().startsWith("{") || candidate.trim().startsWith("[")) {
                                JSONObject f = filter(candidate);
                                if (f.optInt("recordCount") > 0) { json = candidate; break; }
                            }
                        } catch (JSONException ignored) { /* next file */ }
                    }
                    zis.closeEntry();
                }
                if (json == null) throw new IOException("Nenhum JSON com registros de 2026 foi encontrado no ZIP.");
                document = json;
            }
        }
        JSONObject selected = filter(document);
        if (selected.optInt("recordCount") == 0) {
            throw new IOException("Não foram encontrados registros de 2026 neste arquivo.");
        }
        atomicWrite(new File(context.getFilesDir(), IMPORT), selected.toString());
        buildExport(context);
        return selected;
    }

    static JSONObject filter(String text) throws JSONException {
        String content = text.strip();
        JSONObject output = new JSONObject();
        output.put("source", "selected_google_timeline_export");
        output.put("year", 2026);
        JSONObject grouped = new JSONObject();
        int count = 0;
        if (content.startsWith("[")) {
            JSONArray items = new JSONArray(content);
            JSONArray selected = select(items);
            grouped.put("records", selected);
            count = selected.length();
        } else {
            JSONObject doc = new JSONObject(content);
            for (String key : new String[]{"semanticSegments", "timelineObjects", "locations", "records"}) {
                JSONArray array = doc.optJSONArray(key);
                if (array != null) {
                    JSONArray selected = select(array);
                    grouped.put(key, selected);
                    count += selected.length();
                }
            }
            // If the file was already filtered by this app, preserve its grouped data.
            if (count == 0 && doc.has("data") && doc.optJSONObject("data") != null) {
                JSONObject inner = doc.optJSONObject("data");
                for (String key : new String[]{"semanticSegments", "timelineObjects", "locations", "records"}) {
                    JSONArray selected = select(inner.optJSONArray(key));
                    if (selected.length() > 0) { grouped.put(key, selected); count += selected.length(); }
                }
            }
        }
        output.put("recordCount", count);
        output.put("data", grouped);
        return output;
    }

    private static JSONArray select(JSONArray entries) throws JSONException {
        JSONArray selected = new JSONArray();
        if (entries == null) return selected;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item != null && yearOf(item) == 2026) selected.put(item);
        }
        return selected;
    }

    private static int yearOf(JSONObject row) {
        String[] keys = {"startTime", "startTimestamp", "timestamp", "timestampMs", "time", "endTime", "startTimestampMs"};
        for (String key : keys) {
            Object val = row.opt(key);
            int y = parseYear(val);
            if (y != -1) return y;
        }
        for (String nested : new String[]{"duration", "placeVisit", "activitySegment"}) {
            JSONObject node = row.optJSONObject(nested);
            if (node != null) {
                int year = yearOf(node);
                if (year != -1) return year;
            }
        }
        return -1;
    }

    private static int parseYear(Object value) {
        if (value == null || value == JSONObject.NULL) return -1;
        try {
            String raw = String.valueOf(value);
            if (raw.matches("\\d{13}")) return Instant.ofEpochMilli(Long.parseLong(raw)).atZone(ZONE).getYear();
            if (raw.matches("\\d{10}")) return Instant.ofEpochSecond(Long.parseLong(raw)).atZone(ZONE).getYear();
            if (raw.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                // UTC offsets handled when present; avoid inventing a timezone for date-only values.
                try { return Instant.parse(raw).atZone(ZONE).getYear(); }
                catch (Exception ignored) { return raw.startsWith("2026-") ? 2026 : Integer.parseInt(raw.substring(0, 4)); }
            }
        } catch (Exception ignored) { }
        return -1;
    }

    static JSONObject buildExport(Context context) throws IOException, JSONException {
        JSONObject export = new JSONObject();
        export.put("year", 2026);
        export.put("createdAt", Instant.now().toString());
        export.put("notice", "Imported Google records require a manual authorized export; app points are collected only while the visible recording service is enabled.");
        File imported = new File(context.getFilesDir(), IMPORT);
        if (imported.exists()) export.put("googleTimeline", new JSONObject(readFile(imported, MAX_JSON_BYTES)));
        JSONArray own = new JSONArray();
        File samples = new File(context.getFilesDir(), OWN);
        if (samples.exists()) {
            synchronized (LOCK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(samples), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            JSONObject point = new JSONObject(line);
                            if (yearOf(point) == 2026) own.put(point);
                        } catch (JSONException ignored) { /* incomplete sample */ }
                    }
                }
            }
        }
        export.put("appLocations", own);
        JSONArray notes = new JSONArray();
        File noteFile = new File(context.getFilesDir(), NOTES);
        if (noteFile.exists()) {
            synchronized (LOCK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(noteFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try { JSONObject note = new JSONObject(line); if (yearOf(note) == 2026) notes.put(note); }
                        catch (JSONException ignored) { }
                    }
                }
            }
        }
        export.put("voluntaryNotes", notes);
        atomicWrite(new File(context.getFilesDir(), OUTPUT), export.toString(2));
        return export;
    }

    static File outputFile(Context context) { return new File(context.getFilesDir(), OUTPUT); }

    private static String readFile(File f, int limit) throws IOException {
        try (InputStream in = new FileInputStream(f)) { return new String(readLimited(in, limit), StandardCharsets.UTF_8); }
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count, total = 0;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("Arquivo grande demais para esta versão do app (limite: 64 MB).");
            result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }

    private static void atomicWrite(File destination, String data) throws IOException {
        File tmp = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(tmp)) {
            output.write(data.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (!tmp.renameTo(destination)) {
            // Filesystems normally permit this on same path; fallback prevents losing data.
            try (InputStream source = new FileInputStream(tmp); FileOutputStream output = new FileOutputStream(destination)) {
                byte[] bytes = new byte[8192]; int n;
                while ((n = source.read(bytes)) > 0) output.write(bytes, 0, n);
            }
            if (!tmp.delete()) android.util.Log.w("Timeline2026", "Temporary file not removed");
        }
    }
}