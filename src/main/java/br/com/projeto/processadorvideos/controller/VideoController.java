package br.com.projeto.processadorvideos.controller;

import br.com.projeto.processadorvideos.service.VideoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin(origins = "*")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file) {
        try {
            String chaveSalva = videoService.enviarVideoOriginal(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "mensagem", "Vídeo enviado com sucesso para o S3!",
                    "chaveS3", chaveSalva
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Falha ao processar o arquivo: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<?>> listarVideos(
            @RequestParam(required = false, defaultValue = "") String pasta) {
        return ResponseEntity.ok(videoService.listarObjetosDetalhados(pasta));
    }

    @GetMapping("/originais")
    public ResponseEntity<List<?>> listarOriginais() {
        return ResponseEntity.ok(videoService.listarOriginais());
    }

    @GetMapping("/comprimidos")
    public ResponseEntity<List<?>> listarComprimidos() {
        return ResponseEntity.ok(videoService.listarComprimidos());
    }

    @GetMapping("/status")
    public ResponseEntity<?> consultarStatus(@RequestParam("chave") String chave) {
        try {
            return ResponseEntity.ok(videoService.verificarStatusProcessamento(chave));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/download-url")
    public ResponseEntity<?> obterUrlDownload(
            @RequestParam("chave") String chave,
            @RequestParam(defaultValue = "15") int minutos) {
        try {
            String url = videoService.gerarUrlDownload(chave, minutos);
            return ResponseEntity.ok(Map.of(
                    "chaveS3", chave,
                    "urlDownload", url,
                    "expiraEmMinutos", Math.max(1, minutos)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> excluirVideo(@RequestParam("chave") String chave) {
        try {
            videoService.excluirObjeto(chave);
            return ResponseEntity.ok(Map.of(
                    "mensagem", "Objeto removido do S3 com sucesso.",
                    "chaveS3", chave
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}