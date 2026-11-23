package br.com.projeto.processadorvideos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VideoService {

    private static final String PASTA_ORIGINAIS = "videos-originais/";
    private static final String PASTA_COMPRIMIDOS = "videos-comprimidos/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String nomeBucket;

    public VideoService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public String enviarVideoOriginal(MultipartFile arquivo) throws IOException {
        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo de vídeo está vazio.");
        }

        String nomeOriginal = arquivo.getOriginalFilename() != null
                ? arquivo.getOriginalFilename().replaceAll("\\s+", "_")
                : "video.mp4";

        String chaveS3 = PASTA_ORIGINAIS + UUID.randomUUID() + "-" + nomeOriginal;

        PutObjectRequest requisicao = PutObjectRequest.builder()
                .bucket(nomeBucket)
                .key(chaveS3)
                .contentType(arquivo.getContentType())
                .build();

        s3Client.putObject(
                requisicao,
                RequestBody.fromInputStream(arquivo.getInputStream(), arquivo.getSize())
        );

        return chaveS3;
    }

    public List<?> listarObjetosDetalhados(String pasta) {
        String prefixo = (pasta != null && !pasta.isBlank()) ? pasta : "";

        ListObjectsV2Request requisicao = ListObjectsV2Request.builder()
                .bucket(nomeBucket)
                .prefix(prefixo)
                .build();

        return s3Client.listObjectsV2(requisicao)
                .contents()
                .stream()
                .filter(obj -> !obj.key().endsWith("/"))
                .map(this::mapearObjeto)
                .toList();
    }

    public List<?> listarOriginais() {
        return listarObjetosDetalhados(PASTA_ORIGINAIS);
    }

    public List<?> listarComprimidos() {
        return listarObjetosDetalhados(PASTA_COMPRIMIDOS);
    }

    public Map<String, Object> verificarStatusProcessamento(String chaveOriginal) {
        if (chaveOriginal == null || chaveOriginal.isBlank()) {
            throw new IllegalArgumentException("Informe a chaveS3 do vídeo original.");
        }

        String chaveLimpa = chaveOriginal.startsWith("/") ? chaveOriginal.substring(1) : chaveOriginal;

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(nomeBucket)
                    .key(chaveLimpa)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new IllegalArgumentException("Vídeo original não encontrado no S3: " + chaveLimpa);
        }

        String nomeArquivo = chaveLimpa.contains("/")
                ? chaveLimpa.substring(chaveLimpa.lastIndexOf("/") + 1)
                : chaveLimpa;

        List<S3Object> comprimidosEncontrados = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(nomeBucket)
                        .prefix(PASTA_COMPRIMIDOS)
                        .build())
                .contents()
                .stream()
                .filter(obj -> !obj.key().endsWith("/") && obj.key().contains(nomeArquivo))
                .toList();

        boolean comprimido = !comprimidosEncontrados.isEmpty();

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("chaveOriginal", chaveLimpa);
        resposta.put("processado", comprimido);
        resposta.put("status", comprimido ? "COMPRIMIDO" : "AGUARDANDO_COMPRESSAO");
        resposta.put("chavesComprimidas", comprimidosEncontrados.stream().map(S3Object::key).toList());
        return resposta;
    }

    public String gerarUrlDownload(String chaveS3, int minutosValidade) {
        if (chaveS3 == null || chaveS3.isBlank()) {
            throw new IllegalArgumentException("A chaveS3 é obrigatória para gerar a URL.");
        }

        String chaveLimpa = chaveS3.startsWith("/") ? chaveS3.substring(1) : chaveS3;

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(nomeBucket)
                .key(chaveLimpa)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(Math.max(1, minutosValidade)))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public void excluirObjeto(String chaveS3) {
        if (chaveS3 == null || chaveS3.isBlank()) {
            throw new IllegalArgumentException("A chaveS3 é obrigatória para exclusão.");
        }

        String chaveLimpa = chaveS3.startsWith("/") ? chaveS3.substring(1) : chaveS3;

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(nomeBucket)
                .key(chaveLimpa)
                .build());
    }

    private Map<String, Object> mapearObjeto(S3Object obj) {
        long bytes = obj.size() != null ? obj.size() : 0L;
        double tamanhoMb = Math.round((bytes / (1024.0 * 1024.0)) * 100.0) / 100.0;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("chaveS3", obj.key());
        item.put("tamanhoBytes", bytes);
        item.put("tamanhoMb", tamanhoMb);
        item.put("ultimaModificacao", obj.lastModified() != null ? obj.lastModified().toString() : null);
        return item;
    }
}