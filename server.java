import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.io.*;
import java.nio.file.*;

//contém os métodos relacionados ao servidor, como ligar, desligar, gerenciamento de portas 
class Server{
    int porta;
    ServerConnectionHandler serverConnectionHandler;
    public Server(int porta){
        this.porta = porta;
        this.serverConnectionHandler = new ServerConnectionHandler(porta);
    } 
    public void start(){ 
        serverConnectionHandler.servidor_ligado = true;
        serverConnectionHandler.start();
    }
    public void stop(){
        serverConnectionHandler.servidor_ligado = false;
    }
}
//recebe conexões
class ServerConnectionHandler{
    boolean servidor_ligado;
    HttpRequestHandler handler;
    HttpResponseHandler res_handler; 
    ServerSocket server;    

    
    public ServerConnectionHandler(int porta){
        try {
            this.server = new ServerSocket(porta);    
            this.handler = new HttpRequestHandler(); 
            this.res_handler = new HttpResponseHandler(); 
        } catch (Exception e) {
            e.printStackTrace();   
        } 
        
    }
    public void start(){  
        servidor_ligado = true; 
        System.out.println("Servidor rodando!" + "\n" + "http://localhost:12345");
        try {  
            while(servidor_ligado){   
                Socket conn = server.accept(); 
                
                Thread t = new Thread(new Runnable(){
                    @Override
                    public void run(){
                        try {
                            HttpRequest req = handler.processRequest(conn.getInputStream());    
                            HttpResponse res = res_handler.handleRequest(req);
                            OutputStream output = conn.getOutputStream();
                            res.escreverResposta(output);
                            output.flush();      
                            conn.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                    }  
                });
                t.start();     
            }        
        } catch (Exception e) {   
            e.printStackTrace();  
        }
    }

}
//recebe requisições HTTP
enum HttpMethods{
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS 
}
enum HttpVersions{
    HTTP1_0, HTTP1_1
}
class HttpRequest{
    private HttpMethods method;
    private String path;
    private String version; 
    private HashMap<String, String> headers;
    private Map<String, String> queryParams; 
    private byte[] body; 
    public HttpRequest(
        String method, String path, String version
    ) 
    {  
        this.method = HttpMethods.valueOf(method.trim().toUpperCase());
        this.path = path;
        this.version = version; 
        this.headers = new HashMap<String, String>();
        this.queryParams = new HashMap<String, String>();
        this.body = new byte[0]; 
        
    }
    public HttpMethods getMethod(){
        return this.method;
    }
    public String getPath(){
        return this.path;
    }
    public String getVersion(){
        return this.version;
    }
    public String getHeader(String chave){
        return this.headers.get(chave.toLowerCase());  
    }
    public String getQueryParam(String params){
        return this.queryParams.get(params.toLowerCase());
    }
    public byte[] getBody(){
        return this.body;
    } 
    public void addHeader(String key, String valor){
        headers.put(key, valor);
    }  
    public void addParam(String key, String valor){
        queryParams.put(key, valor);
    } 
    public void setBody(byte[] body){
        this.body = body;
    } 
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(method).append(" ")
        .append(path).append(" ")
        .append(version).append("\n");
        headers.forEach((k, v) ->
            b.append(k).append(":").append(" ").append(v).append("\n")

        );
        b.append("\n");
        b.append(new String(body, StandardCharsets.UTF_8));
        return b.toString();
    }
}  

