# backAws — API de Upload e Compressão de Vídeos com AWS

API REST desenvolvida em **Spring Boot** para gerenciar o upload, listagem, consulta de status, geração de URL pré-assinada e exclusão de vídeos no **Amazon S3**, integrada a um pipeline assíncrono na **AWS** (`sa-east-1`) que comprime arquivos `.mp4` automaticamente via **Lambda**, **SSM** e **EC2 (`ffmpeg`)**.

## Arquitetura do Fluxo

1. **API Spring Boot (`backAws`)**: recebe o vídeo via `POST /api/videos/upload` e envia para o bucket S3 no prefixo `videos-originais/`.
2. **Notificação de Evento do S3**: a criação de um objeto `.mp4` em `videos-originais/` dispara a função Lambda `disparar-compressao-ec2`.
3. **AWS Lambda (`disparar-compressao-ec2`)**: lê o evento do S3 e envia o comando via **AWS Systems Manager (`ssm:SendCommand` / `AWS-RunShellScript`)** para a instância EC2 (`i-00d37b56fd321a46c`).
4. **Instância EC2 (`ec2-compressor-videos`)**: executa o script `/usr/local/bin/comprimir_video.sh`, baixa o vídeo original, comprime com `ffmpeg` (`libx264`, `-crf 28`, `-preset veryfast`, áudio `aac` `128k`), salva o resultado em `videos-comprimidos/` no mesmo bucket e limpa os arquivos temporários.
5. **Consulta e Download na API**: permite acompanhar o status do processamento, listar os vídeos e gerar URLs temporárias de download/reprodução.

## Recursos na AWS (`sa-east-1`)

* **Amazon S3**: bucket `repositorio-videos-netoad-2026` com os prefixos `videos-originais/` e `videos-comprimidos/`.
* **AWS Lambda**: função `disparar-compressao-ec2` (Python) com variáveis de ambiente `INSTANCE_ID` e `AWS_REGION_NAME`.
* **Amazon EC2**: instância `i-00d37b56fd321a46c` com `ffmpeg`, `awscli` e `/usr/local/bin/comprimir_video.sh`.
* **IAM Roles**:
  * `role-ec2-compressor-videos` (com `AmazonSSMManagedInstanceCore` e acesso ao S3).
  * `role-lambda-gatilho-ec2` (com `AWSLambdaBasicExecutionRole` e permissão `ssm:SendCommand`).

## Endpoints Principais

* `POST /api/videos/upload` — Envia um arquivo de vídeo (`multipart/form-data`, campo `file`) para `videos-originais/`.
* `GET /api/videos?pasta=` — Lista objetos detalhados do bucket (opcionalmente filtrando por `pasta`).
* `GET /api/videos/originais` — Lista os vídeos em `videos-originais/`.
* `GET /api/videos/comprimidos` — Lista os vídeos processados em `videos-comprimidos/`.
* `GET /api/videos/status?chave=videos-originais/<arquivo>` — Verifica se o vídeo já possui versão comprimida.
* `GET /api/videos/download-url?chave=videos-comprimidos/<arquivo>&minutos=15` — Gera URL pré-assinada temporária para download/reprodução.
* `DELETE /api/videos?chave=<caminho-no-s3>` — Remove um objeto do S3.

## Configuração e Execução Local

1. Configure as propriedades no `src/main/resources/application.properties` (ou via variáveis de ambiente):

```properties
spring.application.name=backAws

aws.region=sa-east-1
aws.s3.bucket=repositorio-videos-netoad-2026
aws.access-key=
aws.secret-key=

spring.servlet.multipart.max-file-size=500MB
spring.servlet.multipart.max-request-size=500MB