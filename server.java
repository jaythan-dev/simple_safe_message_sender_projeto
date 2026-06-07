import java.net.*;
import java.io.*; 

//contém os métodos relacionados ao servidor, como ligar, desligar, gerenciamento de portas 
class Server{
    ServerConnectionHandler serverConnectionHandler;
    public Server(int porta){
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
    ServerSocket server;    
    
    public ServerConnectionHandler(int porta){
        try {
            this.server = new ServerSocket(porta);    
            this.handler = new HttpRequestHandler(); 
        } catch (Exception e) {
            e.printStackTrace();   
        } 
        
    }
    public void start(){  
        servidor_ligado = true; 
        try {  
            while(servidor_ligado){   
                Socket conn = server.accept(); 
                Thread t = new Thread(new Runnable(){
                    @Override
                    public void run(){
                        handler.processRequest(conn);
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
class HttpRequest{
    String method;
    String path;
    String version;
    String headers;  
}
class HttpRequestHandler{
    public void processRequest(Socket request){
        try(
            BufferedReader br = new BufferedReader(new InputStreamReader(request.getInputStream()));  
        ) {     
            String linha = br.readLine();  
            System.out.println(linha);
        } catch (Exception e) {
            e.printStackTrace();   
        }
        
    }
}

public class server{
    public static void main(String[] args) {
        Server server = new Server(12345); 
        server.start();      
    }
}
