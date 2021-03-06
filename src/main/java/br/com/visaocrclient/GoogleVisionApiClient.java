package br.com.visaocrclient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionScopes;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.common.collect.ImmutableList;

import br.com.visaocr.domain.ResultadoVisionAPI;

public class GoogleVisionApiClient {
	
	
	private GoogleCredential credential;
	private static Logger LOGGER = Logger.getLogger(GoogleVisionApiClient.class);
	private Vision vision;
	
	/**
	 * Construtor para ser utilizado com as credenciais configuradas 
	 * na variavel de ambiente
	 */
	public GoogleVisionApiClient() {
		try {
			credential = GoogleCredential.getApplicationDefault().createScoped(VisionScopes.all());
		} catch (IOException e) {
			LOGGER.error("Problemas ao recuperar as credenciais da API do google, verificar configuracao de variavel de ambiente", e);
		} 
	}
	
	/**
	 * 
	 * Sobrecarga utilizada para uma requisicao onde se envia a credencial
	 * 
	 * @param credential {@link GoogleCredential} desejada
	 */
	public GoogleVisionApiClient(GoogleCredential credential) {
		this.credential = credential;
	}
	
	/**
	 * Realiza a inicializacao da API
	 */
	private void inicializaVision() {
		try {
			vision = new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), 
					JacksonFactory.getDefaultInstance(),
					setHttpTimeout(credential)).setApplicationName("Google-VisionLabelSample/1.0").build();
		} catch (GeneralSecurityException e) {
			LOGGER.error("Problemas de seguranca ao criar a api Vision do Google", e);
		} catch (IOException e) {
			LOGGER.error("ERRO ao criar a api Vision do Google", e);
		}
	}
	
	public List<ResultadoVisionAPI> enviarImagensParaEfetuarOCR(List<String> caminhosImagens) {

		/*
		 * Inicializa a API e cria uma instancia para efetuar a requisicao
		 */
		inicializaVision();
		
		
		List<ResultadoVisionAPI> resultados = new ArrayList<ResultadoVisionAPI>();
		List<AnnotateImageRequest> requisicoes = new ArrayList<AnnotateImageRequest>();
		 
		for (String caminho : caminhosImagens) {
			Image conteudoImagemBase64 = realizaLeituraDaImagem(caminho);
			
			AnnotateImageRequest request =
					new AnnotateImageRequest()
					.setImage(conteudoImagemBase64)
					.setFeatures(ImmutableList.of(
							new Feature()
							.setType("TEXT_DETECTION")
							.setMaxResults(Integer.valueOf(1))));
			
			requisicoes.add(request);
			
			LOGGER.debug("Criando imagem para envio: "+caminho);
		}
		
		
		try {
			Vision.Images.Annotate annotate =
					vision.images()
					.annotate(new BatchAnnotateImagesRequest().setRequests(requisicoes));
			annotate.setDisableGZipContent(true);
			
			LOGGER.info("Enviando consulta TEXT_DETECTION");
			BatchAnnotateImagesResponse batchResponse = annotate.execute();
			LOGGER.info("Recuperando resposta TEXT_DETECTION");
			
			List<AnnotateImageResponse> responses = batchResponse.getResponses();

			for (AnnotateImageResponse response : responses) {
				if (response.getTextAnnotations() == null) {
					throw new IOException(
							response.getError() != null
							? response.getError().getMessage()
									: "Unknown error getting image annotations");
				}
				
				ResultadoVisionAPI resultado = new ResultadoVisionAPI();
				
				/*
				 * Recupera apenas o primeiro resultado pois nele esta todo o texto
				 */
				resultado.setTextoNota(response.getTextAnnotations().get(0).getDescription());
				
				/*
				 * Extrai todo o resultado em json da API
				 */
				for (EntityAnnotation entity : response.getTextAnnotations()) {
					
					resultado.appendJsonResultado(entity.toPrettyString());
					
				}
				resultados.add(resultado);
				
			}
		} catch (IOException e) {
			LOGGER.error("ERRO ao criar a resposta da API do google", e);
		}
		return resultados;
	}
	 
	public ResultadoVisionAPI enviarImagemParaVisionAPI(String caminhoImagem) {
		List<String> caminhosImagem = Arrays.asList(caminhoImagem);
		List<ResultadoVisionAPI> resultados = enviarImagensParaEfetuarOCR(caminhosImagem);
		ResultadoVisionAPI resultadoFinal = new ResultadoVisionAPI();
		for (ResultadoVisionAPI resultado : resultados) {
			resultadoFinal = resultado;
			break;
		}
		return resultadoFinal;
	}
	
	
	private Image realizaLeituraDaImagem(String caminhoImagem) {
		byte[] data = null;
		try {
			data = Files.readAllBytes(new File(caminhoImagem).toPath());
		} catch (IOException e) {
			LOGGER.error("ERRO na leitura da imagem", e);
		}
		Image conteudoImagemBase64 = new Image().encodeContent(data);
		return conteudoImagemBase64;
	}
	
	
	private HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
		  return new HttpRequestInitializer() {
		    @Override
		    public void initialize(HttpRequest httpRequest) throws IOException {
		      requestInitializer.initialize(httpRequest);
		      httpRequest.setConnectTimeout(3 * 60000);  // 3 minutes connect timeout
		      httpRequest.setReadTimeout(3 * 60000);  // 3 minutes read timeout
		    }
		  };
	}
}
