package br.com.joserfjunior.timelinebackup;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** SAF DocumentProvider write: user must explicitly choose a writable folder, including Drive when offered. */
final class DriveFolderBackup {
    static final String PREFS = "timeline2026_settings";
    static final String TREE_KEY = "destination_tree_uri";
    static final String DAILY_KEY = "daily_backup_enabled";
    private DriveFolderBackup() {}

    static String upload(Context context) throws Exception {
        String raw = context.getSharedPreferences(PREFS, 0).getString(TREE_KEY, null);
        if (raw == null) throw new IOException("Selecione uma pasta do Google Drive ou outro provedor primeiro.");
        Uri uri = Uri.parse(raw);
        boolean granted = false;
        for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(uri) && permission.isWritePermission()) { granted = true; break; }
        }
        if (!granted) throw new SecurityException("A autorização da pasta expirou. Selecione-a novamente.");
        DocumentFile folder = DocumentFile.fromTreeUri(context, uri);
        if (folder == null || !folder.isDirectory() || !folder.canWrite())
            throw new IOException("O provedor não permite gravar nesta pasta. Selecione outra pasta.");
        JSONObject payload = TimelineStore.buildExport(context);
        String suffix = Instant.now().toString().replace(":", "-").replace(".", "-");
        String filename = "Timeline2026_" + suffix + ".json";
        DocumentFile target = folder.createFile("application/json", filename);
        if (target == null) throw new IOException("Não foi possível criar o arquivo na pasta selecionada.");
        try (OutputStream out = context.getContentResolver().openOutputStream(target.getUri(), "w")) {
            if (out == null) throw new IOException("O provedor recusou a gravação.");
            out.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            target.delete();
            throw e;
        }
        return target.getName();
    }
}