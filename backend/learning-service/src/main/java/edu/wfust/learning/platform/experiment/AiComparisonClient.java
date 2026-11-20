package edu.wfust.learning.platform.experiment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
@Component
public class AiComparisonClient {
    private final RestClient client;
    public AiComparisonClient(@Value("${ai.service.base-url:http://127.0.0.1:8082}")String url){var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Duration.ofSeconds(3));factory.setReadTimeout(Duration.ofSeconds(180));client=RestClient.builder().baseUrl(url).requestFactory(factory).build();}
    public List<Map<String,Object>> compare(String question,boolean includeAblations,String authorization){
        try{
            var result=client.post().uri("/api/ai/compare").header("Authorization",authorization).body(Map.of("question",question,"includeAblations",includeAblations)).retrieve().body(new ParameterizedTypeReference<List<Map<String,Object>>>(){});
            if(result==null||result.isEmpty()||result.size()>8)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI 服务返回的实验结果无效");
            return result;
        }catch(RestClientException exception){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI 服务暂不可用，请检查服务启动状态和模型配置");}
    }

    public List<Map<String,Object>> compareQwenMatrix(String question,String authorization){
        try{
            var result=client.post().uri("/api/ai/compare/qwen-matrix").header("Authorization",authorization)
                    .body(Map.of("question",question)).retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String,Object>>>(){});
            if(result==null||result.size()!=6)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Qwen 矩阵实验结果无效");
            return result;
        }catch(RestClientException exception){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Qwen 矩阵实验服务暂不可用，请检查本地推理配置");}
    }
}
