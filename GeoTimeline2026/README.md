# GeoTimeline 2026 — Android (projeto independente)

Aplicativo **de uso pessoal, consentido e visível** para registrar localizações novas, importar exportações oficiais da Linha do Tempo do Google Maps e salvar cópias de 2026 em uma pasta escolhida pelo usuário no seletor de documentos do Android.

**Este projeto não contém keylogger, leitura de digitação de outros apps, acesso secreto ao GPS, extração clandestina de dados do Maps, nem envio automático para e-mails de terceiros.** O campo **Nota voluntária** registra apenas o que for digitado naquele campo, depois de pressionar Salvar.

## Funcionalidades

- Tela inicial: `https://35.247.217.66.nip.io/advogada-find/` num WebView isolado, sem interface JavaScript nativa e sem captura de formulários.
- **Iniciar GPS** pede autorização, inicia um serviço *foreground* com notificação e ação **PARAR GPS**; **Parar GPS** também fica na tela. Não inicia em boot ou automaticamente.
- Captura ponto de localização com latitude, longitude, horário, precisão e provedor, armazenado somente no espaço privado do app. Nomes de lugares só vêm da exportação do Maps, não são inferidos a partir de GPS.
- **Exportar no Google** orienta o usuário a usar Configurações > Localização > Serviços de localização > Linha do Tempo > Exportar dados da Linha do Tempo.
- **Importar Maps** abre o seletor de arquivos e filtra registros do ano 2026 em JSON da Linha do Tempo (inclusive ZIP contendo JSON compatível).
- **Exportar JSON** salva manualmente um arquivo contendo dados importados, amostras de GPS do próprio app e notas voluntárias de 2026.
- **Pasta no Drive** abre o seletor de pastas do Android (SAF). O usuário deve selecionar a pasta da sua conta Google Drive, caso o provedor disponibilize seleção/escrita de pastas. O destino configurado na conversa foi a pasta em `https://drive.google.com/drive/folders/1-EywkQTBUcI8QvisuHz845U42WNa-xFm`; o ID da pasta **não é uma credencial** e **não permite acesso automático**. O app não consegue selecionar uma pasta do Drive simplesmente a partir do URL.
- **Backup agora** requer confirmação e grava um arquivo JSON na pasta selecionada. **Backup diário** só é habilitado após segunda confirmação do usuário. O Android decide a hora de execução. Se o Google Drive não oferecer uma pasta gravável por SAF, use **Exportar JSON** e salve manualmente no Drive ou integre OAuth/Drive API com seu projeto Google Cloud.

## Arquitetura e fontes

```
app/src/main/java/br/com/joserfjunior/timelinebackup/
  MainActivity.java            # interface, WebView e autorizações explícitas
  LocationCaptureService.java  # serviço foreground com notificação e botão Parar
  TimelineStore.java           # importação/filtragem 2026 e armazenamento local
  DriveFolderBackup.java      # cópia para SAF DocumentProvider autorizada
  DailyBackupJobService.java  # trabalho opcional via JobScheduler
```

O Android limita início de serviços de localização fora de uma atividade visível. O serviço **deve ser iniciado pelo usuário enquanto o app está na tela**. O serviço pode continuar em segundo plano com notificação depois disso, sujeito às permissões, bateria e regras do dispositivo.

## Compilar

1. Abra este diretório como projeto no Android Studio (JDK 17).
2. Configure o Android SDK 35; Gradle plugin 8.7.3 / Gradle 8.9.
3. Compile `:app:assembleDebug` ou aguarde o workflow de GitHub Actions.
4. Instale o `app-debug.apk` no seu próprio Android (API 26+).
5. Inicie manualmente o GPS no próprio app. Para exportação histórica, faça a exportação oficial do Maps e escolha o arquivo em **Importar Maps**.

O workflow publica o APK de teste como artefato da execução; não existe integração de e-mail implícita. **Não carregue arquivos históricos contendo localização para o GitHub**, especialmente se o repositório for público.

## Privacidade

Dados de localização podem revelar moradia, saúde, hábitos e outras informações pessoais. Antes de escolher a pasta de destino, confira se ela é privada e está em sua conta. A permissão de pasta é persistida apenas para permitir backups após autorização. Para desativar, use **Parar backup diário**. Use **Apagar dados locais** para excluir GPS, importações e notas guardados pelo aplicativo; arquivos já enviados ao destino selecionado permanecem lá até o usuário excluí-los. Ações automáticas são informadas em diálogo na ativação.

### Limitações

- Apps Android de terceiros não têm API pública para extrair o armazenamento privado da Linha do Tempo do Maps sem exportação pelo próprio usuário.
- Não é garantido que o provedor Google Drive instalado ofereça `ACTION_OPEN_DOCUMENT_TREE` gravável; seleção e capacidade dependem da versão do Android e do provedor.
- Um ponto GPS não comprova entrada ou permanência em um estabelecimento. Registros estimados podem ter erros.
- A importação filtra dados estruturados reconhecidos (`semanticSegments`, `timelineObjects`, `locations`, `records`). Outros formatos e JSONs grandes exigirão um parser de streaming e testes adicionais.

## Referências

- Android: serviços foreground de localização — https://developer.android.com/about/versions/14/changes/fgs-types-required
- Android: seletor de arquivos e pastas (SAF) — https://developer.android.com/guide/topics/providers/document-provider
- Google: exportação da Linha do Tempo — https://support.google.com/maps/answer/14169818