class HttpValidation{ 
    public static boolean validarMétodo(String method){
        try {
            if(method ==  null){
                return false; 
            }
            String.valueOf(HttpMethods.valueOf(method)); 
            return true; 
            
        } catch (IllegalArgumentException e) {
            return false;
        }   
    }   
    public static boolean validarVersao(String versao){ 
        try {
            if(versao == null){
                return false;
            }
            versao = versao.replace(".", "_").replace("/", ""); 
            HttpVersions.valueOf(versao);
            return true; 
        
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
enum State{
    HEADER, BODY
}
class SocketReader{

    public InputStream readSocket(Socket req){
        try(
            InputStream is = req.getInputStream(); 
        ) {
            return is;
        } catch (Exception e) {
            return null; 
        }
    }
}
class HttpParser{
    //separa os bytes
    public HttpRequest parseRequest(InputStream req_bytes){
        ByteArrayOutputStream acumulador = new ByteArrayOutputStream();  
        byte[] buffer_header = new byte[64];
        byte[] headerEnd = {13, 10, 13, 10}; 
        byte[] buffer_body = new byte[64];
        int bytes_lidos; 
        State estado = State.HEADER;
        int seq = 0; 
        byte[] bytes_header;
        int posicao_inicio_body = 0;
        boolean header_encontrado = false;
        //valor atualizado a cada iteração completa do loop for
    
        try{
            //valor atualizado a cada iteração completa do loop for
            while((bytes_lidos = req_bytes.read(buffer_header)) != -1){
                //ESTADO: 1: headers, 2: body
                if(estado == State.HEADER){
                    // loop continua até "z" ser menor que a quantidade de bytes que foram lidos
                    for(int z = 0; z < bytes_lidos; z++){
                        //para cada byte lido:              
                        if(buffer_header[z] == headerEnd[seq]){
                            seq++; 
                        } else {
                            seq = 0;
                        }
                        if(estado == State.HEADER){
                            acumulador.write(buffer_header[z]);
                        } 
                        if(seq == 4){ 
                            header_encontrado = true;
                            estado = State.BODY;    
                            posicao_inicio_body = z + 1;
                            break;
                        }
                                
                    }
                }
                if(estado == State.BODY){
                    break;
                }
            }
            
            if(!header_encontrado){
                throw new IllegalArgumentException("Header não encontrado!");
            }
            bytes_header = acumulador.toByteArray(); 
            // essa variável representa o tanto de bytes do body que podem ter sido lidos no buffer que lia
            // os headers
            int bytes_body_recebidos = bytes_lidos - posicao_inicio_body;
            ByteArrayOutputStream acumulador_body = new ByteArrayOutputStream();
            //escreve no acumulador todos os bytes já recebidos do body no buffer
            acumulador_body.write(buffer_header, posicao_inicio_body, bytes_body_recebidos);
            String headers_text = new String(bytes_header, StandardCharsets.UTF_8); 
            //separa todas as linhas que foram recebidas na requisição, a primeira sendo a request line; 
            String[] linhas = headers_text.split("\r\n");
            //<---------------------- PARSING DA LINHA DE REQUISIÇÃO-----------------------> 
            String request_line = linhas[0];
            //essa variável contém os 3 elementos da request line
            
            String[] rl = request_line.split(" ");
            if(rl.length != 3){
                throw new IllegalArgumentException("Requisição inválida!");  
            }
            String method = rl[0];
            String path_and_possible_queries = rl[1];
            String version = rl[2];
            //<-----DIVIDINDO O PATH E (POSSÍVEIS) QUERIES-----> 
            String path = "";
            String query = null;
            //se não contém "?", é por que não tem parâmetros de query  
            int index = path_and_possible_queries.indexOf("?"); 
            if(index == -1){
                path = path_and_possible_queries;
                //query permanece null
            }
            else{  
                path = path_and_possible_queries.substring(0, index); 
                query = path_and_possible_queries.substring(index + 1);
            }  
            if(
                HttpValidation.validarMétodo(method) == false ||
                HttpValidation.validarVersao(version) == false
            ){ 
                throw new IllegalArgumentException("Requisição inválida!");  
            }
            HttpRequest req = new HttpRequest(method, path, version); 
            //<----- SEPARANDO QUERIES, SE HOUVEREM ----->
            String[] query_params; 
            if(query == null){
                query_params = null;
            } else {
                query_params = query.split("&");
            }
            
            
        
            for(int i = 0; query_params != null && i < query_params.length  ; i++){
                String chave;
                String valor;
                int indice = query_params[i].indexOf("="); 
                //se não houver valor:
                if(indice == -1){
                    //não há valor
                    valor = null;
                    chave = query_params[i];
                    req.addParam(chave, valor);  
                }
                //se tiver valor:
                else {
                    chave = query_params[i].substring(0, indice).trim();
                    valor = query_params[i].substring(indice + 1).trim(); 
                    if(valor.isBlank()){
                        valor = null;
                    }
                    req.addParam(chave, valor); 
                }
                
                
                  
                
                
            }
            //<----- SEPARANDO HEADERS ----->
            int content_length = 0;
            for(int i = 1; i < linhas.length; i++){
                String chave;
                String valor;
                if(linhas[i].isBlank()){
                    continue;
                }
                int indice_i = linhas[i].indexOf(":");

                if(indice_i == -1){
                    throw new IllegalArgumentException("Requisição inválida: " + linhas[i]); 
                }
                chave = linhas[i].substring(0, indice_i).trim();
                valor = linhas[i].substring(indice_i + 1).trim();  
                if(chave.equalsIgnoreCase("Content-Length")){
                    content_length = Integer.parseInt(valor); 
                }
                req.addHeader(chave, valor); 
            
            }
            int bytes_faltantes = content_length - bytes_body_recebidos;
            int bytes_l = 0;
            while(bytes_faltantes > 0){

                bytes_l = req_bytes.read(buffer_body);
                if(bytes_l == -1){
                    throw new IllegalArgumentException("Requisição inválida!");
                }
                int bytes_usados = Integer.min(bytes_l, bytes_faltantes);
                acumulador_body.write(buffer_body, 0, bytes_usados);           
                bytes_faltantes -= bytes_usados;  
                
            }
            byte[] bytes_body = acumulador_body.toByteArray();
            req.setBody(bytes_body);   
            return req;
            

            
            
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
        
         
    
    }
}
//refatorar essa classe
//separar as responsabilidades
class HttpRequestHandler{
    HttpParser parser;
    public HttpRequestHandler(){
        this.parser = new HttpParser(); 
    }
    public HttpRequest processRequest(InputStream is){   
        HttpRequest req = parser.parseRequest(is);
        System.out.println(req.toString()); 
        return req;
    }
} 
enum HttpStatusCodes{
    OK (200, "OK"),
    BAD_REQUEST(400, "Bad Request"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_SERVER_ERROR(500,"Internal Server Error");

    private final int code;
    private final String phrase;
    HttpStatusCodes(int code, String phrase){ 
        this.code = code;
        this.phrase = phrase;
    }
    public int code(){return code;}
    public String phrase(){return phrase;}
}

interface ResponseHandler{  
    HttpResponse handle(HttpRequest request); 
}
class GetHandler implements ResponseHandler{
    @Override
    public HttpResponse handle(HttpRequest req) {
        String rootDir = "dir";  
        String method = String.valueOf(req.getMethod());   
        String version = String.valueOf(req.getVersion());
        //caso o método seja get : 
        String alvo = req.getPath();  
        HttpResponse.ResponseBuilder builder = HttpResponse.builder();
        if(alvo == null || alvo.isBlank()){
            alvo = "index.html";
        }
        if(method.equalsIgnoreCase("GET")){
            //checa se o path existe(DIRETÓRIO DE BUSCA PADRÃO)     
            if(alvo.startsWith("/")){
                alvo = alvo.substring(1); 
            }    
            //proteção básica contra path traversal
            Path base = Paths.get(rootDir).toAbsolutePath().normalize();
            Path resolved = base.resolve(alvo).normalize();

            if(!resolved.startsWith(base)){
                return HttpResponse.of(HttpStatusCodes.NOT_FOUND);
            }
            try {
                if(Files.isDirectory(resolved)){ 
                    resolved = resolved.resolve("index.html");  
                }
                if(!Files.isRegularFile(resolved)){
                    return HttpResponse.of(HttpStatusCodes.NOT_FOUND);
                    
                    
                }
                byte[] body = Files.readAllBytes(resolved);
                String mime = Files.probeContentType(resolved);  
                
                if(mime == null){
                    return HttpResponse.of(HttpStatusCodes.BAD_REQUEST);
                }
                String nome = resolved.getFileName().toString();
                if(nome.endsWith(".html")){mime = "text/html";}
                else if(nome.endsWith(".css")){mime = "text/css";}
                else if(nome.endsWith(".js")){mime = "application/javascript";} 
                else if(nome.endsWith(".png")){mime = "image/png";}
                else if(nome.endsWith(".jpeg")){mime = "image/jpeg";}
                else mime = "application/octet-stream";

                HttpResponse res = builder.version(version)
                .status(HttpStatusCodes.OK)
                .headers("Content-Type", mime)
                .headers("Content-Length", String.valueOf(body.length))
                .body(body)
                .build();
                return res;  
                
                
            } catch (Exception e) {   
                return HttpResponse.of(HttpStatusCodes.BAD_REQUEST);
            } 
            
        }
        return HttpResponse.of(HttpStatusCodes.INTERNAL_SERVER_ERROR);
    }  
}
  
class ResponseHandlerFactory{ 
    public static ResponseHandler create(String method){

        switch (method.toUpperCase()) {
            case "GET":
                return new GetHandler();
            default:
                throw new IllegalArgumentException("Método inválido!");
        }        
    }
}


class HttpResponseHandler{    
    public HttpResponse handleRequest(HttpRequest req){
        ResponseHandler handler = ResponseHandlerFactory.create(String.valueOf(req.getMethod()));
        return handler.handle(req);
    }
}

final class HttpResponse{ 

    //linha de status
    private final String version;
    private final HttpStatusCodes status;
    //headers http  
    private final HashMap<String, String> headers;
    //corpo 
    private final byte[] message_body;
    public HttpResponse(ResponseBuilder builder){
        this.headers = new HashMap<>(builder.headers);
        this.version = builder.version;
        this.status = builder.status;
        this.message_body = builder.message_body;
    }

    public String getVersion(){return version;}
    public static ResponseBuilder builder(){
        return new ResponseBuilder();
    }
    public static class ResponseBuilder{
        private String version;
        private HttpStatusCodes status;
        //headers http  
        private HashMap<String, String> headers = new HashMap<>();
        //corpo 
        private byte[] message_body; 

        public ResponseBuilder version(String version){
            if(HttpValidation.validarVersao(version)){
                this.version = version;
                return this;
            } else{
                throw new IllegalArgumentException("Versão inválida!");
            }  
        }
        public ResponseBuilder status(HttpStatusCodes status){
            this.status = status;
            return this;
        }

        public ResponseBuilder headers(String chave, String valor){
            this.headers.put(chave, valor);
            return this;
        }
        public ResponseBuilder body(byte[] body){
            this.message_body = body;
            return this;
        }
        public HttpResponse build(){
            return new HttpResponse(this); 
        }
    }
    public void escreverResposta(OutputStream out) throws IOException{
        StringBuilder builder = new StringBuilder();
        //primeiro a request line   
        builder.append(this.version).append(" ").append(this.status.code()).append(" ")
        .append(this.status.phrase())
        .append("\r\n");
        headers.forEach((k, v) ->
            builder.append(k).append(":").append(" ").append(v).append("\r\n")
        );
        builder.append("\r\n"); 
        out.write(builder.toString().getBytes(StandardCharsets.UTF_8));
        if(message_body != null){
            out.write(message_body);
        }
        
    }
    public static HttpResponse of(HttpStatusCodes status){
        return builder()
        .version("HTTP1_1")
        .status(status)
        .build();
        
    }
}


public class server{
    public static void main(String[] args) {
        Server server = new Server(12345);   
        server.start();     

    }
}
