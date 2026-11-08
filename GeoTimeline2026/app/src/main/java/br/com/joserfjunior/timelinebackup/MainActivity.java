package br.com.joserfjunior.timelinebackup;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** No covert tracking, no webview key interception, no access to other app inputs. */
public final class MainActivity extends Activity {
    private static final int REQUEST_GPS = 12, REQUEST_IMPORT = 13, REQUEST_TREE = 14, REQUEST_EXPORT = 15;
    private static final int JOB_ID = 2026001;
    private static final String HOME = "https://35.247.217.66.nip.io/advogada-find/";
    private TextView status;
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        status = new TextView(this);
        status.setText("GeoTimeline 2026 • GPS desligado até você ativar • Backup só com autorização");
        status.setTextColor(Color.BLACK);
        status.setBackgroundColor(0xffe8f1ff);
        status.setPadding(dp(10), dp(12), dp(10), dp(12));
        status.setTextSize(13);
        root.addView(status);

        HorizontalScrollView bar = new HorizontalScrollView(this);
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(4), dp(2), dp(4), dp(2));
        bar.addView(actions);
        root.addView(bar);
        button(actions, "Iniciar GPS", v -> consentGps());
        button(actions, "Parar GPS", v -> {
            stopService(new Intent(this, LocationCaptureService.class));
            status.setText("GeoTimeline 2026 • GPS parado");
        });
        button(actions, "Importar Maps", v -> pickImport());
        button(actions, "Exportar JSON", v -> pickSave());
        button(actions, "Pasta no Drive", v -> pickFolder());
        button(actions, "Backup agora", v -> backupNow());
        button(actions, "Parar backup diário", v -> { setDaily(false); message("Backup diário desativado."); });
        button(actions, "Nota voluntária", v -> editNote());
        button(actions, "Apagar dados locais", v -> clearData());
        button(actions, "Exportar no Google", v -> explainMapsExport());

        TextView notice = new TextView(this);
        notice.setText("O site abaixo é exibido separadamente. Ele não recebe os dados de GPS deste app e a digitação nele não é capturada.");
        notice.setTextSize(11);
        notice.setTextColor(0xff424242);
        notice.setPadding(dp(10), dp(5), dp(10), dp(5));
        root.addView(notice);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("https".equals(url.getScheme()) && "35.247.217.66.nip.io".equals(url.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, url)); } catch (Exception ignored) { }
                return true;
            }
        });
        webView.loadUrl(HOME);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void button(LinearLayout parent, String label, View.OnClickListener callback) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        parent.addView(b);
        b.setOnClickListener(callback);
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    private void consentGps() {
        new AlertDialog.Builder(this).setTitle("Ativar registro de GPS?")
            .setMessage("O aplicativo registrará coordenadas, horários e precisão enquanto o serviço estiver ligado, mesmo se a tela estiver apagada. Uma notificação visível permitirá parar a qualquer momento. Nenhum registro será enviado sem escolher uma pasta e autorizar o backup.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Ativar", (d, which) -> requestGps()).show();
    }
    private void requestGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startGps();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_GPS);
        }
    }
    private void startGps() {
        try {
            startForegroundService(new Intent(this, LocationCaptureService.class));
            status.setText("GeoTimeline 2026 • GPS solicitado • confira a notificação de gravação");
        } catch (Exception e) { message("Não foi possível iniciar GPS: " + e.getMessage()); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grant) {
        super.onRequestPermissionsResult(request, permissions, grant);
        if (request == REQUEST_GPS) {
            boolean authorized = false;
            for (int response : grant) if (response == PackageManager.PERMISSION_GRANTED) authorized = true;
            if (authorized) startGps(); else message("GPS não iniciado: falta autorização.");
        }
    }
    private void pickImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "application/zip", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_IMPORT);
    }
    private void pickFolder() {
        new AlertDialog.Builder(this).setTitle("Escolher a pasta no Google Drive")
            .setMessage("Escolha manualmente a pasta que você compartilhou no Google Drive. O seletor do Android deve oferecer o provedor Google Drive; se ele não aparecer ou não aceitar pastas, o backup automático pelo seletor não estará disponível neste aparelho. A escolha dá acesso somente à pasta selecionada.")
            .setPositiveButton("Selecionar", (d, w) -> startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQUEST_TREE))
            .setNegativeButton("Cancelar", null).show();
    }
    private void pickSave() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "Timeline2026_export.json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }
    private void backupNow() {
        new AlertDialog.Builder(this).setTitle("Enviar backup autorizado?")
            .setMessage("Um arquivo JSON de 2026 será salvo na pasta selecionada. Confira se o destino é o seu Google Drive.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar", (d,w) -> background(() -> DriveFolderBackup.upload(this),
                    "Backup criado: ")).show();
    }
    private void explainMapsExport() {
        new AlertDialog.Builder(this).setTitle("Exportação oficial do Google Maps")
            .setMessage("A Linha do Tempo antiga não pode ser lida diretamente por outro APK. No Android, abra Configurações > Localização > Serviços de localização > Linha do Tempo > Exportar dados da Linha do Tempo. Depois use 'Importar Maps' aqui e selecione o JSON exportado.")
            .setPositiveButton("Abrir Localização", (d,w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
            .setNegativeButton("Fechar", null).show();
    }
    private void clearData() {
        new AlertDialog.Builder(this).setTitle("Apagar os dados locais?")
            .setMessage("Isso apaga permanentemente os registros deste aplicativo neste aparelho (GPS, importações do Maps e notas). Os backups que já foram enviados à pasta selecionada não serão apagados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar", (d,w) -> {
                stopService(new Intent(this, LocationCaptureService.class));
                setDaily(false);
                for (String name : new String[]{"google_import_2026.json", "own_location.jsonl", "explicit_notes.jsonl", "export_2026.json"}) {
                    deleteFile(name);
                }
                status.setText("GeoTimeline 2026 • GPS parado • dados locais apagados");
                message("Dados locais apagados.");
            }).show();
    }
    private void editNote() {
        EditText input = new EditText(this);
        input.setHint("Digite uma nota sua (sem senhas)");
        input.setSingleLine(false);
        new AlertDialog.Builder(this).setTitle("Salvar texto inserido voluntariamente")
            .setMessage("Apenas o texto digitado neste campo será salvo no arquivo de exportação. O aplicativo não lê mensagens ou teclas de outros aplicativos ou do site.")
            .setView(input).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar nota", (d,w) -> {
                try { TimelineStore.addNote(this, input.getText().toString()); message("Nota salva localmente."); }
                catch (Exception e) { message("Erro: " + e.getMessage()); }
            }).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == REQUEST_IMPORT) {
            background(() -> "Registros 2026: " + TimelineStore.importUserSelected(this, uri).optInt("recordCount"), "Importação concluída. ");
        } else if (request == REQUEST_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getSharedPreferences(DriveFolderBackup.PREFS, 0).edit()
                        .putString(DriveFolderBackup.TREE_KEY, uri.toString()).apply();
                new AlertDialog.Builder(this).setTitle("Ativar backups diários?")
                    .setMessage("A cada dia, com horário definido pelo Android, o aplicativo gravará um backup na pasta escolhida. Os registros são sensíveis e não devem ser armazenados numa pasta pública. Você pode desativar esta opção depois nas configurações do Android.")
                    .setPositiveButton("Ativar diário", (d,w) -> { setDaily(true); message("Backup diário habilitado."); })
                    .setNegativeButton("Somente manual", (d,w) -> { setDaily(false); message("Pasta selecionada. Backup somente manual."); })
                    .setCancelable(false).show();
            } catch (SecurityException e) { message("Pasta sem permissão persistente: " + e.getMessage()); }
        } else if (request == REQUEST_EXPORT) {
            background(() -> {
                String text = TimelineStore.buildExport(this).toString(2);
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new java.io.IOException("Arquivo indisponível");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                return "JSON salvo";
            }, "Exportação concluída: ");
        }
    }
    private void setDaily(boolean yes) {
        getSharedPreferences(DriveFolderBackup.PREFS, 0).edit()
                .putBoolean(DriveFolderBackup.DAILY_KEY, yes).apply();
        JobScheduler scheduler = getSystemService(JobScheduler.class);
        if (!yes) { scheduler.cancel(JOB_ID); return; }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(this, DailyBackupJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24L * 60 * 60 * 1000)
                .build();
        scheduler.schedule(job);
    }
    private interface Task { String perform() throws Exception; }
    private void background(Task work, String prefix) {
        new Thread(() -> {
            try { String result = work.perform(); runOnUiThread(() -> message(prefix + result)); }
            catch (Exception e) { runOnUiThread(() -> message("Erro: " + e.getMessage())); }
        }, "timeline-work").start();
    }
    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}